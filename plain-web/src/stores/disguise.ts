import { defineStore } from 'pinia'
import { gqlFetch } from '@/lib/api/gql-client'
import { securityQAGQL } from '@/lib/api/query'
import { verifySecurityAnswerGQL, updateSecurityQAGQL } from '@/lib/api/mutation'

const SETUP_KEY = 'dg_setup'

const DEFAULT_QUESTION = "Tell your best friend's name and who I only know"

interface DisguiseState {
  unlocked: boolean
  setupDone: boolean
  question: string
  activeTab: 'games' | 'feedback'
  loaded: boolean
}

export const useDisguiseStore = defineStore('disguise', {
  state: (): DisguiseState => ({
    unlocked: false,
    setupDone: localStorage.getItem(SETUP_KEY) === '1',
    question: DEFAULT_QUESTION,
    activeTab: 'games',
    loaded: false,
  }),

  getters: {
    isFirstTime: (s) => !s.setupDone,
    currentQuestion: (s) => s.question || DEFAULT_QUESTION,
  },

  actions: {
    /**
     * Load the question + setup-state from the Android device. The actual
     * answer never leaves the phone; we only learn whether one exists.
     */
    async refreshFromServer() {
      try {
        const r = await gqlFetch<{ securityQA: { question: string; hasAnswer: boolean } }>(securityQAGQL)
        if (r?.data?.securityQA) {
          const q = r.data.securityQA.question || DEFAULT_QUESTION
          this.question = q
          if (r.data.securityQA.hasAnswer) {
            this.setupDone = true
            localStorage.setItem(SETUP_KEY, '1')
          }
          this.loaded = true
        }
      } catch {
        // offline / not connected — keep defaults
      }
    },

    async ensureFirstTimeAnswerStored() {
      // The Android side ships with a default answer already stored, so the
      // gate works on first run without any setup. We just sync the question.
      if (!this.loaded) await this.refreshFromServer()
      this.setupDone = true
      localStorage.setItem(SETUP_KEY, '1')
    },

    async tryUnlock(answer: string): Promise<boolean> {
      try {
        const r = await gqlFetch<{ verifySecurityAnswer: boolean }>(verifySecurityAnswerGQL, { answer })
        const ok = !!r?.data?.verifySecurityAnswer
        if (ok) {
          this.unlocked = true
          this.setupDone = true
          localStorage.setItem(SETUP_KEY, '1')
        }
        return ok
      } catch {
        return false
      }
    },

    lock() {
      this.unlocked = false
      this.activeTab = 'games'
    },

    setTab(t: 'games' | 'feedback') {
      this.activeTab = t
    },

    async updateSecurity(currentAnswer: string, newQuestion: string, newAnswer: string): Promise<boolean> {
      try {
        const r = await gqlFetch<{ updateSecurityQA: boolean }>(updateSecurityQAGQL, {
          currentAnswer,
          newQuestion: (newQuestion || '').trim(),
          newAnswer: (newAnswer || '').trim(),
        })
        if (!r?.data?.updateSecurityQA) return false
        this.question = (newQuestion || '').trim() || this.question
        this.setupDone = true
        this.unlocked = true
        localStorage.setItem(SETUP_KEY, '1')
        return true
      } catch {
        return false
      }
    },
  },
})
