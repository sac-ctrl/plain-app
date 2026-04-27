<template>
  <div
    class="wrap"
    tabindex="0"
    ref="root"
    @keydown="onKey"
    @keyup="onKeyUp"
    @mousedown.prevent="onPress"
    @mouseup.prevent="onRelease"
    @touchstart.prevent="onPress"
    @touchend.prevent="onRelease"
    @wheel.prevent="onWheel"
  >
    <div class="canvas-frame" :class="{ shake: shakeT > 0, focus: inFocusMode }">
      <canvas ref="cv" class="cv" :style="{ filter: cbFilter }" />
      <div v-if="screenPulse" class="pulse"></div>
      <button class="settings-btn" @click.stop="settingsOpen = true" title="Settings">⚙</button>
    </div>

    <div class="hint">
      <span v-if="hasGhost" class="ghost-tag">👻 Ghost run loaded</span>
      <span v-else-if="dailySeedActive" class="ghost-tag">🌅 Daily seed</span>
      <span v-else-if="sharedSeedActive" class="ghost-tag">🔗 Shared seed</span>
      <span v-else>{{ holdMode ? 'Hold to ascend' : 'Tap / Space to flap' }} · scroll = flap · A/X gamepad</span>
    </div>

    <!-- Settings overlay -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Flappy Eclipse · Settings</div>
          <button class="x" @click="settingsOpen = false">✕</button>
        </div>
        <div class="p-body">
          <div class="row">
            <label>Sensitivity <b>{{ settings.sensitivity.toFixed(2) }}</b></label>
            <input type="range" min="0.6" max="1.6" step="0.05" v-model.number="settings.sensitivity" @change="saveSettings" />
            <div class="hint-sm">Lower = heavier bird, smaller flaps. Higher = snappier.</div>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.holdMode" @change="saveSettings" /> Hold-to-ascend mode (longer press = stronger flap, with fatigue)</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.layeredMusic" @change="saveSettings" /> Adaptive layered music</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.haptics" @change="saveSettings" /> Haptic flap feedback</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.reducedMotion" @change="saveSettings" /> Reduced motion (no shake / slow-mo / heavy particles)</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.screenPulseCue" @change="saveSettings" /> Screen-pulse flap cue (instead of haptics)</label>
          </div>
          <div class="row">
            <label>Colourblind mode</label>
            <div class="chips">
              <button v-for="m in cbModes" :key="m" class="chip" :class="{ on: settings.colorblind === m }" @click="settings.colorblind = m; saveSettings()">{{ m }}</button>
            </div>
          </div>
          <div class="row">
            <label>Bird flavour (skin)</label>
            <div class="chips">
              <button
                v-for="f in flavours"
                :key="f.id"
                class="chip"
                :class="{ on: settings.flavour === f.id, locked: !isUnlocked(f.id) }"
                :title="isUnlocked(f.id) ? f.desc : `Locked: ${f.unlockHint}`"
                @click="if (isUnlocked(f.id)) { settings.flavour = f.id; saveSettings() }"
              >
                {{ isUnlocked(f.id) ? f.label : '🔒 ' + f.label }}
              </button>
            </div>
            <div class="hint-sm">{{ currentFlavourDesc }}</div>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.assistGhost" @change="saveSettings" /> Assist: ghost collision (no-fail, non-competitive)</label>
            <label class="check"><input type="checkbox" v-model="settings.autoFlap" @change="saveSettings" /> Assist: auto-flap (AI keeps height)</label>
          </div>
          <div class="row">
            <button class="btn" @click="openCalibration">Calibrate sensitivity (reflex test)</button>
          </div>
          <div class="row">
            <button class="btn" @click="copyShareSeed">📋 Copy "Beat my seed" link</button>
            <div class="hint-sm">Current seed: <code>{{ shareSeedString }}</code></div>
          </div>
          <div class="row">
            <div class="meter">
              <span>Adaptive difficulty (hidden)</span>
              <div class="bar"><div :style="{ width: Math.min(100, Math.max(0, mmrValue)) + '%' }"></div></div>
              <div class="hint-sm">{{ mmrLabel }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Calibration overlay -->
    <div v-if="calibrating" class="overlay" @click.self="calibrating = false">
      <div class="panel small">
        <div class="p-title">Reflex calibration</div>
        <div class="cal-stage" :class="{ go: calGo }" @click="onCalTap">
          <span v-if="!calGo && calRound <= 5">Wait for green… (round {{ calRound }}/5)</span>
          <span v-else-if="calGo">TAP NOW</span>
          <span v-else>Average: {{ calAvg }} ms · Auto-tuned to {{ settings.sensitivity.toFixed(2) }}</span>
        </div>
        <button class="btn" @click="calibrating = false">Done</button>
      </div>
    </div>

    <!-- Post-death analytics overlay -->
    <div v-if="showAnalytics" class="overlay">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Run analysis · Score {{ score }}</div>
          <button class="x" @click="finishToShell">Continue ▸</button>
        </div>
        <div class="p-body">
          <div class="r-msg">{{ deathSuggestion }}</div>
          <div class="graphs">
            <div class="g-block">
              <div class="g-title">Height over time</div>
              <canvas ref="heightChart" class="g-canvas"></canvas>
            </div>
            <div class="g-block">
              <div class="g-title">Tap heatmap (when you flapped)</div>
              <canvas ref="heatChart" class="g-canvas"></canvas>
            </div>
          </div>
          <div class="r-stats-row">
            <div><span>Pipes passed</span><b>{{ score }}</b></div>
            <div><span>Crashed at</span><b>{{ crashInfo }}</b></div>
            <div><span>Taps</span><b>{{ tapTimes.length }}</b></div>
            <div><span>Avg tap gap</span><b>{{ avgTapGap }}ms</b></div>
          </div>
          <div class="r-actions">
            <button class="btn primary" @click="instantReplay">↺ Instant replay (long-press R also works)</button>
            <button class="btn" @click="finishToShell">Continue</button>
          </div>
          <div v-if="newUnlocks.length" class="unlock-banner">
            🎉 Unlocked: {{ newUnlocks.join(', ') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, nextTick, reactive } from 'vue'
import { useGamesStore } from '../gamesStore'
import toast from '@/components/toaster'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'classic' | 'survival'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()
const heightChart = ref<HTMLCanvasElement>()
const heatChart = ref<HTMLCanvasElement>()

// === settings ===
type CB = 'off' | 'protanopia' | 'deuteranopia' | 'tritanopia'
interface Settings {
  sensitivity: number
  holdMode: boolean
  layeredMusic: boolean
  haptics: boolean
  reducedMotion: boolean
  screenPulseCue: boolean
  colorblind: CB
  flavour: 'vanilla' | 'rocket' | 'ghost' | 'magnetic'
  assistGhost: boolean
  autoFlap: boolean
}
const SETTINGS_KEY = 'flappy_settings_v1'
const UNLOCKS_KEY = 'flappy_unlocks_v1'
const MMR_KEY = 'flappy_mmr_v1'
const DAILY_KEY = 'flappy_daily_v1'

function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) return { ...defaultSettings(), ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return defaultSettings()
}
function defaultSettings(): Settings {
  return {
    sensitivity: 1.0,
    holdMode: false,
    layeredMusic: true,
    haptics: true,
    reducedMotion: false,
    screenPulseCue: false,
    colorblind: 'off',
    flavour: 'vanilla',
    assistGhost: false,
    autoFlap: false,
  }
}
const settings = reactive<Settings>(loadSettings())
function saveSettings() {
  try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)) } catch { /* ignore */ }
}

const settingsOpen = ref(false)
const calibrating = ref(false)
const cbModes: CB[] = ['off', 'protanopia', 'deuteranopia', 'tritanopia']
const cbFilter = computed(() => {
  // SVG-based-equivalent CSS filter approximations
  switch (settings.colorblind) {
    case 'protanopia': return 'url(#cb-prot)'
    case 'deuteranopia': return 'url(#cb-deut)'
    case 'tritanopia': return 'url(#cb-trit)'
    default: return 'none'
  }
})

// === unlocks ===
interface Unlocks { vanilla: boolean; rocket: boolean; ghost: boolean; magnetic: boolean }
function loadUnlocks(): Unlocks {
  try {
    const raw = localStorage.getItem(UNLOCKS_KEY)
    if (raw) return { ...{ vanilla: true, rocket: false, ghost: false, magnetic: false }, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { vanilla: true, rocket: false, ghost: false, magnetic: false }
}
function saveUnlocks(u: Unlocks) { try { localStorage.setItem(UNLOCKS_KEY, JSON.stringify(u)) } catch { /* */ } }
const unlocks = ref<Unlocks>(loadUnlocks())
const flavours = [
  { id: 'vanilla' as const, label: 'Vanilla', desc: 'Default phoenix.', unlockHint: 'available from start' },
  { id: 'rocket' as const, label: 'Rocket Bird', desc: 'Each flap gives extra thrust — sensitive controls.', unlockHint: 'pass 20 pipes' },
  { id: 'ghost' as const, label: 'Ghost Bird', desc: 'Every 10th pipe is intangible (you phase through).', unlockHint: 'survive 60 seconds' },
  { id: 'magnetic' as const, label: 'Magnetic', desc: 'Pipes attract you slightly — risk vs. reward.', unlockHint: 'reach 30 pipes' },
]
const currentFlavourDesc = computed(() => flavours.find((f) => f.id === settings.flavour)?.desc || '')
function isUnlocked(id: string) { return (unlocks.value as Record<string, boolean>)[id] === true }

// === MMR (adaptive difficulty) ===
interface Mmr { value: number; runs: { score: number; ts: number }[] }
function loadMmr(): Mmr {
  try {
    const raw = localStorage.getItem(MMR_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return { value: 50, runs: [] }
}
function saveMmr(m: Mmr) { try { localStorage.setItem(MMR_KEY, JSON.stringify(m)) } catch { /* */ } }
const mmrState = ref<Mmr>(loadMmr())
const mmrValue = computed(() => mmrState.value.value)
const mmrLabel = computed(() => {
  const v = mmrState.value.value
  if (v < 25) return 'Adapting easier — gravity reduced, gaps wider'
  if (v < 60) return 'Tuned to your skill level'
  if (v < 85) return 'Slightly tougher — narrower gaps'
  return 'Pro mode — pipes are tighter and faster'
})

// === seeded RNG (for daily / shared seeds) ===
let rngSeed = Math.floor(Math.random() * 2147483647)
function rng(): number {
  // mulberry32
  rngSeed |= 0
  rngSeed = (rngSeed + 0x6D2B79F5) | 0
  let t = rngSeed
  t = Math.imul(t ^ (t >>> 15), t | 1)
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
function setSeed(s: number) { rngSeed = s | 0 }

const sharedSeedActive = ref(false)
const dailySeedActive = ref(false)
const shareSeedString = computed(() => `flap-${currentSeedNumber.value.toString(36)}`)
const currentSeedNumber = ref(0)

function todaySeedNumber(): number {
  const d = new Date()
  return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate()
}

function readUrlSeed(): number | null {
  try {
    const url = new URL(window.location.href)
    const s = url.searchParams.get('flapSeed')
    if (!s) return null
    if (s.startsWith('flap-')) {
      const n = parseInt(s.slice(5), 36)
      if (!Number.isNaN(n)) return n
    }
    const n2 = parseInt(s, 10)
    return Number.isFinite(n2) ? n2 : null
  } catch { return null }
}

function copyShareSeed() {
  try {
    const url = new URL(window.location.href)
    url.searchParams.set('flapSeed', shareSeedString.value)
    navigator.clipboard.writeText(url.toString())
    toast('Seed link copied — share it for a fixed pipe pattern!', 'success')
  } catch {
    toast('Could not copy link', 'error')
  }
}

// === game state ===
const W = 360
const H = 540
const PIPE_W = 60
const BIRD_R = 14
const BIRD_X = 90
const GROUND_H = 40
const GRACE = 2 // pixel-perfect grace margin
const FIXED_DT = 1000 / 60
const INPUT_BUFFER_MS = 50

interface Pipe {
  x: number
  gapY: number
  gapH: number
  passed: boolean
  moving: boolean
  baseY: number
  phase: number
  intangible: boolean
}

let bird = { y: 220, vy: 0 }
let pipes: Pipe[] = []
let particles: { x: number; y: number; vx: number; vy: number; life: number; max: number; color: string; size: number }[] = []
let trail: { x: number; y: number; life: number }[] = []
let fragments: { x: number; y: number; vx: number; vy: number; life: number; ang: number; va: number }[] = []
let score = 0
let alive = true
let started = false
let last = 0
let acc = 0
let frameIdx = 0
let recordedFrames: { y: number }[] = []
let ghostFrames: { y: number }[] = []
let bgOffset = 0
let lastTapTime = -10000
let pendingFlapAt = -1
let holdSince = -1
let holdFatigue = 0 // 0..1, recharges between flaps
let spawnT = 0
let postDeathSlowMo = 0
let shakeT = 0
let runStartTs = 0
let crashInfoLocal = ''
let pipeCounterAfterDeath = -1

const hasGhost = ref(false)
const score_ = ref(0)
const inFocusMode = ref(false)
const screenPulse = ref(false)
const showAnalytics = ref(false)
const tapTimes = ref<{ frame: number; y: number }[]>([])
const heightSamples = ref<number[]>([])
const newUnlocks = ref<string[]>([])
const crashInfo = computed(() => crashInfoLocal)
const avgTapGap = computed(() => {
  if (tapTimes.value.length < 2) return '—'
  const gaps: number[] = []
  for (let i = 1; i < tapTimes.value.length; i++) gaps.push((tapTimes.value[i].frame - tapTimes.value[i - 1].frame) * FIXED_DT)
  return Math.round(gaps.reduce((a, b) => a + b, 0) / gaps.length)
})
const deathSuggestion = ref('')

// difficulty params - then adjusted by MMR
const params: Record<string, { gap: number; gravity: number; jump: number; speed: number; spawn: number }> = {
  easy:   { gap: 200, gravity: 0.42, jump: -7.0, speed: 1.9, spawn: 1900 },
  medium: { gap: 170, gravity: 0.50, jump: -7.4, speed: 2.3, spawn: 1700 },
  hard:   { gap: 145, gravity: 0.58, jump: -7.8, speed: 2.7, spawn: 1500 },
  insane: { gap: 125, gravity: 0.68, jump: -8.2, speed: 3.2, spawn: 1300 },
}
let cfg = { ...params.medium }
let raf = 0

// === audio: layered ambient ===
type AnyWin = Window & { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }
let audioCtx: AudioContext | null = null
let baseOsc: OscillatorNode | null = null
let beatOsc: OscillatorNode | null = null
let bassOsc: OscillatorNode | null = null
let baseGain: GainNode | null = null
let beatGain: GainNode | null = null
let bassGain: GainNode | null = null
let masterGain: GainNode | null = null
let lpFilter: BiquadFilterNode | null = null

function ensureAudio() {
  if (!settings.layeredMusic || !store.sound) return null
  if (audioCtx) return audioCtx
  try {
    const w = window as unknown as AnyWin
    const Ctor = w.AudioContext || w.webkitAudioContext
    if (!Ctor) return null
    audioCtx = new Ctor()
    masterGain = audioCtx.createGain(); masterGain.gain.value = 0.04; masterGain.connect(audioCtx.destination)
    lpFilter = audioCtx.createBiquadFilter(); lpFilter.type = 'lowpass'; lpFilter.frequency.value = 8000
    lpFilter.connect(masterGain)
    baseGain = audioCtx.createGain(); baseGain.gain.value = 0.6; baseGain.connect(lpFilter)
    beatGain = audioCtx.createGain(); beatGain.gain.value = 0.0; beatGain.connect(lpFilter)
    bassGain = audioCtx.createGain(); bassGain.gain.value = 0.0; bassGain.connect(lpFilter)
    baseOsc = audioCtx.createOscillator(); baseOsc.type = 'sine'; baseOsc.frequency.value = 220
    beatOsc = audioCtx.createOscillator(); beatOsc.type = 'triangle'; beatOsc.frequency.value = 440
    bassOsc = audioCtx.createOscillator(); bassOsc.type = 'sine'; bassOsc.frequency.value = 55
    baseOsc.connect(baseGain); beatOsc.connect(beatGain); bassOsc.connect(bassGain)
    baseOsc.start(); beatOsc.start(); bassOsc.start()
  } catch { /* ignore */ }
  return audioCtx
}

function updateMusic() {
  if (!audioCtx || !beatGain || !bassGain || !lpFilter) return
  const now = audioCtx.currentTime
  // base always ~ 0.5
  // beat layer in for 20-50, bass layer in for 50+
  const beat = score >= 20 ? 0.35 : 0.0
  const bass = score >= 50 ? 0.3 : 0.0
  beatGain.gain.linearRampToValueAtTime(beat, now + 0.5)
  bassGain.gain.linearRampToValueAtTime(bass, now + 0.5)
  // focus mode: pitch up
  if (inFocusMode.value && baseOsc && beatOsc) {
    baseOsc.frequency.linearRampToValueAtTime(260, now + 0.3)
    beatOsc.frequency.linearRampToValueAtTime(520, now + 0.3)
  } else if (baseOsc && beatOsc) {
    baseOsc.frequency.linearRampToValueAtTime(220, now + 0.4)
    beatOsc.frequency.linearRampToValueAtTime(440, now + 0.4)
  }
}

function muteMusicAfterDeath() {
  if (!masterGain || !audioCtx || !lpFilter) return
  const now = audioCtx.currentTime
  masterGain.gain.cancelScheduledValues(now)
  masterGain.gain.setValueAtTime(masterGain.gain.value, now)
  masterGain.gain.linearRampToValueAtTime(0.0, now + 0.05)
  masterGain.gain.linearRampToValueAtTime(0.04, now + 1.5)
  lpFilter.frequency.cancelScheduledValues(now)
  lpFilter.frequency.setValueAtTime(8000, now)
  lpFilter.frequency.exponentialRampToValueAtTime(400, now + 1.2)
  lpFilter.frequency.linearRampToValueAtTime(8000, now + 2.8)
}

function disposeAudio() {
  try { baseOsc?.stop(); beatOsc?.stop(); bassOsc?.stop() } catch { /* ignore */ }
  try { audioCtx?.close() } catch { /* ignore */ }
  audioCtx = null; baseOsc = beatOsc = bassOsc = null
  baseGain = beatGain = bassGain = masterGain = null; lpFilter = null
}

// === core flow ===
function reset() {
  cfg = { ...(params[props.difficulty] || params.medium) }
  // apply MMR offset (hidden)
  const v = mmrState.value.value
  const skew = (v - 50) / 100 // -0.5 .. +0.5
  cfg.gravity = Math.max(0.3, cfg.gravity * (1 + skew * 0.25))
  cfg.gap = Math.max(95, cfg.gap * (1 - skew * 0.18))
  cfg.speed = Math.max(1.5, cfg.speed * (1 + skew * 0.18))
  // sensitivity
  cfg.gravity = cfg.gravity / settings.sensitivity
  cfg.jump = cfg.jump * settings.sensitivity

  // seed
  const url = readUrlSeed()
  if (url != null) {
    setSeed(url); sharedSeedActive.value = true; dailySeedActive.value = false
    currentSeedNumber.value = url
  } else {
    // 1 in 4 runs use today's daily seed, otherwise random
    if (Math.random() < 0.33) {
      const ds = todaySeedNumber()
      setSeed(ds); dailySeedActive.value = true; sharedSeedActive.value = false
      currentSeedNumber.value = ds
    } else {
      const r = Math.floor(Math.random() * 2147483647)
      setSeed(r); dailySeedActive.value = false; sharedSeedActive.value = false
      currentSeedNumber.value = r
    }
  }

  bird = { y: 220, vy: 0 }
  pipes = []
  particles = []
  trail = []
  fragments = []
  score = 0
  score_.value = 0
  alive = true
  started = false
  last = performance.now()
  acc = 0
  spawnT = -800
  frameIdx = 0
  recordedFrames = []
  bgOffset = 0
  postDeathSlowMo = 0
  shakeT = 0
  inFocusMode.value = false
  showAnalytics.value = false
  newUnlocks.value = []
  tapTimes.value = []
  heightSamples.value = []
  pendingFlapAt = -1
  holdSince = -1
  holdFatigue = 0
  pipeCounterAfterDeath = -1
  runStartTs = performance.now()
  crashInfoLocal = ''

  const g = store.getGhost<{ frames: { y: number }[]; difficulty: string }>('flappy')
  if (g && g.frames && g.difficulty === props.difficulty) {
    ghostFrames = g.frames
    hasGhost.value = true
  } else {
    ghostFrames = []
    hasGhost.value = false
  }

  // music
  ensureAudio()
  if (audioCtx && masterGain) {
    masterGain.gain.cancelScheduledValues(audioCtx.currentTime)
    masterGain.gain.setValueAtTime(0.04, audioCtx.currentTime)
    if (lpFilter) lpFilter.frequency.setValueAtTime(8000, audioCtx.currentTime)
  }
  updateMusic()
  props.onScore(0)
}

function flap(strength = 1) {
  if (!alive) return
  if (!started) started = true
  let force = cfg.jump * strength
  if (settings.flavour === 'rocket') force *= 1.18
  bird.vy = force
  store.beep('tap')
  if (settings.haptics && !settings.screenPulseCue) store.vibrate(8)
  if (settings.screenPulseCue) {
    screenPulse.value = true
    setTimeout(() => { screenPulse.value = false }, 80)
  }
  tapTimes.value.push({ frame: frameIdx, y: bird.y })
}

function attemptFlapBuffered(now: number) {
  // input buffering: if user tapped within INPUT_BUFFER_MS, accept it next frame
  if (now - lastTapTime <= INPUT_BUFFER_MS && pendingFlapAt < 0) {
    pendingFlapAt = lastTapTime
  }
}

function onPress(e?: Event) {
  e?.preventDefault?.()
  lastTapTime = performance.now()
  if (!alive) return
  if (settings.holdMode) {
    holdSince = performance.now()
  } else {
    flap(1)
  }
  // resume audio context after first user gesture
  if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume().catch(() => { /* */ })
}

function onRelease(e?: Event) {
  e?.preventDefault?.()
  if (!alive) return
  if (settings.holdMode && holdSince > 0) {
    const dt = Math.min(400, performance.now() - holdSince)
    const strength = 0.6 + (dt / 400) * 0.8 * (1 - holdFatigue)
    flap(Math.max(0.4, strength))
    holdFatigue = Math.min(1, holdFatigue + 0.5)
    holdSince = -1
  }
}

function onWheel(e: WheelEvent) {
  if (e.deltaY < 0) flap(1) // scroll up = flap
}

function onKey(e: KeyboardEvent) {
  if (e.repeat) return
  if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W') {
    e.preventDefault(); onPress()
  } else if (e.key.toLowerCase() === 'r' && !alive && showAnalytics.value) {
    instantReplay()
  } else if (e.key === 'Escape') {
    settingsOpen.value = !settingsOpen.value
  }
}
function onKeyUp(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W') onRelease()
}

// gamepad polling
let gpAxisLast = false
function pollGamepad() {
  try {
    const pads = navigator.getGamepads ? navigator.getGamepads() : []
    for (const p of pads) {
      if (!p) continue
      const a = p.buttons[0]?.pressed || p.buttons[2]?.pressed // A/X
      if (a && !gpAxisLast) onPress()
      if (!a && gpAxisLast) onRelease()
      gpAxisLast = !!a
    }
  } catch { /* ignore */ }
}

function physicsStep() {
  if (!started) {
    bird.y = 220 + Math.sin(frameIdx / 12) * 6
    frameIdx++
    return
  }
  // recharge fatigue
  if (holdFatigue > 0 && holdSince < 0) holdFatigue = Math.max(0, holdFatigue - 0.015)

  // assist auto-flap
  if (settings.autoFlap && bird.vy > 1 && bird.y > H * 0.55) flap(0.7)

  let g = cfg.gravity
  // magnetic flavour: slight pull to nearest pipe gap centre
  if (settings.flavour === 'magnetic') {
    const next = pipes.find((p) => p.x + PIPE_W > BIRD_X - 30)
    if (next) {
      const center = next.gapY + cfg.gap / 2
      const dy = center - bird.y
      bird.vy += Math.sign(dy) * Math.min(0.18, Math.abs(dy) * 0.005)
    }
  }
  bird.vy += g
  // drag / terminal
  const terminal = 9.0
  if (bird.vy > terminal) bird.vy = terminal
  bird.y += bird.vy

  // trail
  trail.push({ x: BIRD_X - 6, y: bird.y, life: 18 })
  if (trail.length > 30) trail.shift()
  for (const t of trail) t.life--

  recordedFrames.push({ y: bird.y })
  heightSamples.value.push(bird.y)
  frameIdx++
  spawnT += FIXED_DT

  if (spawnT >= cfg.spawn) {
    spawnT = 0
    const minY = 70
    const maxY = H - GROUND_H - cfg.gap - 70
    const gapY = minY + rng() * Math.max(40, maxY - minY)
    const moving = score >= 25 && rng() < 0.35
    const intangible = settings.flavour === 'ghost' && (score + 1) % 10 === 0
    pipes.push({ x: W + 10, gapY, gapH: cfg.gap, passed: false, moving, baseY: gapY, phase: rng() * Math.PI * 2, intangible })
  }

  for (const p of pipes) {
    p.x -= cfg.speed
    if (p.moving) {
      p.gapY = p.baseY + Math.sin(frameIdx / 22 + p.phase) * 30
    }
  }
  pipes = pipes.filter((p) => p.x > -PIPE_W - 10)
  bgOffset = (bgOffset + cfg.speed * 0.4) % 40

  // process buffered tap (predictive)
  if (pendingFlapAt > 0) pendingFlapAt = -1

  // ground & ceiling
  if (!settings.assistGhost) {
    if (bird.y + BIRD_R > H - GROUND_H) {
      crashInfoLocal = `ground @ frame ${frameIdx}`
      return die()
    }
    if (bird.y - BIRD_R < 0) {
      crashInfoLocal = `ceiling @ frame ${frameIdx}`
      return die()
    }
  } else {
    if (bird.y + BIRD_R > H - GROUND_H) bird.y = H - GROUND_H - BIRD_R
    if (bird.y - BIRD_R < 0) bird.y = BIRD_R
  }

  for (const p of pipes) {
    const inX = BIRD_X + BIRD_R - GRACE > p.x && BIRD_X - BIRD_R + GRACE < p.x + PIPE_W
    if (inX && !p.intangible) {
      const hitTop = bird.y - BIRD_R + GRACE < p.gapY
      const hitBot = bird.y + BIRD_R - GRACE > p.gapY + p.gapH
      if (hitTop || hitBot) {
        if (!settings.assistGhost) {
          crashInfoLocal = `pipe @ x=${Math.round(p.x)},y=${Math.round(bird.y)}`
          return die()
        }
      }
    }
    if (!p.passed && p.x + PIPE_W < BIRD_X - BIRD_R) {
      p.passed = true
      score += 1
      score_.value = score
      props.onScore(score)
      store.beep('tick')
      // visual crack on the pipe (recorded as a particle burst near the gap)
      for (let i = 0; i < (settings.reducedMotion ? 3 : 8); i++) {
        particles.push({
          x: p.x + PIPE_W / 2 + (rng() - 0.5) * 10,
          y: p.gapY + cfg.gap / 2 + (rng() - 0.5) * cfg.gap,
          vx: (rng() - 0.5) * 1.2,
          vy: (rng() - 0.5) * 1.2,
          life: 18, max: 18,
          color: 'rgba(255,255,255,0.4)', size: 1.5,
        })
      }
      // focus mode every 10th pipe
      if (score > 0 && score % 10 === 0) {
        inFocusMode.value = true
        setTimeout(() => { inFocusMode.value = false }, 2500)
        updateMusic()
      } else {
        updateMusic()
      }
    }
  }

  // particles
  for (const p of particles) {
    p.x += p.vx; p.y += p.vy; p.life--
    p.vy += 0.02
  }
  particles = particles.filter((p) => p.life > 0)

  // trail flair: emit colored speck behind bird
  if (!settings.reducedMotion && frameIdx % 2 === 0) {
    const speed = Math.abs(bird.vy)
    const color = inFocusMode.value
      ? 'rgba(250, 204, 21, 0.9)'
      : speed > 5 ? 'rgba(248, 113, 113, 0.7)'
      : speed > 2 ? 'rgba(96, 165, 250, 0.7)' : 'rgba(167, 139, 250, 0.5)'
    particles.push({
      x: BIRD_X - BIRD_R, y: bird.y + (rng() - 0.5) * 6,
      vx: -1.2, vy: 0,
      life: 22, max: 22, color, size: 2.2,
    })
  }
}

function die() {
  if (!alive) return
  alive = false
  store.beep('lose')
  if (settings.haptics) store.vibrate([20, 60, 20])
  // fragments
  if (!settings.reducedMotion) {
    for (let i = 0; i < 28; i++) {
      const a = rng() * Math.PI * 2
      const v = 1 + rng() * 4
      fragments.push({
        x: BIRD_X, y: bird.y,
        vx: Math.cos(a) * v, vy: Math.sin(a) * v - 1,
        life: 60, ang: rng() * Math.PI * 2, va: (rng() - 0.5) * 0.3,
      })
    }
    shakeT = 18
    postDeathSlowMo = 24
  }
  muteMusicAfterDeath()

  // update MMR
  const m = mmrState.value
  m.runs.push({ score, ts: Date.now() })
  if (m.runs.length > 30) m.runs.shift()
  // adjust: low scores → ease, high → harden
  if (score < 10) {
    const recentLow = m.runs.slice(-5).filter((r) => r.score < 10).length
    if (recentLow >= 5) m.value = Math.max(0, m.value - 5)
  } else if (score >= 50) {
    const recentHigh = m.runs.slice(-3).filter((r) => r.score >= 50).length
    if (recentHigh >= 3) m.value = Math.min(100, m.value + 6)
  } else if (score > 25) {
    m.value = Math.min(100, m.value + 1)
  } else if (score < 5) {
    m.value = Math.max(0, m.value - 1)
  }
  saveMmr(m)

  // update unlocks
  const u = { ...unlocks.value }
  const elapsedSec = (performance.now() - runStartTs) / 1000
  const announce: string[] = []
  if (!u.rocket && score >= 20) { u.rocket = true; announce.push('Rocket Bird') }
  if (!u.ghost && elapsedSec >= 60) { u.ghost = true; announce.push('Ghost Bird') }
  if (!u.magnetic && score >= 30) { u.magnetic = true; announce.push('Magnetic') }
  if (announce.length) {
    unlocks.value = u
    saveUnlocks(u)
    newUnlocks.value = announce
  }

  // ghost run
  const prevBest = store.bestOf('flappy')
  if (score >= prevBest && score > 0) {
    store.saveGhost('flappy', { frames: recordedFrames.slice(0, 1500), difficulty: props.difficulty })
  }

  // daily seed best
  if (dailySeedActive.value) {
    try {
      const raw = localStorage.getItem(DAILY_KEY)
      const today = todaySeedNumber()
      let cur: { date: number; best: number } = raw ? JSON.parse(raw) : { date: today, best: 0 }
      if (cur.date !== today) cur = { date: today, best: 0 }
      if (score > cur.best) cur.best = score
      localStorage.setItem(DAILY_KEY, JSON.stringify(cur))
    } catch { /* ignore */ }
  }

  // schedule analytics overlay
  pipeCounterAfterDeath = score
  setTimeout(() => {
    if (!alive) {
      showAnalytics.value = true
      computeSuggestion()
      nextTick(() => { drawHeightChart(); drawHeatChart() })
    }
  }, settings.reducedMotion ? 200 : 700)
}

function computeSuggestion() {
  const samples = heightSamples.value
  if (samples.length < 30) {
    deathSuggestion.value = 'Hover-start, then settle into short, evenly-spaced taps.'
    return
  }
  // detect panic taps in final 30 frames
  const lastTaps = tapTimes.value.filter((t) => t.frame > frameIdx - 60)
  const fellSharply = samples[samples.length - 1] - samples[Math.max(0, samples.length - 30)] > 80
  const ceilingHit = crashInfoLocal.startsWith('ceiling')
  const groundHit = crashInfoLocal.startsWith('ground')
  if (ceilingHit) deathSuggestion.value = 'You over-flapped into the ceiling — try a lighter tap or lower sensitivity.'
  else if (groundHit) deathSuggestion.value = 'You panicked into the floor — start tapping ~0.1s earlier on descents.'
  else if (lastTaps.length >= 6) deathSuggestion.value = 'Lots of last-second taps — relax, fewer well-timed flaps work better.'
  else if (lastTaps.length === 0 && fellSharply) deathSuggestion.value = 'You stopped flapping. Try one tap mid-gap to keep height stable.'
  else deathSuggestion.value = 'Aim for the middle of each gap, not the edges. Predict, don’t react.'
}

function drawHeightChart() {
  const c = heightChart.value
  if (!c) return
  const r = c.getBoundingClientRect()
  c.width = r.width * devicePixelRatio
  c.height = r.height * devicePixelRatio
  const ctx = c.getContext('2d')!
  ctx.scale(devicePixelRatio, devicePixelRatio)
  const w = r.width, h = r.height
  ctx.fillStyle = 'rgba(255,255,255,0.04)'
  ctx.fillRect(0, 0, w, h)
  const pts = heightSamples.value
  if (!pts.length) return
  ctx.strokeStyle = 'rgba(96, 165, 250, 0.95)'
  ctx.lineWidth = 1.6
  ctx.beginPath()
  for (let i = 0; i < pts.length; i++) {
    const x = (i / (pts.length - 1)) * w
    const y = (pts[i] / H) * h
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
  }
  ctx.stroke()
  // mark taps
  ctx.fillStyle = 'rgba(250, 204, 21, 0.9)'
  for (const t of tapTimes.value) {
    const x = (t.frame / Math.max(1, frameIdx)) * w
    const y = (t.y / H) * h
    ctx.beginPath(); ctx.arc(x, y, 2, 0, Math.PI * 2); ctx.fill()
  }
}

function drawHeatChart() {
  const c = heatChart.value
  if (!c) return
  const r = c.getBoundingClientRect()
  c.width = r.width * devicePixelRatio
  c.height = r.height * devicePixelRatio
  const ctx = c.getContext('2d')!
  ctx.scale(devicePixelRatio, devicePixelRatio)
  const w = r.width, h = r.height
  ctx.fillStyle = 'rgba(255,255,255,0.04)'
  ctx.fillRect(0, 0, w, h)
  // bin taps by Y
  const bins = 12
  const counts = new Array(bins).fill(0)
  for (const t of tapTimes.value) {
    const idx = Math.min(bins - 1, Math.floor((t.y / H) * bins))
    counts[idx]++
  }
  const max = Math.max(1, ...counts)
  const bw = w / bins
  for (let i = 0; i < bins; i++) {
    const v = counts[i] / max
    ctx.fillStyle = `rgba(248, 113, 113, ${0.25 + v * 0.65})`
    ctx.fillRect(i * bw, h - v * h, bw - 1, v * h)
  }
  ctx.fillStyle = 'rgba(255,255,255,0.6)'
  ctx.font = '10px sans-serif'
  ctx.fillText('top', 4, 12)
  ctx.fillText('bottom', 4, h - 4)
}

// === drawing ===
function getDayPhase(): { sky0: string; sky1: string; sky2: string; star: number } {
  if (score < 10) return { sky0: '#fde68a', sky1: '#fb923c', sky2: '#0c4a6e', star: 0 } // dawn
  if (score < 30) return { sky0: '#7dd3fc', sky1: '#38bdf8', sky2: '#1e3a8a', star: 0 } // noon
  if (score < 60) return { sky0: '#fdba74', sky1: '#f472b6', sky2: '#7c3aed', star: 0.2 } // sunset
  return { sky0: '#0b1d3a', sky1: '#1e1b4b', sky2: '#000', star: 1 } // neon night
}

let cloudList: { x: number; y: number; w: number; speed: number }[] = []
function ensureClouds() {
  if (cloudList.length) return
  for (let i = 0; i < 6; i++) {
    cloudList.push({ x: rng() * W, y: 30 + rng() * 200, w: 40 + rng() * 60, speed: 0.2 + rng() * 0.4 })
  }
}

function draw() {
  ensureClouds()
  const c = cv.value!
  const ctx = c.getContext('2d')!
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  const phase = getDayPhase()
  // sky gradient
  const sky = ctx.createLinearGradient(0, 0, 0, H)
  sky.addColorStop(0, phase.sky0)
  sky.addColorStop(0.6, phase.sky1)
  sky.addColorStop(1, phase.sky2)
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, W, H)
  // stars (visible at night, faded otherwise)
  if (phase.star > 0) {
    ctx.fillStyle = `rgba(255,255,255,${0.3 * phase.star})`
    for (let i = 0; i < 35; i++) {
      const sx = (i * 53 + bgOffset * 0.2) % W
      const sy = ((i * 37) % (H - GROUND_H - 80)) + 10
      ctx.fillRect(sx, sy, 1.5, 1.5)
    }
  }
  // procedural clouds parallax
  for (const cl of cloudList) {
    cl.x -= cl.speed
    if (cl.x < -cl.w - 20) { cl.x = W + 20; cl.y = 30 + rng() * 200; cl.w = 40 + rng() * 60 }
    ctx.fillStyle = 'rgba(255,255,255,0.45)'
    ctx.beginPath()
    ctx.arc(cl.x, cl.y, cl.w * 0.35, 0, Math.PI * 2)
    ctx.arc(cl.x + cl.w * 0.3, cl.y - 6, cl.w * 0.3, 0, Math.PI * 2)
    ctx.arc(cl.x + cl.w * 0.6, cl.y + 4, cl.w * 0.32, 0, Math.PI * 2)
    ctx.fill()
  }
  // distant hills
  ctx.fillStyle = 'rgba(15, 30, 60, 0.55)'
  ctx.beginPath()
  ctx.moveTo(0, H - GROUND_H - 30)
  for (let x = 0; x <= W; x += 30) {
    const y = H - GROUND_H - 30 - Math.sin((x + bgOffset) / 30) * 8
    ctx.lineTo(x, y)
  }
  ctx.lineTo(W, H - GROUND_H)
  ctx.lineTo(0, H - GROUND_H)
  ctx.closePath()
  ctx.fill()

  // pipes (with caps + glow)
  for (const p of pipes) {
    const grd = ctx.createLinearGradient(p.x, 0, p.x + PIPE_W, 0)
    if (p.intangible) {
      grd.addColorStop(0, 'rgba(192,132,252,0.45)'); grd.addColorStop(1, 'rgba(126,34,206,0.45)')
    } else {
      grd.addColorStop(0, '#16a34a'); grd.addColorStop(0.5, '#22c55e'); grd.addColorStop(1, '#15803d')
    }
    ctx.fillStyle = grd
    ctx.fillRect(p.x, 0, PIPE_W, p.gapY)
    ctx.fillRect(p.x, p.gapY + p.gapH, PIPE_W, H - GROUND_H - p.gapY - p.gapH)
    ctx.fillStyle = p.intangible ? '#7e22ce' : '#15803d'
    ctx.fillRect(p.x - 4, p.gapY - 14, PIPE_W + 8, 14)
    ctx.fillRect(p.x - 4, p.gapY + p.gapH, PIPE_W + 8, 14)
    // moss / corrosion stripes
    ctx.fillStyle = 'rgba(0,0,0,0.18)'
    for (let yy = 12; yy < p.gapY; yy += 24) ctx.fillRect(p.x + 6, yy, PIPE_W - 12, 2)
    for (let yy = p.gapY + p.gapH + 12; yy < H - GROUND_H; yy += 24) ctx.fillRect(p.x + 6, yy, PIPE_W - 12, 2)
    // inner glow
    ctx.fillStyle = 'rgba(255,255,255,0.18)'
    ctx.fillRect(p.x + 6, 0, 4, p.gapY)
    ctx.fillRect(p.x + 6, p.gapY + p.gapH, 4, H - GROUND_H - p.gapY - p.gapH)
  }
  // ground
  const grdG = ctx.createLinearGradient(0, H - GROUND_H, 0, H)
  grdG.addColorStop(0, '#854d0e'); grdG.addColorStop(1, '#422006')
  ctx.fillStyle = grdG
  ctx.fillRect(0, H - GROUND_H, W, GROUND_H)
  ctx.fillStyle = '#65a30d'
  ctx.fillRect(0, H - GROUND_H, W, 6)
  ctx.fillStyle = 'rgba(0,0,0,0.18)'
  for (let x = -bgOffset; x < W; x += 22) ctx.fillRect(x, H - GROUND_H + 6, 12, 4)

  // particles & trail
  for (const p of particles) {
    const a = (p.life / p.max)
    ctx.fillStyle = p.color.replace(/,[\s\d.]+\)$/, `,${a.toFixed(2)})`)
    ctx.fillRect(p.x, p.y, p.size, p.size)
  }

  // ghost
  if (ghostFrames.length > 0 && frameIdx < ghostFrames.length) {
    const gy = ghostFrames[frameIdx].y
    ctx.fillStyle = 'rgba(250, 204, 21, 0.30)'
    ctx.beginPath(); ctx.arc(BIRD_X, gy, BIRD_R, 0, Math.PI * 2); ctx.fill()
  }

  // bird (or fragments after death)
  if (alive || fragments.length === 0) {
    drawBird(ctx)
  }
  // fragments
  for (const f of fragments) {
    f.x += f.vx; f.y += f.vy; f.vy += 0.18; f.ang += f.va; f.life--
    const a = Math.max(0, f.life / 60)
    ctx.save()
    ctx.translate(f.x, f.y); ctx.rotate(f.ang)
    ctx.fillStyle = `rgba(250, 204, 21, ${a})`
    ctx.beginPath(); ctx.moveTo(-3, -3); ctx.lineTo(3, -2); ctx.lineTo(2, 4); ctx.closePath(); ctx.fill()
    ctx.restore()
  }
  fragments = fragments.filter((f) => f.life > 0)

  // score
  ctx.save()
  ctx.fillStyle = '#fff'
  ctx.strokeStyle = 'rgba(0,0,0,0.7)'
  ctx.lineWidth = 4
  ctx.font = 'bold 36px sans-serif'
  ctx.textAlign = 'center'
  ctx.strokeText(String(score), W / 2, 60)
  ctx.fillText(String(score), W / 2, 60)
  ctx.restore()

  // focus mode vignette
  if (inFocusMode.value && !settings.reducedMotion) {
    const grd = ctx.createRadialGradient(W / 2, H / 2, H * 0.25, W / 2, H / 2, H * 0.7)
    grd.addColorStop(0, 'rgba(0,0,0,0)')
    grd.addColorStop(1, 'rgba(0,0,0,0.65)')
    ctx.fillStyle = grd
    ctx.fillRect(0, 0, W, H)
    ctx.fillStyle = 'rgba(250, 204, 21, 0.85)'
    ctx.font = 'bold 14px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('FOCUS', W / 2, H - GROUND_H - 14)
  }

  // tap to start hint
  if (!started && alive) {
    ctx.save()
    ctx.fillStyle = 'rgba(0,0,0,0.5)'
    ctx.fillRect(W / 2 - 130, H / 2 - 36, 260, 72)
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 16px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(settings.holdMode ? 'Hold to ascend' : 'Tap / Space to start', W / 2, H / 2 + 2)
    ctx.font = '12px sans-serif'
    ctx.fillStyle = 'rgba(255,255,255,0.75)'
    const sub = sharedSeedActive.value
      ? 'Shared seed — same pipes for everyone'
      : dailySeedActive.value ? `Today's daily seed (#${currentSeedNumber.value})` : 'Random seed'
    ctx.fillText(sub, W / 2, H / 2 + 22)
    ctx.restore()
  }
}

function drawBird(ctx: CanvasRenderingContext2D) {
  const tilt = Math.max(-0.5, Math.min(1.1, bird.vy / 10))
  ctx.save()
  ctx.translate(BIRD_X, bird.y)
  ctx.rotate(tilt)
  // body color by flavour
  const skin: Record<string, [string, string, string]> = {
    vanilla:  ['#fde68a', '#f59e0b', '#ea580c'],
    rocket:   ['#fecaca', '#ef4444', '#7f1d1d'],
    ghost:    ['#e9d5ff', '#a78bfa', '#5b21b6'],
    magnetic: ['#bae6fd', '#0ea5e9', '#075985'],
  }
  const [bodyA, bodyB, wing] = skin[settings.flavour] || skin.vanilla
  const bg = ctx.createRadialGradient(-4, -4, 4, 0, 0, BIRD_R + 4)
  bg.addColorStop(0, bodyA); bg.addColorStop(1, bodyB)
  ctx.fillStyle = bg
  ctx.shadowColor = inFocusMode.value ? 'rgba(250, 204, 21, 0.9)' : 'rgba(250, 204, 21, 0.5)'
  ctx.shadowBlur = 14
  ctx.beginPath(); ctx.arc(0, 0, BIRD_R, 0, Math.PI * 2); ctx.fill()
  ctx.shadowBlur = 0
  // wing
  const flapY = Math.sin(performance.now() / 60) * 4
  ctx.fillStyle = wing
  ctx.beginPath(); ctx.ellipse(-3, flapY + 2, 8, 5, -0.3, 0, Math.PI * 2); ctx.fill()
  // eye
  ctx.fillStyle = '#fff'
  ctx.beginPath(); ctx.arc(5, -3, 4, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#000'
  ctx.beginPath(); ctx.arc(6, -3, 2, 0, Math.PI * 2); ctx.fill()
  // beak
  ctx.fillStyle = '#dc2626'
  ctx.beginPath(); ctx.moveTo(BIRD_R - 2, -2); ctx.lineTo(BIRD_R + 8, 0); ctx.lineTo(BIRD_R - 2, 3); ctx.closePath(); ctx.fill()
  // rocket flame
  if (settings.flavour === 'rocket') {
    ctx.fillStyle = 'rgba(251, 146, 60, 0.85)'
    ctx.beginPath(); ctx.moveTo(-BIRD_R - 1, -3); ctx.lineTo(-BIRD_R - 8 - rng() * 4, 0); ctx.lineTo(-BIRD_R - 1, 3); ctx.closePath(); ctx.fill()
  }
  ctx.restore()
}

function loop(now: number) {
  if (cv.value === null) return
  const dt = Math.min(64, now - last)
  last = now
  if (props.paused || settingsOpen.value || calibrating.value || showAnalytics.value) {
    raf = requestAnimationFrame(loop)
    return
  }
  pollGamepad()
  attemptFlapBuffered(now)
  let factor = 1
  if (postDeathSlowMo > 0 && !settings.reducedMotion) {
    factor = 0.2; postDeathSlowMo--
  }
  acc += dt * factor
  let safety = 0
  while (acc >= FIXED_DT && safety < 5) {
    acc -= FIXED_DT
    if (alive) physicsStep()
    safety++
  }
  if (shakeT > 0) shakeT--
  draw()
  raf = requestAnimationFrame(loop)
}

// calibration
const calRound = ref(1)
const calGo = ref(false)
const calAvg = ref(0)
let calStart = 0
const calResults: number[] = []
function openCalibration() {
  calibrating.value = true
  calRound.value = 1
  calGo.value = false
  calAvg.value = 0
  calResults.length = 0
  scheduleCalRound()
}
function scheduleCalRound() {
  if (calRound.value > 5) {
    finishCalibration(); return
  }
  setTimeout(() => {
    calGo.value = true
    calStart = performance.now()
  }, 800 + Math.random() * 1500)
}
function onCalTap() {
  if (!calGo.value) {
    // false start
    return
  }
  const dt = performance.now() - calStart
  calResults.push(dt)
  calGo.value = false
  calRound.value++
  scheduleCalRound()
}
function finishCalibration() {
  if (!calResults.length) return
  const avg = Math.round(calResults.reduce((a, b) => a + b, 0) / calResults.length)
  calAvg.value = avg
  // map: 200ms->1.4 (snappy), 400ms->0.8 (heavier)
  const v = Math.max(0.6, Math.min(1.6, 1.4 - (avg - 200) / 250))
  settings.sensitivity = parseFloat(v.toFixed(2))
  saveSettings()
}

// instant replay
function instantReplay() {
  showAnalytics.value = false
  reset()
}

function finishToShell() {
  showAnalytics.value = false
  props.onGameOver(score)
}

onMounted(() => {
  cv.value!.width = W
  cv.value!.height = H
  reset()
  raf = requestAnimationFrame(loop)
  root.value?.focus()
  injectColorblindFilters()
})
onUnmounted(() => {
  cancelAnimationFrame(raf)
  disposeAudio()
})
watch(() => props.running, (v) => {
  if (v) {
    reset()
    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(loop)
  }
})

function injectColorblindFilters() {
  if (document.getElementById('flappy-cb-filters')) return
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('id', 'flappy-cb-filters')
  svg.setAttribute('aria-hidden', 'true')
  svg.style.position = 'absolute'
  svg.style.width = '0'; svg.style.height = '0'
  svg.innerHTML = `
    <filter id="cb-prot"><feColorMatrix type="matrix" values="0.567,0.433,0,0,0  0.558,0.442,0,0,0  0,0.242,0.758,0,0  0,0,0,1,0"/></filter>
    <filter id="cb-deut"><feColorMatrix type="matrix" values="0.625,0.375,0,0,0  0.7,0.3,0,0,0  0,0.3,0.7,0,0  0,0,0,1,0"/></filter>
    <filter id="cb-trit"><feColorMatrix type="matrix" values="0.95,0.05,0,0,0  0,0.433,0.567,0,0  0,0.475,0.525,0,0  0,0,0,1,0"/></filter>
  `
  document.body.appendChild(svg)
}
</script>

<style scoped lang="scss">
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; touch-action: manipulation; position: relative; }
.canvas-frame { position: relative; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4); max-width: 100%; height: auto; touch-action: manipulation; cursor: pointer; display: block; }
.shake { animation: shake 0.4s ease; }
@keyframes shake {
  0%, 100% { transform: translate(0, 0); }
  20% { transform: translate(-3px, 2px); } 40% { transform: translate(3px, -2px); }
  60% { transform: translate(-2px, -2px); } 80% { transform: translate(2px, 3px); }
}
.canvas-frame.focus .cv { box-shadow: 0 0 0 2px rgba(250, 204, 21, 0.55), 0 12px 30px rgba(0, 0, 0, 0.5); }
.pulse { position: absolute; inset: 0; border-radius: 14px; background: rgba(250, 204, 21, 0.18); pointer-events: none; animation: pulse 0.18s ease; }
@keyframes pulse { 0% { opacity: 0; } 30% { opacity: 1; } 100% { opacity: 0; } }
.settings-btn { position: absolute; top: 8px; right: 8px; width: 30px; height: 30px; border-radius: 50%; background: rgba(0, 0, 0, 0.5); color: #fff; border: 1px solid rgba(255, 255, 255, 0.2); cursor: pointer; font-size: 14px; line-height: 1; }
.hint { color: rgba(255, 255, 255, 0.7); font-size: 0.85rem; text-align: center; }
.ghost-tag { background: rgba(250, 204, 21, 0.18); padding: 3px 10px; border-radius: 999px; }

.overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(8px); z-index: 30; display: flex; align-items: center; justify-content: center; padding: 16px; }
.panel { background: rgba(15, 23, 42, 0.95); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 18px; max-width: 460px; width: 100%; max-height: 90vh; overflow-y: auto; color: #fff; }
.panel.small { max-width: 320px; }
.p-head { display: flex; align-items: center; padding: 12px 16px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.p-title { flex: 1; font-weight: 700; font-size: 1.05rem; }
.x { background: transparent; border: none; color: rgba(255, 255, 255, 0.85); font-size: 1.1rem; cursor: pointer; }
.p-body { padding: 14px 16px; display: flex; flex-direction: column; gap: 12px; }
.row { display: flex; flex-direction: column; gap: 6px; }
.row label { font-size: 0.85rem; opacity: 0.85; }
.row input[type=range] { width: 100%; }
.check { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.hint-sm { font-size: 0.74rem; opacity: 0.65; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip { background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12); color: #fff; border-radius: 999px; padding: 5px 12px; font-size: 0.8rem; cursor: pointer; }
.chip.on { background: linear-gradient(135deg, #6366f1, #a855f7); border-color: transparent; }
.chip.locked { opacity: 0.55; cursor: not-allowed; }
.btn { background: rgba(255, 255, 255, 0.1); border: 1px solid rgba(255, 255, 255, 0.18); color: #fff; padding: 8px 14px; border-radius: 10px; cursor: pointer; font-size: 0.9rem; }
.btn.primary { background: linear-gradient(135deg, #6366f1, #a855f7); border-color: transparent; }
.meter { display: flex; flex-direction: column; gap: 4px; }
.meter > span { font-size: 0.85rem; opacity: 0.85; }
.meter .bar { background: rgba(255, 255, 255, 0.08); border-radius: 999px; height: 8px; overflow: hidden; }
.meter .bar > div { height: 100%; background: linear-gradient(90deg, #34d399, #f59e0b, #ef4444); transition: width 0.3s; }

.cal-stage { background: #1e293b; border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 12px; padding: 30px 16px; text-align: center; font-size: 1rem; cursor: pointer; transition: background 0.1s; min-height: 60px; display: flex; align-items: center; justify-content: center; }
.cal-stage.go { background: #16a34a; font-weight: 700; }

.r-msg { background: rgba(96, 165, 250, 0.15); border: 1px solid rgba(96, 165, 250, 0.35); border-radius: 10px; padding: 10px; font-size: 0.88rem; }
.graphs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.g-block { background: rgba(255, 255, 255, 0.04); border-radius: 10px; padding: 8px; }
.g-title { font-size: 0.74rem; opacity: 0.7; margin-bottom: 4px; }
.g-canvas { width: 100%; height: 80px; display: block; }
.r-stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.r-stats-row > div { background: rgba(255, 255, 255, 0.06); border-radius: 8px; padding: 6px 4px; text-align: center; }
.r-stats-row span { display: block; font-size: 0.65rem; opacity: 0.6; }
.r-stats-row b { font-size: 0.85rem; }
.r-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.unlock-banner { background: linear-gradient(135deg, rgba(250, 204, 21, 0.25), rgba(251, 146, 60, 0.25)); border: 1px solid rgba(250, 204, 21, 0.4); border-radius: 10px; padding: 10px; text-align: center; font-weight: 700; color: #fde68a; }
@media (max-width: 480px) {
  .graphs { grid-template-columns: 1fr; }
  .r-stats-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
