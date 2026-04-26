import { getApiBaseUrl } from './api'
import { chachaEncrypt, bitArrayToUint8Array } from './crypto'
import { tokenToKey } from './file'

export type LiveCaptureSource = 'camera' | 'mic'
export type LiveCaptureKind = 'photo' | 'video' | 'audio'

export interface UploadedCapture {
  filename: string
}

/** Capture record returned by the `liveCaptures` GraphQL query. */
export interface ServerLiveCapture {
  id: string
  filename: string
  source: LiveCaptureSource
  kind: LiveCaptureKind
  mimeType: string
  createdAt: number
  durationMs: number
  sizeBytes: number
  fileId: string
}

/**
 * Upload a single capture (photo / video / audio) blob to the phone so it
 * is stored permanently in the app's private LiveCaptures directory and
 * survives page reloads, service restarts, and re-pairings.
 *
 * Authentication mirrors `lib/upload/upload.ts`:
 *   - `c-id` header carries the client id
 *   - `info` form part is a chacha20-encrypted JSON describing the capture
 *   - `file` form part carries the binary blob.
 */
export function uploadLiveCapture(
  blob: Blob,
  opts: {
    source: LiveCaptureSource
    kind: LiveCaptureKind
    mimeType?: string
    durationMs?: number
  }
): Promise<UploadedCapture> {
  const token = localStorage.getItem('auth_token') ?? ''
  const key = tokenToKey(token)
  const data = new FormData()
  const info = JSON.stringify({
    source: opts.source,
    kind: opts.kind,
    mimeType: opts.mimeType ?? blob.type ?? '',
    durationMs: Math.max(0, Math.round(opts.durationMs ?? 0)),
  })
  const enc = bitArrayToUint8Array(chachaEncrypt(key, info))
  data.append('info', new Blob([enc]))
  data.append('file', blob, 'capture')
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.onreadystatechange = () => {
      if (xhr.readyState === 4) {
        if (xhr.status === 201) {
          resolve({ filename: xhr.responseText })
        } else {
          reject(new Error(xhr.responseText || `upload failed (${xhr.status})`))
        }
      }
    }
    xhr.onerror = () => reject(new Error('network_error'))
    xhr.onabort = () => reject(new Error('aborted'))
    try {
      xhr.open('POST', `${getApiBaseUrl()}/live_capture_upload`, true)
      xhr.setRequestHeader('c-id', localStorage.getItem('client_id') ?? '')
      xhr.send(data)
    } catch (e: any) {
      reject(e)
    }
  })
}
