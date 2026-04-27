<template>
  <div class="hm-wrap" :class="theme">
    <div class="hm-top">
      <div class="hm-title">
        <div class="hm-tag" :style="{ color: accent }">HOLLOW MINDS</div>
        <div class="hm-sub">Crack the cipher · {{ codeLen }} pegs · {{ palSize }} colors</div>
      </div>
      <div class="hm-stats">
        <div class="hm-att">Attempts {{ attemptsLeft }}</div>
        <div v-if="mode === 'blitz'" class="hm-time" :class="{ low: time < 15 }">⏱ {{ time }}s</div>
        <div v-else-if="mode === 'daily'" class="hm-daily">Daily · {{ today }}</div>
        <div v-else class="hm-mode">Mode · {{ mode }}</div>
      </div>
    </div>

    <div class="hm-powers">
      <button class="hm-chip" :disabled="!alive || powers.reveal <= 0" @click="useReveal">
        👁 Reveal · {{ powers.reveal }}
      </button>
      <button class="hm-chip" :disabled="!alive || powers.eliminate <= 0" @click="useEliminate">
        ✖ Eliminate · {{ powers.eliminate }}
      </button>
      <button class="hm-chip" :disabled="!alive || powers.undo <= 0 || !guesses.length" @click="useUndo">
        ↶ Undo · {{ powers.undo }}
      </button>
      <button class="hm-chip ghost" @click="showSettings = !showSettings">⚙ Settings</button>
    </div>

    <div v-if="showSettings" class="hm-settings">
      <label>
        Theme
        <select v-model="settings.theme" @change="persist">
          <option value="neon">Neon</option>
          <option value="candy">Candy</option>
          <option value="mono">Mono</option>
          <option value="nature">Nature</option>
        </select>
      </label>
      <label>
        Mode
        <select v-model="settings.mode" @change="restartFromSettings">
          <option value="classic">Classic</option>
          <option value="daily">Daily seed</option>
          <option value="blitz">Blitz (90s)</option>
          <option value="endless">Endless</option>
        </select>
      </label>
      <label>
        Code length
        <select v-model.number="settings.codeLen" @change="restartFromSettings">
          <option :value="3">3</option>
          <option :value="4">4</option>
          <option :value="5">5</option>
          <option :value="6">6</option>
        </select>
      </label>
      <label>
        Colors
        <select v-model.number="settings.paletteSize" @change="restartFromSettings">
          <option :value="4">4</option>
          <option :value="6">6</option>
          <option :value="7">7</option>
          <option :value="8">8</option>
        </select>
      </label>
      <label>
        Attempts
        <select v-model.number="settings.maxAttempts" @change="restartFromSettings">
          <option :value="6">6</option>
          <option :value="8">8</option>
          <option :value="10">10</option>
          <option :value="12">12</option>
        </select>
      </label>
      <label class="cb"><input type="checkbox" v-model="settings.allowDup" @change="restartFromSettings" /> Allow duplicates</label>
      <label class="cb"><input type="checkbox" v-model="settings.showColorblind" @change="persist" /> Colorblind glyphs</label>
      <label class="cb"><input type="checkbox" v-model="settings.sound" @change="persist" /> Sound</label>
      <label class="cb"><input type="checkbox" v-model="settings.haptics" @change="persist" /> Haptics</label>
      <div class="hm-statline">
        Best score: <b>{{ settings.bestScore }}</b> · Wins: {{ settings.wins }} · Losses: {{ settings.losses }}
        <span v-if="settings.bestAttempts > 0"> · Fewest guesses: {{ settings.bestAttempts }}</span>
      </div>
    </div>

    <div class="hm-board">
      <div v-for="(g, i) in guesses" :key="'g'+i" class="hm-row">
        <span class="hm-num">{{ i + 1 }}</span>
        <div class="hm-pegs">
          <span v-for="(p, j) in g.pegs" :key="j" class="hm-peg" :style="{ background: palette[p] }">
            <span v-if="settings.showColorblind" class="hm-glyph">{{ glyphs[p % glyphs.length] }}</span>
          </span>
        </div>
        <div class="hm-fb">
          <span v-for="b in g.black" :key="'b'+b" class="hm-fb-peg black" />
          <span v-for="w in g.white" :key="'w'+w" class="hm-fb-peg white" />
        </div>
      </div>
      <div v-for="i in (effAttempts - guesses.length)" :key="'p'+i" class="hm-row placeholder">
        <span class="hm-num">{{ guesses.length + i }}</span>
        <div class="hm-pegs">
          <span v-for="j in codeLen" :key="j" class="hm-peg empty" />
        </div>
      </div>
    </div>

    <div class="hm-current">
      <span class="hm-label">Guess →</span>
      <span v-for="(c, i) in current" :key="i" class="hm-peg slot"
            :style="{ background: c == null ? 'rgba(255,255,255,0.08)' : palette[c] }"
            @click="clearSlot(i)">
        <span v-if="settings.showColorblind && c != null" class="hm-glyph">{{ glyphs[c % glyphs.length] }}</span>
      </span>
      <button class="hm-submit" :disabled="!ready || !alive" @click="submit">Submit</button>
    </div>

    <div class="hm-palette">
      <button v-for="c in palSize" :key="c" class="hm-color"
              :class="{ out: hintMask.has(c - 1) }"
              :style="{ background: palette[c - 1] }"
              :disabled="hintMask.has(c - 1) || !alive"
              @click="pick(c - 1)">
        <span v-if="settings.showColorblind" class="hm-glyph">{{ glyphs[(c - 1) % glyphs.length] }}</span>
      </button>
    </div>

    <div v-if="!alive" class="hm-end" :class="{ won }">
      <div v-if="won">✓ Cracked in {{ guesses.length }} · +{{ scoreEarned }}</div>
      <div v-else>
        <div>✗ Code was:</div>
        <div class="hm-pegs reveal">
          <span v-for="(p, i) in secret" :key="i" class="hm-peg" :style="{ background: palette[p] }" />
        </div>
      </div>
      <button class="hm-chip" @click="newRound">{{ mode === 'endless' ? 'Next round' : 'Play again' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()

interface HollowSettings {
  mode: 'classic' | 'daily' | 'blitz' | 'endless'
  codeLen: number
  paletteSize: number
  maxAttempts: number
  allowDup: boolean
  theme: 'neon' | 'candy' | 'mono' | 'nature'
  sound: boolean
  haptics: boolean
  showColorblind: boolean
  bestScore: number
  bestAttempts: number
  wins: number
  losses: number
  lastDailyDate: string
  lastPowerRefresh: string
}

const STORAGE_KEY = 'plain.hollowminds.v1'
const POWER_KEY = 'plain.hollowminds.powers.v1'

const defaults: HollowSettings = {
  mode: 'classic', codeLen: 4, paletteSize: 6, maxAttempts: 10, allowDup: true,
  theme: 'neon', sound: true, haptics: true, showColorblind: false,
  bestScore: 0, bestAttempts: 0, wins: 0, losses: 0,
  lastDailyDate: '', lastPowerRefresh: '',
}

const settings = reactive<HollowSettings>({ ...defaults })
try { Object.assign(settings, JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')) } catch {}
const powers = reactive({ reveal: 2, eliminate: 2, undo: 1 })
try { Object.assign(powers, JSON.parse(localStorage.getItem(POWER_KEY) || '{}')) } catch {}
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}
const today = todayStr()
if (settings.lastPowerRefresh !== today) {
  powers.reveal = Math.max(2, powers.reveal)
  powers.eliminate = Math.max(2, powers.eliminate)
  powers.undo = Math.max(1, powers.undo)
  settings.lastPowerRefresh = today
}

function persist() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(settings)) } catch {}
  try { localStorage.setItem(POWER_KEY, JSON.stringify(powers)) } catch {}
}

const palettes: Record<string, string[]> = {
  neon:   ['#FF3B6E','#FFD166','#38FFB1','#38BDF8','#A855F7','#F97316','#FF66E0','#B8FF66'],
  candy:  ['#FF7AA8','#FFC371','#FFE76A','#7BE495','#6FC3FF','#B28DFF','#FF9CD7','#CFF09E'],
  mono:   ['#E8E8E8','#C0C0C0','#909090','#606060','#404040','#B0B0FF','#FFB0B0','#B0FFB0'],
  nature: ['#8FBC8F','#4682B4','#CD853F','#DC143C','#FFD700','#9370DB','#20B2AA','#FF7F50'],
}
const glyphs = ['●','■','▲','◆','★','♥','♣','♠']

const palette = computed(() => palettes[settings.theme] || palettes.neon)
const theme = computed(() => `theme-${settings.theme}`)
const accent = computed(() => palette.value[2])

// Difficulty maps to defaults but settings can override
const diff = computed(() => props.difficulty)
const codeLen = computed(() => {
  if (diff.value === 'easy') return 4
  if (diff.value === 'hard') return 5
  if (diff.value === 'insane') return 6
  return settings.codeLen
})
const palSize = computed(() => {
  if (diff.value === 'easy') return 6
  if (diff.value === 'hard') return 7
  if (diff.value === 'insane') return 8
  return settings.paletteSize
})
const effAttempts = computed(() => {
  if (diff.value === 'easy') return 12
  if (diff.value === 'hard') return 10
  if (diff.value === 'insane') return 8
  return settings.maxAttempts
})

const mode = computed(() => settings.mode)

function seedFromDate(): number {
  let h = 2166136261 >>> 0
  const s = todayStr()
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619) >>> 0
  }
  return h
}
function makeRng(seed: number) {
  let s = seed || 1
  return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return ((s >>> 0) % 1_000_000) / 1_000_000 }
}

function makeCode(): number[] {
  const rnd = mode.value === 'daily' ? makeRng(seedFromDate()) : Math.random
  const out: number[] = []
  if (settings.allowDup) {
    while (out.length < codeLen.value) out.push(Math.floor(rnd() * palSize.value))
  } else {
    const pool = Array.from({ length: palSize.value }, (_, i) => i)
    while (out.length < codeLen.value && pool.length > 0) {
      const i = Math.floor(rnd() * pool.length)
      out.push(pool.splice(i, 1)[0])
    }
  }
  return out
}

const secret = ref<number[]>([])
const current = ref<(number | null)[]>([])
const guesses = ref<{ pegs: number[]; black: number; white: number }[]>([])
const alive = ref(true)
const won = ref(false)
const time = ref(0)
const scoreEarned = ref(0)
const hintMask = ref(new Set<number>())
const showSettings = ref(false)
let timer = 0

const attemptsLeft = computed(() => effAttempts.value - guesses.value.length)
const ready = computed(() => current.value.every((c) => c != null))

function newRound() {
  secret.value = makeCode()
  current.value = Array(codeLen.value).fill(null)
  guesses.value = []
  alive.value = true
  won.value = false
  scoreEarned.value = 0
  hintMask.value = new Set()
  if (mode.value === 'blitz') {
    time.value = 90
    clearInterval(timer)
    timer = window.setInterval(() => {
      if (!props.running || !alive.value) return
      time.value -= 1
      if (time.value <= 0) end(false)
    }, 1000)
  } else {
    clearInterval(timer)
    time.value = 0
  }
  props.onScore(0)
}
function restartFromSettings() {
  persist()
  newRound()
}

function pick(ci: number) {
  if (!alive.value) return
  const i = current.value.findIndex((c) => c == null)
  if (i < 0) return
  const next = current.value.slice() as (number | null)[]
  next[i] = ci
  current.value = next
  if (settings.haptics && navigator.vibrate) navigator.vibrate(10)
  store.beep('tick')
}
function clearSlot(i: number) {
  if (!alive.value) return
  const next = current.value.slice() as (number | null)[]
  next[i] = null
  current.value = next
}

function score(secretArr: number[], guess: number[]) {
  let black = 0
  const sLeft: number[] = []
  const gLeft: number[] = []
  for (let i = 0; i < secretArr.length; i++) {
    if (guess[i] === secretArr[i]) black++
    else { sLeft.push(secretArr[i]); gLeft.push(guess[i]) }
  }
  let white = 0
  const pool = sLeft.slice()
  for (const g of gLeft) {
    const idx = pool.indexOf(g)
    if (idx >= 0) { white++; pool.splice(idx, 1) }
  }
  return { black, white }
}

function submit() {
  const g = current.value.filter((c): c is number => c != null)
  if (g.length !== codeLen.value) return
  const { black, white } = score(secret.value, g)
  guesses.value = [...guesses.value, { pegs: g, black, white }]
  current.value = Array(codeLen.value).fill(null)
  if (settings.haptics && navigator.vibrate) navigator.vibrate(20)
  if (black === codeLen.value) end(true)
  else if (guesses.value.length >= effAttempts.value) end(false)
}

function end(winFlag: boolean) {
  alive.value = false
  won.value = winFlag
  clearInterval(timer)
  if (winFlag) {
    const left = effAttempts.value - guesses.value.length
    const multi = diff.value === 'insane' ? 3 : diff.value === 'hard' ? 2 : 1
    const tBonus = mode.value === 'blitz' ? time.value * 5 : 0
    const s = (200 + left * 60 + tBonus) * multi
    scoreEarned.value = s
    props.onScore((p) => p + s)
    settings.wins++
    if (s > settings.bestScore) settings.bestScore = s
    if (settings.bestAttempts === 0 || guesses.value.length < settings.bestAttempts)
      settings.bestAttempts = guesses.value.length
    if (mode.value === 'daily') settings.lastDailyDate = todayStr()
    store.beep('win')
  } else {
    settings.losses++
    store.beep('lose')
  }
  persist()
  if (mode.value !== 'endless') props.onGameOver(scoreEarned.value)
}

function useReveal() {
  if (powers.reveal <= 0 || !alive.value) return
  const empty = current.value.map((c, i) => c == null ? i : -1).filter((i) => i >= 0)
  if (empty.length === 0) return
  const pos = empty[Math.floor(Math.random() * empty.length)]
  const next = current.value.slice() as (number | null)[]
  next[pos] = secret.value[pos]
  current.value = next
  powers.reveal--; persist()
}
function useEliminate() {
  if (powers.eliminate <= 0 || !alive.value) return
  const notIn = []
  const inSecret = new Set(secret.value)
  for (let i = 0; i < palSize.value; i++) if (!inSecret.has(i) && !hintMask.value.has(i)) notIn.push(i)
  if (notIn.length === 0) return
  const pick = notIn[Math.floor(Math.random() * notIn.length)]
  const next = new Set(hintMask.value); next.add(pick); hintMask.value = next
  powers.eliminate--; persist()
}
function useUndo() {
  if (powers.undo <= 0 || guesses.value.length === 0 || !alive.value) return
  guesses.value = guesses.value.slice(0, -1)
  powers.undo--; persist()
}

watch(() => props.running, (v) => { if (v) newRound() })
onMounted(newRound)
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.hm-wrap { width: min(440px, 100%); display: flex; flex-direction: column; gap: 8px; padding: 10px; color: #fff;
  background: linear-gradient(160deg, #0B1024, #160E2C); border-radius: 18px; }
.hm-top { display: flex; justify-content: space-between; align-items: center; }
.hm-tag { font-size: 0.72rem; font-weight: 800; letter-spacing: 0.1em; }
.hm-sub { font-size: 0.7rem; color: rgba(255,255,255,0.6); }
.hm-stats { text-align: right; font-size: 0.78rem; }
.hm-att { font-weight: 700; }
.hm-time.low { color: #EF4444; }
.hm-daily { color: #38FFB1; font-size: 0.7rem; }
.hm-mode { color: rgba(255,255,255,0.6); font-size: 0.7rem; text-transform: capitalize; }
.hm-powers { display: flex; gap: 6px; flex-wrap: wrap; }
.hm-chip { padding: 5px 9px; font-size: 0.72rem; border-radius: 9px; border: 1px solid rgba(255,255,255,0.15);
  background: rgba(255,255,255,0.06); color: #fff; cursor: pointer; }
.hm-chip:disabled { opacity: 0.35; cursor: not-allowed; }
.hm-chip.ghost { background: transparent; }
.hm-settings { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 10px;
  background: rgba(255,255,255,0.04); border-radius: 12px; font-size: 0.72rem; }
.hm-settings label { display: flex; flex-direction: column; gap: 3px; color: rgba(255,255,255,0.7); }
.hm-settings label.cb { flex-direction: row; align-items: center; gap: 6px; }
.hm-settings select { background: rgba(0,0,0,0.4); color: #fff; border: 1px solid rgba(255,255,255,0.15);
  border-radius: 6px; padding: 4px; }
.hm-statline { grid-column: 1 / -1; padding-top: 4px; color: rgba(255,255,255,0.55); font-size: 0.7rem; }
.hm-board { flex: 1; min-height: 0; max-height: 260px; overflow-y: auto;
  display: flex; flex-direction: column; gap: 4px; padding: 6px;
  background: rgba(0,0,0,0.25); border-radius: 12px; }
.hm-row { display: flex; align-items: center; gap: 4px; }
.hm-row.placeholder { opacity: 0.35; }
.hm-num { width: 18px; font-size: 0.65rem; color: rgba(255,255,255,0.4); }
.hm-pegs { display: flex; gap: 2px; }
.hm-peg { width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 0.7rem; color: #000; font-weight: 800; box-shadow: inset 0 -2px 4px rgba(0,0,0,0.25); }
.hm-peg.empty { background: rgba(255,255,255,0.05); border: 1px dashed rgba(255,255,255,0.15); box-shadow: none; }
.hm-peg.slot { border: 2px solid rgba(168,85,247,0.5); cursor: pointer; }
.hm-fb { margin-left: auto; display: flex; gap: 2px; flex-wrap: wrap; max-width: 56px; }
.hm-fb-peg { width: 9px; height: 9px; border-radius: 50%; }
.hm-fb-peg.black { background: #000; border: 1px solid #fff; }
.hm-fb-peg.white { background: #fff; border: 1px solid #000; }
.hm-current { display: flex; align-items: center; gap: 6px; padding: 6px;
  background: rgba(255,255,255,0.04); border-radius: 12px; }
.hm-label { font-size: 0.7rem; color: rgba(255,255,255,0.55); }
.hm-submit { margin-left: auto; padding: 7px 14px; font-size: 0.78rem; font-weight: 800;
  border-radius: 9px; border: none; background: #38FFB1; color: #000; cursor: pointer; }
.hm-submit:disabled { background: rgba(255,255,255,0.1); color: rgba(255,255,255,0.3); cursor: not-allowed; }
.hm-palette { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px; padding: 6px 0; }
.hm-color { width: 36px; height: 36px; border-radius: 50%; border: 2px solid rgba(255,255,255,0.2); cursor: pointer;
  display: flex; align-items: center; justify-content: center; font-weight: 800; color: #000; font-size: 0.78rem; }
.hm-color.out, .hm-color:disabled { opacity: 0.25; cursor: not-allowed; }
.hm-end { padding: 8px; border-radius: 12px; background: rgba(255,255,255,0.05); text-align: center;
  display: flex; flex-direction: column; gap: 6px; align-items: center; color: #FF7AA8; font-weight: 700; }
.hm-end.won { color: #38FFB1; }
.hm-pegs.reveal { justify-content: center; margin-top: 4px; }
.hm-glyph { font-size: 0.7rem; }
</style>
