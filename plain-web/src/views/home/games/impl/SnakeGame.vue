<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <div
      class="canvas-frame"
      :class="[`theme-${settings.theme}`, { shake: shakeT > 0, low: settings.batterySaver }]"
      @mousedown.prevent="onPointerDown"
      @mousemove.prevent="onPointerMove"
      @mouseup.prevent="onPointerUp"
      @touchstart.prevent="onPointerDown"
      @touchmove.prevent="onPointerMove"
      @touchend.prevent="onPointerUp"
    >
      <canvas ref="cv" class="cv" :style="{ filter: cbFilter }" />
      <button class="settings-btn" @click.stop="settingsOpen = true" @touchstart.stop @touchend.stop @mousedown.stop @mouseup.stop title="Settings">⚙</button>
      <div class="hud" :class="{ left: settings.leftHanded }">
        <div class="hud-pill"><span>SCORE</span><b>{{ score }}</b></div>
        <div class="hud-pill" v-if="combo > 1"><span>COMBO</span><b>x{{ combo }}</b></div>
        <div class="hud-pill" v-if="props.mode === 'time'"><span>TIME</span><b>{{ Math.max(0, Math.ceil(timeLeftMs / 1000)) }}s</b></div>
        <div class="hud-pill" v-if="snakeLen > 3"><span>LEN</span><b>{{ snakeLen }}</b></div>
      </div>
      <div class="powers">
        <div v-for="p in activePowers" :key="p.kind + p.id" class="power-pill" :class="p.kind">
          <span>{{ powerIcon(p.kind) }} {{ powerLabel(p.kind) }}</span>
          <b>{{ Math.ceil(p.timeLeft / 1000) }}s</b>
        </div>
      </div>
    </div>

    <div class="hint">
      <span v-if="hasGhost" class="tag">👻 Ghost run loaded</span>
      <span v-else-if="dailySeedActive" class="tag">🌅 Daily seed</span>
      <span v-else-if="sharedSeedActive" class="tag">🔗 Shared seed</span>
      <span v-else>{{ hintText }}</span>
    </div>

    <div class="dpad" v-if="settings.showDpad" :class="{ left: settings.leftHanded }">
      <button @click="set(0, -1)" @touchstart.stop @touchend.stop>▲</button>
      <div class="row">
        <button @click="set(-1, 0)" @touchstart.stop @touchend.stop>◀</button>
        <button class="undo" @click="undoMove" @touchstart.stop @touchend.stop :disabled="!canUndo">↺</button>
        <button @click="set(1, 0)" @touchstart.stop @touchend.stop>▶</button>
      </div>
      <button @click="set(0, 1)" @touchstart.stop @touchend.stop>▼</button>
    </div>

    <!-- Settings overlay -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false" @touchstart.stop @touchend.stop @mousedown.stop @mouseup.stop @wheel.stop>
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Viper · Settings</div>
          <button class="x" @click="settingsOpen = false">✕</button>
        </div>
        <div class="p-body">
          <div class="row">
            <label>Movement <b>{{ settings.movement === 'grid' ? 'Grid' : 'Smooth vector' }}</b></label>
            <div class="chips">
              <button class="chip" :class="{ on: settings.movement === 'grid' }" @click="settings.movement = 'grid'; saveSettings(); softReset()">Grid</button>
              <button class="chip" :class="{ on: settings.movement === 'smooth' }" @click="settings.movement = 'smooth'; saveSettings(); softReset()">Smooth</button>
            </div>
          </div>
          <div class="row">
            <label>Base speed <b>{{ settings.baseSpeed }}</b></label>
            <div class="chips">
              <button class="chip" :class="{ on: settings.baseSpeed === 'slow' }" @click="settings.baseSpeed = 'slow'; saveSettings()">Slow</button>
              <button class="chip" :class="{ on: settings.baseSpeed === 'normal' }" @click="settings.baseSpeed = 'normal'; saveSettings()">Normal</button>
              <button class="chip" :class="{ on: settings.baseSpeed === 'fast' }" @click="settings.baseSpeed = 'fast'; saveSettings()">Fast</button>
            </div>
          </div>
          <div class="row">
            <label>Swipe sensitivity <b>{{ settings.swipeSens.toFixed(2) }}</b></label>
            <input type="range" min="0.4" max="2.0" step="0.05" v-model.number="settings.swipeSens" @change="saveSettings" />
            <div class="hint-sm">Lower = longer swipe needed. Higher = quick flicks turn.</div>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.deadZone" @change="saveSettings" /> Centre dead-zone (rest thumb)</label>
            <label class="check"><input type="checkbox" v-model="settings.wrap" @change="saveSettings" /> Wrap-around walls (snake exits one side, enters the other)</label>
            <label class="check"><input type="checkbox" v-model="settings.tilt" @change="onTiltToggle" /> Tilt steering (gyro left/right)</label>
            <label class="check"><input type="checkbox" v-model="settings.showDpad" @change="saveSettings" /> Show on-screen D-pad (accessibility)</label>
            <label class="check"><input type="checkbox" v-model="settings.leftHanded" @change="saveSettings" /> Left-handed UI (mirror controls)</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.haptics" @change="saveSettings" /> Haptic feedback</label>
            <label class="check"><input type="checkbox" v-model="settings.layeredMusic" @change="saveSettings" /> Adaptive layered music</label>
            <label class="check"><input type="checkbox" v-model="settings.swipeTick" @change="saveSettings" /> Swipe tick sound</label>
            <label class="check"><input type="checkbox" v-model="settings.voiceHints" @change="saveSettings" /> Voice hints ("Nice combo")</label>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.reducedMotion" @change="saveSettings" /> Reduced motion (no shake / particles)</label>
            <label class="check"><input type="checkbox" v-model="settings.highContrast" @change="saveSettings" /> High contrast outlines</label>
            <label class="check"><input type="checkbox" v-model="settings.batterySaver" @change="saveSettings" /> Battery saver (30 fps, no glow)</label>
            <label class="check"><input type="checkbox" v-model="settings.assistPredict" @change="saveSettings" /> Assist: predict next food (pulsing ring)</label>
          </div>
          <div class="row">
            <label>Colourblind</label>
            <div class="chips">
              <button v-for="m in cbModes" :key="m" class="chip" :class="{ on: settings.colorblind === m }" @click="settings.colorblind = m; saveSettings()">{{ m }}</button>
            </div>
          </div>
          <div class="row">
            <label>Theme (arena)</label>
            <div class="chips">
              <button v-for="t in themes" :key="t.id" class="chip" :class="{ on: settings.theme === t.id, locked: !isThemeUnlocked(t.id) }" :title="isThemeUnlocked(t.id) ? t.desc : `Locked: ${t.unlock}`" @click="if (isThemeUnlocked(t.id)) { settings.theme = t.id; saveSettings() }">
                {{ isThemeUnlocked(t.id) ? t.label : '🔒 ' + t.label }}
              </button>
            </div>
            <div class="hint-sm">{{ currentThemeDesc }}</div>
          </div>
          <div class="row">
            <label>Snake skin</label>
            <div class="chips">
              <button v-for="s in skins" :key="s.id" class="chip" :class="{ on: settings.skin === s.id, locked: !isSkinUnlocked(s.id) }" :title="isSkinUnlocked(s.id) ? s.desc : `Locked: ${s.unlock}`" @click="if (isSkinUnlocked(s.id)) { settings.skin = s.id; saveSettings() }">
                {{ isSkinUnlocked(s.id) ? s.label : '🔒 ' + s.label }}
              </button>
            </div>
          </div>
          <div class="row">
            <label>Trail effect</label>
            <div class="chips">
              <button v-for="tr in trails" :key="tr" class="chip" :class="{ on: settings.trail === tr }" @click="settings.trail = tr; saveSettings()">{{ tr }}</button>
            </div>
          </div>
          <div class="row">
            <button class="btn" @click="openCalibration">📐 Calibrate swipe sensitivity</button>
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
    <div v-if="calibrating" class="overlay" @click.self="calibrating = false" @touchstart.stop @touchend.stop @mousedown.stop @mouseup.stop @wheel.stop>
      <div class="panel small">
        <div class="p-title">Swipe calibration</div>
        <div class="cal-stage" @touchstart.prevent="onCalStart" @touchmove.prevent="onCalMove" @touchend.prevent="onCalEnd" @mousedown.prevent="onCalStart" @mousemove.prevent="onCalMove" @mouseup.prevent="onCalEnd">
          <span v-if="calRound <= 5">Swipe across in any direction at your natural speed. ({{ calRound }}/5)</span>
          <span v-else>Avg swipe length: {{ Math.round(calAvg) }}px → sensitivity {{ settings.swipeSens.toFixed(2) }}</span>
        </div>
        <button class="btn" @click="calibrating = false">Done</button>
      </div>
    </div>

    <!-- Post-death analytics overlay -->
    <div v-if="showAnalytics" class="overlay" @touchstart.stop @touchend.stop @mousedown.stop @mouseup.stop @wheel.stop>
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Run analysis · Score {{ score }}</div>
          <button class="x" @click="finishToShell">Continue ▸</button>
        </div>
        <div class="p-body">
          <div class="r-msg">{{ deathSuggestion }}</div>
          <div class="graphs">
            <div class="g-block">
              <div class="g-title">Path heatmap (where the snake spent time)</div>
              <canvas ref="heatChart" class="g-canvas"></canvas>
            </div>
            <div class="g-block">
              <div class="g-title">Reactions per second (turns)</div>
              <canvas ref="rxChart" class="g-canvas"></canvas>
            </div>
          </div>
          <div class="r-stats-row">
            <div><span>Length</span><b>{{ snakeLen }}</b></div>
            <div><span>Crashed at</span><b>{{ crashInfo }}</b></div>
            <div><span>Turns</span><b>{{ turnLog.length }}</b></div>
            <div><span>Avg dist/food</span><b>{{ avgDistanceLabel }}</b></div>
          </div>
          <div class="r-actions">
            <button class="btn primary" @click="instantReplay">↺ Instant replay</button>
            <button class="btn" @click="watchReplay">▶ Watch ghost replay</button>
            <button class="btn" @click="finishToShell">Continue</button>
          </div>
          <div v-if="newUnlocks.length" class="unlock-banner">🎉 Unlocked: {{ newUnlocks.join(', ') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useGamesStore } from '../gamesStore'
import toast from '@/components/toaster'

type CB = 'off' | 'protanopia' | 'deuteranopia' | 'tritanopia'
type Theme = 'cyber' | 'zen' | 'toxic' | 'void'
type Skin = 'neon' | 'crystal' | 'lava' | 'ice' | 'ghost' | 'pixel' | 'golden'
type Trail = 'none' | 'sparkle' | 'fire' | 'smoke' | 'rainbow'
type Movement = 'grid' | 'smooth'
type FoodKind = 'normal' | 'golden' | 'poison' | 'timed' | 'magnetic'
type PowerKind = 'phase' | 'magnet' | 'shrink' | 'slow' | 'double'

interface Settings {
  movement: Movement
  baseSpeed: 'slow' | 'normal' | 'fast'
  swipeSens: number
  deadZone: boolean
  wrap: boolean
  tilt: boolean
  showDpad: boolean
  leftHanded: boolean
  haptics: boolean
  layeredMusic: boolean
  swipeTick: boolean
  voiceHints: boolean
  reducedMotion: boolean
  highContrast: boolean
  batterySaver: boolean
  assistPredict: boolean
  colorblind: CB
  theme: Theme
  skin: Skin
  trail: Trail
}
interface Unlocks { skins: Record<Skin, boolean>; themes: Record<Theme, boolean> }
interface Mmr { value: number; runs: { score: number; ts: number }[] }

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'classic' | 'time' | 'survival' | 'challenge'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()
const heatChart = ref<HTMLCanvasElement>()
const rxChart = ref<HTMLCanvasElement>()

const SETTINGS_KEY = 'snake_settings_v1'
const UNLOCKS_KEY = 'snake_unlocks_v1'
const MMR_KEY = 'snake_mmr_v1'
const DAILY_KEY = 'snake_daily_v1'

function defaultSettings(): Settings {
  return {
    movement: 'grid', baseSpeed: 'normal', swipeSens: 1.0, deadZone: false, wrap: false,
    tilt: false, showDpad: true, leftHanded: false, haptics: true, layeredMusic: true,
    swipeTick: false, voiceHints: false, reducedMotion: false, highContrast: false,
    batterySaver: false, assistPredict: false, colorblind: 'off', theme: 'cyber',
    skin: 'neon', trail: 'sparkle',
  }
}
function loadSettings(): Settings {
  try { const r = localStorage.getItem(SETTINGS_KEY); if (r) return { ...defaultSettings(), ...JSON.parse(r) } } catch { /* */ }
  return defaultSettings()
}
const settings = reactive<Settings>(loadSettings())
function saveSettings() { try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)) } catch { /* */ } }

function loadUnlocks(): Unlocks {
  const def: Unlocks = {
    skins: { neon: true, crystal: false, lava: false, ice: false, ghost: false, pixel: false, golden: false },
    themes: { cyber: true, zen: false, toxic: false, void: false },
  }
  try { const r = localStorage.getItem(UNLOCKS_KEY); if (r) { const j = JSON.parse(r); return { skins: { ...def.skins, ...(j.skins || {}) }, themes: { ...def.themes, ...(j.themes || {}) } } } } catch { /* */ }
  return def
}
function saveUnlocks(u: Unlocks) { try { localStorage.setItem(UNLOCKS_KEY, JSON.stringify(u)) } catch { /* */ } }
const unlocks = ref<Unlocks>(loadUnlocks())

function loadMmr(): Mmr {
  try { const r = localStorage.getItem(MMR_KEY); if (r) return JSON.parse(r) } catch { /* */ }
  return { value: 50, runs: [] }
}
function saveMmr(m: Mmr) { try { localStorage.setItem(MMR_KEY, JSON.stringify(m)) } catch { /* */ } }
const mmrState = ref<Mmr>(loadMmr())
const mmrValue = computed(() => mmrState.value.value)
const mmrLabel = computed(() => {
  const v = mmrState.value.value
  if (v < 25) return 'Adapting easier — slower start, larger food hitbox'
  if (v < 60) return 'Tuned to your skill level'
  if (v < 85) return 'Slightly tougher — moving obstacles, faster speed'
  return 'Pro mode — shrinking arena, frequent poison'
})

const cbModes: CB[] = ['off', 'protanopia', 'deuteranopia', 'tritanopia']
const cbFilter = computed(() => {
  switch (settings.colorblind) {
    case 'protanopia': return 'url(#snake-cb-prot)'
    case 'deuteranopia': return 'url(#snake-cb-deut)'
    case 'tritanopia': return 'url(#snake-cb-trit)'
    default: return 'none'
  }
})

const themes: { id: Theme; label: string; desc: string; unlock: string }[] = [
  { id: 'cyber', label: 'Cyber Grid', desc: 'Pulsing neon grid lines.', unlock: 'available from start' },
  { id: 'zen', label: 'Zen Garden', desc: 'Sand ripples react to motion.', unlock: 'play 5 runs' },
  { id: 'toxic', label: 'Toxic Waste', desc: 'Bubbling green pools.', unlock: 'eat 5 poison foods (survive a debuff)' },
  { id: 'void', label: 'Void', desc: 'Pure black — battery saver.', unlock: 'reach length 30' },
]
const skins: { id: Skin; label: string; desc: string; unlock: string }[] = [
  { id: 'neon',    label: 'Neon',    desc: 'Default cyan glow.', unlock: 'available from start' },
  { id: 'crystal', label: 'Crystal', desc: 'Translucent shimmer.', unlock: 'reach length 20' },
  { id: 'lava',    label: 'Lava',    desc: 'Molten orange / red.', unlock: 'score 200 in one run' },
  { id: 'ice',     label: 'Ice',     desc: 'Frozen blue / white.', unlock: 'survive 90 seconds' },
  { id: 'ghost',   label: 'Ghost',   desc: 'Semi-transparent.', unlock: 'use phase power 3 times in a run' },
  { id: 'pixel',   label: 'Pixel',   desc: '8-bit chunky.', unlock: 'play 10 runs' },
  { id: 'golden',  label: 'Golden',  desc: 'Pure gold trail.', unlock: 'eat 20 golden foods total' },
]
const trails: Trail[] = ['none', 'sparkle', 'fire', 'smoke', 'rainbow']
const currentThemeDesc = computed(() => themes.find((t) => t.id === settings.theme)?.desc || '')
function isSkinUnlocked(id: Skin) { return unlocks.value.skins[id] === true }
function isThemeUnlocked(id: Theme) { return unlocks.value.themes[id] === true }

let rngSeed = Math.floor(Math.random() * 2147483647)
function rng(): number {
  rngSeed |= 0
  rngSeed = (rngSeed + 0x6D2B79F5) | 0
  let t = rngSeed
  t = Math.imul(t ^ (t >>> 15), t | 1)
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
function setSeed(s: number) { rngSeed = s | 0 }
function todaySeedNumber(): number {
  const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate()
}
const sharedSeedActive = ref(false)
const dailySeedActive = ref(false)
const currentSeedNumber = ref(0)
const shareSeedString = computed(() => `viper-${currentSeedNumber.value.toString(36)}`)

function readUrlSeed(): number | null {
  try {
    const url = new URL(window.location.href)
    const s = url.searchParams.get('viperSeed')
    if (!s) return null
    if (s.startsWith('viper-')) { const n = parseInt(s.slice(6), 36); if (!Number.isNaN(n)) return n }
    const n2 = parseInt(s, 10); return Number.isFinite(n2) ? n2 : null
  } catch { return null }
}
function copyShareSeed() {
  try {
    const url = new URL(window.location.href)
    url.searchParams.set('viperSeed', shareSeedString.value)
    navigator.clipboard.writeText(url.toString())
    toast('Seed link copied — share it for a fixed maze!', 'success')
  } catch { toast('Could not copy link', 'error') }
}

const W = 360
const H = 540
const COLS = 18
const ROWS = 27
const CELL = W / COLS
const HUD_TOP = 0

interface Cell { x: number; y: number }
interface Food { x: number; y: number; kind: FoodKind; spawnT: number; vx?: number; vy?: number }
interface Power { id: number; kind: PowerKind; timeLeft: number }

let snakeGrid: Cell[] = []
let snakeSmooth: { x: number; y: number }[] = [] // continuous coordinates of segments (head first)
let dir = { x: 1, y: 0 }
let pendingDir = { x: 1, y: 0 }
let food: Food = { x: 5, y: 5, kind: 'normal', spawnT: 0 }
let powerUp: Food | null = null
let walls: Cell[] = []
let smoothPos = { x: 0, y: 0 }
let smoothVel = { x: 1, y: 0 }
let smoothBody: { x: number; y: number; t: number }[] = [] // trailing positions for smooth body
let alive = true
let started = false
let raf = 0
let last = 0
let acc = 0
let stepAcc = 0
let frameIdx = 0
let runStartTs = 0
let activePowersInternal: Power[] = []
let _powerId = 1
let particles: { x: number; y: number; vx: number; vy: number; life: number; max: number; color: string; size: number }[] = []
let popups: { x: number; y: number; text: string; life: number; color: string }[] = []
let arenaShrink = 0
let bgT = 0
let recordedMoves: { dir: { x: number; y: number }; frame: number; foodAt?: Cell }[] = []
let ghostMoves: { dir: { x: number; y: number }; frame: number; foodAt?: Cell }[] = []
let isReplaying = false
let pathHeat: number[] = new Array(COLS * ROWS).fill(0)
let turnLogInternal: { frame: number }[] = []
let crashInfoLocal = ''
let undoSnapshot: { snake: Cell[]; dir: { x: number; y: number }; food: Food } | null = null
let poisonControlsInverted = 0 // ms remaining
let goldenEatsInRun = 0
let phaseUsedInRun = 0
let poisonEatsInRun = 0
let comboRecharge = 0 // ms since last food
let arenaMovingObs: { x: number; y: number; dx: number; dy: number; phase: number }[] = []

const score_ = ref(0)
const combo = ref(1)
const snakeLen = ref(3)
const score = computed(() => score_.value)
const activePowers = ref<Power[]>([])
const showAnalytics = ref(false)
const newUnlocks = ref<string[]>([])
const settingsOpen = ref(false)
const calibrating = ref(false)
const shakeT = ref(0)
const hasGhost = ref(false)
const turnLog = ref<{ frame: number }[]>([])
const deathSuggestion = ref('')
const crashInfo = computed(() => crashInfoLocal)
const canUndo = computed(() => alive && undoSnapshot !== null)
const timeLeftMs = ref(0)
const avgDistanceLabel = computed(() => {
  if (recordedMoves.length === 0 || score_.value === 0) return '—'
  return `${(recordedMoves.length / Math.max(1, score_.value / 10)).toFixed(1)} steps`
})
const hintText = computed(() => {
  const c: string[] = []
  if (settings.movement === 'smooth') c.push('Swipe to steer (continuous)')
  else c.push('Swipe / arrows to turn')
  if (settings.tilt) c.push('tilt = lateral')
  if (settings.wrap) c.push('walls wrap')
  return c.join(' · ')
})

// difficulty params
const baseSpeedMap: Record<string, Record<string, number>> = {
  slow:   { easy: 180, medium: 150, hard: 130, insane: 110 },
  normal: { easy: 140, medium: 110, hard:  90, insane:  72 },
  fast:   { easy: 105, medium:  85, hard:  68, insane:  54 },
}
let stepIntervalMs = 110
function recomputeSpeed() {
  const set = baseSpeedMap[settings.baseSpeed] || baseSpeedMap.normal
  let s = set[props.difficulty] || 110
  // MMR adjust
  const skew = (mmrState.value.value - 50) / 100
  s = s * (1 - skew * 0.18)
  // active power slow
  if (activePowersInternal.find((p) => p.kind === 'slow')) s *= 1.6
  stepIntervalMs = Math.max(40, Math.min(260, s))
}

// === audio (layered) ===
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
    lpFilter = audioCtx.createBiquadFilter(); lpFilter.type = 'lowpass'; lpFilter.frequency.value = 8000; lpFilter.connect(masterGain)
    baseGain = audioCtx.createGain(); baseGain.gain.value = 0.5; baseGain.connect(lpFilter)
    beatGain = audioCtx.createGain(); beatGain.gain.value = 0.0; beatGain.connect(lpFilter)
    bassGain = audioCtx.createGain(); bassGain.gain.value = 0.0; bassGain.connect(lpFilter)
    baseOsc = audioCtx.createOscillator(); baseOsc.type = 'sine'; baseOsc.frequency.value = 196
    beatOsc = audioCtx.createOscillator(); beatOsc.type = 'triangle'; beatOsc.frequency.value = 392
    bassOsc = audioCtx.createOscillator(); bassOsc.type = 'sine'; bassOsc.frequency.value = 49
    baseOsc.connect(baseGain); beatOsc.connect(beatGain); bassOsc.connect(bassGain)
    baseOsc.start(); beatOsc.start(); bassOsc.start()
  } catch { /* ignore */ }
  return audioCtx
}
function updateMusic() {
  if (!audioCtx || !beatGain || !bassGain) return
  const now = audioCtx.currentTime
  const beat = score_.value >= 30 ? 0.32 : score_.value >= 10 ? 0.18 : 0.0
  const bass = score_.value >= 60 ? 0.28 : 0.0
  beatGain.gain.linearRampToValueAtTime(beat, now + 0.5)
  bassGain.gain.linearRampToValueAtTime(bass, now + 0.5)
}
function muteMusicAfterDeath() {
  if (!masterGain || !audioCtx || !lpFilter) return
  const now = audioCtx.currentTime
  masterGain.gain.cancelScheduledValues(now)
  masterGain.gain.setValueAtTime(masterGain.gain.value, now)
  masterGain.gain.linearRampToValueAtTime(0, now + 0.05)
  masterGain.gain.linearRampToValueAtTime(0.04, now + 1.5)
  lpFilter.frequency.cancelScheduledValues(now)
  lpFilter.frequency.setValueAtTime(8000, now)
  lpFilter.frequency.exponentialRampToValueAtTime(380, now + 1.0)
  lpFilter.frequency.linearRampToValueAtTime(8000, now + 2.5)
}
function disposeAudio() {
  try { baseOsc?.stop(); beatOsc?.stop(); bassOsc?.stop() } catch { /* */ }
  try { audioCtx?.close() } catch { /* */ }
  audioCtx = null; baseOsc = beatOsc = bassOsc = null
  baseGain = beatGain = bassGain = masterGain = null; lpFilter = null
}

// === voice (TTS) ===
function speak(text: string) {
  if (!settings.voiceHints || !store.sound) return
  try {
    const u = new SpeechSynthesisUtterance(text)
    u.volume = 0.5; u.rate = 1.05; u.pitch = 1.1
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(u)
  } catch { /* */ }
}

function powerLabel(k: PowerKind): string {
  return ({ phase: 'Phase', magnet: 'Magnet', shrink: 'Shrunk', slow: 'Slow', double: 'x2' } as Record<PowerKind, string>)[k]
}
function powerIcon(k: PowerKind): string {
  return ({ phase: '👻', magnet: '🧲', shrink: '✂', slow: '⏳', double: '✨' } as Record<PowerKind, string>)[k]
}

// === core flow ===
function softReset() { if (!alive) return; reset() }

function reset() {
  // seed
  const url = readUrlSeed()
  if (url != null) {
    setSeed(url); sharedSeedActive.value = true; dailySeedActive.value = false; currentSeedNumber.value = url
  } else if (props.mode === 'challenge' || rng() < 0.33) {
    const ds = todaySeedNumber()
    setSeed(ds); dailySeedActive.value = true; sharedSeedActive.value = false; currentSeedNumber.value = ds
  } else {
    const r = Math.floor(Math.random() * 2147483647)
    setSeed(r); dailySeedActive.value = false; sharedSeedActive.value = false; currentSeedNumber.value = r
  }

  snakeGrid = [
    { x: Math.floor(COLS / 2),     y: Math.floor(ROWS / 2) },
    { x: Math.floor(COLS / 2) - 1, y: Math.floor(ROWS / 2) },
    { x: Math.floor(COLS / 2) - 2, y: Math.floor(ROWS / 2) },
  ]
  smoothPos = { x: snakeGrid[0].x * CELL + CELL / 2, y: snakeGrid[0].y * CELL + CELL / 2 }
  smoothVel = { x: 1, y: 0 }
  smoothBody = []
  for (let i = 0; i < 3; i++) smoothBody.push({ x: smoothPos.x - i * CELL, y: smoothPos.y, t: -i })
  dir = { x: 1, y: 0 }
  pendingDir = { x: 1, y: 0 }
  walls = []
  arenaMovingObs = []
  particles = []
  popups = []
  alive = true
  started = false
  score_.value = 0
  combo.value = 1
  snakeLen.value = 3
  recordedMoves = []
  pathHeat = new Array(COLS * ROWS).fill(0)
  turnLogInternal = []
  turnLog.value = []
  showAnalytics.value = false
  newUnlocks.value = []
  undoSnapshot = null
  poisonControlsInverted = 0
  goldenEatsInRun = 0
  phaseUsedInRun = 0
  poisonEatsInRun = 0
  comboRecharge = 0
  arenaShrink = 0
  bgT = 0
  frameIdx = 0
  shakeT.value = 0
  activePowersInternal = []
  activePowers.value = []
  isReplaying = false
  timeLeftMs.value = props.mode === 'time' ? 60000 : 0
  if (props.mode === 'challenge') buildMaze()
  placeFood()
  recomputeSpeed()
  // ghost
  const g = store.getGhost<{ moves: { dir: { x: number; y: number }; frame: number; foodAt?: Cell }[]; difficulty: string; score: number }>('snake')
  if (g && g.moves && g.difficulty === props.difficulty) {
    ghostMoves = g.moves; hasGhost.value = true
  } else { ghostMoves = []; hasGhost.value = false }
  ensureAudio()
  if (audioCtx && masterGain) {
    masterGain.gain.cancelScheduledValues(audioCtx.currentTime)
    masterGain.gain.setValueAtTime(0.04, audioCtx.currentTime)
    if (lpFilter) lpFilter.frequency.setValueAtTime(8000, audioCtx.currentTime)
  }
  updateMusic()
  runStartTs = performance.now()
  last = performance.now()
  acc = 0; stepAcc = 0
  props.onScore(0)
}

function buildMaze() {
  const n = 6 + Math.floor(rng() * 6)
  for (let i = 0; i < n; i++) {
    const w = { x: 1 + Math.floor(rng() * (COLS - 2)), y: 1 + Math.floor(rng() * (ROWS - 2)) }
    if (Math.abs(w.x - snakeGrid[0].x) < 2 && Math.abs(w.y - snakeGrid[0].y) < 2) continue
    walls.push(w)
  }
  // moving obstacles only at high MMR
  if (mmrState.value.value > 60) {
    const m = 1 + Math.floor(rng() * 3)
    for (let i = 0; i < m; i++) {
      arenaMovingObs.push({ x: 2 + Math.floor(rng() * (COLS - 4)), y: 2 + Math.floor(rng() * (ROWS - 4)), dx: rng() < 0.5 ? -1 : 1, dy: 0, phase: rng() * Math.PI * 2 })
    }
  }
}

function isFreeCell(x: number, y: number): boolean {
  if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false
  if (snakeGrid.find((s) => s.x === x && s.y === y)) return false
  if (walls.find((w) => w.x === x && w.y === y)) return false
  return true
}

function placeFood() {
  // determine food kind: poison rare if MMR high, golden rare, timed sometimes, magnetic rare
  const r = rng()
  let kind: FoodKind = 'normal'
  if (mmrState.value.value > 70 && r < 0.18) kind = 'poison'
  else if (r < 0.10) kind = 'golden'
  else if (r < 0.16) kind = 'timed'
  else if (r < 0.22) kind = 'magnetic'
  for (let i = 0; i < 200; i++) {
    const fx = Math.floor(rng() * COLS), fy = Math.floor(rng() * ROWS)
    if (isFreeCell(fx, fy)) {
      food = { x: fx, y: fy, kind, spawnT: frameIdx, vx: 0, vy: 0 }
      return
    }
  }
}

function set(x: number, y: number) {
  if (!alive) return
  if (snakeGrid.length > 1 && dir.x === -x && dir.y === -y) return
  // possibly invert if poison
  if (poisonControlsInverted > 0) { x = -x; y = -y }
  pendingDir = { x, y }
  if (!started) started = true
  if (settings.swipeTick) store.beep('tick')
}

function applyPower(kind: PowerKind) {
  store.beep('power')
  if (settings.haptics) store.vibrate(40)
  const id = _powerId++
  let timeLeft = 5000
  if (kind === 'slow') timeLeft = 6000
  if (kind === 'double') timeLeft = 8000
  if (kind === 'phase') { timeLeft = 5000; phaseUsedInRun++ }
  if (kind === 'magnet') timeLeft = 6000
  if (kind === 'shrink') {
    const target = Math.max(3, Math.floor(snakeGrid.length / 2))
    snakeGrid = snakeGrid.slice(0, target)
    smoothBody = smoothBody.slice(0, target)
    snakeLen.value = snakeGrid.length
    timeLeft = 1500
    floatPopup(snakeGrid[0].x * CELL, snakeGrid[0].y * CELL, '✂ shrink', '#22c55e')
  }
  activePowersInternal.push({ id, kind, timeLeft })
  activePowers.value = activePowersInternal.slice()
  recomputeSpeed()
}

function eatFood(f: Food) {
  let pts = 10
  if (f.kind === 'golden') { pts = 30; goldenEatsInRun++; floatPopup(f.x * CELL, f.y * CELL, '+30 GOLD', '#facc15') ; comboRecharge = 0 }
  else if (f.kind === 'poison') { pts = -5; poisonEatsInRun++; poisonControlsInverted = 4000; floatPopup(f.x * CELL, f.y * CELL, '☠ INVERT', '#a855f7'); store.beep('lose') }
  else if (f.kind === 'timed') { pts = 15; if (props.mode === 'time') timeLeftMs.value += 5000; floatPopup(f.x * CELL, f.y * CELL, '+15 TIMED', '#38bdf8') }
  else if (f.kind === 'magnetic') { pts = 20; applyPower('magnet'); floatPopup(f.x * CELL, f.y * CELL, '🧲 MAGNET', '#f472b6') }
  else floatPopup(f.x * CELL, f.y * CELL, '+10', '#10b981')

  // combo
  if (f.kind !== 'poison') {
    if (comboRecharge < 3000) combo.value = Math.min(5, combo.value + 1)
    else combo.value = 1
    comboRecharge = 0
  } else {
    combo.value = 1
  }
  pts = pts * combo.value
  score_.value = Math.max(0, score_.value + pts)
  props.onScore(score_.value)
  store.beep(pts > 0 ? 'tick' : 'lose')
  if (settings.haptics) store.vibrate(15)

  // particles ripple
  if (!settings.reducedMotion) {
    for (let i = 0; i < 14; i++) {
      const a = (i / 14) * Math.PI * 2
      particles.push({ x: f.x * CELL + CELL / 2, y: f.y * CELL + CELL / 2, vx: Math.cos(a) * 1.6, vy: Math.sin(a) * 1.6, life: 24, max: 24, color: foodColor(f.kind), size: 2 })
    }
  }
  // grow / shrink
  if (f.kind === 'poison') {
    if (snakeGrid.length > 4) snakeGrid = snakeGrid.slice(0, snakeGrid.length - 1)
    if (smoothBody.length > 4) smoothBody = smoothBody.slice(0, smoothBody.length - 1)
  } else {
    const grow = f.kind === 'golden' ? 3 : 1
    for (let g = 0; g < grow; g++) {
      const tail = snakeGrid[snakeGrid.length - 1]
      snakeGrid.push({ x: tail.x, y: tail.y })
      const sb = smoothBody[smoothBody.length - 1]
      if (sb) smoothBody.push({ x: sb.x, y: sb.y, t: sb.t - 1 })
    }
  }
  snakeLen.value = snakeGrid.length
  if (combo.value >= 3) {
    speak('Nice combo')
  }
  updateMusic()
  placeFood()
}

function foodColor(k: FoodKind): string {
  switch (k) {
    case 'golden': return 'rgba(250, 204, 21, 0.9)'
    case 'poison': return 'rgba(168, 85, 247, 0.9)'
    case 'timed':  return 'rgba(56, 189, 248, 0.9)'
    case 'magnetic': return 'rgba(244, 114, 182, 0.9)'
    default: return 'rgba(236, 72, 153, 0.9)'
  }
}

function floatPopup(x: number, y: number, text: string, color: string) {
  if (settings.reducedMotion) return
  popups.push({ x, y, text, life: 50, color })
}

function snapshotForUndo() {
  undoSnapshot = {
    snake: snakeGrid.map((s) => ({ ...s })),
    dir: { ...dir },
    food: { ...food },
  }
}
function undoMove() {
  if (!undoSnapshot) return
  snakeGrid = undoSnapshot.snake.map((s) => ({ ...s }))
  dir = { ...undoSnapshot.dir }
  pendingDir = { ...undoSnapshot.dir }
  food = { ...undoSnapshot.food }
  snakeLen.value = snakeGrid.length
  undoSnapshot = null
  if (settings.haptics) store.vibrate(20)
}

function stepGrid() {
  if (!alive || !started) return
  snapshotForUndo()
  // record turn
  if (dir.x !== pendingDir.x || dir.y !== pendingDir.y) {
    turnLogInternal.push({ frame: frameIdx })
    turnLog.value = turnLogInternal.slice()
  }
  dir = pendingDir
  recordedMoves.push({ dir: { ...dir }, frame: frameIdx, foodAt: { x: food.x, y: food.y } })

  // magnetic auto-attract food
  if (activePowersInternal.find((p) => p.kind === 'magnet')) {
    const head = snakeGrid[0]
    const dx = head.x - food.x, dy = head.y - food.y
    if (Math.abs(dx) <= 4 && Math.abs(dy) <= 4 && (dx !== 0 || dy !== 0)) {
      food.x += Math.sign(dx); food.y += Math.sign(dy)
    }
  }
  // timed food expires/hops
  if (food.kind === 'timed' && frameIdx - food.spawnT > 60 * 5) placeFood()

  let head = { x: snakeGrid[0].x + dir.x, y: snakeGrid[0].y + dir.y }
  // wrap or wall
  if (settings.wrap) {
    if (head.x < 0) head.x = COLS - 1
    if (head.x >= COLS) head.x = 0
    if (head.y < 0) head.y = ROWS - 1
    if (head.y >= ROWS) head.y = 0
  } else if (head.x < 0 || head.x >= COLS || head.y < 0 || head.y >= ROWS) {
    crashInfoLocal = `wall @ frame ${frameIdx}`
    return die()
  }
  // self collision (unless phase)
  if (!activePowersInternal.find((p) => p.kind === 'phase')) {
    if (snakeGrid.find((s, i) => i > 0 && s.x === head.x && s.y === head.y)) {
      crashInfoLocal = `tail seg ${snakeGrid.findIndex((s, i) => i > 0 && s.x === head.x && s.y === head.y)} @ frame ${frameIdx}`
      return die()
    }
  }
  if (walls.find((w) => w.x === head.x && w.y === head.y)) {
    crashInfoLocal = `obstacle @ frame ${frameIdx}`
    return die()
  }
  if (arenaMovingObs.find((o) => Math.round(o.x) === head.x && Math.round(o.y) === head.y)) {
    crashInfoLocal = `moving obs @ frame ${frameIdx}`
    return die()
  }
  snakeGrid.unshift(head)
  pathHeat[head.y * COLS + head.x] = (pathHeat[head.y * COLS + head.x] || 0) + 1
  if (head.x === food.x && head.y === food.y) {
    eatFood(food)
  } else {
    snakeGrid.pop()
  }
  // power-up grab: 5% chance to spawn a power-up after 6 foods
  if (!powerUp && rng() < 0.005 && score_.value > 30) {
    const kinds: PowerKind[] = ['phase', 'slow', 'double']
    const k = kinds[Math.floor(rng() * kinds.length)]
    for (let i = 0; i < 80; i++) {
      const px = Math.floor(rng() * COLS), py = Math.floor(rng() * ROWS)
      if (isFreeCell(px, py)) { powerUp = { x: px, y: py, kind: 'normal', spawnT: frameIdx, vx: 0, vy: 0 }; ;(powerUp as unknown as { _power: PowerKind })._power = k; break }
    }
  }
  if (powerUp && head.x === powerUp.x && head.y === powerUp.y) {
    applyPower((powerUp as unknown as { _power: PowerKind })._power)
    powerUp = null
  }
}

function stepSmooth(dtMs: number) {
  if (!alive || !started) return
  // turn velocity smoothly toward pendingDir
  if (pendingDir.x !== smoothVel.x || pendingDir.y !== smoothVel.y) {
    smoothVel = { x: pendingDir.x, y: pendingDir.y }
    turnLogInternal.push({ frame: frameIdx })
    turnLog.value = turnLogInternal.slice()
  }
  const speed = (CELL / stepIntervalMs) * dtMs
  smoothPos.x += smoothVel.x * speed
  smoothPos.y += smoothVel.y * speed
  if (settings.wrap) {
    if (smoothPos.x < 0) smoothPos.x = W
    if (smoothPos.x > W) smoothPos.x = 0
    if (smoothPos.y < 0) smoothPos.y = H
    if (smoothPos.y > H) smoothPos.y = 0
  } else if (smoothPos.x < 0 || smoothPos.x > W || smoothPos.y < 0 || smoothPos.y > H) {
    crashInfoLocal = `wall @ smooth frame ${frameIdx}`; return die()
  }
  smoothBody.unshift({ x: smoothPos.x, y: smoothPos.y, t: frameIdx })
  // limit body length so each segment is roughly CELL apart and there are snakeLen segments
  while (smoothBody.length > snakeLen.value * 4) smoothBody.pop()

  // self-collision: head vs body (skip near segments)
  if (!activePowersInternal.find((p) => p.kind === 'phase')) {
    for (let i = 12; i < smoothBody.length; i++) {
      const seg = smoothBody[i]
      if (Math.hypot(seg.x - smoothPos.x, seg.y - smoothPos.y) < CELL * 0.55) {
        crashInfoLocal = `tail seg ${i} (smooth) @ frame ${frameIdx}`; return die()
      }
    }
  }
  // wall obstacles
  for (const w of walls) {
    const cx = w.x * CELL + CELL / 2, cy = w.y * CELL + CELL / 2
    if (Math.hypot(cx - smoothPos.x, cy - smoothPos.y) < CELL * 0.85) {
      crashInfoLocal = `obstacle (smooth) @ frame ${frameIdx}`; return die()
    }
  }
  // food eat
  const fx = food.x * CELL + CELL / 2, fy = food.y * CELL + CELL / 2
  if (Math.hypot(fx - smoothPos.x, fy - smoothPos.y) < CELL * 0.7) {
    eatFood(food)
  }
  // heatmap based on grid cell of head
  const hx = Math.max(0, Math.min(COLS - 1, Math.floor(smoothPos.x / CELL)))
  const hy = Math.max(0, Math.min(ROWS - 1, Math.floor(smoothPos.y / CELL)))
  pathHeat[hy * COLS + hx] = (pathHeat[hy * COLS + hx] || 0) + 0.1
}

function die() {
  if (!alive) return
  alive = false
  store.beep('lose')
  if (settings.haptics) store.vibrate([20, 60, 20])
  speak('Crashed')
  if (!settings.reducedMotion) {
    // shatter
    const head = settings.movement === 'grid' ? { x: snakeGrid[0].x * CELL + CELL / 2, y: snakeGrid[0].y * CELL + CELL / 2 } : smoothPos
    for (let i = 0; i < 36; i++) {
      const a = rng() * Math.PI * 2, v = 1 + rng() * 4
      particles.push({ x: head.x, y: head.y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 60, max: 60, color: skinColor(0), size: 2.4 })
    }
    shakeT.value = 18
  }
  muteMusicAfterDeath()
  // MMR adjust
  const m = mmrState.value
  m.runs.push({ score: score_.value, ts: Date.now() })
  if (m.runs.length > 30) m.runs.shift()
  if (score_.value < 50) m.value = Math.max(0, m.value - 2)
  else if (score_.value > 200) m.value = Math.min(100, m.value + 4)
  else m.value = Math.max(0, Math.min(100, m.value + (score_.value > 100 ? 1 : -1)))
  saveMmr(m)
  // unlocks
  const u = JSON.parse(JSON.stringify(unlocks.value)) as Unlocks
  const announce: string[] = []
  const elapsedSec = (performance.now() - runStartTs) / 1000
  if (!u.skins.crystal && snakeLen.value >= 20) { u.skins.crystal = true; announce.push('Crystal skin') }
  if (!u.skins.lava && score_.value >= 200) { u.skins.lava = true; announce.push('Lava skin') }
  if (!u.skins.ice && elapsedSec >= 90) { u.skins.ice = true; announce.push('Ice skin') }
  if (!u.skins.ghost && phaseUsedInRun >= 3) { u.skins.ghost = true; announce.push('Ghost skin') }
  if (!u.skins.golden) {
    const totalGolden = (Number(localStorage.getItem('snake_golden_total') || 0) + goldenEatsInRun)
    localStorage.setItem('snake_golden_total', String(totalGolden))
    if (totalGolden >= 20) { u.skins.golden = true; announce.push('Golden skin') }
  } else { localStorage.setItem('snake_golden_total', String(Number(localStorage.getItem('snake_golden_total') || 0) + goldenEatsInRun)) }
  if (!u.skins.pixel) {
    const plays = (store.statsOf as unknown as (id: string) => { plays: number })('snake').plays + 1
    if (plays >= 10) { u.skins.pixel = true; announce.push('Pixel skin') }
  }
  if (!u.themes.zen) {
    const plays = (store.statsOf as unknown as (id: string) => { plays: number })('snake').plays + 1
    if (plays >= 5) { u.themes.zen = true; announce.push('Zen Garden theme') }
  }
  if (!u.themes.toxic && poisonEatsInRun >= 5) { u.themes.toxic = true; announce.push('Toxic Waste theme') }
  if (!u.themes.void && snakeLen.value >= 30) { u.themes.void = true; announce.push('Void theme') }
  if (announce.length) { unlocks.value = u; saveUnlocks(u); newUnlocks.value = announce }
  // ghost run
  const prevBest = store.bestOf('snake')
  if (score_.value >= prevBest && score_.value > 0) {
    store.saveGhost('snake', { moves: recordedMoves.slice(0, 3000), difficulty: props.difficulty, score: score_.value })
  }
  // daily best
  if (dailySeedActive.value) {
    try {
      const raw = localStorage.getItem(DAILY_KEY)
      const today = todaySeedNumber()
      let cur: { date: number; best: number } = raw ? JSON.parse(raw) : { date: today, best: 0 }
      if (cur.date !== today) cur = { date: today, best: 0 }
      if (score_.value > cur.best) cur.best = score_.value
      localStorage.setItem(DAILY_KEY, JSON.stringify(cur))
    } catch { /* */ }
  }
  setTimeout(() => {
    if (!alive) {
      showAnalytics.value = true
      computeSuggestion()
      nextTick(() => { drawHeatChart(); drawRxChart() })
    }
  }, settings.reducedMotion ? 200 : 750)
}

function computeSuggestion() {
  const wallEnd = crashInfoLocal.startsWith('wall')
  const tailEnd = crashInfoLocal.startsWith('tail')
  const obs = crashInfoLocal.startsWith('obstacle') || crashInfoLocal.startsWith('moving')
  const lateTurns = turnLogInternal.filter((t) => t.frame > frameIdx - 30).length
  if (tailEnd && snakeLen.value > 12) deathSuggestion.value = 'You curled into your own tail. When long, hug the walls and uncoil into the centre slowly.'
  else if (wallEnd) deathSuggestion.value = 'You hit a wall. Try enabling wrap-around in settings, or swipe a hair earlier.'
  else if (obs) deathSuggestion.value = 'You hit an obstacle. Pre-plan a route through the maze; pause briefly when you spot a corridor.'
  else if (lateTurns >= 4) deathSuggestion.value = 'A flurry of late turns at the end. Slow down a notch (Settings → Base speed).'
  else deathSuggestion.value = 'Solid run. Try to keep food collection in straight lines to extend your combo multiplier.'
}

function drawHeatChart() {
  const c = heatChart.value; if (!c) return
  const r = c.getBoundingClientRect(); c.width = r.width * devicePixelRatio; c.height = r.height * devicePixelRatio
  const ctx = c.getContext('2d')!; ctx.scale(devicePixelRatio, devicePixelRatio)
  const w = r.width, h = r.height
  ctx.fillStyle = 'rgba(255,255,255,0.04)'; ctx.fillRect(0, 0, w, h)
  let max = 1
  for (const v of pathHeat) if (v > max) max = v
  const cw = w / COLS, ch = h / ROWS
  for (let y = 0; y < ROWS; y++) {
    for (let x = 0; x < COLS; x++) {
      const v = (pathHeat[y * COLS + x] || 0) / max
      if (v > 0.02) {
        ctx.fillStyle = `rgba(56, 189, 248, ${0.15 + v * 0.7})`
        ctx.fillRect(x * cw, y * ch, cw - 0.5, ch - 0.5)
      }
    }
  }
}
function drawRxChart() {
  const c = rxChart.value; if (!c) return
  const r = c.getBoundingClientRect(); c.width = r.width * devicePixelRatio; c.height = r.height * devicePixelRatio
  const ctx = c.getContext('2d')!; ctx.scale(devicePixelRatio, devicePixelRatio)
  const w = r.width, h = r.height
  ctx.fillStyle = 'rgba(255,255,255,0.04)'; ctx.fillRect(0, 0, w, h)
  // bin turns into N buckets
  const bins = 16
  const counts = new Array(bins).fill(0)
  const maxFrame = Math.max(60, frameIdx)
  for (const t of turnLogInternal) {
    const idx = Math.min(bins - 1, Math.floor((t.frame / maxFrame) * bins))
    counts[idx]++
  }
  const max = Math.max(1, ...counts)
  const bw = w / bins
  for (let i = 0; i < bins; i++) {
    const v = counts[i] / max
    ctx.fillStyle = `rgba(244, 114, 182, ${0.25 + v * 0.65})`
    ctx.fillRect(i * bw, h - v * h, bw - 1, v * h)
  }
  ctx.fillStyle = 'rgba(255,255,255,0.6)'; ctx.font = '10px sans-serif'
  ctx.fillText('start', 4, 12); ctx.fillText('death', w - 32, 12)
}

// === input ===
let pointerStart: { x: number; y: number; t: number } | null = null
function clientXY(e: Event): { x: number; y: number } {
  const me = e as MouseEvent; const te = e as TouchEvent
  if ('touches' in te && te.touches && te.touches.length) return { x: te.touches[0].clientX, y: te.touches[0].clientY }
  if ('changedTouches' in te && te.changedTouches && te.changedTouches.length) return { x: te.changedTouches[0].clientX, y: te.changedTouches[0].clientY }
  return { x: me.clientX, y: me.clientY }
}
function onPointerDown(e: Event) {
  const p = clientXY(e); pointerStart = { x: p.x, y: p.y, t: performance.now() }
  if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume().catch(() => { /* */ })
}
function onPointerMove(e: Event) {
  if (!pointerStart || settings.movement === 'grid') return
  // smooth: continuous steering — set direction toward pointer relative to start
  const p = clientXY(e)
  const dx = p.x - pointerStart.x, dy = p.y - pointerStart.y
  const m = Math.hypot(dx, dy); const dz = settings.deadZone ? 18 : 6
  if (m < dz) return
  if (Math.abs(dx) > Math.abs(dy)) set(dx > 0 ? 1 : -1, 0)
  else set(0, dy > 0 ? 1 : -1)
}
function onPointerUp(e: Event) {
  if (!pointerStart) return
  const p = clientXY(e)
  const dx = p.x - pointerStart.x, dy = p.y - pointerStart.y
  const m = Math.hypot(dx, dy)
  // sensitivity: lower swipeSens = need longer swipe
  const threshold = 36 / settings.swipeSens
  const dz = settings.deadZone ? 16 : 0
  if (m >= Math.max(dz, threshold)) {
    if (Math.abs(dx) > Math.abs(dy)) set(dx > 0 ? 1 : -1, 0)
    else set(0, dy > 0 ? 1 : -1)
  } else if (m < 8 && performance.now() - pointerStart.t < 220) {
    // tap: pause / resume
    if (!started) started = true
  }
  pointerStart = null
}
function onKey(e: KeyboardEvent) {
  if (e.repeat) return
  const m: Record<string, [number, number]> = {
    ArrowUp: [0, -1], w: [0, -1], W: [0, -1],
    ArrowDown: [0, 1], s: [0, 1], S: [0, 1],
    ArrowLeft: [-1, 0], a: [-1, 0], A: [-1, 0],
    ArrowRight: [1, 0], d: [1, 0], D: [1, 0],
  }
  const v = m[e.key]
  if (v) { e.preventDefault(); set(v[0], v[1]); return }
  if (e.key === ' ' || e.key.toLowerCase() === 'r') { if (!alive && showAnalytics.value) instantReplay(); else if (!started) started = true }
  if (e.key === 'Escape') settingsOpen.value = !settingsOpen.value
  if (e.key.toLowerCase() === 'z') undoMove()
}
function onKeyUp(_e: KeyboardEvent) { /* reserved */ }

// tilt
let tiltHandler: ((e: DeviceOrientationEvent) => void) | null = null
function onTiltToggle() {
  saveSettings()
  if (settings.tilt) enableTilt(); else disableTilt()
}
function enableTilt() {
  if (typeof window === 'undefined' || tiltHandler) return
  tiltHandler = (e: DeviceOrientationEvent) => {
    const g = e.gamma || 0 // -90..90 left/right
    if (Math.abs(g) > 18) set(g > 0 ? 1 : -1, 0)
  }
  type DOEv = typeof DeviceOrientationEvent & { requestPermission?: () => Promise<string> }
  const Ev = DeviceOrientationEvent as unknown as DOEv
  if (Ev && typeof Ev.requestPermission === 'function') {
    Ev.requestPermission().then((s: string) => { if (s === 'granted' && tiltHandler) window.addEventListener('deviceorientation', tiltHandler) }).catch(() => { /* */ })
  } else {
    window.addEventListener('deviceorientation', tiltHandler)
  }
}
function disableTilt() { if (tiltHandler) { window.removeEventListener('deviceorientation', tiltHandler); tiltHandler = null } }

// background pause
function onVisibilityChange() {
  if (document.hidden && started && alive && !showAnalytics.value) {
    settingsOpen.value = true // act as a pause overlay
  }
}

// === drawing ===
function skinColor(i: number): string {
  const t = i / Math.max(1, snakeLen.value)
  switch (settings.skin) {
    case 'crystal': return `rgba(186, 230, 253, ${1 - t * 0.6})`
    case 'lava':    return `rgba(${255 - t * 60}, ${110 - t * 60}, ${30 - t * 20}, 1)`
    case 'ice':     return `rgba(${190 + t * 20}, ${220 + t * 20}, 255, 1)`
    case 'ghost':   return `rgba(167, 139, 250, ${0.4 + 0.3 * (1 - t)})`
    case 'pixel':   return i % 2 === 0 ? '#22c55e' : '#16a34a'
    case 'golden':  return `rgba(${250 - t * 30}, ${204 - t * 60}, ${21}, 1)`
    default:        return `rgba(56, 189, 248, ${1 - t * 0.55})`
  }
}

function drawTheme(ctx: CanvasRenderingContext2D) {
  switch (settings.theme) {
    case 'cyber': {
      const grd = ctx.createLinearGradient(0, 0, 0, H)
      grd.addColorStop(0, '#0b0e1f'); grd.addColorStop(1, '#1e1b4b')
      ctx.fillStyle = grd; ctx.fillRect(0, 0, W, H)
      ctx.strokeStyle = `rgba(168, 85, 247, ${0.08 + 0.04 * Math.sin(bgT / 30)})`
      for (let i = 0; i <= COLS; i++) { ctx.beginPath(); ctx.moveTo(i * CELL, 0); ctx.lineTo(i * CELL, H); ctx.stroke() }
      for (let j = 0; j <= ROWS; j++) { ctx.beginPath(); ctx.moveTo(0, j * CELL); ctx.lineTo(W, j * CELL); ctx.stroke() }
      break
    }
    case 'zen': {
      ctx.fillStyle = '#fde68a'; ctx.fillRect(0, 0, W, H)
      ctx.strokeStyle = 'rgba(146, 64, 14, 0.18)'
      for (let r = 30; r < W; r += 18) {
        ctx.beginPath()
        for (let x = 0; x < W; x += 6) {
          const y = (r + Math.sin((x + bgT) / 22) * 4)
          if (x === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
        }
        ctx.stroke()
      }
      break
    }
    case 'toxic': {
      ctx.fillStyle = '#0a2a14'; ctx.fillRect(0, 0, W, H)
      for (let i = 0; i < 18; i++) {
        const x = (i * 73 + bgT * 0.6) % W
        const y = (i * 41) % H
        ctx.fillStyle = `rgba(34, 197, 94, ${0.12 + 0.08 * Math.sin((bgT + i * 30) / 25)})`
        ctx.beginPath(); ctx.arc(x, y, 24 + (i % 3) * 8, 0, Math.PI * 2); ctx.fill()
      }
      break
    }
    case 'void':
    default: {
      ctx.fillStyle = '#000'; ctx.fillRect(0, 0, W, H)
      break
    }
  }
}

function drawSnakeGrid(ctx: CanvasRenderingContext2D) {
  // body
  for (let i = snakeGrid.length - 1; i >= 1; i--) {
    const s = snakeGrid[i]
    const col = skinColor(i)
    ctx.fillStyle = col
    if (!settings.batterySaver) { ctx.shadowColor = col; ctx.shadowBlur = 6 }
    ctx.fillRect(s.x * CELL + 1, s.y * CELL + 1, CELL - 2, CELL - 2)
    ctx.shadowBlur = 0
  }
  // head: viper wedge
  if (snakeGrid.length === 0) return
  const h = snakeGrid[0]
  const cx = h.x * CELL + CELL / 2, cy = h.y * CELL + CELL / 2
  const ang = Math.atan2(dir.y, dir.x)
  ctx.save()
  ctx.translate(cx, cy); ctx.rotate(ang)
  if (!settings.batterySaver) { ctx.shadowColor = skinColor(0); ctx.shadowBlur = 12 }
  ctx.fillStyle = skinColor(0)
  ctx.beginPath()
  ctx.moveTo(CELL * 0.55, 0)
  ctx.lineTo(-CELL * 0.45, -CELL * 0.45)
  ctx.lineTo(-CELL * 0.45, CELL * 0.45)
  ctx.closePath(); ctx.fill()
  ctx.shadowBlur = 0
  // eyes
  ctx.fillStyle = '#fde68a'
  ctx.beginPath(); ctx.arc(CELL * 0.15, -CELL * 0.18, 2.2, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(CELL * 0.15, CELL * 0.18, 2.2, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#000'
  ctx.beginPath(); ctx.arc(CELL * 0.18, -CELL * 0.18, 1.0, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(CELL * 0.18, CELL * 0.18, 1.0, 0, Math.PI * 2); ctx.fill()
  ctx.restore()
}

function drawSnakeSmooth(ctx: CanvasRenderingContext2D) {
  // body — sample every 4 stored positions
  for (let i = smoothBody.length - 1; i >= 1; i -= 2) {
    const s = smoothBody[i]
    const col = skinColor(i / 4)
    ctx.fillStyle = col
    if (!settings.batterySaver) { ctx.shadowColor = col; ctx.shadowBlur = 6 }
    ctx.beginPath(); ctx.arc(s.x, s.y, CELL * 0.42, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
  }
  // head
  const ang = Math.atan2(smoothVel.y, smoothVel.x)
  ctx.save()
  ctx.translate(smoothPos.x, smoothPos.y); ctx.rotate(ang)
  if (!settings.batterySaver) { ctx.shadowColor = skinColor(0); ctx.shadowBlur = 12 }
  ctx.fillStyle = skinColor(0)
  ctx.beginPath()
  ctx.moveTo(CELL * 0.55, 0)
  ctx.lineTo(-CELL * 0.45, -CELL * 0.45)
  ctx.lineTo(-CELL * 0.45, CELL * 0.45)
  ctx.closePath(); ctx.fill()
  ctx.shadowBlur = 0
  ctx.fillStyle = '#fde68a'
  ctx.beginPath(); ctx.arc(CELL * 0.15, -CELL * 0.18, 2.2, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(CELL * 0.15, CELL * 0.18, 2.2, 0, Math.PI * 2); ctx.fill()
  ctx.restore()
}

function drawFood(ctx: CanvasRenderingContext2D) {
  const cx = food.x * CELL + CELL / 2, cy = food.y * CELL + CELL / 2
  const wob = Math.sin(bgT / 8) * 1.5
  if (food.kind === 'golden') {
    ctx.fillStyle = '#facc15'; ctx.shadowColor = '#facc15'; ctx.shadowBlur = settings.batterySaver ? 0 : 18
    ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.4, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.strokeStyle = '#fde68a'; ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.55, 0, Math.PI * 2); ctx.stroke()
  } else if (food.kind === 'poison') {
    ctx.fillStyle = '#a855f7'; ctx.shadowColor = '#a855f7'; ctx.shadowBlur = settings.batterySaver ? 0 : 14
    ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.42, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#fff'; ctx.font = `${Math.round(CELL * 0.7)}px serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillText('☠', cx, cy + wob)
  } else if (food.kind === 'timed') {
    const remain = Math.max(0, 5 - (frameIdx - food.spawnT) / 60)
    ctx.fillStyle = '#38bdf8'; ctx.shadowColor = '#38bdf8'; ctx.shadowBlur = settings.batterySaver ? 0 : 14
    ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * (0.3 + 0.1 * remain), 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.5, -Math.PI / 2, -Math.PI / 2 + (remain / 5) * Math.PI * 2); ctx.stroke(); ctx.lineWidth = 1
  } else if (food.kind === 'magnetic') {
    ctx.fillStyle = '#f472b6'; ctx.shadowColor = '#f472b6'; ctx.shadowBlur = settings.batterySaver ? 0 : 14
    ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.4, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#fff'; ctx.font = `${Math.round(CELL * 0.6)}px serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillText('🧲', cx, cy + wob)
  } else {
    ctx.fillStyle = '#ec4899'; ctx.shadowColor = '#ec4899'; ctx.shadowBlur = settings.batterySaver ? 0 : 12
    ctx.beginPath(); ctx.arc(cx, cy + wob, CELL * 0.36, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
  }
  // assist predict ring
  if (settings.assistPredict) {
    ctx.strokeStyle = `rgba(250, 204, 21, ${0.5 + 0.3 * Math.sin(bgT / 6)})`
    ctx.lineWidth = 1.5
    ctx.beginPath(); ctx.arc(cx, cy, CELL * 0.7, 0, Math.PI * 2); ctx.stroke(); ctx.lineWidth = 1
  }
}

function drawWalls(ctx: CanvasRenderingContext2D) {
  ctx.fillStyle = '#475569'
  for (const w of walls) ctx.fillRect(w.x * CELL + 2, w.y * CELL + 2, CELL - 4, CELL - 4)
  // moving obstacles
  ctx.fillStyle = '#94a3b8'
  for (const o of arenaMovingObs) ctx.fillRect(Math.round(o.x) * CELL + 4, Math.round(o.y) * CELL + 4, CELL - 8, CELL - 8)
  // power up
  if (powerUp) {
    const pk = (powerUp as unknown as { _power: PowerKind })._power
    const pcol = pk === 'phase' ? '#a78bfa' : pk === 'slow' ? '#38bdf8' : pk === 'double' ? '#facc15' : '#22c55e'
    ctx.fillStyle = pcol; ctx.shadowColor = pcol; ctx.shadowBlur = settings.batterySaver ? 0 : 16
    ctx.beginPath(); ctx.arc(powerUp.x * CELL + CELL / 2, powerUp.y * CELL + CELL / 2, CELL * 0.4, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#fff'; ctx.font = `${Math.round(CELL * 0.5)}px sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillText(powerIcon(pk), powerUp.x * CELL + CELL / 2, powerUp.y * CELL + CELL / 2)
  }
}

function drawTrail(ctx: CanvasRenderingContext2D) {
  if (settings.trail === 'none' || settings.batterySaver) return
  const head = settings.movement === 'grid' && snakeGrid.length
    ? { x: snakeGrid[0].x * CELL + CELL / 2, y: snakeGrid[0].y * CELL + CELL / 2 } : smoothPos
  if (frameIdx % 2 === 0) {
    let color = 'rgba(56, 189, 248, 0.7)'; let size = 2.0
    if (settings.trail === 'sparkle') { color = `hsla(${(frameIdx * 9) % 360}, 80%, 70%, 0.9)`; size = 1.6 }
    if (settings.trail === 'fire')   { color = `rgba(${239 + Math.random() * 16}, ${110 + Math.random() * 70}, ${30 + Math.random() * 50}, 0.9)`; size = 2.4 }
    if (settings.trail === 'smoke')  { color = `rgba(200, 200, 220, ${0.18 + Math.random() * 0.2})`; size = 3.0 }
    if (settings.trail === 'rainbow'){ color = `hsla(${(frameIdx * 4) % 360}, 90%, 60%, 0.85)`; size = 2.0 }
    particles.push({ x: head.x, y: head.y, vx: -dir.x * 0.4 + (Math.random() - 0.5) * 0.6, vy: -dir.y * 0.4 + (Math.random() - 0.5) * 0.6, life: 26, max: 26, color, size })
  }
}

function draw() {
  const c = cv.value!
  const ctx = c.getContext('2d')!
  drawTheme(ctx)
  // shrinking arena
  if (arenaShrink > 0) {
    ctx.strokeStyle = 'rgba(239, 68, 68, 0.6)'; ctx.lineWidth = 2
    ctx.strokeRect(arenaShrink, arenaShrink, W - arenaShrink * 2, H - arenaShrink * 2); ctx.lineWidth = 1
  }
  drawWalls(ctx)
  drawFood(ctx)
  drawTrail(ctx)
  // particles
  for (const p of particles) {
    p.x += p.vx; p.y += p.vy; p.life--
    p.vy += 0.03
    const a = (p.life / p.max)
    ctx.fillStyle = p.color.replace(/,[\s\d.]+\)$/, `,${a.toFixed(2)})`)
    ctx.fillRect(p.x, p.y, p.size, p.size)
  }
  particles = particles.filter((p) => p.life > 0)
  // ghost (replay overlay)
  if (ghostMoves.length > 0 && frameIdx < ghostMoves.length && !isReplaying) {
    const m = ghostMoves[Math.min(frameIdx, ghostMoves.length - 1)]
    if (m && m.foodAt) {
      ctx.fillStyle = 'rgba(250, 204, 21, 0.18)'
      ctx.beginPath(); ctx.arc(m.foodAt.x * CELL + CELL / 2, m.foodAt.y * CELL + CELL / 2, CELL * 0.3, 0, Math.PI * 2); ctx.fill()
    }
  }
  // snake
  if (alive || particles.length === 0) {
    if (settings.movement === 'grid') drawSnakeGrid(ctx); else drawSnakeSmooth(ctx)
  }
  // popups
  for (const p of popups) {
    p.life--
    p.y -= 0.6
    const a = p.life / 50
    ctx.fillStyle = p.color.replace(/[\d.]+\)$/, `${a.toFixed(2)})`)
    ctx.font = 'bold 12px sans-serif'; ctx.textAlign = 'center'
    ctx.fillText(p.text, p.x + CELL / 2, p.y)
  }
  popups = popups.filter((p) => p.life > 0)
  // poison invert tint
  if (poisonControlsInverted > 0) {
    ctx.fillStyle = `rgba(168, 85, 247, ${0.10 + 0.06 * Math.sin(bgT / 4)})`
    ctx.fillRect(0, 0, W, H)
  }
  // start hint
  if (!started && alive) {
    ctx.save()
    ctx.fillStyle = 'rgba(0,0,0,0.55)'; ctx.fillRect(W / 2 - 140, H / 2 - 36, 280, 72)
    ctx.fillStyle = '#fff'; ctx.font = 'bold 16px sans-serif'; ctx.textAlign = 'center'
    ctx.fillText(settings.movement === 'smooth' ? 'Swipe to begin steering' : 'Swipe / arrow keys to begin', W / 2, H / 2 + 2)
    ctx.font = '12px sans-serif'; ctx.fillStyle = 'rgba(255,255,255,0.75)'
    ctx.fillText(sharedSeedActive.value ? 'Shared seed' : (dailySeedActive.value ? `Today's daily seed (#${currentSeedNumber.value})` : 'Random seed'), W / 2, H / 2 + 22)
    ctx.restore()
  }
}

function loop(now: number) {
  if (cv.value === null) return
  const targetFps = settings.batterySaver ? 30 : 60
  const minDt = 1000 / targetFps
  const dt = Math.min(64, now - last)
  last = now
  if (props.paused || settingsOpen.value || calibrating.value || showAnalytics.value) {
    raf = requestAnimationFrame(loop); return
  }
  if (dt < minDt - 1) { raf = requestAnimationFrame(loop); return }
  acc += dt
  // power timers
  for (let i = activePowersInternal.length - 1; i >= 0; i--) {
    activePowersInternal[i].timeLeft -= dt
    if (activePowersInternal[i].timeLeft <= 0) {
      const k = activePowersInternal[i].kind
      activePowersInternal.splice(i, 1)
      if (k === 'slow' || k === 'double' || k === 'magnet') recomputeSpeed()
    }
  }
  activePowers.value = activePowersInternal.slice()
  // poison timer
  if (poisonControlsInverted > 0) poisonControlsInverted = Math.max(0, poisonControlsInverted - dt)
  comboRecharge += dt
  if (comboRecharge > 4500 && combo.value > 1) combo.value = 1
  // time mode
  if (props.mode === 'time' && started) {
    timeLeftMs.value -= dt
    if (timeLeftMs.value <= 0) { crashInfoLocal = `time up @ frame ${frameIdx}`; return die() }
  }
  // moving obstacles
  for (const o of arenaMovingObs) {
    o.x += o.dx * 0.04
    if (o.x < 1 || o.x > COLS - 2) o.dx *= -1
  }
  // shrinking arena (high MMR + classic > 60s)
  if (mmrState.value.value > 80 && (performance.now() - runStartTs) > 60000) arenaShrink = Math.min(40, arenaShrink + dt * 0.005)

  // physics step(s)
  if (settings.movement === 'grid') {
    stepAcc += dt
    let safety = 0
    while (stepAcc >= stepIntervalMs && alive && safety < 5) {
      stepAcc -= stepIntervalMs
      stepGrid()
      safety++
    }
  } else {
    if (alive) stepSmooth(dt)
  }
  bgT += dt * 0.03
  frameIdx++
  if (shakeT.value > 0) shakeT.value--
  draw()
  raf = requestAnimationFrame(loop)
}

// === calibration ===
const calRound = ref(1)
const calAvg = ref(0)
const calLengths: number[] = []
let calStart: { x: number; y: number; t: number } | null = null
function openCalibration() { calibrating.value = true; calRound.value = 1; calAvg.value = 0; calLengths.length = 0 }
function onCalStart(e: Event) { const p = clientXY(e); calStart = { x: p.x, y: p.y, t: performance.now() } }
function onCalMove(_e: Event) { /* noop */ }
function onCalEnd(e: Event) {
  if (!calStart) return
  const p = clientXY(e); const m = Math.hypot(p.x - calStart.x, p.y - calStart.y)
  if (m > 12) { calLengths.push(m); calRound.value++ }
  calStart = null
  if (calRound.value > 5) {
    const avg = calLengths.reduce((a, b) => a + b, 0) / calLengths.length
    calAvg.value = avg
    // if user makes long swipes naturally → lower sensitivity (longer threshold). Map 40px→1.5, 200px→0.5
    const v = Math.max(0.4, Math.min(2.0, 1.6 - (avg - 40) / 200))
    settings.swipeSens = parseFloat(v.toFixed(2))
    saveSettings()
  }
}

// === replay ===
async function watchReplay() {
  if (recordedMoves.length < 4) { toast('No replay data', 'info'); return }
  showAnalytics.value = false
  isReplaying = true
  // soft reset & play recorded moves
  reset()
  started = true
  for (const mv of recordedMoves) {
    pendingDir = { ...mv.dir }
    await new Promise((r) => setTimeout(r, stepIntervalMs * 0.7))
    if (!isReplaying) return
  }
  isReplaying = false
  showAnalytics.value = true
  nextTick(() => { drawHeatChart(); drawRxChart() })
}

function instantReplay() { showAnalytics.value = false; reset() }
function finishToShell() { showAnalytics.value = false; props.onGameOver(score_.value) }

onMounted(() => {
  cv.value!.width = W; cv.value!.height = H
  reset()
  raf = requestAnimationFrame(loop)
  root.value?.focus()
  injectColorblindFilters()
  if (settings.tilt) enableTilt()
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onUnmounted(() => {
  cancelAnimationFrame(raf)
  disposeAudio()
  disableTilt()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
watch(() => props.running, (v) => { if (v) { reset(); cancelAnimationFrame(raf); raf = requestAnimationFrame(loop) } })
watch(() => settings.baseSpeed, () => recomputeSpeed())

function injectColorblindFilters() {
  if (document.getElementById('snake-cb-filters')) return
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('id', 'snake-cb-filters'); svg.setAttribute('aria-hidden', 'true')
  svg.style.position = 'absolute'; svg.style.width = '0'; svg.style.height = '0'
  svg.innerHTML = `
    <filter id="snake-cb-prot"><feColorMatrix type="matrix" values="0.567,0.433,0,0,0  0.558,0.442,0,0,0  0,0.242,0.758,0,0  0,0,0,1,0"/></filter>
    <filter id="snake-cb-deut"><feColorMatrix type="matrix" values="0.625,0.375,0,0,0  0.7,0.3,0,0,0  0,0.3,0.7,0,0  0,0,0,1,0"/></filter>
    <filter id="snake-cb-trit"><feColorMatrix type="matrix" values="0.95,0.05,0,0,0  0,0.433,0.567,0,0  0,0.475,0.525,0,0  0,0,0,1,0"/></filter>
  `
  document.body.appendChild(svg)
}
</script>

<style scoped lang="scss">
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; touch-action: manipulation; position: relative; }
.canvas-frame { position: relative; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4); max-width: 100%; height: auto; touch-action: none; cursor: pointer; display: block; }
.shake { animation: shake 0.4s ease; }
@keyframes shake {
  0%, 100% { transform: translate(0, 0); }
  20% { transform: translate(-3px, 2px); } 40% { transform: translate(3px, -2px); }
  60% { transform: translate(-2px, -2px); } 80% { transform: translate(2px, 3px); }
}
.canvas-frame.low .cv { box-shadow: 0 6px 16px rgba(0, 0, 0, 0.3); }
.pulse { position: absolute; inset: 0; border-radius: 14px; background: rgba(168, 85, 247, 0.18); pointer-events: none; animation: pulse 0.18s ease; }
@keyframes pulse { 0% { opacity: 0; } 30% { opacity: 1; } 100% { opacity: 0; } }
.settings-btn { position: absolute; top: 8px; right: 8px; width: 30px; height: 30px; border-radius: 50%; background: rgba(0, 0, 0, 0.5); color: #fff; border: 1px solid rgba(255, 255, 255, 0.2); cursor: pointer; font-size: 14px; line-height: 1; z-index: 5; }
.hud { position: absolute; top: 8px; left: 8px; display: flex; flex-direction: column; gap: 4px; z-index: 4; pointer-events: none; }
.hud.left { left: auto; right: 48px; align-items: flex-end; }
.hud-pill { background: rgba(0, 0, 0, 0.55); color: #fff; border-radius: 8px; padding: 3px 8px; font-size: 0.72rem; display: inline-flex; gap: 6px; }
.hud-pill span { opacity: 0.7; }
.hud-pill b { font-weight: 700; }
.powers { position: absolute; bottom: 8px; left: 8px; right: 8px; display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; z-index: 4; pointer-events: none; }
.power-pill { display: inline-flex; gap: 6px; align-items: center; padding: 3px 10px; border-radius: 999px; font-size: 0.74rem; color: #fff; backdrop-filter: blur(4px); background: rgba(0, 0, 0, 0.6); }
.power-pill.slow { background: rgba(56, 189, 248, 0.45); border: 1px solid rgba(56, 189, 248, 0.6); }
.power-pill.double { background: rgba(250, 204, 21, 0.45); border: 1px solid rgba(250, 204, 21, 0.6); }
.power-pill.shrink { background: rgba(34, 197, 94, 0.45); border: 1px solid rgba(34, 197, 94, 0.6); }
.power-pill.phase { background: rgba(167, 139, 250, 0.45); border: 1px solid rgba(167, 139, 250, 0.6); }
.power-pill.magnet { background: rgba(244, 114, 182, 0.45); border: 1px solid rgba(244, 114, 182, 0.6); }

.hint { color: rgba(255, 255, 255, 0.7); font-size: 0.85rem; text-align: center; }
.tag { background: rgba(56, 189, 248, 0.18); padding: 3px 10px; border-radius: 999px; }

.dpad { display: grid; gap: 6px; justify-items: center; align-self: stretch; align-items: center; padding: 6px 0; }
.dpad.left { justify-self: start; }
.dpad .row { display: flex; gap: 18px; align-items: center; }
.dpad button { min-width: 56px; min-height: 56px; border-radius: 50%; border: none; background: rgba(255, 255, 255, 0.10); color: #fff; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; font-size: 1.1rem; }
.dpad button:active { background: rgba(255, 255, 255, 0.25); }
.dpad button.undo { background: rgba(250, 204, 21, 0.22); border: 1px solid rgba(250, 204, 21, 0.45); }
.dpad button:disabled { opacity: 0.4; }

.overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(8px); z-index: 30; display: flex; align-items: center; justify-content: center; padding: 16px; }
.panel { background: rgba(15, 23, 42, 0.95); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 18px; max-width: 480px; width: 100%; max-height: 90vh; overflow-y: auto; color: #fff; }
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
.chip.on { background: linear-gradient(135deg, #14b8a6, #6366f1); border-color: transparent; }
.chip.locked { opacity: 0.55; cursor: not-allowed; }
.btn { background: rgba(255, 255, 255, 0.1); border: 1px solid rgba(255, 255, 255, 0.18); color: #fff; padding: 8px 14px; border-radius: 10px; cursor: pointer; font-size: 0.9rem; }
.btn.primary { background: linear-gradient(135deg, #14b8a6, #6366f1); border-color: transparent; }
.meter { display: flex; flex-direction: column; gap: 4px; }
.meter > span { font-size: 0.85rem; opacity: 0.85; }
.meter .bar { background: rgba(255, 255, 255, 0.08); border-radius: 999px; height: 8px; overflow: hidden; }
.meter .bar > div { height: 100%; background: linear-gradient(90deg, #34d399, #f59e0b, #ef4444); transition: width 0.3s; }
.cal-stage { background: #1e293b; border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 12px; padding: 30px 16px; text-align: center; font-size: 0.95rem; cursor: pointer; min-height: 80px; display: flex; align-items: center; justify-content: center; touch-action: none; }
.r-msg { background: rgba(56, 189, 248, 0.15); border: 1px solid rgba(56, 189, 248, 0.35); border-radius: 10px; padding: 10px; font-size: 0.88rem; }
.graphs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.g-block { background: rgba(255, 255, 255, 0.04); border-radius: 10px; padding: 8px; }
.g-title { font-size: 0.74rem; opacity: 0.7; margin-bottom: 4px; }
.g-canvas { width: 100%; height: 90px; display: block; }
.r-stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.r-stats-row > div { background: rgba(255, 255, 255, 0.06); border-radius: 8px; padding: 6px 4px; text-align: center; }
.r-stats-row span { display: block; font-size: 0.65rem; opacity: 0.6; }
.r-stats-row b { font-size: 0.85rem; }
.r-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.unlock-banner { background: linear-gradient(135deg, rgba(20, 184, 166, 0.25), rgba(99, 102, 241, 0.25)); border: 1px solid rgba(20, 184, 166, 0.4); border-radius: 10px; padding: 10px; text-align: center; font-weight: 700; color: #a7f3d0; }
@media (max-width: 480px) {
  .graphs { grid-template-columns: 1fr; }
  .r-stats-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
