import { getApiBaseUrl, getApiHeaders } from './api'
import { chachaEncrypt, chachaDecrypt, arrayBufferToBitArray, bitArrayToUint8Array } from './crypto'
import { tokenToKey } from './file'
import { wrapWithReplayProtection } from './time-sync'

const TIMEOUT = 30000

/**
 * Universal sanitizer for outgoing GraphQL variables.
 *  - undefined  -> dropped
 *  - null       -> kept (nullable schema fields still work)
 *  - strings    -> kept VERBATIM (must NOT trim: WebRTC SDP/ICE payloads
 *                  rely on exact `\r\n` line endings — trimming breaks the
 *                  remote SDP parser and the camera/mic stream never starts)
 *  - arrays     -> recursively sanitized
 *  - objects    -> recursively sanitized
 * Never throws.
 */
export function sanitizeGqlInput<T = any>(value: T): T {
  if (value === undefined) return undefined as any
  if (value === null) return null as any
  if (typeof value === 'string') return value as any
  if (Array.isArray(value)) {
    return value
      .filter((v) => v !== undefined)
      .map((v) => sanitizeGqlInput(v)) as any
  }
  if (typeof value === 'object') {
    const out: any = {}
    for (const k of Object.keys(value as any)) {
      const v = (value as any)[k]
      if (v === undefined) continue
      out[k] = sanitizeGqlInput(v)
    }
    return out
  }
  return value
}

export interface GqlResult<T = any> {
  data: T
  errors?: Array<{ message: string; path?: string[] }>
}

// Deduplicate concurrent identical requests (same query + variables).
// If an identical request is already in-flight, callers share the same promise.
const pendingRequests = new Map<string, Promise<GqlResult<any>>>()

export async function gqlFetch<T = any>(query: string, variables?: Record<string, any>): Promise<GqlResult<T>> {
  const cleanVars = variables ? sanitizeGqlInput(variables) : variables
  const dedupeKey = JSON.stringify({ query, variables: cleanVars })
  const pending = pendingRequests.get(dedupeKey)
  if (pending) return pending as Promise<GqlResult<T>>

  const promise = doGqlFetch<T>(query, cleanVars)
  pendingRequests.set(dedupeKey, promise)
  try {
    return await promise
  } finally {
    pendingRequests.delete(dedupeKey)
  }
}

async function doGqlFetch<T = any>(query: string, variables?: Record<string, any>): Promise<GqlResult<T>> {
  const url = `${getApiBaseUrl()}/graphql`
  const token = localStorage.getItem('auth_token') ?? ''
  const key = tokenToKey(token)

  const json = JSON.stringify({ query, variables })
  console.info(`[request] ${json}`)

  const startTime = performance.now()
  const payload = wrapWithReplayProtection(json)
  const body = bitArrayToUint8Array(chachaEncrypt(key, payload))
  const encryptTime = performance.now()

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT)

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { ...getApiHeaders() },
      body,
      signal: controller.signal,
    })

    if (response.status === 401) {
      localStorage.removeItem('auth_token')
      window.location.reload()
      throw new GqlError('unauthorized', 401)
    }
    if (response.status === 403) {
      throw new GqlError('web_access_disabled', 403)
    }

    const arrayBuffer = await response.arrayBuffer()
    const apiEndTime = performance.now()
    const text = chachaDecrypt(key, arrayBufferToBitArray(arrayBuffer))
    const decryptEndTime = performance.now()

    console.info(`[response] ${text}`)
    console.info(`[time] encrypt: ${encryptTime - startTime}ms, api: ${apiEndTime - encryptTime}ms, decrypt: ${decryptEndTime - apiEndTime}ms`)

    return JSON.parse(text)
  } catch (e: any) {
    if (e instanceof GqlError) throw e
    if (e.name === 'AbortError') throw new GqlError('connection_timeout')
    throw new GqlError(e.message || 'network_error')
  } finally {
    clearTimeout(timer)
  }
}

export class GqlError extends Error {
  constructor(
    message: string,
    public status?: number,
  ) {
    super(message)
    this.name = 'GqlError'
  }
}
