import { defineStore } from 'pinia'

export type Difficulty = 'easy' | 'medium' | 'hard' | 'insane'

interface Stats {
  best: number
  plays: number
  totalScore: number
}

interface State {
  sound: boolean
  haptics: boolean
  theme: 'neon' | 'dark' | 'glass'
  coins: number
  best: Record<string, number>
  plays: Record<string, number>
  totalScore: Record<string, number>
}

const KEY = 'games_store_v1'

function load(): State {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return { ...defaults(), ...JSON.parse(raw) }
  } catch {
    /* ignore */
  }
  return defaults()
}

function defaults(): State {
  return {
    sound: true,
    haptics: true,
    theme: 'neon',
    coins: 0,
    best: {},
    plays: {},
    totalScore: {},
  }
}

function persist(s: State) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* ignore */
  }
}

export const useGamesStore = defineStore('games', {
  state: (): State => load(),

  getters: {
    bestOf:
      (s) =>
      (id: string): number =>
        s.best[id] || 0,
    statsOf:
      (s) =>
      (id: string): Stats => ({
        best: s.best[id] || 0,
        plays: s.plays[id] || 0,
        totalScore: s.totalScore[id] || 0,
      }),
  },

  actions: {
    save() {
      persist(this.$state)
    },
    finishRun(id: string, score: number): { newBest: boolean } {
      const prev = this.best[id] || 0
      const newBest = score > prev
      if (newBest) this.best[id] = score
      this.plays[id] = (this.plays[id] || 0) + 1
      this.totalScore[id] = (this.totalScore[id] || 0) + score
      this.coins += Math.max(1, Math.floor(score / 5))
      this.save()
      return { newBest }
    },
    toggleSound() {
      this.sound = !this.sound
      this.save()
    },
    toggleHaptics() {
      this.haptics = !this.haptics
      this.save()
    },
    setTheme(t: 'neon' | 'dark' | 'glass') {
      this.theme = t
      this.save()
    },
    beep(kind: 'tick' | 'win' | 'lose' | 'tap' = 'tap') {
      if (!this.sound) return
      try {
        type AnyWin = Window & { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }
        const w = window as unknown as AnyWin
        const Ctor = w.AudioContext || w.webkitAudioContext
        if (!Ctor) return
        const ctx = new Ctor()
        const o = ctx.createOscillator()
        const g = ctx.createGain()
        o.connect(g)
        g.connect(ctx.destination)
        const map = { tick: 660, tap: 440, win: 880, lose: 200 }
        o.frequency.value = map[kind] || 440
        o.type = kind === 'lose' ? 'sawtooth' : 'sine'
        g.gain.setValueAtTime(0.0001, ctx.currentTime)
        g.gain.exponentialRampToValueAtTime(0.15, ctx.currentTime + 0.01)
        g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.18)
        o.start()
        o.stop(ctx.currentTime + 0.2)
        setTimeout(() => ctx.close(), 300)
      } catch {
        /* ignore */
      }
    },
    vibrate(ms = 30) {
      if (!this.haptics) return
      if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        try {
          navigator.vibrate(ms)
        } catch {
          /* ignore */
        }
      }
    },
  },
})
