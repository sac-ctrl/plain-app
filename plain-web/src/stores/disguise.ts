import { defineStore } from 'pinia'

const Q_KEY = 'dg_question'
const A_KEY = 'dg_answer_hash'
const SETUP_KEY = 'dg_setup'

const DEFAULT_QUESTION = "Tell your best friend's name and who I only know"
const DEFAULT_ANSWER = 'Nitish Kumar'

function normalize(s: string): string {
  return (s || '').trim().toLowerCase().replace(/\s+/g, ' ')
}

async function sha256Hex(s: string): Promise<string> {
  const data = new TextEncoder().encode(s)
  const buf = await crypto.subtle.digest('SHA-256', data)
  const bytes = new Uint8Array(buf)
  let out = ''
  for (let i = 0; i < bytes.length; i++) out += bytes[i].toString(16).padStart(2, '0')
  return out
}

interface DisguiseState {
  unlocked: boolean
  setupDone: boolean
  question: string
  activeTab: 'games' | 'feedback'
}

export const useDisguiseStore = defineStore('disguise', {
  state: (): DisguiseState => ({
    unlocked: false,
    setupDone: localStorage.getItem(SETUP_KEY) === '1',
    question: localStorage.getItem(Q_KEY) || DEFAULT_QUESTION,
    activeTab: 'games',
  }),

  getters: {
    isFirstTime: (s) => !s.setupDone,
    currentQuestion: (s) => s.question || DEFAULT_QUESTION,
  },

  actions: {
    async ensureFirstTimeAnswerStored() {
      if (this.setupDone) return
      const hash = await sha256Hex(normalize(DEFAULT_ANSWER))
      localStorage.setItem(Q_KEY, DEFAULT_QUESTION)
      localStorage.setItem(A_KEY, hash)
      localStorage.setItem(SETUP_KEY, '1')
      this.question = DEFAULT_QUESTION
      this.setupDone = true
    },

    async tryUnlock(answer: string): Promise<boolean> {
      await this.ensureFirstTimeAnswerStored()
      const stored = localStorage.getItem(A_KEY) || ''
      const got = await sha256Hex(normalize(answer))
      if (got === stored) {
        this.unlocked = true
        return true
      }
      return false
    },

    lock() {
      this.unlocked = false
      this.activeTab = 'games'
    },

    setTab(t: 'games' | 'feedback') {
      this.activeTab = t
    },

    async updateSecurity(currentAnswer: string, newQuestion: string, newAnswer: string): Promise<boolean> {
      const ok = await this.tryUnlock(currentAnswer)
      if (!ok) return false
      const q = (newQuestion || '').trim() || DEFAULT_QUESTION
      const hash = await sha256Hex(normalize(newAnswer))
      localStorage.setItem(Q_KEY, q)
      localStorage.setItem(A_KEY, hash)
      localStorage.setItem(SETUP_KEY, '1')
      this.question = q
      this.setupDone = true
      this.unlocked = true
      return true
    },
  },
})
