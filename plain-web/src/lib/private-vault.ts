const VERSION = 'v1'
const PBKDF2_ITERATIONS = 200_000
const SALT_LEN = 16
const IV_LEN = 12

function toB64(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf)
  let s = ''
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i])
  return btoa(s)
}

function fromB64(s: string): Uint8Array {
  const bin = atob(s)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

export function generateSalt(): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(SALT_LEN))
}

export async function deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const enc = new TextEncoder()
  const baseKey = await crypto.subtle.importKey(
    'raw', enc.encode(passphrase), 'PBKDF2', false, ['deriveKey']
  )
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  )
}

export async function encryptJson(key: CryptoKey, salt: Uint8Array, payload: unknown): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(IV_LEN))
  const data = new TextEncoder().encode(JSON.stringify(payload))
  const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, data)
  return `${VERSION}.${toB64(salt)}.${toB64(iv)}.${toB64(ct)}`
}

export interface ParsedBlob {
  version: string
  salt: Uint8Array
  iv: Uint8Array
  ciphertext: Uint8Array
}

export function parseBlob(blob: string): ParsedBlob | null {
  const parts = blob.split('.')
  if (parts.length !== 4 || parts[0] !== VERSION) return null
  try {
    return {
      version: parts[0],
      salt: fromB64(parts[1]),
      iv: fromB64(parts[2]),
      ciphertext: fromB64(parts[3]),
    }
  } catch {
    return null
  }
}

export async function decryptJson<T = unknown>(key: CryptoKey, blob: string): Promise<T> {
  const parsed = parseBlob(blob)
  if (!parsed) throw new Error('vault_invalid_blob')
  const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: parsed.iv }, key, parsed.ciphertext)
  return JSON.parse(new TextDecoder().decode(pt)) as T
}

export function readSaltFromBlob(blob: string): Uint8Array | null {
  return parseBlob(blob)?.salt ?? null
}
