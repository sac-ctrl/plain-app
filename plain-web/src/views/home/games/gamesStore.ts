import { defineStore } from 'pinia'

export type Difficulty = 'easy' | 'medium' | 'hard' | 'insane'
export type GameMode = 'classic' | 'time' | 'survival' | 'challenge'
export type ThemeName = 'neon' | 'dark' | 'glass' | 'sunset'

export interface LbEntry {
  score: number
  date: number
  mode: GameMode
  difficulty: Difficulty
}

export interface DailyChallenge {
  id: string
  gameId: string
  title: string
  target: number
  metric: 'score' | 'plays'
  done: boolean
  reward: number
}

interface State {
  sound: boolean
  haptics: boolean
  theme: ThemeName
  coins: number
  best: Record<string, number>
  plays: Record<string, number>
  totalScore: Record<string, number>
  totalTime: Record<string, number>
  lb: Record<string, LbEntry[]>
  lastPlayed: string | null
  lastPlayedAt: number
  ghosts: Record<string, unknown>
  daily: { date: string; items: DailyChallenge[] }
  streak: number
  streakDate: string
}

const KEY = 'games_store_v2'

function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
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
    totalTime: {},
    lb: {},
    lastPlayed: null,
    lastPlayedAt: 0,
    ghosts: {},
    daily: { date: '', items: [] },
    streak: 0,
    streakDate: '',
  }
}

function load(): State {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return { ...defaults(), ...JSON.parse(raw) }
    // migrate v1 if present
    const old = localStorage.getItem('games_store_v1')
    if (old) {
      const o = JSON.parse(old)
      return { ...defaults(), ...o }
    }
  } catch {
    /* ignore */
  }
  return defaults()
}

function persist(s: State) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* ignore */
  }
}

const DAILY_POOL: { gameId: string; title: string; target: number; metric: 'score' | 'plays'; reward: number }[] = [
  { gameId: 'snake', title: 'Score 80 in Snake Neon', target: 80, metric: 'score', reward: 30 },
  { gameId: 'flappy', title: 'Score 12 in Flappy Bird', target: 12, metric: 'score', reward: 30 },
  { gameId: '2048', title: 'Reach 400 in 2048', target: 400, metric: 'score', reward: 30 },
  { gameId: 'cardodge', title: 'Score 60 in Car Dodge', target: 60, metric: 'score', reward: 30 },
  { gameId: 'reaction', title: 'Play 3 reaction rounds', target: 3, metric: 'plays', reward: 25 },
  { gameId: 'memory', title: 'Win Memory Flip', target: 200, metric: 'score', reward: 25 },
  { gameId: 'shooter', title: 'Score 200 in Space Shooter', target: 200, metric: 'score', reward: 35 },
  { gameId: 'sliding', title: 'Win Sliding Puzzle', target: 1, metric: 'plays', reward: 25 },
  { gameId: 'dice', title: 'Win a Dice Battle', target: 50, metric: 'score', reward: 30 },
  { gameId: 'brick', title: 'Score 200 in Brick Breaker', target: 200, metric: 'score', reward: 30 },
  { gameId: 'aim', title: 'Hit 20 in Aim Trainer', target: 200, metric: 'score', reward: 25 },
  { gameId: 'runner', title: 'Score 80 in Endless Runner', target: 80, metric: 'score', reward: 30 },
  { gameId: 'colorswitch', title: 'Score 60 in Color Switch', target: 60, metric: 'score', reward: 30 },
  { gameId: 'math', title: 'Get 10-streak in Quick Math', target: 75, metric: 'score', reward: 25 },
  { gameId: 'tap', title: 'Reach length 8 in Tap Pattern', target: 80, metric: 'score', reward: 25 },
]

function pickDaily(): DailyChallenge[] {
  const arr = DAILY_POOL.slice().sort(() => Math.random() - 0.5).slice(0, 3)
  return arr.map((it, i) => ({
    id: `${todayStr()}-${i}`,
    gameId: it.gameId,
    title: it.title,
    target: it.target,
    metric: it.metric,
    reward: it.reward,
    done: false,
  }))
}

export const useGamesStore = defineStore('games', {
  state: (): State => load(),

  getters: {
    bestOf:
      (s) =>
      (id: string): number =>
        s.best[id] || 0,
    statsOf: (s) => (id: string) => ({
      best: s.best[id] || 0,
      plays: s.plays[id] || 0,
      totalScore: s.totalScore[id] || 0,
      avg: s.plays[id] ? Math.round((s.totalScore[id] || 0) / s.plays[id]) : 0,
      totalTime: s.totalTime[id] || 0,
    }),
    leaderboardOf:
      (s) =>
      (id: string): LbEntry[] =>
        (s.lb[id] || []).slice().sort((a, b) => b.score - a.score).slice(0, 10),
    todayChallenges(): DailyChallenge[] {
      const t = todayStr()
      if (this.daily.date !== t) {
        this.daily = { date: t, items: pickDaily() }
        persist(this.$state)
      }
      return this.daily.items
    },
  },

  actions: {
    save() {
      persist(this.$state)
    },
    finishRun(
      id: string,
      score: number,
      mode: GameMode = 'classic',
      difficulty: Difficulty = 'medium',
      timeMs = 0
    ): { newBest: boolean; rank: number; rewardEarned: number } {
      const prev = this.best[id] || 0
      const newBest = score > prev
      if (newBest) this.best[id] = score
      this.plays[id] = (this.plays[id] || 0) + 1
      this.totalScore[id] = (this.totalScore[id] || 0) + score
      this.totalTime[id] = (this.totalTime[id] || 0) + Math.round(timeMs / 1000)
      const earned = Math.max(1, Math.floor(score / 5))
      this.coins += earned
      // leaderboard
      const list = this.lb[id] || []
      list.push({ score, date: Date.now(), mode, difficulty })
      list.sort((a, b) => b.score - a.score)
      this.lb[id] = list.slice(0, 10)
      const rank = this.lb[id].findIndex((e) => e.score === score && e.date === list[list.length - 1].date) + 1
      this.lastPlayed = id
      this.lastPlayedAt = Date.now()
      // daily challenges
      let rewardEarned = 0
      this.todayChallenges.forEach((c) => {
        if (c.done) return
        if (c.gameId !== id) return
        if (c.metric === 'plays') {
          c.target -= 1
          if (c.target <= 0) {
            c.done = true
            rewardEarned += c.reward
          }
        } else {
          if (score >= c.target) {
            c.done = true
            rewardEarned += c.reward
          }
        }
      })
      this.coins += rewardEarned
      // streak
      const t = todayStr()
      if (this.streakDate !== t) {
        const yesterday = new Date(); yesterday.setDate(yesterday.getDate() - 1)
        const yStr = `${yesterday.getFullYear()}-${yesterday.getMonth() + 1}-${yesterday.getDate()}`
        this.streak = this.streakDate === yStr ? this.streak + 1 : 1
        this.streakDate = t
      }
      this.save()
      return { newBest, rank, rewardEarned }
    },
    saveGhost(id: string, data: unknown) {
      this.ghosts[id] = data
      this.save()
    },
    getGhost<T = unknown>(id: string): T | null {
      return (this.ghosts[id] as T) || null
    },
    toggleSound() {
      this.sound = !this.sound
      this.save()
    },
    toggleHaptics() {
      this.haptics = !this.haptics
      this.save()
    },
    setTheme(t: ThemeName) {
      this.theme = t
      this.save()
    },
    addCoins(n: number) {
      this.coins += n
      this.save()
    },
    beep(kind: 'tick' | 'win' | 'lose' | 'tap' | 'power' = 'tap') {
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
        const map: Record<string, number> = { tick: 660, tap: 440, win: 880, lose: 200, power: 1100 }
        o.frequency.value = map[kind] || 440
        o.type = kind === 'lose' ? 'sawtooth' : kind === 'power' ? 'square' : 'sine'
        g.gain.setValueAtTime(0.0001, ctx.currentTime)
        g.gain.exponentialRampToValueAtTime(0.16, ctx.currentTime + 0.01)
        g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.22)
        o.start()
        o.stop(ctx.currentTime + 0.24)
        setTimeout(() => ctx.close(), 320)
      } catch {
        /* ignore */
      }
    },
    vibrate(ms: number | number[] = 30) {
      if (!this.haptics) return
      if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        try {
          navigator.vibrate(ms as number)
        } catch {
          /* ignore */
        }
      }
    },
  },
})

export const fakeLeaderboardNames = [
  'NeonFox', 'PixelNinja', 'DragonByte', 'AceShadow', 'MysticOwl', 'CrimsonHawk', 'IronWolf', 'EchoTiger',
  'SilverFox', 'StormRider', 'BlazeKnight', 'CyberSage', 'FrostMage', 'LunarRogue', 'NovaWitch',
]
