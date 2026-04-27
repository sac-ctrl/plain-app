import { defineStore } from 'pinia'
import { deriveKey, encryptJson, decryptJson, generateSalt, readSaltFromBlob } from '@/lib/private-vault'

const SALT_KEY = 'pv_salt'
const CHECK_KEY = 'pv_check'
const CHECK_PLAINTEXT = 'plain-private-vault-check'

interface VaultState {
  unlocked: boolean
  hasVault: boolean
}

let cachedKey: CryptoKey | null = null
let cachedSalt: Uint8Array | null = null

function loadSalt(): Uint8Array | null {
  const v = localStorage.getItem(SALT_KEY)
  if (!v) return null
  try {
    const bin = atob(v)
    const out = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
    return out
  } catch {
    return null
  }
}

function saveSalt(salt: Uint8Array) {
  let s = ''
  for (let i = 0; i < salt.length; i++) s += String.fromCharCode(salt[i])
  localStorage.setItem(SALT_KEY, btoa(s))
}

export const useVaultStore = defineStore('vault', {
  state: (): VaultState => ({
    unlocked: false,
    hasVault: !!localStorage.getItem(CHECK_KEY) || !!localStorage.getItem(SALT_KEY),
  }),
  actions: {
    async setupNew(passphrase: string) {
      const salt = generateSalt()
      const key = await deriveKey(passphrase, salt)
      const check = await encryptJson(key, salt, CHECK_PLAINTEXT)
      saveSalt(salt)
      localStorage.setItem(CHECK_KEY, check)
      cachedKey = key
      cachedSalt = salt
      this.unlocked = true
      this.hasVault = true
    },
    async unlock(passphrase: string, blobForSalt?: string | null): Promise<boolean> {
      let salt = loadSalt()
      if (!salt && blobForSalt) {
        salt = readSaltFromBlob(blobForSalt)
        if (salt) saveSalt(salt)
      }
      if (!salt) return false
      const key = await deriveKey(passphrase, salt)
      const check = localStorage.getItem(CHECK_KEY)
      if (check) {
        try {
          const v = await decryptJson<string>(key, check)
          if (v !== CHECK_PLAINTEXT) return false
        } catch {
          return false
        }
      } else if (blobForSalt) {
        try {
          await decryptJson(key, blobForSalt)
        } catch {
          return false
        }
        const newCheck = await encryptJson(key, salt, CHECK_PLAINTEXT)
        localStorage.setItem(CHECK_KEY, newCheck)
      } else {
        const newCheck = await encryptJson(key, salt, CHECK_PLAINTEXT)
        localStorage.setItem(CHECK_KEY, newCheck)
      }
      cachedKey = key
      cachedSalt = salt
      this.unlocked = true
      this.hasVault = true
      return true
    },
    lock() {
      cachedKey = null
      cachedSalt = null
      this.unlocked = false
    },
    async encrypt(payload: { title: string; content: string }): Promise<string> {
      if (!cachedKey || !cachedSalt) throw new Error('vault_locked')
      return encryptJson(cachedKey, cachedSalt, payload)
    },
    async decrypt(blob: string): Promise<{ title: string; content: string } | null> {
      if (!cachedKey) return null
      try {
        return await decryptJson<{ title: string; content: string }>(cachedKey, blob)
      } catch {
        return null
      }
    },
    forgetVault() {
      localStorage.removeItem(SALT_KEY)
      localStorage.removeItem(CHECK_KEY)
      cachedKey = null
      cachedSalt = null
      this.unlocked = false
      this.hasVault = false
    },
  },
})
