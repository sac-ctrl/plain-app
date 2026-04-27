<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp"
       :style="{ filter: cbFilter, transform: `scale(${s.uiScale / 100})`, transformOrigin: 'top center' }">
    <svg width="0" height="0" style="position:absolute">
      <filter id="cb-protan"><feColorMatrix type="matrix"
        values="0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0"/></filter>
      <filter id="cb-deutan"><feColorMatrix type="matrix"
        values="0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0"/></filter>
      <filter id="cb-tritan"><feColorMatrix type="matrix"
        values="0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0"/></filter>
    </svg>
    <canvas ref="cv" class="cv"
      @mousemove="onMouse" @mousedown="onMouse"
      @touchstart.prevent="onTouch" @touchmove.prevent="onTouch" @touchend.prevent="onTouchEnd"
      @click="onClick" />
    <div class="hud-top">
      <span class="chip">W{{ world + 1 }} · L{{ levelInWorld + 1 }} · {{ worldLabel }}</span>
      <span class="chip">{{ aliveBricks }} bricks</span>
      <span class="chip" v-if="combo > 1">x{{ combo }}</span>
      <span class="chip">♥ {{ lives }}</span>
      <span class="chip" v-if="powerLabel">{{ powerLabel }} {{ Math.ceil(powerLeft / 60) }}s</span>
      <span class="chip" v-if="debuffLabel">⚠ {{ debuffLabel }}</span>
      <span class="chip">◇ {{ s.shards }}</span>
      <span class="chip flex"></span>
      <button class="chip btn" @click.stop="settingsOpen = true">⚙</button>
    </div>
    <div class="centerline" v-if="!started && alive && !analyticsOpen && !settingsOpen">
      <div class="big">Tap to launch</div>
      <div class="sub">Drag = paddle · Shift = precision · ⚙ for everything</div>
    </div>

    <!-- Settings overlay -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
      <div class="panel">
        <div class="row head">
          <h3>Crusher · Settings</h3>
          <button class="x" @click="settingsOpen = false">✕</button>
        </div>
        <div class="grid">
          <label>World
            <select v-model="s.world">
              <option v-for="w in worldList" :key="w.id" :value="w.id">{{ w.label }}</option>
            </select>
          </label>
          <label>Music genre
            <select v-model="s.music">
              <option value="synthwave">Synthwave</option>
              <option value="chiptune">Chiptune</option>
              <option value="lofi">Lo-fi</option>
              <option value="industrial">Industrial</option>
            </select>
          </label>
          <label>Paddle shape
            <select v-model="s.paddleShape">
              <option value="classic">Classic</option>
              <option value="curved">Curved</option>
              <option value="twin">Twin-split</option>
            </select>
          </label>
          <label>Paddle material
            <select v-model="s.paddleMat">
              <option value="neon">Neon</option>
              <option value="carbon">Carbon</option>
              <option value="glass">Glass</option>
            </select>
          </label>
          <label>Ball trail
            <select v-model="s.ballTrail">
              <option value="comet">Comet</option>
              <option value="rainbow">Rainbow</option>
              <option value="golden">Golden</option>
              <option value="electric">Electric</option>
            </select>
          </label>
          <label>Colourblind
            <select v-model="s.colourblind">
              <option value="off">off</option>
              <option value="protanopia">protanopia</option>
              <option value="deuteranopia">deuteranopia</option>
              <option value="tritanopia">tritanopia</option>
            </select>
          </label>
        </div>
        <div class="slider">
          <span>Mouse smoothing: {{ s.mouseSmoothing }}</span>
          <input type="range" min="0" max="100" v-model.number="s.mouseSmoothing">
        </div>
        <div class="slider">
          <span>Keyboard ramp: {{ s.kbRamp }} ms</span>
          <input type="range" min="0" max="600" step="20" v-model.number="s.kbRamp">
        </div>
        <div class="slider">
          <span>UI scale: {{ s.uiScale }}%</span>
          <input type="range" min="80" max="150" step="5" v-model.number="s.uiScale">
        </div>
        <div class="toggles">
          <label><input type="checkbox" v-model="s.ghostLine"> Trajectory ghost line</label>
          <label><input type="checkbox" v-model="s.spin"> Realistic ball spin (paddle velocity)</label>
          <label><input type="checkbox" v-model="s.haptics"> Haptics</label>
          <label><input type="checkbox" v-model="s.reducedMotion"> Reduced motion</label>
          <label><input type="checkbox" v-model="s.highContrast"> High contrast</label>
          <label><input type="checkbox" v-model="s.announcer"> Voice announcer</label>
          <label><input type="checkbox" v-model="s.audioViz"> Audio visualiser rings</label>
          <label><input type="checkbox" v-model="s.assistLock"> Assist · ball locks to paddle on respawn</label>
          <label><input type="checkbox" v-model="s.assistAim"> Assist · paddle auto-aim</label>
          <label><input type="checkbox" v-model="s.assistInfinite"> Assist · infinite lives (no rank)</label>
        </div>
        <div class="row foot">
          <button class="btn" @click="startCalibration">Calibrate (paddle test)</button>
          <button class="btn ghost" @click="exportSeed">Copy seed</button>
          <button class="btn ghost" @click="enterReplay" :disabled="!s.ghostBest?.length">▶ Watch replay</button>
        </div>
        <div class="hint">
          <b>Adaptive:</b> {{ s.adaptive.deaths }} recent deaths · {{ s.adaptive.clears }} recent clears
          <span v-if="s.adaptive.deaths >= 3"> · paddle widened</span>
          <span v-if="s.adaptive.clears >= 5"> · denser bricks</span>
        </div>
      </div>
    </div>

    <!-- Analytics overlay -->
    <div v-if="analyticsOpen" class="overlay">
      <div class="panel">
        <div class="row head"><h3>Run analysis · {{ score }} pts</h3></div>
        <div class="row stats">
          <div class="tile"><span>Rank</span><b :style="{ color: rankColor(rank) }">{{ rank }}</b></div>
          <div class="tile"><span>Bricks</span><b>{{ runStats.broken }}</b></div>
          <div class="tile"><span>Bounces</span><b>{{ runStats.paddleHits }}</b></div>
          <div class="tile"><span>Power-ups</span><b>{{ runStats.powerCaught }}</b></div>
          <div class="tile"><span>Best combo</span><b>{{ runStats.maxCombo }}</b></div>
          <div class="tile"><span>Effic.</span><b>{{ effLabel }}</b></div>
        </div>
        <div class="ana-section">
          <div class="ana-label">Miss heatmap</div>
          <div class="heatmap">
            <div class="zone" v-for="(v, k) in runStats.misses" :key="k">
              <div class="bar" :style="{ width: heatPct(v) + '%' }"></div>
              <span>{{ k }} · {{ v }}</span>
            </div>
          </div>
        </div>
        <div class="ana-section">
          <div class="ana-label">Bounce trajectory (last balls)</div>
          <svg class="graph" viewBox="0 0 240 60" preserveAspectRatio="none">
            <polyline :points="trajPoly" fill="none" stroke="#60a5fa" stroke-width="1.4"/>
          </svg>
        </div>
        <div class="tip"><b>Tip:</b> {{ runTip }}</div>
        <div v-if="runUnlocks.length" class="unlock">🎉 Unlocked: {{ runUnlocks.join(', ') }}</div>
        <div class="row foot">
          <button class="btn" @click="replayRun">↺ Instant replay</button>
          <button class="btn ghost" @click="finishRun">Continue</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, computed } from 'vue'
import { useGamesStore } from '../gamesStore'

type Brick = {
  x: number; y: number; w: number; h: number; alive: boolean;
  hp: number; maxHp: number; kind: 'normal' | 'fire' | 'ice' | 'electric' | 'metal' | 'boss' | 'regen';
  hue: number; vx?: number; weakX?: number; weakY?: number;
  rotPhase?: number; regenAt?: number;
}
type Ball = { x: number; y: number; vx: number; vy: number; r: number; sticky?: boolean; trailHue: number; tinyT: number }
type Drop = { x: number; y: number; kind: PowerKind; hue: number }
type Laser = { x: number; y: number }
type Particle = { x: number; y: number; vx: number; vy: number; life: number; max: number; col: string; size: number; rot?: number }
type FloatTxt = { x: number; y: number; t: number; max: number; text: string; col: string }
type RingFx = { x: number; y: number; r: number; max: number; col: string }
type PowerKind = 'longer' | 'multi' | 'slow' | 'sticky' | 'laser' | 'wrap' | 'gravity' | 'shield' | 'nuke' | 'shrink' | 'short' | 'reverse' | 'invisible'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'campaign' | 'endless' | 'boss' | 'daily' | 'classic' | 'survival'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const W = 360, H = 540

const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()

// ─── Settings (persisted) ─────────────────────────────────────────────────
type Adaptive = { deaths: number; clears: number }
type Settings = {
  world: number; music: 'synthwave' | 'chiptune' | 'lofi' | 'industrial';
  paddleShape: 'classic' | 'curved' | 'twin'; paddleMat: 'neon' | 'carbon' | 'glass';
  ballTrail: 'comet' | 'rainbow' | 'golden' | 'electric'; colourblind: 'off' | 'protanopia' | 'deuteranopia' | 'tritanopia';
  mouseSmoothing: number; kbRamp: number; uiScale: number;
  ghostLine: boolean; spin: boolean; haptics: boolean; reducedMotion: boolean;
  highContrast: boolean; announcer: boolean; audioViz: boolean;
  assistLock: boolean; assistAim: boolean; assistInfinite: boolean;
  shards: number; adaptive: Adaptive;
  unlocked: { paddles: string[]; trails: string[]; arenas: number[]; music: string[] };
  ghostBest: number[]; bestRank: string;
  daily?: { date: string; score: number };
}
function defaultSettings(): Settings {
  return {
    world: 0, music: 'synthwave', paddleShape: 'classic', paddleMat: 'neon',
    ballTrail: 'comet', colourblind: 'off',
    mouseSmoothing: 20, kbRamp: 220, uiScale: 100,
    ghostLine: true, spin: true, haptics: true, reducedMotion: false,
    highContrast: false, announcer: true, audioViz: true,
    assistLock: false, assistAim: false, assistInfinite: false,
    shards: 0, adaptive: { deaths: 0, clears: 0 },
    unlocked: { paddles: ['classic'], trails: ['comet'], arenas: [0], music: ['synthwave'] },
    ghostBest: [], bestRank: 'E',
  }
}
const s = reactive<Settings>(defaultSettings())
function loadSettings() {
  try {
    const raw = localStorage.getItem('brick_settings_v1')
    if (!raw) return
    Object.assign(s, defaultSettings(), JSON.parse(raw))
  } catch (_) { /* ignore */ }
}
function saveSettings() {
  try { localStorage.setItem('brick_settings_v1', JSON.stringify(s)) } catch (_) { /* ignore */ }
}
loadSettings()
watch(() => JSON.stringify(s), saveSettings)

const cbFilter = computed(() => {
  switch (s.colourblind) {
    case 'protanopia': return 'url(#cb-protan)'
    case 'deuteranopia': return 'url(#cb-deutan)'
    case 'tritanopia': return 'url(#cb-tritan)'
    default: return 'none'
  }
})

// ─── Worlds ───────────────────────────────────────────────────────────────
const worldList = [
  { id: 0, label: 'Factory · conveyor bricks' },
  { id: 1, label: 'Crystal Caverns · refractive angles' },
  { id: 2, label: 'Neon Temple · regenerating bricks' },
  { id: 3, label: 'Sanctum · dense walls' },
  { id: 4, label: 'Cosmos · gravity wells' },
  { id: 5, label: 'Abyss · invisible flickers' },
  { id: 6, label: 'Inferno · fire chains' },
  { id: 7, label: 'Zero · all elements + boss' },
]
const world = computed(() => s.world)
const worldLabel = computed(() => worldList[world.value]?.label.split(' · ')[0] || 'Factory')

// ─── Mode mapping ─────────────────────────────────────────────────────────
const mode = computed<'campaign' | 'endless' | 'boss' | 'daily'>(() => {
  const m = (props.mode || 'campaign').toLowerCase()
  if (m === 'classic') return 'campaign'
  if (m === 'survival') return 'endless'
  return (m as any) || 'campaign'
})

// ─── Game state ───────────────────────────────────────────────────────────
let paddle = { x: W / 2 - 60, w: 120, h: 12, y: H - 30, vx: 0, prevX: W / 2 - 60 }
let balls: Ball[] = []
let bricks: Brick[] = []
let drops: Drop[] = []
let lasers: Laser[] = []
let particles: Particle[] = []
let texts: FloatTxt[] = []
let rings: RingFx[] = []
let lastLaserShot = 0
let started = ref(false)
let alive = true
const score = ref(0)
const lives = ref(3)
const level = ref(1)
const levelInWorld = ref(0)
const combo = ref(0)
const aliveBricks = ref(0)
const settingsOpen = ref(false)
const analyticsOpen = ref(false)

// power-up state
const powerLabel = ref('')
const powerLeft = ref(0)
let powerKind: PowerKind | '' = ''
let stickyT = 0
let laserT = 0
let wideT = 0
let slowT = 0
let wrapT = 0
let gravityT = 0
let shrinkT = 0
let shieldCharges = 0
const debuffLabel = ref('')
let debuffT = 0
let reverseT = 0
let invisibleT = 0
let shortT = 0

// camera fx
let shakeT = 0
let shakeMag = 0
let zoomT = 1.0
let cinematicSlowT = 0

// keyboard movement
let keyL = false, keyR = false, keyShift = false
let kbVel = 0
let touchPrecision = false

// gamepad
let lastGamepadX = 0

// trajectory cache
let traj: { x: number; y: number }[] = []
let trajCacheT = 0

// run analytics
const runStats = reactive({ broken: 0, paddleHits: 0, powerCaught: 0, maxCombo: 0, misses: { left: 0, centre: 0, right: 0 }, history: [] as number[] })
const runTip = ref('')
const runUnlocks = ref<string[]>([])
const trajPoly = ref('')
const rank = ref('E')

// replay
let recordX: number[] = []
let replayActive = false
let replayIdx = 0
let replayPaddleX: number[] = []

// calibration
let calActive = false
let calStart = 0
const calReact: number[] = []

// rng
let seed = 0
function rand() {
  seed = (seed * 9301 + 49297) % 233280
  return seed / 233280
}
function todaySeed() {
  const d = new Date()
  return d.getFullYear() * 372 + (d.getMonth() + 1) * 31 + d.getDate()
}

// ─── Difficulty ───────────────────────────────────────────────────────────
const cfgBase: Record<string, { speed: number; rows: number; lives: number }> = {
  easy: { speed: 3.4, rows: 3, lives: 5 },
  medium: { speed: 4.4, rows: 4, lives: 4 },
  hard: { speed: 5.4, rows: 5, lives: 3 },
  insane: { speed: 6.6, rows: 6, lives: 2 },
}
let baseSpeed = cfgBase.medium.speed
let baseRows = cfgBase.medium.rows
let baseLives = cfgBase.medium.lives

function applyAdaptive() {
  if (s.adaptive.deaths >= 3) { paddle.w = 150; baseSpeed *= 0.92 }
  if (s.adaptive.clears >= 5) baseRows = Math.min(8, baseRows + 1)
}

function configureForRun() {
  const c = cfgBase[props.difficulty] || cfgBase.medium
  baseSpeed = c.speed
  baseRows = c.rows
  baseLives = s.assistInfinite ? 999 : c.lives
  if (mode.value === 'endless') baseLives = Math.max(2, baseLives - 1)
  if (mode.value === 'daily') seed = todaySeed()
  else seed = Math.floor(Math.random() * 100000)
  applyAdaptive()
}

// ─── Build level ──────────────────────────────────────────────────────────
function buildLevel(lvl: number) {
  bricks = []
  const cols = 8
  const bw = (W - 40) / cols
  const bh = 18
  const w = world.value
  const rows = Math.min(8, baseRows + Math.floor((lvl - 1) / 3))
  if (mode.value === 'boss' || (w === 7 && lvl % 3 === 0)) { buildBoss(); return }
  for (let r = 0; r < rows; r++) {
    for (let i = 0; i < cols; i++) {
      if (lvl > 1 && rand() < 0.06 + Math.min(0.18, lvl * 0.012)) continue
      const x = 20 + i * bw
      const y = 60 + r * (bh + 5)
      let kind: Brick['kind'] = 'normal'
      let hp = 1, hue = 200 + r * 28
      const roll = rand()
      if (w === 0 && r === 0) { kind = 'normal'; hp = 2; hue = 30 }
      else if (w === 1 && roll < 0.25) { kind = 'electric'; hue = 50 }
      else if (w === 2 && roll < 0.35) { kind = 'regen'; hue = 280 }
      else if (w === 3 && r >= 2 && roll < 0.4) { kind = 'metal'; hp = 99 }
      else if (w === 4 && roll < 0.2) { kind = 'fire'; hue = 18 }
      else if (w === 5 && roll < 0.4) { kind = 'normal'; hp = 1; hue = 220 }
      else if (w === 6 && roll < 0.45) { kind = 'fire'; hue = 8 }
      else if (w === 7) {
        if (roll < 0.18) kind = 'fire'
        else if (roll < 0.36) kind = 'ice'
        else if (roll < 0.54) kind = 'electric'
        else if (roll < 0.62) { kind = 'metal'; hp = 99 }
      }
      if (kind !== 'metal' && r < 2 && lvl > 3 && rand() < 0.15) hp = 2
      bricks.push({
        x, y, w: bw - 4, h: bh, alive: true, kind,
        hp, maxHp: hp, hue,
        vx: w === 0 && r % 2 === 0 ? 0.4 : (w === 7 && rand() < 0.2 ? 0.5 * (rand() < 0.5 ? 1 : -1) : 0),
        rotPhase: rand() * Math.PI * 2,
      })
    }
  }
}
function buildBoss() {
  const cx = W / 2, cy = 100, w = 200, h = 80
  bricks = [{
    x: cx - w / 2, y: cy, w, h, alive: true, kind: 'boss',
    hp: 16 + level.value * 2, maxHp: 16 + level.value * 2, hue: 320,
    weakX: cx, weakY: cy + h / 2, rotPhase: 0,
  }]
  // mini guard bricks
  for (let i = 0; i < 6; i++) {
    bricks.push({
      x: 30 + i * 50, y: 200, w: 38, h: 14, alive: true, kind: i % 2 === 0 ? 'fire' : 'ice',
      hp: 1, maxHp: 1, hue: i % 2 === 0 ? 18 : 200, vx: 0.3 * (i % 2 === 0 ? 1 : -1),
    })
  }
}

function spawnBall(x: number, y: number) {
  const ang = (rand() * 0.5 - 0.25) - Math.PI / 2
  const sp = baseSpeed
  balls.push({
    x, y,
    vx: Math.cos(ang) * sp * (rand() < 0.5 ? -1 : 1),
    vy: Math.sin(ang) * sp,
    r: shrinkT > 0 ? 4 : 7, trailHue: 200, tinyT: 0,
  })
}

function reset() {
  configureForRun()
  score.value = 0; lives.value = baseLives; level.value = 1; levelInWorld.value = 0
  combo.value = 0; powerLabel.value = ''; powerLeft.value = 0; powerKind = ''
  stickyT = laserT = wideT = slowT = wrapT = gravityT = shrinkT = 0; shieldCharges = 0
  debuffLabel.value = ''; debuffT = reverseT = invisibleT = shortT = 0
  paddle = { x: W / 2 - 60, w: 120, h: 12, y: H - 30, vx: 0, prevX: W / 2 - 60 }
  balls = []; drops = []; lasers = []; particles = []; texts = []; rings = []
  runStats.broken = 0; runStats.paddleHits = 0; runStats.powerCaught = 0
  runStats.maxCombo = 0; runStats.misses.left = 0; runStats.misses.centre = 0; runStats.misses.right = 0
  runStats.history = []
  runTip.value = ''; runUnlocks.value = []; trajPoly.value = ''
  rank.value = 'E'
  recordX = []; replayActive = false; replayIdx = 0
  buildLevel(1)
  spawnBall(paddle.x + paddle.w / 2, paddle.y - 30)
  if (s.assistLock) { balls[0].sticky = true }
  started.value = false
  alive = true
  analyticsOpen.value = false
  props.onScore(0)
  computeAliveBricks()
}

function computeAliveBricks() { aliveBricks.value = bricks.filter((b) => b.alive && b.kind !== 'metal').length }

// ─── Input ────────────────────────────────────────────────────────────────
function setPad(x: number, raw = false) {
  const target = Math.max(0, Math.min(W - paddle.w, x - paddle.w / 2))
  if (raw || s.mouseSmoothing === 0) { paddle.x = target; return }
  const k = 1 - s.mouseSmoothing / 110
  paddle.x = paddle.x + (target - paddle.x) * k
}
function onMouse(e: MouseEvent) {
  if (settingsOpen.value || analyticsOpen.value) return
  const r = cv.value!.getBoundingClientRect()
  setPad((e.clientX - r.left) * (W / r.width))
}
function onTouch(e: TouchEvent) {
  if (settingsOpen.value || analyticsOpen.value) return
  if (!started.value && alive) launch()
  touchPrecision = e.touches.length >= 2
  const r = cv.value!.getBoundingClientRect()
  setPad((e.touches[0].clientX - r.left) * (W / r.width))
  releaseSticky()
}
function onTouchEnd() { touchPrecision = false }
function onClick() { if (!started.value && alive && !settingsOpen.value && !analyticsOpen.value) launch() }
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key.toLowerCase() === 'a') keyL = true
  if (e.key === 'ArrowRight' || e.key.toLowerCase() === 'd') keyR = true
  if (e.key === 'Shift') keyShift = true
  if (e.key === ' ' || e.key === 'Enter') { if (!started.value && alive) launch(); else releaseSticky() }
}
function onKeyUp(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key.toLowerCase() === 'a') keyL = false
  if (e.key === 'ArrowRight' || e.key.toLowerCase() === 'd') keyR = false
  if (e.key === 'Shift') keyShift = false
}
function launch() {
  started.value = true
  for (const b of balls) b.sticky = false
}
function releaseSticky() { for (const b of balls) b.sticky = false }

function pollGamepad() {
  if (typeof navigator === 'undefined' || !navigator.getGamepads) return
  const gps = navigator.getGamepads()
  for (const g of gps) {
    if (!g) continue
    const ax = g.axes[0] || 0
    if (Math.abs(ax) > 0.1) lastGamepadX = ax
    const rt = g.buttons[7]?.value || 0
    if (rt > 0.5) keyShift = true
    return
  }
  lastGamepadX = 0
}

function precision() { return keyShift || touchPrecision }

// ─── Audio (extra layered) ────────────────────────────────────────────────
let audioCtx: AudioContext | null = null
let lastBeatT = 0
function audio() {
  if (!audioCtx) {
    try { const Ctor = (window as any).AudioContext || (window as any).webkitAudioContext; audioCtx = Ctor ? new Ctor() : null } catch (_) { /* ignore */ }
  }
  return audioCtx
}
function tone(freq: number, dur = 0.12, type: OscillatorType = 'sine', gain = 0.1) {
  if (!store.sound) return
  const c = audio(); if (!c) return
  try {
    const o = c.createOscillator(); const g = c.createGain()
    o.type = type; o.frequency.value = freq
    o.connect(g); g.connect(c.destination)
    g.gain.setValueAtTime(0.0001, c.currentTime)
    g.gain.exponentialRampToValueAtTime(gain, c.currentTime + 0.01)
    g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + dur)
    o.start(); o.stop(c.currentTime + dur + 0.02)
  } catch (_) { /* ignore */ }
}
function paddleThwack(speed: number) {
  const f = 220 + Math.min(400, speed * 30)
  tone(f, 0.08, 'sine', 0.12)
}
function brickShatter(hue: number) {
  tone(420 + hue, 0.08, 'triangle', 0.1)
  tone(820 + hue, 0.16, 'sine', 0.08)
}
function powerChime() { tone(660, 0.08, 'square'); tone(990, 0.16, 'sine') }
function deathSub() { tone(80, 0.5, 'sawtooth', 0.16); tone(40, 0.7, 'sine', 0.12) }
function announce(text: string) {
  if (!s.announcer) return
  try {
    const u = new SpeechSynthesisUtterance(text)
    u.rate = 1.1; u.pitch = 1.05; u.volume = 0.6
    window.speechSynthesis.cancel(); window.speechSynthesis.speak(u)
  } catch (_) { /* ignore */ }
}

// ─── Power-ups ────────────────────────────────────────────────────────────
function spawnDrop(x: number, y: number) {
  const r = rand()
  let kind: PowerKind
  let hue = 280
  // 8% debuff, 60% tier1, 25% tier2, 7% tier3
  if (r < 0.08) {
    const dr = rand()
    kind = dr < 0.34 ? 'short' : dr < 0.67 ? 'reverse' : 'invisible'
    hue = 0
  } else if (r < 0.68) {
    const tr = rand()
    if (tr < 0.2) kind = 'longer'
    else if (tr < 0.4) kind = 'multi'
    else if (tr < 0.6) kind = 'slow'
    else if (tr < 0.8) kind = 'sticky'
    else kind = 'laser'
    hue = 200
  } else if (r < 0.93) {
    const tr = rand()
    if (tr < 0.34) kind = 'wrap'
    else if (tr < 0.67) kind = 'gravity'
    else kind = 'shield'
    hue = 280
  } else {
    kind = rand() < 0.5 ? 'nuke' : 'shrink'
    hue = 320
  }
  drops.push({ x, y, kind, hue })
}
function applyPower(kind: PowerKind) {
  runStats.powerCaught += 1
  let label = ''
  switch (kind) {
    case 'longer': paddle.w = Math.min(220, paddle.w + 30); wideT = 60 * 10; label = 'Longer'; break
    case 'multi': {
      const b0 = balls[0]; if (b0) {
        for (let i = 0; i < 2; i++) {
          const ang = (rand() - 0.5) * 0.6 - Math.PI / 2
          balls.push({ x: b0.x, y: b0.y, vx: Math.cos(ang) * baseSpeed, vy: Math.sin(ang) * baseSpeed, r: b0.r, trailHue: b0.trailHue, tinyT: 0 })
        }
      }
      label = 'Multi-ball'; break
    }
    case 'slow': slowT = 60 * 10; label = 'Slow ball'; break
    case 'sticky': stickyT = 60 * 12; label = 'Sticky'; break
    case 'laser': laserT = 60 * 10; label = 'Laser'; break
    case 'wrap': wrapT = 60 * 10; label = 'Wrap walls'; break
    case 'gravity': gravityT = 60 * 8; label = 'Gravity well'; break
    case 'shield': shieldCharges += 1; label = 'Shield +1'; break
    case 'nuke': screenNuke(); label = 'NUKE'; announce('Crusher'); break
    case 'shrink': shrinkT = 60 * 8; for (const b of balls) b.r = 4; label = 'Tiny ball'; break
    case 'short': paddle.w = Math.max(60, paddle.w - 30); shortT = 60 * 8; debuffLabel.value = 'Short'; debuffT = shortT; return
    case 'reverse': reverseT = 60 * 8; debuffLabel.value = 'Reverse'; debuffT = reverseT; return
    case 'invisible': invisibleT = 60 * 8; debuffLabel.value = 'Invisible'; debuffT = invisibleT; return
  }
  powerKind = kind; powerLabel.value = label
  powerLeft.value = Math.max(wideT, slowT, stickyT, laserT, wrapT, gravityT, shrinkT, 60 * 6)
  powerChime(); store.vibrate(20)
  pushFloat(paddle.x + paddle.w / 2, paddle.y - 8, label, '#a78bfa')
  pushRing(paddle.x + paddle.w / 2, paddle.y, 'rgba(168,139,250,0.6)')
}
function screenNuke() {
  for (const b of bricks) {
    if (b.alive && b.kind !== 'metal' && b.kind !== 'boss') {
      b.alive = false; spawnFragments(b.x + b.w / 2, b.y + b.h / 2, b.hue)
      score.value += 10; runStats.broken += 1
    }
  }
  shakeT = 18; shakeMag = 8
  if (s.audioViz) pushRing(W / 2, H / 2, 'rgba(255,150,80,0.7)', 280)
  computeAliveBricks(); props.onScore(score.value)
}

// ─── Particles / fx helpers ───────────────────────────────────────────────
function spawnFragments(x: number, y: number, hue: number, n = 12) {
  if (s.reducedMotion) n = 4
  for (let i = 0; i < n; i++) {
    const a = rand() * Math.PI * 2; const v = 1 + rand() * 3.2
    particles.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 0.6, life: 28, max: 28, col: `hsl(${hue}, 90%, 65%)`, size: 2 + rand() * 2, rot: rand() * Math.PI })
  }
}
function pushFloat(x: number, y: number, text: string, col: string) {
  texts.push({ x, y, t: 0, max: 36, text, col })
}
function pushRing(x: number, y: number, col: string, max = 40) {
  if (!s.audioViz) return
  rings.push({ x, y, r: 4, max, col })
}

// ─── Brick break logic ───────────────────────────────────────────────────
function damageBrick(b: Brick, x: number, y: number, fromBall = true, ballRef?: Ball) {
  b.hp -= 1
  if (b.kind === 'metal' && b.hp > 1) b.hp = 99
  if (b.hp <= 0) {
    b.alive = false
    spawnFragments(x, y, b.hue, b.kind === 'boss' ? 36 : 14)
    brickShatter(b.hue)
    if (s.audioViz) pushRing(x, y, `hsla(${b.hue},80%,60%,0.7)`, 40)
    score.value += b.kind === 'boss' ? 200 : 10 + (b.kind === 'fire' ? 4 : 0)
    runStats.broken += 1
    combo.value += 1
    runStats.maxCombo = Math.max(runStats.maxCombo, combo.value)
    if (combo.value === 10) announce('Good')
    else if (combo.value === 25) announce('Excellent')
    else if (combo.value === 50) announce('Crusher')
    if (rand() < 0.18) spawnDrop(x, y)
    if (b.kind === 'fire') heatWave(x, y)
    else if (b.kind === 'ice') { for (const ba of balls) { ba.vx *= 0.6; ba.vy *= 0.6 } pushRing(x, y, 'rgba(125,211,252,0.7)', 60) }
    else if (b.kind === 'electric') chainElectric(b)
    else if (b.kind === 'regen') b.regenAt = performance.now() + 3000
    if (lastBeatT === 0 || (runStats.broken % 5 === 0)) { tone(330, 0.06, 'square', 0.07); lastBeatT = performance.now() }
    // beat drop
    if (runStats.broken % 10 === 0) tone(120, 0.18, 'square', 0.12)
    if (b.kind === 'boss') { announce('Boss down'); shakeT = 30; shakeMag = 10 }
    pushFloat(x, y, `+${b.kind === 'boss' ? 200 : 10}`, `hsl(${b.hue},80%,70%)`)
    if (ballRef) ballRef.trailHue = b.hue
    props.onScore(score.value)
    computeAliveBricks()
  } else {
    score.value += 2
    props.onScore(score.value)
    if (b.kind === 'metal') tone(900, 0.06, 'sawtooth', 0.05)
  }
}
function heatWave(x: number, y: number) {
  for (const o of bricks) {
    if (!o.alive || o.kind === 'metal' || o === undefined) continue
    const dx = o.x + o.w / 2 - x, dy = o.y + o.h / 2 - y
    if (Math.hypot(dx, dy) < 60) o.hp = Math.max(0, o.hp - 1)
    if (o.hp <= 0 && o.alive) { o.alive = false; spawnFragments(o.x + o.w / 2, o.y + o.h / 2, o.hue, 8); score.value += 10; runStats.broken += 1 }
  }
  pushRing(x, y, 'rgba(248,113,113,0.7)', 90)
  computeAliveBricks(); props.onScore(score.value)
}
function chainElectric(start: Brick) {
  let cur = start; const visited = new Set<Brick>([cur])
  for (let step = 0; step < 3; step++) {
    let nearest: Brick | null = null; let nd = 999
    for (const o of bricks) {
      if (!o.alive || o.kind === 'metal' || visited.has(o)) continue
      const d = Math.hypot(cur.x - o.x, cur.y - o.y)
      if (d < nd && d < 90) { nd = d; nearest = o }
    }
    if (!nearest) break
    visited.add(nearest)
    pushRing((cur.x + nearest.x) / 2, (cur.y + nearest.y) / 2, 'rgba(250,204,21,0.8)', 30)
    nearest.hp = Math.max(0, nearest.hp - 1)
    if (nearest.hp <= 0) { nearest.alive = false; spawnFragments(nearest.x + nearest.w / 2, nearest.y + nearest.h / 2, nearest.hue, 8); score.value += 10; runStats.broken += 1 }
    cur = nearest
  }
  computeAliveBricks(); props.onScore(score.value)
}

// ─── Main step ────────────────────────────────────────────────────────────
const FIXED_DT = 1000 / 240
let acc = 0
let lastTs = 0
let raf = 0
function loop(ts?: number) {
  if (!alive && !analyticsOpen.value) { raf = requestAnimationFrame(loop); return }
  const t = ts || performance.now()
  const dt = Math.min(64, t - (lastTs || t)); lastTs = t
  if (props.paused || settingsOpen.value || analyticsOpen.value) {
    draw(); raf = requestAnimationFrame(loop); return
  }
  pollGamepad()
  acc += dt
  let safety = 0
  while (acc >= FIXED_DT && alive && safety < 20) { acc -= FIXED_DT; step(); safety++ }
  draw()
  raf = requestAnimationFrame(loop)
}

function step() {
  if (replayActive) { stepReplay(); return }
  // gamepad pull (continuous)
  if (Math.abs(lastGamepadX) > 0.1) {
    const sp = (precision() ? 3 : 6) * lastGamepadX
    paddle.x = Math.max(0, Math.min(W - paddle.w, paddle.x + sp))
  }
  // keyboard ramp
  const ramp = s.kbRamp / 1000
  if (keyL || keyR) {
    const target = (keyR ? 1 : 0) - (keyL ? 1 : 0)
    const dir = (reverseT > 0) ? -target : target
    kbVel += (dir * (precision() ? 3 : 6) - kbVel) * (ramp <= 0 ? 1 : Math.min(1, FIXED_DT / (ramp * 1000)))
  } else {
    kbVel *= 0.7
  }
  paddle.x = Math.max(0, Math.min(W - paddle.w, paddle.x + kbVel))
  // assist auto-aim
  if (s.assistAim && balls.length) {
    const b = balls[0]; const tx = b.x - paddle.w / 2
    paddle.x += (tx - paddle.x) * 0.04
    paddle.x = Math.max(0, Math.min(W - paddle.w, paddle.x))
  }
  paddle.vx = paddle.x - paddle.prevX; paddle.prevX = paddle.x
  recordX.push(paddle.x)
  if (recordX.length > 1800) recordX.shift()

  // calibration
  if (calActive && started.value) {
    const b = balls[0]
    if (b && b.y > paddle.y - 20 && b.vy > 0) {
      const target = b.x
      const dx = Math.abs(target - (paddle.x + paddle.w / 2))
      if (dx < 50) { calReact.push(performance.now() - calStart); calStart = performance.now() }
      if (calReact.length >= 5) {
        const avg = calReact.reduce((a, c) => a + c, 0) / calReact.length
        s.mouseSmoothing = Math.max(0, Math.min(100, Math.round(100 - avg / 8)))
        calActive = false
        pushFloat(paddle.x + paddle.w / 2, paddle.y - 12, `Sens ${s.mouseSmoothing}`, '#22d3ee')
      }
    }
  }
  // brick movement (worlds)
  for (const b of bricks) {
    if (!b.alive) {
      if (b.kind === 'regen' && b.regenAt && performance.now() > b.regenAt) {
        b.alive = true; b.hp = b.maxHp; b.regenAt = undefined; computeAliveBricks()
      }
      continue
    }
    if (b.vx) { b.x += b.vx; if (b.x < 10 || b.x + b.w > W - 10) b.vx = -b.vx! }
    if (b.rotPhase != null) b.rotPhase += 0.04
  }
  // last-brick slow-mo
  if (cinematicSlowT > 0) cinematicSlowT -= 1
  const slowMul = (slowT > 0 ? 0.65 : 1) * (cinematicSlowT > 0 ? 0.6 : 1)

  // balls
  const FIVE_DEG = Math.PI / 90
  for (const ball of balls) {
    if (ball.sticky) {
      ball.x = paddle.x + paddle.w / 2
      ball.y = paddle.y - ball.r - 1
      continue
    }
    if (gravityT > 0) ball.vy += 0.012
    let nx = ball.x + ball.vx * slowMul
    let ny = ball.y + ball.vy * slowMul
    // wall
    if (wrapT > 0) {
      if (nx < 0) nx += W
      if (nx > W) nx -= W
    } else {
      if (nx < ball.r) { nx = ball.r; ball.vx = -ball.vx }
      if (nx > W - ball.r) { nx = W - ball.r; ball.vx = -ball.vx }
    }
    if (ny < ball.r) { ny = ball.r; ball.vy = -ball.vy }
    // paddle
    if (ny > paddle.y - ball.r && ny < paddle.y + paddle.h && nx > paddle.x && nx < paddle.x + paddle.w && ball.vy > 0) {
      ny = paddle.y - ball.r
      ball.vy = -Math.abs(ball.vy)
      const off = (nx - (paddle.x + paddle.w / 2)) / (paddle.w / 2)
      const sp = Math.hypot(ball.vx, ball.vy)
      ball.vx = off * Math.max(sp, baseSpeed) + (s.spin ? paddle.vx * 0.4 : 0)
      // re-normalize keep speed
      const sp2 = Math.hypot(ball.vx, ball.vy)
      if (sp2 > 0) {
        const k = sp / sp2
        ball.vx *= k; ball.vy *= k
      }
      // avoid horizontal lock
      if (Math.abs(ball.vy) < FIVE_DEG * sp) ball.vy = (ball.vy < 0 ? -1 : 1) * FIVE_DEG * sp
      paddleThwack(sp)
      runStats.paddleHits += 1
      if (s.audioViz) pushRing(nx, paddle.y, 'rgba(96,165,250,0.6)', 28)
      store.vibrate(8)
      // twin-paddle detect: split bricks in middle gap could miss; OK keep simple
    } else if (ny > H + 20) {
      // ball lost — record miss zone
      const zone: 'left' | 'centre' | 'right' = ball.x < W / 3 ? 'left' : ball.x < (2 * W) / 3 ? 'centre' : 'right'
      runStats.misses[zone] += 1
      ball.r = -1
      continue
    }
    // bricks (per-ball)
    for (const b of bricks) {
      if (!b.alive) continue
      if (nx + ball.r > b.x && nx - ball.r < b.x + b.w && ny + ball.r > b.y && ny - ball.r < b.y + b.h) {
        // determine collision side
        const overlapL = nx + ball.r - b.x
        const overlapR = b.x + b.w - (nx - ball.r)
        const overlapT = ny + ball.r - b.y
        const overlapB = b.y + b.h - (ny - ball.r)
        const minO = Math.min(overlapL, overlapR, overlapT, overlapB)
        if (minO === overlapL || minO === overlapR) ball.vx = -ball.vx
        else ball.vy = -ball.vy
        // crystal world: random extra deflection
        if (world.value === 1 && rand() < 0.5) {
          const a = (rand() - 0.5) * 0.6
          const sp = Math.hypot(ball.vx, ball.vy)
          const cur = Math.atan2(ball.vy, ball.vx) + a
          ball.vx = Math.cos(cur) * sp; ball.vy = Math.sin(cur) * sp
        }
        if (b.kind === 'metal') break // just bounce, no damage
        damageBrick(b, b.x + b.w / 2, b.y + b.h / 2, true, ball)
        // last brick slow-mo
        if (aliveBricks.value === 1) cinematicSlowT = 60
        break
      }
    }
    ball.x = nx; ball.y = ny
  }
  balls = balls.filter((b) => b.r > 0 && b.y < H + 20)
  if (balls.length === 0) {
    lives.value -= 1
    deathSub(); store.beep('lose'); store.vibrate(40)
    runStats.history.push(score.value)
    s.adaptive.deaths = Math.min(6, s.adaptive.deaths + 1)
    if (lives.value <= 0) { announce('Uh oh'); endRun(); return }
    spawnBall(paddle.x + paddle.w / 2, paddle.y - 30)
    if (s.assistLock) balls[0].sticky = true
    started.value = false; combo.value = 0
  }

  // drops
  for (const d of drops) {
    d.y += 2
    // gentle magnet toward paddle when near
    const dx = (paddle.x + paddle.w / 2) - d.x
    if (Math.abs(d.y - paddle.y) < 80) d.x += Math.sign(dx) * Math.min(0.6, Math.abs(dx) * 0.04)
    if (d.y > paddle.y && d.y < paddle.y + paddle.h && d.x > paddle.x && d.x < paddle.x + paddle.w) {
      applyPower(d.kind); (d as any).y = H + 100
    }
  }
  drops = drops.filter((d) => d.y < H + 20)

  // lasers
  if (laserT > 0 && performance.now() - lastLaserShot > 240) {
    lastLaserShot = performance.now()
    lasers.push({ x: paddle.x + 8, y: paddle.y })
    lasers.push({ x: paddle.x + paddle.w - 8, y: paddle.y })
    tone(1100, 0.04, 'sawtooth', 0.06)
  }
  for (const l of lasers) l.y -= 8
  for (const l of lasers) {
    for (const b of bricks) {
      if (!b.alive) continue
      if (l.x > b.x && l.x < b.x + b.w && l.y < b.y + b.h && l.y > b.y - 6) {
        if (b.kind !== 'metal') damageBrick(b, l.x, l.y, false)
        l.y = -100
      }
    }
  }
  lasers = lasers.filter((l) => l.y > -10)

  // particles, texts, rings
  for (const p of particles) { p.x += p.vx; p.y += p.vy; p.vy += 0.06; p.life -= 1 }
  particles = particles.filter((p) => p.life > 0)
  for (const t of texts) { t.t += 1; t.y -= 0.4 }
  texts = texts.filter((t) => t.t < t.max)
  for (const r of rings) r.r += (r.max - r.r) * 0.12
  rings = rings.filter((r) => r.r < r.max - 1)

  // timers tick
  if (wideT > 0) wideT -= 1; else if (paddle.w > 120 && shortT === 0) paddle.w = 120
  if (shortT > 0) shortT -= 1; else if (paddle.w < 120 && wideT === 0) paddle.w = 120
  if (slowT > 0) slowT -= 1
  if (stickyT > 0) stickyT -= 1
  if (laserT > 0) laserT -= 1
  if (wrapT > 0) wrapT -= 1
  if (gravityT > 0) gravityT -= 1
  if (shrinkT > 0) shrinkT -= 1; else if (balls.length && balls[0].r === 4) balls.forEach((b) => b.r = 7)
  if (reverseT > 0) reverseT -= 1
  if (invisibleT > 0) invisibleT -= 1
  if (debuffT > 0) debuffT -= 1; else debuffLabel.value = ''
  if (powerLeft.value > 0) powerLeft.value -= 1; else powerLabel.value = ''
  if (shakeT > 0) shakeT -= 1

  // combo decay if no brick
  if (balls.length && balls[0].vy < 0 === false && combo.value > 0 && performance.now() - lastBeatT > 2000 && runStats.broken > 0 && combo.value > 0) {
    // (no-op)
  }

  // trajectory cache (every 4 frames)
  if (++trajCacheT >= 4) { trajCacheT = 0; computeTrajectory() }

  // level complete
  if (aliveBricks.value === 0) nextLevel()
}

function computeTrajectory() {
  if (!s.ghostLine || balls.length === 0) { traj = []; return }
  const b = balls[0]
  let x = b.x, y = b.y, vx = b.vx, vy = b.vy
  traj = []
  for (let i = 0; i < 60; i++) {
    x += vx; y += vy
    if (x < b.r || x > W - b.r) { vx = -vx }
    if (y < b.r) { vy = -vy }
    if (y > paddle.y - b.r) break
    if (i % 2 === 0) traj.push({ x, y })
  }
}

function nextLevel() {
  s.adaptive.clears = Math.min(8, s.adaptive.clears + 1)
  score.value += 100
  level.value += 1
  levelInWorld.value += 1
  if (levelInWorld.value >= 4) { levelInWorld.value = 0; s.world = (s.world + 1) % 8; pushFloat(W / 2, H / 2, `World ${s.world + 1} →`, '#fde68a'); announce('New world') }
  buildLevel(level.value)
  balls = []
  spawnBall(paddle.x + paddle.w / 2, paddle.y - 30)
  if (s.assistLock) balls[0].sticky = true
  started.value = false
  store.beep('win')
  computeAliveBricks()
}

function endRun() {
  alive = false
  // rank: based on bricks/(missed+1)
  const eff = runStats.broken / (runStats.history.length + 1)
  const r = eff > 35 ? 'S' : eff > 25 ? 'A' : eff > 18 ? 'B' : eff > 12 ? 'C' : eff > 6 ? 'D' : 'E'
  rank.value = r
  if (rankWeight(r) > rankWeight(s.bestRank)) s.bestRank = r
  // unlocks
  if (runStats.broken >= 30 && !s.unlocked.paddles.includes('curved')) { s.unlocked.paddles.push('curved'); runUnlocks.value.push('Curved paddle') }
  if (runStats.maxCombo >= 25 && !s.unlocked.trails.includes('rainbow')) { s.unlocked.trails.push('rainbow'); runUnlocks.value.push('Rainbow trail') }
  if (level.value >= 5 && !s.unlocked.arenas.includes(world.value + 1) && world.value + 1 < 8) { s.unlocked.arenas.push(world.value + 1); runUnlocks.value.push(`World ${world.value + 2}`) }
  if (runStats.powerCaught >= 8 && !s.unlocked.music.includes('chiptune')) { s.unlocked.music.push('chiptune'); runUnlocks.value.push('Chiptune') }
  if (runStats.broken >= 60 && !s.unlocked.paddles.includes('twin')) { s.unlocked.paddles.push('twin'); runUnlocks.value.push('Twin paddle') }
  // shards
  s.shards += Math.round(runStats.broken / 4 + runStats.maxCombo / 5 + runStats.powerCaught)
  // tip
  const m = runStats.misses
  const worstZone = (Object.keys(m) as Array<'left' | 'centre' | 'right'>).sort((a, b) => m[b] - m[a])[0]
  runTip.value = worstZone === 'centre'
    ? 'Most misses dead-centre — slow down and let the paddle catch up.'
    : `Most misses on the ${worstZone} — track the ball there sooner.`
  // trajectory polyline (last balls history)
  trajPoly.value = runStats.history.map((v, i) => `${(i / Math.max(1, runStats.history.length - 1)) * 240},${60 - Math.min(60, v / 5)}`).join(' ')
  // save replay
  s.ghostBest = recordX.slice(-1800)
  // daily
  if (mode.value === 'daily') {
    const dKey = new Date().toISOString().slice(0, 10)
    if (!s.daily || s.daily.date !== dKey || score.value > s.daily.score) s.daily = { date: dKey, score: score.value }
  }
  saveSettings()
  analyticsOpen.value = true
}

function rankWeight(r: string) { return ['E', 'D', 'C', 'B', 'A', 'S'].indexOf(r) }
function rankColor(r: string) {
  return r === 'S' ? '#fde68a' : r === 'A' ? '#a78bfa' : r === 'B' ? '#60a5fa' : r === 'C' ? '#22d3ee' : r === 'D' ? '#94a3b8' : '#cbd5e1'
}

// ─── Replay ──────────────────────────────────────────────────────────────
function enterReplay() {
  if (!s.ghostBest?.length) return
  settingsOpen.value = false
  replayPaddleX = s.ghostBest.slice()
  replayActive = true
  replayIdx = 0
  reset()
  started.value = true
}
function stepReplay() {
  if (replayIdx >= replayPaddleX.length) { replayActive = false; analyticsOpen.value = false; reset(); return }
  paddle.x = replayPaddleX[replayIdx++]
}

function replayRun() { analyticsOpen.value = false; reset() }
function finishRun() {
  analyticsOpen.value = false
  props.onGameOver(score.value)
}

// ─── Calibration ─────────────────────────────────────────────────────────
function startCalibration() {
  settingsOpen.value = false
  calActive = true; calStart = performance.now(); calReact.length = 0
  reset(); started.value = true
}
function exportSeed() {
  const txt = `crusher seed: ${seed} (world ${world.value + 1}, level ${level.value})`
  try { navigator.clipboard?.writeText(txt) } catch (_) { /* ignore */ }
  pushFloat(W / 2, H / 2, 'Seed copied', '#fbbf24')
}

// ─── Effects/derived ─────────────────────────────────────────────────────
const effLabel = computed(() => {
  const e = runStats.broken / Math.max(1, runStats.paddleHits)
  return e.toFixed(2) + ' /bounce'
})
function heatPct(v: number) {
  const t = Math.max(1, runStats.misses.left + runStats.misses.centre + runStats.misses.right)
  return Math.round((v / t) * 100)
}

// ─── Draw ────────────────────────────────────────────────────────────────
function draw() {
  const c = cv.value!.getContext('2d')!
  // dynamic zoom + shake
  zoomT += ((balls[0] && Math.hypot(balls[0].vx, balls[0].vy) > baseSpeed * 1.4 ? 1.04 : 1.0) - zoomT) * 0.05
  c.save()
  c.fillStyle = '#000'; c.fillRect(0, 0, W, H)
  // background gradient by world
  const bgs: Record<number, [string, string]> = {
    0: ['#0b0e1f', '#1f2937'], 1: ['#020617', '#0f172a'], 2: ['#311b92', '#1a0b3d'],
    3: ['#0f172a', '#1e3a8a'], 4: ['#0b132b', '#3b0764'], 5: ['#020617', '#020617'],
    6: ['#3a0a0a', '#7f1d1d'], 7: ['#000', '#1e293b'],
  }
  const [g1, g2] = bgs[world.value] || bgs[0]
  const grad = c.createLinearGradient(0, 0, 0, H)
  grad.addColorStop(0, g1); grad.addColorStop(1, g2); c.fillStyle = grad; c.fillRect(0, 0, W, H)
  // grid (parallax)
  c.strokeStyle = s.highContrast ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.06)'
  c.lineWidth = 1
  const off = (performance.now() / 60) % 30
  for (let x = -off; x < W; x += 30) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, H); c.stroke() }
  for (let y = -off; y < H; y += 30) { c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke() }
  // shake
  if (shakeT > 0 && !s.reducedMotion) {
    const dx = (rand() - 0.5) * shakeMag, dy = (rand() - 0.5) * shakeMag
    c.translate(dx, dy)
  }
  // bricks
  for (const b of bricks) {
    if (!b.alive) {
      if (b.kind === 'regen' && b.regenAt) {
        const left = (b.regenAt - performance.now()) / 3000
        c.strokeStyle = `hsla(280, 80%, 60%, ${0.3 + (1 - left) * 0.5})`
        c.strokeRect(b.x, b.y, b.w, b.h)
      }
      continue
    }
    const hpRatio = b.hp / Math.max(1, b.maxHp)
    if (b.kind === 'metal') {
      c.fillStyle = '#94a3b8'
      c.fillRect(b.x, b.y, b.w, b.h)
      c.fillStyle = 'rgba(255,255,255,0.18)'
      c.fillRect(b.x + 2, b.y + 2, b.w - 4, 3)
    } else if (b.kind === 'boss') {
      const hue = 320 + Math.sin((b.rotPhase || 0) * 2) * 20
      c.fillStyle = `hsl(${hue}, 75%, 55%)`
      c.fillRect(b.x, b.y, b.w, b.h)
      c.strokeStyle = '#fde68a'; c.lineWidth = 2
      c.strokeRect(b.x, b.y, b.w, b.h)
      // weak point pulses
      c.fillStyle = `rgba(253,224,71,${0.4 + Math.sin((b.rotPhase || 0) * 3) * 0.4})`
      c.beginPath(); c.arc(b.x + b.w / 2, b.y + b.h / 2, 12, 0, Math.PI * 2); c.fill()
      // hp bar
      c.fillStyle = '#0008'; c.fillRect(b.x, b.y - 6, b.w, 4)
      c.fillStyle = '#f43f5e'; c.fillRect(b.x, b.y - 6, b.w * hpRatio, 4)
    } else {
      let hue = b.hue
      if (b.kind === 'fire') hue = 14 + Math.sin((b.rotPhase || 0) * 4) * 8
      else if (b.kind === 'ice') hue = 200
      else if (b.kind === 'electric') hue = 50 + Math.sin((b.rotPhase || 0) * 6) * 8
      c.fillStyle = `hsl(${hue}, 80%, ${50 + (1 - hpRatio) * 10}%)`
      if (s.highContrast) { c.fillStyle = `hsl(${hue}, 100%, 70%)` }
      c.fillRect(b.x, b.y, b.w, b.h)
      if (b.maxHp > 1 && b.hp < b.maxHp) {
        c.strokeStyle = 'rgba(0,0,0,0.5)'; c.lineWidth = 1
        c.beginPath(); c.moveTo(b.x + 2, b.y + b.h - 4); c.lineTo(b.x + b.w - 4, b.y + 4); c.stroke()
      }
      // glow
      if (!s.reducedMotion && (b.kind === 'fire' || b.kind === 'electric' || b.kind === 'ice')) {
        c.fillStyle = `hsla(${hue}, 90%, 70%, 0.18)`
        c.fillRect(b.x - 2, b.y - 2, b.w + 4, b.h + 4)
      }
      if (b.kind === 'electric') {
        c.strokeStyle = '#facc15'; c.lineWidth = 1
        c.beginPath(); c.moveTo(b.x + 4, b.y + b.h / 2); c.lineTo(b.x + b.w / 2, b.y + 2); c.lineTo(b.x + b.w - 4, b.y + b.h / 2); c.stroke()
      }
    }
  }
  // trajectory ghost
  if (s.ghostLine && traj.length && balls[0]) {
    c.strokeStyle = 'rgba(96,165,250,0.6)'; c.lineWidth = 1; c.setLineDash([2, 4])
    c.beginPath(); c.moveTo(balls[0].x, balls[0].y)
    for (const p of traj) c.lineTo(p.x, p.y)
    c.stroke(); c.setLineDash([])
  }
  // paddle
  drawPaddle(c)
  // balls
  for (const b of balls) {
    drawBall(c, b)
  }
  // drops
  for (const d of drops) {
    c.fillStyle = `hsl(${d.hue}, 80%, 60%)`
    c.beginPath(); c.arc(d.x, d.y, 8, 0, Math.PI * 2); c.fill()
    c.strokeStyle = '#fff'; c.lineWidth = 1; c.stroke()
    c.fillStyle = '#fff'; c.font = 'bold 9px sans-serif'; c.textAlign = 'center'
    c.fillText(dropIcon(d.kind), d.x, d.y + 3)
  }
  // lasers
  c.fillStyle = '#ef4444'
  for (const l of lasers) c.fillRect(l.x - 1, l.y - 8, 2, 8)
  // particles
  for (const p of particles) {
    const a = p.life / p.max
    c.globalAlpha = a; c.fillStyle = p.col
    c.fillRect(p.x, p.y, p.size, p.size)
  }
  c.globalAlpha = 1
  // floats
  for (const t of texts) {
    c.globalAlpha = 1 - t.t / t.max; c.fillStyle = t.col; c.font = 'bold 14px sans-serif'; c.textAlign = 'center'
    c.fillText(t.text, t.x, t.y)
  }
  c.globalAlpha = 1
  // rings
  for (const r of rings) {
    c.strokeStyle = r.col; c.lineWidth = 2; c.globalAlpha = 1 - r.r / r.max
    c.beginPath(); c.arc(r.x, r.y, r.r, 0, Math.PI * 2); c.stroke()
  }
  c.globalAlpha = 1
  // bottom death-zone glow
  c.fillStyle = 'rgba(239,68,68,0.06)'; c.fillRect(0, paddle.y + 18, W, H - paddle.y - 18)
  // hud overlays already in dom
  // shield indicator
  if (shieldCharges > 0) {
    c.strokeStyle = '#60a5fa'; c.lineWidth = 2
    c.beginPath(); c.arc(paddle.x + paddle.w / 2, paddle.y + paddle.h / 2, paddle.w / 2 + 6, 0, Math.PI * 2); c.stroke()
  }
  if (cinematicSlowT > 0) {
    c.fillStyle = 'rgba(0,0,0,0.18)'; c.fillRect(0, 0, W, H)
  }
  if (invisibleT > 0) {
    c.fillStyle = 'rgba(0,0,0,0.85)'; c.fillRect(0, 0, W, H)
  }
  c.restore()
}

function drawPaddle(c: CanvasRenderingContext2D) {
  const matFill = s.paddleMat === 'carbon' ? '#1f2937'
    : s.paddleMat === 'glass' ? 'rgba(255,255,255,0.6)' : '#fff'
  if (laserT > 0) c.fillStyle = '#ef4444'
  else c.fillStyle = matFill
  if (s.paddleShape === 'twin') {
    const half = (paddle.w - 18) / 2
    c.fillRect(paddle.x, paddle.y, half, paddle.h)
    c.fillRect(paddle.x + half + 18, paddle.y, half, paddle.h)
  } else {
    c.fillRect(paddle.x, paddle.y, paddle.w, paddle.h)
    if (s.paddleShape === 'curved') {
      c.beginPath(); c.moveTo(paddle.x, paddle.y + paddle.h)
      c.quadraticCurveTo(paddle.x + paddle.w / 2, paddle.y + paddle.h + 6, paddle.x + paddle.w, paddle.y + paddle.h)
      c.lineTo(paddle.x + paddle.w, paddle.y); c.lineTo(paddle.x, paddle.y); c.closePath(); c.fill()
    }
  }
  if (s.paddleMat === 'neon') {
    c.shadowColor = '#a78bfa'; c.shadowBlur = 14
    c.fillRect(paddle.x, paddle.y, paddle.w, paddle.h)
    c.shadowBlur = 0
  }
  // reflection on floor
  c.globalAlpha = 0.18
  c.fillRect(paddle.x, paddle.y + paddle.h + 4, paddle.w, 6)
  c.globalAlpha = 1
}
function drawBall(c: CanvasRenderingContext2D, b: Ball) {
  // trail
  if (!s.reducedMotion) {
    for (let i = 1; i <= 6; i++) {
      const tx = b.x - b.vx * i * 0.6, ty = b.y - b.vy * i * 0.6
      const hue = s.ballTrail === 'rainbow' ? (b.trailHue + i * 25) % 360 : s.ballTrail === 'golden' ? 50 : s.ballTrail === 'electric' ? 220 : b.trailHue
      c.fillStyle = `hsla(${hue}, 90%, 65%, ${0.3 - i * 0.04})`
      c.beginPath(); c.arc(tx, ty, b.r * (1 - i * 0.12), 0, Math.PI * 2); c.fill()
    }
  }
  c.shadowColor = `hsl(${b.trailHue}, 90%, 65%)`; c.shadowBlur = 12
  c.fillStyle = '#fff'
  c.beginPath(); c.arc(b.x, b.y, b.r, 0, Math.PI * 2); c.fill()
  c.shadowBlur = 0
}
function dropIcon(k: PowerKind) {
  switch (k) {
    case 'longer': return 'W'
    case 'multi': return '+'
    case 'slow': return '⏱'
    case 'sticky': return 'S'
    case 'laser': return 'L'
    case 'wrap': return '↻'
    case 'gravity': return '↓'
    case 'shield': return '◯'
    case 'nuke': return '✸'
    case 'shrink': return '·'
    case 'short': return '!'
    case 'reverse': return '⇄'
    case 'invisible': return '?'
    default: return '·'
  }
}

// ─── Lifecycle ───────────────────────────────────────────────────────────
onMounted(() => {
  cv.value!.width = W; cv.value!.height = H
  reset()
  raf = requestAnimationFrame(loop)
  root.value?.focus()
})
onUnmounted(() => { cancelAnimationFrame(raf); try { audioCtx?.close() } catch (_) { /* ignore */ } })
watch(() => props.running, (v) => { if (v) { reset() } })
watch(() => props.mode, () => reset())
watch(() => props.difficulty, () => reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 6px; outline: none; position: relative; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; touch-action: none; }
.hud-top {
  position: absolute; top: 6px; left: 8px; right: 8px;
  display: flex; gap: 6px; flex-wrap: wrap; align-items: center; z-index: 5;
}
.hud-top .chip {
  font-size: 11px; color: #fff; background: rgba(0,0,0,0.55); padding: 3px 8px; border-radius: 999px;
  font-weight: 600;
}
.hud-top .chip.btn { cursor: pointer; }
.hud-top .chip.flex { background: transparent; flex: 1; padding: 0; }
.centerline { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; pointer-events: none; }
.centerline .big { color: #fff; font-weight: 700; font-size: 20px; }
.centerline .sub { color: rgba(255,255,255,0.7); font-size: 12px; margin-top: 4px; }
.overlay {
  position: absolute; inset: 0; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center;
  z-index: 20; padding: 16px;
}
.panel {
  width: 100%; max-width: 340px; background: #0f172a; border-radius: 16px; padding: 14px; color: #fff;
  max-height: 90%; overflow: auto;
}
.panel .row { display: flex; align-items: center; gap: 8px; }
.panel .row.head { justify-content: space-between; }
.panel h3 { font-size: 16px; font-weight: 700; margin: 0; }
.panel .x { background: transparent; color: #fff; border: none; font-size: 18px; cursor: pointer; }
.panel .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px; }
.panel label { font-size: 11px; color: rgba(255,255,255,0.85); display: flex; flex-direction: column; gap: 4px; }
.panel select, .panel input[type=range] { width: 100%; box-sizing: border-box; padding: 4px; background: #1e293b; color: #fff; border: 1px solid #334155; border-radius: 6px; }
.panel .slider { margin-top: 8px; font-size: 11px; color: rgba(255,255,255,0.85); }
.panel .toggles { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: rgba(255,255,255,0.92); }
.panel .toggles label { flex-direction: row; gap: 6px; align-items: center; font-size: 12px; }
.panel .row.foot { margin-top: 12px; display: flex; gap: 8px; }
.panel .btn { padding: 8px 14px; border: none; background: #6366f1; color: #fff; border-radius: 999px; cursor: pointer; font-weight: 700; font-size: 12px; }
.panel .btn.ghost { background: rgba(255,255,255,0.18); color: #fff; }
.panel .btn:disabled { opacity: 0.5; cursor: not-allowed; }
.panel .hint { margin-top: 8px; font-size: 11px; color: rgba(255,255,255,0.7); }
.panel .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 6px; }
.panel .tile { background: rgba(255,255,255,0.08); border-radius: 8px; padding: 6px; text-align: center; }
.panel .tile span { font-size: 10px; color: rgba(255,255,255,0.6); display: block; }
.panel .tile b { font-size: 14px; }
.panel .ana-section { margin-top: 10px; }
.panel .ana-label { font-size: 11px; color: rgba(255,255,255,0.7); margin-bottom: 4px; }
.panel .heatmap { display: flex; flex-direction: column; gap: 4px; }
.panel .heatmap .zone { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.panel .heatmap .bar { height: 8px; background: linear-gradient(90deg, #fb7185, #f59e0b); border-radius: 4px; min-width: 4px; }
.panel .graph { width: 100%; height: 60px; background: rgba(255,255,255,0.08); border-radius: 6px; }
.panel .tip { background: rgba(96,165,250,0.18); border-radius: 8px; padding: 6px; font-size: 12px; margin-top: 8px; }
.panel .unlock { background: rgba(250,204,21,0.18); border-radius: 8px; padding: 6px; color: #fde68a; font-weight: 700; font-size: 12px; margin-top: 8px; }
</style>
