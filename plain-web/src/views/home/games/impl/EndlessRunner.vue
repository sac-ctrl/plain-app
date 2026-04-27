<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <canvas ref="cv" class="cv" :style="cbFilter"
      @pointerdown="pdown" @pointermove="pmove" @pointerup="pup" @pointercancel="pup"
    />
    <!-- HUD -->
    <div class="topbar">
      <div class="hud-pill">{{ Math.floor(distance) }}m</div>
      <div class="hud-pill" v-if="combo > 1">x{{ combo }} combo</div>
      <div class="hud-pill" v-if="powers.length">
        <span v-for="p in powers" :key="p.id" class="pwr" :style="{ color: p.color }">{{ p.icon }} {{ Math.ceil(p.left/60) }}s</span>
      </div>
      <div class="hud-pill" v-if="gameMode === 'Time'">⏱ {{ Math.max(0, Math.ceil(timeLeftMs/1000)) }}s</div>
      <div class="hud-pill" v-if="gameMode === 'Stage'">{{ stageLabel }}</div>
      <div class="hud-pill" v-if="missionLabel">🎯 {{ missionLabel }}</div>
      <div class="hud-pill" v-if="dailyActive">🌅 daily</div>
      <button class="settings" @click="settingsOpen = true">⚙</button>
    </div>
    <div class="hint" v-if="!started && alive">{{ startHint }}</div>

    <!-- near-miss screen edge glow -->
    <div class="edgeglow" :class="{ on: nearMissGlowMs > 0 }"></div>

    <!-- Voice toast -->
    <transition name="voice">
      <div v-if="voiceMsg" class="voice">{{ voiceMsg }}</div>
    </transition>

    <!-- Settings -->
    <transition name="fade">
      <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
        <div class="card">
          <div class="card-h"><b>Lane Rush · Settings</b><button class="x" @click="settingsOpen = false">×</button></div>

          <div class="grp">
            <label>Mode</label>
            <div class="chips">
              <button v-for="m in MODES" :key="m" class="chip" :class="{ on: gameMode === m }" @click="setMode(m)">{{ m }}</button>
            </div>
          </div>

          <div class="grp">
            <label>Lanes</label>
            <div class="chips">
              <button v-for="n in [3,4,5]" :key="n" class="chip" :class="{ on: laneCount === n }" @click="laneCount = n; clampLane(); persist()">{{ n }} lanes</button>
            </div>
          </div>

          <div class="grp two">
            <div>
              <label>Swipe length threshold (px): {{ swipeLen }}</label>
              <input type="range" min="10" max="120" step="2" v-model.number="swipeLen" @change="persist" />
            </div>
            <div>
              <label>Centre dead-zone (px): {{ deadZone }}</label>
              <input type="range" min="0" max="60" step="2" v-model.number="deadZone" @change="persist" />
            </div>
          </div>

          <div class="grp two">
            <div><label><input type="checkbox" v-model="useTilt" @change="persist" /> Tilt steering (gyro)</label></div>
            <div>
              <label>Tilt sensitivity: {{ tiltSens.toFixed(2) }}</label>
              <input type="range" min="0.4" max="2.5" step="0.05" v-model.number="tiltSens" @change="persist" />
            </div>
          </div>

          <div class="grp two">
            <div><label><input type="checkbox" v-model="haptics" @change="persist" /> Haptics</label></div>
            <div><label><input type="checkbox" v-model="oneHanded" @change="persist" /> One-handed swipe zone</label></div>
          </div>

          <div class="grp">
            <label>One-handed side</label>
            <div class="chips">
              <button class="chip" :class="{ on: oneHandedSide === 'right' }" :disabled="!oneHanded" @click="oneHandedSide = 'right'; persist()">Right</button>
              <button class="chip" :class="{ on: oneHandedSide === 'left' }" :disabled="!oneHanded" @click="oneHandedSide = 'left'; persist()">Left</button>
            </div>
          </div>

          <div class="grp">
            <label>Theme (cycles every 5 runs unless pinned)</label>
            <div class="chips">
              <button v-for="t in THEMES" :key="t.id" class="chip" :class="{ on: theme === t.id }" @click="theme = t.id; persist()">{{ t.name }}</button>
              <button class="chip" :class="{ on: themePin }" @click="themePin = !themePin; persist()">{{ themePin ? '📌 pinned' : 'pin' }}</button>
            </div>
          </div>

          <div class="grp">
            <label>Vehicle skin (unlock by playing) · {{ unlockedSkins.length }}/{{ SKINS.length }}</label>
            <div class="chips">
              <button v-for="s in SKINS" :key="s.id" class="chip" :class="{ on: vehicleSkin === s.id, locked: !unlockedSkins.includes(s.id) }"
                :disabled="!unlockedSkins.includes(s.id)" @click="vehicleSkin = s.id; persist()">
                {{ unlockedSkins.includes(s.id) ? s.name : '🔒 ' + s.name }}
              </button>
            </div>
          </div>

          <div class="grp">
            <label>Permanent upgrades · coins {{ store.coins }}</label>
            <div class="chips">
              <button class="chip" :class="{ on: upShield > 0 }" :disabled="upShield >= 3 || store.coins < 30 * (upShield+1)" @click="buyUpgrade('shield')">🛡 Shield duration {{ upShield }}/3</button>
              <button class="chip" :class="{ on: upMagnet > 0 }" :disabled="upMagnet >= 3 || store.coins < 30 * (upMagnet+1)" @click="buyUpgrade('magnet')">🧲 Magnet range {{ upMagnet }}/3</button>
              <button class="chip" :class="{ on: upSlow > 0 }" :disabled="upSlow >= 3 || store.coins < 30 * (upSlow+1)" @click="buyUpgrade('slow')">⏳ Slow-mo {{ upSlow }}/3</button>
              <button class="chip" :class="{ on: upDouble > 0 }" :disabled="upDouble >= 3 || store.coins < 30 * (upDouble+1)" @click="buyUpgrade('double')">✨ Double score {{ upDouble }}/3</button>
            </div>
          </div>

          <div class="grp two">
            <div><label><input type="checkbox" v-model="reducedMotion" @change="persist" /> Reduced motion</label></div>
            <div><label><input type="checkbox" v-model="highContrast" @change="persist" /> High contrast outlines</label></div>
            <div><label><input type="checkbox" v-model="batterySaver" @change="persist" /> Battery saver (30 fps)</label></div>
            <div><label><input type="checkbox" v-model="announcer" @change="persist" /> Voice announcer (toast)</label></div>
            <div><label><input type="checkbox" v-model="layeredMusic" @change="persist" /> Adaptive layered music</label></div>
            <div><label><input type="checkbox" v-model="audioDuck" @change="persist" /> Audio ducking</label></div>
          </div>

          <div class="grp">
            <label>Assist modes</label>
            <div class="chips">
              <button class="chip" :class="{ on: assistAutoSlow }" @click="assistAutoSlow = !assistAutoSlow; persist()">Auto-slow on approach</button>
              <button class="chip" :class="{ on: assistLaneGlow }" @click="assistLaneGlow = !assistLaneGlow; persist()">Lane highlight</button>
              <button class="chip" :class="{ on: assistInfShield }" @click="assistInfShield = !assistInfShield; persist()">Practice (∞ shield)</button>
            </div>
          </div>

          <div class="grp">
            <label>Colourblind</label>
            <div class="chips">
              <button v-for="m in CB_MODES" :key="m" class="chip" :class="{ on: colorblind === m }" @click="colorblind = m as any; persist()">{{ m }}</button>
            </div>
          </div>

          <div class="grp two">
            <div>
              <label>Adaptive difficulty (hidden MMR): {{ Math.round(mmr) }}</label>
              <div class="bar"><div class="fill" :style="{ width: mmr + '%' }"></div></div>
              <small class="muted">{{ mmrTip }}</small>
            </div>
            <div>
              <button class="chip wide" @click="openCalibration">📐 Calibrate swipe</button>
              <button class="chip wide" @click="watchGhost = !watchGhost">{{ watchGhost ? '👻 Ghost ON' : '👻 Watch ghost' }}</button>
              <button class="chip wide" @click="copyChallenge">🔗 Share daily challenge</button>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- Calibration overlay -->
    <transition name="fade">
      <div v-if="calibrationOpen" class="overlay" @click.self="calibrationOpen = false">
        <div class="card">
          <div class="card-h"><b>Swipe calibration ({{ Math.min(calRound,5) }}/5)</b><button class="x" @click="calibrationOpen = false">×</button></div>
          <div class="cal-pad" @pointerdown="calStart" @pointerup="calEnd" @pointermove="calMove">
            <div v-if="calRound <= 5">{{ calMessage || 'Swipe across at your natural speed' }}</div>
            <div v-else>Avg swipe: <b>{{ calAvg }}px</b> — applied as threshold.</div>
          </div>
          <div class="row">
            <button class="chip wide" @click="applyCalibration">Apply</button>
            <button class="chip wide" @click="calibrationOpen = false">Cancel</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Post-run analytics -->
    <transition name="fade">
      <div v-if="showAnalytics" class="overlay" @click.self="showAnalytics = false">
        <div class="card analytics">
          <div class="card-h"><b>Run · {{ Math.floor(distance) }}m · {{ runCoins }} coins</b><button class="x" @click="onContinue">×</button></div>
          <div class="row tips">{{ runTip }}</div>
          <div class="row">
            <div class="stat"><label>Top combo</label><b>x{{ stats.maxComboRun }}</b></div>
            <div class="stat"><label>Near misses</label><b>{{ stats.nearMissRun }}</b></div>
            <div class="stat"><label>Avg reaction</label><b>{{ avgReaction }}ms</b></div>
            <div class="stat"><label>Deadliest</label><b>{{ deadliest }}</b></div>
          </div>
          <div class="row">
            <div>
              <label>Lane heatmap</label>
              <div class="heat">
                <div v-for="(v,i) in laneHeat" :key="i" class="heatcol" :style="{ height: (heatScale(v) * 100) + '%' }">
                  <span>L{{ i+1 }}</span>
                </div>
              </div>
            </div>
            <div class="grow">
              <label>Reaction graph (ms per response)</label>
              <svg class="reactsvg" :viewBox="`0 0 ${reactionGraphW} 80`" preserveAspectRatio="none">
                <polyline :points="reactPoints" fill="none" stroke="#38bdf8" stroke-width="2" />
                <line x1="0" :x2="reactionGraphW" y1="40" y2="40" stroke="#475569" stroke-dasharray="3,3" />
              </svg>
            </div>
          </div>
          <div v-if="newUnlocks.length" class="row unlocks">🎉 Unlocked: {{ newUnlocks.join(', ') }}</div>
          <div class="row">
            <button class="chip wide primary" @click="onReplay">↺ Instant replay</button>
            <button class="chip wide" @click="watchGhost = true; onReplay()">👻 Race the ghost</button>
            <button class="chip wide" @click="exportReplay">⬇ Share replay link</button>
            <button class="chip wide" @click="onContinue">Continue</button>
          </div>
          <details class="crash">
            <summary>Crash log</summary>
            <pre>{{ crashLog }}</pre>
          </details>
        </div>
      </div>
    </transition>

    <!-- SVG colourblind filters -->
    <svg width="0" height="0" style="position:absolute">
      <defs>
        <filter id="lr-cb-pro"><feColorMatrix type="matrix" values="0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0" /></filter>
        <filter id="lr-cb-deu"><feColorMatrix type="matrix" values="0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0" /></filter>
        <filter id="lr-cb-tri"><feColorMatrix type="matrix" values="0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0" /></filter>
      </defs>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: string
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()

// ============================
// Constants
// ============================
const MODES = ['Endless', 'Stage', 'Time', 'Mission'] as const
type Mode = typeof MODES[number]
const CB_MODES = ['off', 'protanopia', 'deuteranopia', 'tritanopia'] as const

const THEMES = [
  { id: 'asphalt', name: 'Asphalt', sky: '#0f172a', road: '#1e293b', stripe: '#facc15' },
  { id: 'rain', name: 'Rain', sky: '#1e293b', road: '#0f172a', stripe: '#bae6fd' },
  { id: 'night', name: 'Night City', sky: '#020617', road: '#1e1b4b', stripe: '#fbbf24' },
  { id: 'desert', name: 'Desert', sky: '#fde68a', road: '#7c2d12', stripe: '#fff7ed' },
  { id: 'neon', name: 'Neon', sky: '#0a001f', road: '#3b0764', stripe: '#22d3ee' },
] as const

const SKINS = [
  { id: 'starter', name: 'Starter', body: '#ef4444', glow: '#fecaca', ability: 'none' },
  { id: 'cyber', name: 'Cyber', body: '#06b6d4', glow: '#67e8f9', ability: '+5% combo' },
  { id: 'phantom', name: 'Phantom', body: '#1e1b4b', glow: '#a78bfa', ability: '+1 buffer slot' },
  { id: 'inferno', name: 'Inferno', body: '#f97316', glow: '#fed7aa', ability: 'shield burns' },
  { id: 'glacier', name: 'Glacier', body: '#bae6fd', glow: '#e0f2fe', ability: 'slip 0.1s' },
  { id: 'volt', name: 'Volt', body: '#facc15', glow: '#fef3c7', ability: '+1 magnet' },
  { id: 'meteor', name: 'Meteor', body: '#374151', glow: '#9ca3af', ability: 'tougher near miss' },
  { id: 'azure', name: 'Azure', body: '#3b82f6', glow: '#bfdbfe', ability: 'voice doubled' },
  { id: 'jade', name: 'Jade', body: '#22c55e', glow: '#bbf7d0', ability: '+10% coins' },
  { id: 'sakura', name: 'Sakura', body: '#ec4899', glow: '#fbcfe8', ability: 'softer crash' },
  { id: 'midnight', name: 'Midnight', body: '#0f172a', glow: '#475569', ability: 'stealth' },
  { id: 'royal', name: 'Royal', body: '#7c3aed', glow: '#ddd6fe', ability: 'crown trail' },
  { id: 'sunset', name: 'Sunset', body: '#fb923c', glow: '#fde68a', ability: 'long combo' },
  { id: 'forest', name: 'Forest', body: '#16a34a', glow: '#86efac', ability: 'eco' },
  { id: 'arctic', name: 'Arctic', body: '#e0f2fe', glow: '#bae6fd', ability: 'cooler engine' },
  { id: 'lava', name: 'Lava', body: '#dc2626', glow: '#fca5a5', ability: 'hot exhaust' },
  { id: 'mint', name: 'Mint', body: '#34d399', glow: '#a7f3d0', ability: 'fresh' },
  { id: 'rose', name: 'Rose', body: '#f43f5e', glow: '#fda4af', ability: 'lucky' },
  { id: 'mono', name: 'Mono', body: '#737373', glow: '#d4d4d4', ability: 'minimal' },
  { id: 'gold', name: 'Gold', body: '#facc15', glow: '#fde68a', ability: 'rich' },
] as const

const OBSTACLES = [
  { id: 'car',     w: 0.65, h: 1.25, c: '#3b82f6', label: 'sedan' },
  { id: 'truck',   w: 0.85, h: 1.55, c: '#94a3b8', label: 'truck' },
  { id: 'police',  w: 0.85, h: 1.30, c: '#1e40af', label: 'police' },
  { id: 'moto',    w: 0.30, h: 0.70, c: '#facc15', label: 'moto' },
  { id: 'barrel',  w: 0.45, h: 0.45, c: '#ea580c', label: 'barrel' },
  { id: 'drone',   w: 0.50, h: 0.50, c: '#22d3ee', label: 'drone' },
  { id: 'block',   w: 0.95, h: 0.30, c: '#fde68a', label: 'block' },
  { id: 'elite',   w: 0.70, h: 1.10, c: '#dc2626', label: 'elite' },
  { id: 'boss',    w: 4.20, h: 1.80, c: '#0f172a', label: 'boss' },
] as const

// ============================
// Persisted settings
// ============================
const SAVE_KEY = 'lane_rush_v1'
const laneCount = ref(3)
const swipeLen = ref(36)
const deadZone = ref(14)
const useTilt = ref(false)
const tiltSens = ref(1.0)
const haptics = ref(true)
const reducedMotion = ref(false)
const highContrast = ref(false)
const batterySaver = ref(false)
const oneHanded = ref(false)
const oneHandedSide = ref<'left' | 'right'>('right')
const colorblind = ref<'off' | 'protanopia' | 'deuteranopia' | 'tritanopia'>('off')
const announcer = ref(true)
const layeredMusic = ref(true)
const audioDuck = ref(true)
const assistAutoSlow = ref(false)
const assistLaneGlow = ref(false)
const assistInfShield = ref(false)
const theme = ref<'asphalt'|'rain'|'night'|'desert'|'neon'>('asphalt')
const themePin = ref(false)
const themeRunCount = ref(0)
const vehicleSkin = ref<string>('starter')
const unlockedSkins = ref<string[]>(['starter'])
const upShield = ref(0)
const upMagnet = ref(0)
const upSlow = ref(0)
const upDouble = ref(0)
const mmr = ref(50)
const stats = reactive({ longest: 0, maxComboAll: 0, maxComboRun: 0, nearMissRun: 0, totalRuns: 0, totalNearMiss: 0 })
const watchGhost = ref(false)
const ghostFrames = ref<{ d: number; lane: number; px: number }[]>([])

function loadSettings() {
  try {
    const j = JSON.parse(localStorage.getItem(SAVE_KEY) || '{}')
    Object.entries(j).forEach(([k, v]) => {
      const m: Record<string, any> = {
        laneCount, swipeLen, deadZone, useTilt, tiltSens, haptics, reducedMotion, highContrast,
        batterySaver, oneHanded, oneHandedSide, colorblind, announcer, layeredMusic, audioDuck,
        assistAutoSlow, assistLaneGlow, assistInfShield, theme, themePin, themeRunCount,
        vehicleSkin, upShield, upMagnet, upSlow, upDouble, mmr,
      }
      if (m[k] != null && v != null) m[k].value = v
    })
    if (Array.isArray(j.unlockedSkins)) unlockedSkins.value = j.unlockedSkins
    if (j.stats) Object.assign(stats, j.stats)
    if (Array.isArray(j.ghostFrames)) ghostFrames.value = j.ghostFrames
  } catch { /* ignore */ }
}
function persist() {
  try {
    localStorage.setItem(SAVE_KEY, JSON.stringify({
      laneCount: laneCount.value, swipeLen: swipeLen.value, deadZone: deadZone.value,
      useTilt: useTilt.value, tiltSens: tiltSens.value, haptics: haptics.value,
      reducedMotion: reducedMotion.value, highContrast: highContrast.value, batterySaver: batterySaver.value,
      oneHanded: oneHanded.value, oneHandedSide: oneHandedSide.value, colorblind: colorblind.value,
      announcer: announcer.value, layeredMusic: layeredMusic.value, audioDuck: audioDuck.value,
      assistAutoSlow: assistAutoSlow.value, assistLaneGlow: assistLaneGlow.value, assistInfShield: assistInfShield.value,
      theme: theme.value, themePin: themePin.value, themeRunCount: themeRunCount.value,
      vehicleSkin: vehicleSkin.value, unlockedSkins: unlockedSkins.value,
      upShield: upShield.value, upMagnet: upMagnet.value, upSlow: upSlow.value, upDouble: upDouble.value,
      mmr: mmr.value, stats, ghostFrames: ghostFrames.value.slice(0, 3000),
    }))
  } catch { /* ignore */ }
}

const cbFilter = computed(() => {
  if (colorblind.value === 'off') return ''
  const m: Record<string, string> = { protanopia: 'url(#lr-cb-pro)', deuteranopia: 'url(#lr-cb-deu)', tritanopia: 'url(#lr-cb-tri)' }
  return { filter: m[colorblind.value] || '' }
})

// ============================
// World state
// ============================
const W = 480, H = 720
const horizonY = H * 0.28
const carBaseY = H * 0.86
const roadTopWidth = 90
const roadBottomWidth = 0.92 * W

type Obs = { id: string; w: number; h: number; c: string; lane: number; z: number; passed: boolean; neared: boolean; spawnAtMs: number; vxLane?: number; vyLane?: number; bossGap?: number; isBoss?: boolean }
type PickKind = 'shield' | 'slow' | 'magnet' | 'double' | 'coin'
type Pick = { kind: PickKind; lane: number; z: number; collected: boolean; phase: number }
type Particle = { x: number; y: number; vx: number; vy: number; life: number; max: number; col: string; size: number }
type Power = { id: number; kind: 'shield'|'slow'|'magnet'|'double'; left: number; icon: string; color: string }

const obstacles = reactive<Obs[]>([])
const pickups = reactive<Pick[]>([])
const particles = reactive<Particle[]>([])
const powers = reactive<Power[]>([])
let nextPwrId = 1

const playerLane = ref(1)
const playerLaneFloat = ref(1)
const laneBuffer = ref<number | null>(null)
const distance = ref(0)
const runCoins = ref(0)
const combo = ref(1)
const speed = ref(140)
const baseSpeed = ref(140)
const maxSpeed = ref(560)
const alive = ref(true)
const started = ref(false)
const timeLeftMs = ref(60000)
const stageTarget = ref(500)
const stageIdx = ref(0)
const settingsOpen = ref(false)
const showAnalytics = ref(false)
const calibrationOpen = ref(false)
const dailyActive = ref(false)
const nearMissGlowMs = ref(0)
const voiceMsg = ref('')
let voiceTimer: any = null
const newUnlocks = ref<string[]>([])
const startHint = ref('Swipe / arrows / A·D · Tap left or right half')
const crashLog = ref('')
let lastObsSpawnAt = 0
const reactionLog = ref<number[]>([])
const laneHeat = ref<number[]>([0, 0, 0, 0, 0])
const deathLog = reactive<Record<string, number>>({})
let runStartedAt = 0
let warningWindowMs = 1300
let dynamicWarning = 1300
let speedDilation = 1.0
let dilationT = 0
let shakeT = 0
const recordedFrames: { d: number; lane: number; px: number }[] = []
let ghostIdx = 0
let frameNo = 0

// ============================
// Mode / setup
// ============================
const gameMode = ref<Mode>('Endless')
function setMode(m: Mode) { gameMode.value = m; reset() }
watch(() => props.mode, (m) => {
  if (!m) return
  const norm = m.charAt(0).toUpperCase() + m.slice(1)
  if ((MODES as readonly string[]).includes(norm)) gameMode.value = norm as Mode
  else if ((MODES as readonly string[]).includes(m as any)) gameMode.value = m as Mode
})

function configure() {
  const map: Record<string, [number, number]> = { easy: [120, 380], medium: [150, 480], hard: [180, 560], insane: [220, 700] }
  const k = (props.difficulty as string) in map ? props.difficulty as string : 'medium'
  baseSpeed.value = map[k][0]
  maxSpeed.value = map[k][1]
  // Mode-specific timeLeft / stageTarget
  if (gameMode.value === 'Time') timeLeftMs.value = 60_000
  if (gameMode.value === 'Stage') {
    const stages = [500, 1000, 2000]
    stageTarget.value = stages[stageIdx.value % stages.length]
  }
  // Adaptive: reaction-based warning window
  const avg = reactionLog.value.length ? reactionLog.value.reduce((a, b) => a + b, 0) / reactionLog.value.length : 700
  dynamicWarning = Math.max(700, Math.min(2200, avg * 2.0))
  warningWindowMs = dynamicWarning
}

function clampLane() { playerLane.value = Math.max(0, Math.min(laneCount.value - 1, playerLane.value)); playerLaneFloat.value = playerLane.value }

function laneCenterX(lane: number, z = 0): number {
  const t = z / 120
  const top = (lane + 0.5) / laneCount.value * roadTopWidth + (W - roadTopWidth) / 2
  const bot = (lane + 0.5) / laneCount.value * roadBottomWidth + (W - roadBottomWidth) / 2
  return top + (bot - top) * (1 - t)
}
function projectY(z: number): number {
  // z=0 → carBaseY, z=120 → horizonY
  const t = Math.max(0, Math.min(1, z / 120))
  return carBaseY + (horizonY - carBaseY) * t
}
function projectScale(z: number): number {
  const t = Math.max(0, Math.min(1, z / 120))
  return 1 - 0.85 * t
}

// ============================
// Daily seed RNG
// ============================
function todaySeed(): number {
  const d = new Date(); const s = `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, '0')}${String(d.getUTCDate()).padStart(2, '0')}`
  return Array.from(s).reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0
}
function makeRng(seed: number) { let x = (seed || Date.now()) >>> 0; return () => { x = (x * 1664525 + 1013904223) >>> 0; return x / 4294967296 } }
let rng = makeRng(Date.now())

function readChallengeSeed(): number | null {
  const m = location.hash.match(/laneSeed=([\w-]+)/)
  if (m) return Array.from(m[1]).reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0
  return null
}

function copyChallenge() {
  const seed = todaySeed().toString(36)
  const url = location.origin + location.pathname + '#laneSeed=' + seed
  try { navigator.clipboard.writeText(url); voice('🔗 Daily link copied to clipboard') } catch { voice(url) }
}

// ============================
// Reset / spawn
// ============================
function reset() {
  configure()
  obstacles.length = 0; pickups.length = 0; particles.length = 0; powers.length = 0
  recordedFrames.length = 0; reactionLog.value = []; laneHeat.value = [0, 0, 0, 0, 0]
  for (const k in deathLog) delete deathLog[k]
  newUnlocks.value = []
  distance.value = 0; runCoins.value = 0; combo.value = 1
  speed.value = baseSpeed.value
  alive.value = true; started.value = false
  shakeT = 0; dilationT = 0; speedDilation = 1
  ghostIdx = 0; frameNo = 0
  runStartedAt = performance.now()
  // Daily / challenge
  const challenge = readChallengeSeed()
  if (challenge != null) { rng = makeRng(challenge); dailyActive.value = true }
  else if (gameMode.value === 'Mission' || rng() < 0.5) { rng = makeRng(todaySeed()); dailyActive.value = true }
  else { rng = makeRng(Date.now()); dailyActive.value = false }
  playerLane.value = Math.floor(laneCount.value / 2); playerLaneFloat.value = playerLane.value
  laneBuffer.value = null
  if (gameMode.value === 'Time') timeLeftMs.value = 60_000
  if (gameMode.value === 'Stage') { stageIdx.value = 0; stageTarget.value = [500, 1000, 2000][0] }
  // theme cycle every 5 runs unless pinned
  if (!themePin.value) {
    themeRunCount.value = (themeRunCount.value + 1) % 5
    if (themeRunCount.value === 0) {
      const ids = THEMES.map(t => t.id); theme.value = ids[Math.floor(rng() * ids.length)]
    }
  }
  props.onScore(0)
  startHint.value = oneHanded.value
    ? `Swipe in your ${oneHandedSide.value} half · ↩ buffer enabled`
    : 'Swipe / tap halves · arrows · A·D · ↩ buffers next'
}

function spawnObstacle(forceBoss = false) {
  const distM = distance.value
  if (forceBoss || (distM > 0 && Math.floor(distM / 2000) > Math.floor((distM - 12) / 2000))) {
    // Boss truck blockade
    obstacles.push({
      id: 'boss', w: laneCount.value * 0.95, h: 1.8, c: '#0f172a',
      lane: -0.5, z: 130, passed: false, neared: false, spawnAtMs: performance.now(),
      isBoss: true, bossGap: Math.floor(rng() * laneCount.value), vxLane: rng() < 0.5 ? -0.012 : 0.012,
    })
    voice('🛑 Boss truck incoming!')
    return
  }
  const pool = OBSTACLES.filter(o => o.id !== 'boss')
  // Adaptive: avoid moto until mmr > 30, drone+elite gated to higher mmr
  const filtered = pool.filter(o => {
    if (o.id === 'moto' && mmr.value < 25) return false
    if (o.id === 'drone' && mmr.value < 40) return false
    if (o.id === 'elite' && mmr.value < 55) return false
    return true
  })
  const def = filtered[Math.floor(rng() * filtered.length)]
  const lane = Math.floor(rng() * laneCount.value)
  const obs: Obs = {
    id: def.id, w: def.w, h: def.h, c: def.c, lane, z: 130, passed: false, neared: false, spawnAtMs: performance.now(),
  }
  if (def.id === 'drone' || def.id === 'moto') obs.vxLane = (rng() - 0.5) * (def.id === 'drone' ? 0.012 : 0.008)
  if (def.id === 'elite') obs.vxLane = 0 // will follow lane in update
  obstacles.push(obs)
  // Sometimes spawn a coin or pickup nearby
  if (rng() < 0.6) {
    pickups.push({ kind: 'coin', lane: Math.floor(rng() * laneCount.value), z: 130, collected: false, phase: rng() * Math.PI * 2 })
  }
  if (rng() < 0.05) {
    const ks: PickKind[] = ['shield', 'slow', 'magnet', 'double']
    pickups.push({ kind: ks[Math.floor(rng() * ks.length)], lane: Math.floor(rng() * laneCount.value), z: 130, collected: false, phase: rng() * Math.PI * 2 })
  }
}

// ============================
// Input handling
// ============================
function changeLane(delta: number, source: string = 'swipe') {
  if (!alive.value) return
  if (!started.value) started.value = true
  // edge protection
  const target = playerLane.value + delta
  if (target < 0 || target >= laneCount.value) {
    haptic(8); return
  }
  // if mid-animation, buffer
  if (Math.abs(playerLane.value - playerLaneFloat.value) > 0.1 && laneBuffer.value == null) {
    const slots = vehicleSkin.value === 'phantom' ? 2 : 1
    if (laneBuffer.value == null && slots >= 1) laneBuffer.value = delta
    return
  }
  playerLane.value = target
  haptic(15)
  reactionLog.value.push(performance.now() - lastObsSpawnAt)
  if (reactionLog.value.length > 80) reactionLog.value.shift()
  laneHeat.value[target] = (laneHeat.value[target] || 0) + 1
}

let pdownX = 0; let pdownY = 0; let pdownT = 0; let dragging = false
function pdown(e: PointerEvent) {
  const rect = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect()
  const x = e.clientX - rect.left
  // one-handed: only count if in the active half
  if (oneHanded.value) {
    const half = rect.width / 2
    if (oneHandedSide.value === 'right' && x < half) return
    if (oneHandedSide.value === 'left' && x >= half) return
  }
  pdownX = e.clientX; pdownY = e.clientY; pdownT = performance.now(); dragging = true
}
function pmove(e: PointerEvent) {
  if (!dragging) return
  const dx = e.clientX - pdownX
  if (Math.abs(dx) > swipeLen.value && Math.abs(dx) > deadZone.value) {
    changeLane(dx > 0 ? 1 : -1, 'drag')
    dragging = false
  }
}
function pup(e: PointerEvent) {
  if (!dragging) return
  dragging = false
  const dx = e.clientX - pdownX
  const dy = e.clientY - pdownY
  const dt = performance.now() - pdownT
  if (Math.abs(dx) >= swipeLen.value && Math.abs(dx) > Math.abs(dy)) {
    changeLane(dx > 0 ? 1 : -1, 'swipe-end')
  } else if (Math.abs(dx) < deadZone.value && Math.abs(dy) < deadZone.value && dt < 200) {
    // Tap left/right half
    const rect = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect()
    const x = e.clientX - rect.left
    if (x < rect.width * 0.5) changeLane(-1, 'tap')
    else changeLane(1, 'tap')
  }
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key === 'a' || e.key === 'A') { changeLane(-1, 'key'); e.preventDefault() }
  else if (e.key === 'ArrowRight' || e.key === 'd' || e.key === 'D') { changeLane(1, 'key'); e.preventDefault() }
  else if (e.key === ' ' || e.key === 'Enter') { if (!started.value) started.value = true; e.preventDefault() }
}

// ============================
// Tilt steering
// ============================
let lastTiltDir = 0
function tiltHandler(e: DeviceOrientationEvent) {
  if (!useTilt.value || !alive.value) return
  const g = (e.gamma || 0) * tiltSens.value
  const want = g > 8 ? 1 : g < -8 ? -1 : 0
  if (want !== 0 && want !== lastTiltDir) { changeLane(want, 'tilt'); lastTiltDir = want }
  if (want === 0) lastTiltDir = 0
}

// ============================
// Audio / haptics / voice
// ============================
function haptic(ms = 12) {
  if (!haptics.value) return
  try { (navigator as any).vibrate?.(ms) } catch { /* ignore */ }
}
function beep(kind: 'lane' | 'near' | 'pickup' | 'crash' | 'tick' | 'power') {
  try {
    const stMap: Record<string, any> = { lane: 'tap', near: 'win', pickup: 'win', crash: 'lose', tick: 'tap', power: 'win' }
    store.beep(stMap[kind] || 'tap')
  } catch { /* ignore */ }
}
function voice(msg: string) {
  if (!announcer.value) return
  voiceMsg.value = msg
  if (voiceTimer) clearTimeout(voiceTimer)
  voiceTimer = setTimeout(() => { voiceMsg.value = '' }, 1400)
}

// ============================
// Game loop
// ============================
let prevTs = 0
let rafId = 0
function loop(ts: number) {
  if (!cv.value) { rafId = requestAnimationFrame(loop); return }
  const targetFps = batterySaver.value ? 30 : 60
  const dt = Math.min(0.05, (ts - prevTs) / 1000) || 1 / targetFps
  prevTs = ts
  step(dt)
  draw()
  if (!batterySaver.value) {
    rafId = requestAnimationFrame(loop)
  } else {
    setTimeout(() => { rafId = requestAnimationFrame(loop) }, 1000 / 30 - 4)
  }
}

function step(dt: number) {
  if (props.paused || settingsOpen.value || calibrationOpen.value || showAnalytics.value || !alive.value) return
  if (!started.value) return
  frameNo++
  const dilatedDt = dt / speedDilation
  if (dilationT > 0) { dilationT -= dt * 1000; if (dilationT <= 0) speedDilation = 1 }
  if (nearMissGlowMs.value > 0) nearMissGlowMs.value = Math.max(0, nearMissGlowMs.value - dt * 1000)
  // ramp speed
  speed.value = Math.min(maxSpeed.value, speed.value + 6 * dilatedDt)
  // adaptive: if mmr low, slower ramp
  if (mmr.value < 30) speed.value = Math.min(maxSpeed.value, speed.value + 2 * dilatedDt)

  // distance
  distance.value += speed.value * dilatedDt * 0.06
  // animate lane
  const lerp = Math.min(1, dt * (vehicleSkin.value === 'glacier' ? 9 : 14))
  playerLaneFloat.value += (playerLane.value - playerLaneFloat.value) * lerp
  // execute buffer
  if (Math.abs(playerLane.value - playerLaneFloat.value) < 0.05 && laneBuffer.value != null) {
    const d = laneBuffer.value; laneBuffer.value = null; changeLane(d, 'buffer')
  }

  // time mode
  if (gameMode.value === 'Time') {
    timeLeftMs.value -= dt * 1000
    if (timeLeftMs.value <= 0) { crashLog.value = `time up at ${Math.floor(distance.value)}m`; die('timeup'); return }
  }
  // stage check
  if (gameMode.value === 'Stage' && distance.value >= stageTarget.value) {
    voice(`✓ Stage ${stageTarget.value}m cleared`)
    stageIdx.value++
    const stages = [500, 1000, 2000]
    stageTarget.value = stages[stageIdx.value % stages.length] + Math.floor(stageIdx.value / 3) * 500
  }

  // spawn cadence
  const spawnGap = Math.max(0.18, 0.95 - speed.value / maxSpeed.value * 0.5)
  if (performance.now() - lastObsSpawnAt > spawnGap * 1000) {
    spawnObstacle(); lastObsSpawnAt = performance.now()
  }

  // assist auto-slow
  if (assistAutoSlow.value) {
    const close = obstacles.find(o => o.z > 8 && o.z < 28 && Math.round(o.lane) === playerLane.value)
    if (close) speed.value = Math.max(baseSpeed.value, speed.value - 80 * dt)
  }

  // update obstacles
  for (const o of obstacles) {
    o.z -= speed.value * dilatedDt * 0.06
    if (o.id === 'elite' && o.z < 80) {
      const t = (o.lane - playerLane.value)
      o.lane += (-t) * 0.018
    }
    if (o.vxLane != null) {
      o.lane += o.vxLane!
      if (o.lane < 0) { o.lane = 0; o.vxLane = -o.vxLane! }
      if (o.lane > laneCount.value - 1) { o.lane = laneCount.value - 1; o.vxLane = -o.vxLane! }
    }
    if (o.isBoss && o.bossGap != null) {
      o.bossGap! += o.vxLane!
      if (o.bossGap! < 0) { o.bossGap = 0; o.vxLane = -o.vxLane! }
      if (o.bossGap! > laneCount.value - 1) { o.bossGap = laneCount.value - 1; o.vxLane = -o.vxLane! }
    }
    // near miss check
    if (!o.neared && o.z > -0.6 && o.z < 1.2) {
      const dist = Math.abs(playerLaneFloat.value - (o.isBoss ? -1 : o.lane))
      if (dist > (o.isBoss ? 0 : 0.4) && dist < 1.2) {
        o.neared = true
        if (!o.isBoss) {
          stats.nearMissRun++; combo.value = Math.min(20, combo.value + 1)
          if (combo.value > stats.maxComboRun) stats.maxComboRun = combo.value
          haptic(20); voice('💨 Near miss')
          beep('near')
          dilationT = 80; speedDilation = 0.95
          nearMissGlowMs.value = 240
          spawnPickupSpark(laneCenterX(o.lane), projectY(o.z), '#fde68a', 12)
        }
      }
    }
    // collision
    if (o.z > -0.4 && o.z < 0.6 && !o.passed) {
      let hit = false
      if (o.isBoss) {
        if (Math.round(playerLane.value) !== o.bossGap) hit = true
      } else {
        if (Math.abs(playerLaneFloat.value - o.lane) < 0.55) hit = true
      }
      if (hit) {
        if (assistInfShield.value) { o.passed = true; continue }
        const sh = powers.find(p => p.kind === 'shield')
        if (sh) {
          powers.splice(powers.indexOf(sh), 1)
          o.passed = true
          spawnPickupSpark(laneCenterX(o.lane), projectY(o.z), '#22d3ee', 30)
          haptic(35); voice('🛡 Shield broke')
          beep('crash')
          continue
        }
        crashLog.value = `frame ${frameNo} · obs ${o.id} · lane ${o.lane.toFixed(1)} · player ${playerLane.value} · z ${o.z.toFixed(2)}`
        deathLog[o.id] = (deathLog[o.id] || 0) + 1
        die(o.id); return
      }
    }
    // overtaken bonus
    if (!o.passed && o.z < -0.6) {
      o.passed = true
      if (!o.isBoss) {
        const pts = 5 * (powers.find(p => p.kind === 'double') ? 2 : 1)
        runCoins.value += 0
        props.onScore(s => (typeof s === 'number' ? s : 0) + pts * combo.value)
      }
    }
  }
  // remove far behind
  for (let i = obstacles.length - 1; i >= 0; i--) if (obstacles[i].z < -3) obstacles.splice(i, 1)

  // pickups
  for (const p of pickups) {
    p.z -= speed.value * dilatedDt * 0.06
    p.phase += dt * 6
    if (p.collected) continue
    // magnet
    const magnet = powers.find(pw => pw.kind === 'magnet')
    let collectDist = magnet ? (1.2 + 0.4 * upMagnet.value) : 0.4
    const dx = Math.abs(playerLaneFloat.value - p.lane)
    if (p.z > -0.4 && p.z < 0.7 && dx < collectDist) {
      p.collected = true
      collectPickup(p)
    }
  }
  for (let i = pickups.length - 1; i >= 0; i--) if (pickups[i].z < -3) pickups.splice(i, 1)

  // powers tick
  for (const pw of powers) pw.left -= dt * 60
  for (let i = powers.length - 1; i >= 0; i--) if (powers[i].left <= 0) powers.splice(i, 1)

  // particles
  for (const pa of particles) { pa.x += pa.vx; pa.y += pa.vy; pa.vy += 0.05; pa.life-- }
  for (let i = particles.length - 1; i >= 0; i--) if (particles[i].life <= 0) particles.splice(i, 1)

  if (shakeT > 0) shakeT -= dt * 1000

  // record / ghost
  if (frameNo % 2 === 0) recordedFrames.push({ d: distance.value, lane: playerLane.value, px: playerLaneFloat.value })
  // base score per frame
  if (frameNo % 4 === 0) props.onScore(s => (typeof s === 'number' ? s : 0) + 1 * (powers.find(p => p.kind === 'double') ? 2 : 1))
}

function collectPickup(p: Pick) {
  if (p.kind === 'coin') {
    runCoins.value++
    spawnPickupSpark(laneCenterX(p.lane), projectY(p.z), '#facc15', 8)
    props.onScore(s => (typeof s === 'number' ? s : 0) + 2)
    haptic(10); beep('pickup')
    return
  }
  haptic(20); beep('power')
  const map: Record<string, [string, string, number]> = {
    shield: ['🛡', '#22d3ee', 60 * (8 + 4 * upShield.value)],
    slow: ['⏳', '#38bdf8', 60 * (2 + 2 * upSlow.value)],
    magnet: ['🧲', '#f472b6', 60 * (5 + 3 * upMagnet.value)],
    double: ['✨', '#facc15', 60 * (5 + 3 * upDouble.value)],
  }
  const [icon, color, left] = map[p.kind]
  if (p.kind === 'slow') { dilationT = (2000 + 2000 * upSlow.value); speedDilation = 0.55 }
  powers.push({ id: nextPwrId++, kind: p.kind as any, left, icon, color })
  voice(`${icon} ${p.kind} active`)
  spawnPickupSpark(laneCenterX(p.lane), projectY(p.z), color, 18)
}

function spawnPickupSpark(x: number, y: number, col: string, n: number) {
  if (reducedMotion.value) return
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2
    particles.push({ x, y, vx: Math.cos(a) * 1.6, vy: Math.sin(a) * 1.6 - 0.8, life: 30, max: 30, col, size: 2.2 })
  }
}

function die(reason: string) {
  if (!alive.value) return
  alive.value = false
  haptic(60); beep('crash')
  shakeT = 280
  speedDilation = 1.8; dilationT = 500
  if (!reducedMotion.value) {
    for (let i = 0; i < 40; i++) {
      const a = Math.random() * Math.PI * 2
      const v = 1 + Math.random() * 4
      particles.push({ x: laneCenterX(playerLaneFloat.value, 0), y: carBaseY, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 1, life: 50 + Math.floor(Math.random() * 30), max: 80, col: ['#ef4444', '#fbbf24', '#94a3b8'][i % 3], size: 2 })
    }
  }
  voice(reason === 'timeup' ? '⏱ Time up' : '💥 Crash!')
  // adapt MMR
  if (distance.value < 500) mmr.value = Math.max(0, mmr.value - 4)
  else if (distance.value > 2500) mmr.value = Math.min(100, mmr.value + 5)
  else mmr.value = Math.min(100, mmr.value + 1)
  // unlocks
  const newly: string[] = []
  function unlock(id: string) { if (!unlockedSkins.value.includes(id)) { unlockedSkins.value.push(id); newly.push(SKINS.find(s => s.id === id)?.name || id) } }
  if (distance.value > 300) unlock('cyber')
  if (stats.maxComboRun >= 5) unlock('phantom')
  if (stats.nearMissRun >= 10) unlock('inferno')
  if (distance.value > 1000) unlock('glacier')
  if (runCoins.value >= 25) unlock('volt')
  if (distance.value > 2000) unlock('meteor')
  if (combo.value >= 10) unlock('azure')
  if (runCoins.value >= 50) unlock('jade')
  if (stats.totalRuns >= 5) unlock('sakura')
  if (stats.totalRuns >= 15) unlock('midnight')
  if (distance.value > 3000) unlock('royal')
  if (stats.maxComboRun >= 15) unlock('sunset')
  if (stats.totalRuns >= 25) unlock('forest')
  if (distance.value > 4000) unlock('arctic')
  if (stats.totalNearMiss + stats.nearMissRun >= 100) unlock('lava')
  if (stats.totalRuns >= 40) unlock('mint')
  if (stats.totalRuns >= 60) unlock('rose')
  if (distance.value > 5000) unlock('mono')
  if (distance.value > 6000) unlock('gold')
  newUnlocks.value = newly
  stats.totalRuns++
  stats.totalNearMiss += stats.nearMissRun
  if (combo.value > stats.maxComboAll) stats.maxComboAll = combo.value
  if (distance.value > stats.longest) {
    stats.longest = Math.floor(distance.value)
    ghostFrames.value = recordedFrames.slice(-3000)
  }
  store.addCoins(runCoins.value + Math.floor(distance.value / 100))
  persist()
  setTimeout(() => { showAnalytics.value = true }, 600)
}

function onReplay() { showAnalytics.value = false; reset(); started.value = true }
function onContinue() {
  showAnalytics.value = false
  props.onGameOver(Math.floor(distance.value))
}
function exportReplay() {
  try {
    const data = btoa(JSON.stringify(recordedFrames.slice(0, 1000)))
    const url = location.origin + location.pathname + '#laneReplay=' + data.slice(0, 200)
    navigator.clipboard.writeText(url); voice('🔗 Replay link copied')
  } catch { voice('Could not copy') }
}

// ============================
// Drawing
// ============================
function draw() {
  const c = cv.value!.getContext('2d')!
  const dpr = window.devicePixelRatio || 1
  if (cv.value!.width !== W * dpr) { cv.value!.width = W * dpr; cv.value!.height = H * dpr }
  cv.value!.style.width = '100%'; cv.value!.style.height = '100%'
  c.setTransform(dpr, 0, 0, dpr, 0, 0)
  c.clearRect(0, 0, W, H)
  const t = THEMES.find(x => x.id === theme.value)!
  // Sky gradient
  const sky = c.createLinearGradient(0, 0, 0, horizonY)
  sky.addColorStop(0, t.sky); sky.addColorStop(1, mix(t.sky, t.road, 0.5))
  c.fillStyle = sky; c.fillRect(0, 0, W, horizonY)
  // sun / moon based on theme
  if (theme.value === 'desert') { c.fillStyle = '#fef9c3'; c.beginPath(); c.arc(W * 0.7, horizonY * 0.5, 30, 0, Math.PI * 2); c.fill() }
  if (theme.value === 'night' || theme.value === 'neon') {
    for (let i = 0; i < 30; i++) { c.fillStyle = '#e2e8f0'; const x = (i * 73 + frameNo * 0.3) % W; c.fillRect(x, (i * 41 % horizonY), 2, 2) }
  }
  // Road quad (perspective)
  const sx = (shakeT > 0 && !reducedMotion.value ? (Math.random() - 0.5) * 6 : 0)
  const sy = (shakeT > 0 && !reducedMotion.value ? (Math.random() - 0.5) * 6 : 0)
  c.save(); c.translate(sx, sy)
  c.fillStyle = t.road
  c.beginPath()
  c.moveTo((W - roadTopWidth) / 2, horizonY)
  c.lineTo((W + roadTopWidth) / 2, horizonY)
  c.lineTo((W + roadBottomWidth) / 2, H)
  c.lineTo((W - roadBottomWidth) / 2, H)
  c.closePath(); c.fill()
  // weather: rain dots
  if (theme.value === 'rain' && !reducedMotion.value) {
    c.strokeStyle = 'rgba(186,230,253,0.6)'; c.lineWidth = 1
    for (let i = 0; i < 60; i++) { const x = (i * 37 + frameNo * 6) % W; const y = (i * 53 + frameNo * 12) % H; c.beginPath(); c.moveTo(x, y); c.lineTo(x - 2, y + 8); c.stroke() }
  }
  // lane stripes (perspective)
  c.strokeStyle = t.stripe; c.lineWidth = 2; c.setLineDash([6, 14])
  for (let i = 1; i < laneCount.value; i++) {
    c.beginPath()
    const xt = ((i) / laneCount.value) * roadTopWidth + (W - roadTopWidth) / 2
    const xb = ((i) / laneCount.value) * roadBottomWidth + (W - roadBottomWidth) / 2
    c.moveTo(xt, horizonY); c.lineTo(xb, H); c.stroke()
  }
  c.setLineDash([])
  // lane glow assist
  if (assistLaneGlow.value) {
    const xb1 = (playerLane.value / laneCount.value) * roadBottomWidth + (W - roadBottomWidth) / 2
    const xb2 = ((playerLane.value + 1) / laneCount.value) * roadBottomWidth + (W - roadBottomWidth) / 2
    const xt1 = (playerLane.value / laneCount.value) * roadTopWidth + (W - roadTopWidth) / 2
    const xt2 = ((playerLane.value + 1) / laneCount.value) * roadTopWidth + (W - roadTopWidth) / 2
    c.fillStyle = 'rgba(34,211,238,0.10)'
    c.beginPath(); c.moveTo(xt1, horizonY); c.lineTo(xt2, horizonY); c.lineTo(xb2, H); c.lineTo(xb1, H); c.closePath(); c.fill()
  }
  // speed lines
  if (speed.value > maxSpeed.value * 0.8 && !reducedMotion.value) {
    c.strokeStyle = 'rgba(255,255,255,0.4)'; c.lineWidth = 2
    for (let i = 0; i < 8; i++) {
      const x = (i * 47 + frameNo * 14) % W
      c.beginPath(); c.moveTo(x, horizonY + 10); c.lineTo(x, H - 10); c.stroke()
    }
  }
  // pickups (back-to-front)
  const sortedPick = [...pickups].filter(p => !p.collected && p.z > -0.5).sort((a, b) => b.z - a.z)
  for (const p of sortedPick) {
    const x = laneCenterX(p.lane, p.z); const y = projectY(p.z); const sc = projectScale(p.z)
    c.save(); c.translate(x, y); c.scale(sc, sc); c.translate(0, Math.sin(p.phase) * 4)
    if (p.kind === 'coin') {
      c.fillStyle = '#facc15'; c.beginPath(); c.arc(0, 0, 8, 0, Math.PI * 2); c.fill()
      c.strokeStyle = '#92400e'; c.lineWidth = 2; c.stroke()
    } else {
      const map = { shield: '🛡', slow: '⏳', magnet: '🧲', double: '✨' } as const
      c.fillStyle = '#fff'; c.font = 'bold 16px sans-serif'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(map[p.kind], 0, 0)
      c.strokeStyle = 'rgba(255,255,255,0.3)'; c.lineWidth = 2; c.beginPath(); c.arc(0, 0, 14, 0, Math.PI * 2); c.stroke()
    }
    c.restore()
  }
  // obstacles back-to-front
  const sortedObs = [...obstacles].filter(o => o.z > -0.5).sort((a, b) => b.z - a.z)
  for (const o of sortedObs) {
    if (o.isBoss) { drawBoss(c, o); continue }
    const x = laneCenterX(o.lane, o.z); const y = projectY(o.z); const sc = projectScale(o.z)
    drawObstacle(c, x, y, sc, o)
  }
  // ghost car
  if (watchGhost.value && ghostFrames.value.length) {
    const g = ghostFrames.value[ghostIdx]
    if (g) {
      const gx = laneCenterX(g.px, 0)
      drawCar(c, gx, carBaseY, 1, 0.35, '#a78bfa', '#c4b5fd')
    }
    if (frameNo % 2 === 0) ghostIdx = Math.min(ghostFrames.value.length - 1, ghostIdx + 1)
  }
  // player
  const px = laneCenterX(playerLaneFloat.value, 0)
  const skin = SKINS.find(s => s.id === vehicleSkin.value) || SKINS[0]
  drawCar(c, px, carBaseY, 1, 1, skin.body, skin.glow)
  // shield aura
  if (powers.find(p => p.kind === 'shield')) {
    c.strokeStyle = 'rgba(34,211,238,0.6)'; c.lineWidth = 3
    c.beginPath(); c.arc(px, carBaseY - 10, 26, 0, Math.PI * 2); c.stroke()
  }
  // particles
  for (const pa of particles) {
    c.fillStyle = pa.col + Math.round((pa.life / pa.max) * 255).toString(16).padStart(2, '0')
    c.fillRect(pa.x - pa.size / 2, pa.y - pa.size / 2, pa.size, pa.size)
  }
  c.restore()
  // mode hud bottom
  if (gameMode.value === 'Stage') {
    c.fillStyle = 'rgba(255,255,255,0.7)'; c.font = '11px sans-serif'; c.textAlign = 'center'
    c.fillText(`${Math.floor(distance.value)} / ${stageTarget.value}m`, W / 2, H - 8)
  }
  // night-overlay tint
  if (theme.value === 'night' || theme.value === 'neon') {
    c.fillStyle = 'rgba(2,6,23,0.25)'; c.fillRect(0, 0, W, H)
  }
  if (theme.value === 'rain') {
    c.fillStyle = 'rgba(15,23,42,0.18)'; c.fillRect(0, 0, W, H)
  }
}

function drawCar(c: CanvasRenderingContext2D, x: number, y: number, sc: number, alpha: number, body: string, glow: string) {
  c.save(); c.translate(x, y); c.scale(sc, sc); c.globalAlpha = alpha
  // glow underneath
  const grd = c.createRadialGradient(0, 6, 4, 0, 6, 32)
  grd.addColorStop(0, glow); grd.addColorStop(1, 'rgba(0,0,0,0)')
  c.fillStyle = grd; c.fillRect(-32, -10, 64, 30)
  // body
  c.fillStyle = body
  c.beginPath(); roundedRect(c, -16, -22, 32, 44, 6); c.fill()
  if (highContrast.value) { c.strokeStyle = '#fff'; c.lineWidth = 2; c.stroke() }
  // windshield
  c.fillStyle = '#0f172a'; c.fillRect(-12, -16, 24, 10)
  // wheels
  c.fillStyle = '#111'; c.fillRect(-18, -12, 4, 10); c.fillRect(14, -12, 4, 10); c.fillRect(-18, 6, 4, 10); c.fillRect(14, 6, 4, 10)
  c.restore()
}

function drawObstacle(c: CanvasRenderingContext2D, x: number, y: number, sc: number, o: Obs) {
  c.save(); c.translate(x, y); c.scale(sc, sc)
  const w = o.w * 32; const h = o.h * 22
  if (o.id === 'drone') {
    c.fillStyle = o.c; c.beginPath(); c.arc(0, 0, w * 0.5, 0, Math.PI * 2); c.fill()
    c.fillStyle = '#0ea5e9'; c.fillRect(-w * 0.6, -1, w * 1.2, 2)
  } else if (o.id === 'barrel') {
    c.fillStyle = o.c; c.beginPath(); c.arc(0, 0, w * 0.5, 0, Math.PI * 2); c.fill()
    c.strokeStyle = '#7c2d12'; c.lineWidth = 2; c.beginPath(); c.arc(0, 0, w * 0.5, 0, Math.PI * 2); c.stroke()
  } else if (o.id === 'block') {
    c.fillStyle = o.c; c.fillRect(-w / 2, -h / 2, w, h)
    c.fillStyle = '#a16207'
    for (let i = 0; i < 4; i++) c.fillRect(-w / 2 + i * (w / 4), -h / 2, 2, h)
  } else if (o.id === 'moto') {
    c.fillStyle = o.c; c.fillRect(-w / 2, -h / 2, w, h * 0.6)
    c.fillStyle = '#000'; c.beginPath(); c.arc(0, h / 2 - 4, 6, 0, Math.PI * 2); c.fill()
  } else if (o.id === 'police') {
    c.fillStyle = o.c; c.beginPath(); roundedRect(c, -w / 2, -h / 2, w, h, 6); c.fill()
    c.fillStyle = '#fff'; c.fillRect(-w / 2, 0, w, 4)
    // siren flash
    c.fillStyle = (frameNo % 20 < 10) ? '#ef4444' : '#3b82f6'
    c.fillRect(-6, -h / 2 - 4, 12, 4)
  } else if (o.id === 'truck') {
    c.fillStyle = o.c; c.fillRect(-w / 2, -h / 2, w, h)
    c.fillStyle = '#1e293b'; c.fillRect(-w / 2 + 4, -h / 2 + 4, w - 8, 8)
  } else if (o.id === 'elite') {
    c.shadowBlur = 12; c.shadowColor = '#ef4444'
    c.fillStyle = o.c; c.beginPath(); roundedRect(c, -w / 2, -h / 2, w, h, 6); c.fill()
    c.shadowBlur = 0; c.fillStyle = '#fff'; c.fillRect(-2, -h / 2, 4, h)
  } else {
    // sedan
    c.fillStyle = o.c; c.beginPath(); roundedRect(c, -w / 2, -h / 2, w, h, 6); c.fill()
    c.fillStyle = '#0f172a'; c.fillRect(-w / 2 + 4, -h / 2 + 4, w - 8, h * 0.4)
  }
  if (highContrast.value) { c.strokeStyle = '#fff'; c.lineWidth = 2; c.stroke() }
  c.restore()
}

function drawBoss(c: CanvasRenderingContext2D, o: Obs) {
  // Boss spans the road; gap is at lane bossGap
  const y = projectY(o.z); const sc = projectScale(o.z)
  const yTop = y - 28 * sc; const yBot = y + 28 * sc
  c.save()
  for (let i = 0; i < laneCount.value; i++) {
    if (i === Math.round(o.bossGap || 0)) continue
    const left = (i / laneCount.value) * (roadBottomWidth * (1 - o.z / 120) + roadTopWidth * (o.z / 120))
    const cx = laneCenterX(i, o.z)
    c.fillStyle = '#0f172a'
    c.fillRect(cx - 22 * sc, yTop, 44 * sc, yBot - yTop)
    c.fillStyle = '#dc2626'; c.fillRect(cx - 22 * sc, yTop, 44 * sc, 4 * sc)
    c.fillStyle = '#facc15'; c.fillRect(cx - 22 * sc, yBot - 4 * sc, 44 * sc, 4 * sc)
  }
  // siren
  c.fillStyle = (frameNo % 16 < 8) ? '#ef4444' : '#fff'; c.fillRect(W / 2 - 6, yTop - 6 * sc, 12, 4)
  c.restore()
}

function roundedRect(c: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  c.beginPath()
  c.moveTo(x + r, y); c.lineTo(x + w - r, y); c.quadraticCurveTo(x + w, y, x + w, y + r)
  c.lineTo(x + w, y + h - r); c.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
  c.lineTo(x + r, y + h); c.quadraticCurveTo(x, y + h, x, y + h - r)
  c.lineTo(x, y + r); c.quadraticCurveTo(x, y, x + r, y)
  c.closePath()
}

function mix(a: string, b: string, t: number): string {
  const ax = parseInt(a.slice(1), 16), bx = parseInt(b.slice(1), 16)
  const ar = (ax >> 16) & 255, ag = (ax >> 8) & 255, ab = ax & 255
  const br = (bx >> 16) & 255, bg = (bx >> 8) & 255, bb = bx & 255
  const r = Math.round(ar + (br - ar) * t); const g = Math.round(ag + (bg - ag) * t); const bl = Math.round(ab + (bb - ab) * t)
  return '#' + ((1 << 24) + (r << 16) + (g << 8) + bl).toString(16).slice(1)
}

// ============================
// Calibration
// ============================
const calRound = ref(1)
const calLengths = ref<number[]>([])
const calMessage = ref('')
const calAvg = computed(() => calLengths.value.length ? Math.round(calLengths.value.reduce((a, b) => a + b, 0) / calLengths.value.length) : 0)
let calStartX = 0; let calStartY = 0
function openCalibration() { calRound.value = 1; calLengths.value = []; calibrationOpen.value = true }
function calStart(e: PointerEvent) { calStartX = e.clientX; calStartY = e.clientY }
function calMove(_e: PointerEvent) { /* show hint */ }
function calEnd(e: PointerEvent) {
  if (calRound.value > 5) return
  const d = Math.hypot(e.clientX - calStartX, e.clientY - calStartY)
  if (d < 12) return
  calLengths.value.push(d); calMessage.value = `Round ${calRound.value}: ${Math.round(d)}px`
  calRound.value++
}
function applyCalibration() { if (calLengths.value.length) swipeLen.value = Math.max(10, Math.min(120, Math.round(calAvg.value * 0.7))); persist(); calibrationOpen.value = false }

// ============================
// Buy upgrade
// ============================
function buyUpgrade(kind: 'shield' | 'magnet' | 'slow' | 'double') {
  const refs = { shield: upShield, magnet: upMagnet, slow: upSlow, double: upDouble }
  const r = refs[kind]
  const cost = 30 * (r.value + 1)
  if (store.coins < cost) return
  if (r.value >= 3) return
  store.coins -= cost; r.value++
  voice('✓ Upgrade purchased'); persist()
}

// ============================
// Mission label / stage label
// ============================
const missionLabel = computed(() => {
  if (gameMode.value !== 'Mission') return ''
  const seed = todaySeed() % 4
  const objs = ['Travel 1500m', 'Collect 20 coins', 'Hit x10 combo', '15 near misses']
  return objs[seed]
})
const stageLabel = computed(() => `Stage ${stageIdx.value + 1} → ${stageTarget.value}m`)
const mmrTip = computed(() => mmr.value < 25 ? 'Easier ramp / longer warning' : mmr.value < 60 ? 'Tuned to your skill' : mmr.value < 85 ? 'Tougher: more elites & moving cars' : 'Pro: drones + boss faster')

// ============================
// Analytics derived
// ============================
const reactionGraphW = 240
const avgReaction = computed(() => reactionLog.value.length ? Math.round(reactionLog.value.reduce((a, b) => a + b, 0) / reactionLog.value.length) : 0)
const reactPoints = computed(() => {
  if (!reactionLog.value.length) return ''
  const max = Math.max(...reactionLog.value, 1500)
  return reactionLog.value.slice(-40).map((r, i) => {
    const x = (i / 40) * reactionGraphW
    const y = 80 - (r / max) * 70
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
const deadliest = computed(() => {
  let best = ''; let n = 0
  for (const k in deathLog) { if (deathLog[k] > n) { n = deathLog[k]; best = k } }
  return best || '—'
})
const runTip = computed(() => {
  if (avgReaction.value > 900) return 'Tip: tap a hair earlier — your reactions are running ~1s.'
  if (deadliest.value === 'truck') return 'Tip: line up your lane two seconds out from trucks.'
  if (deadliest.value === 'elite') return 'Tip: elites follow your lane — break right just before contact.'
  if (deadliest.value === 'boss') return 'Tip: start drifting toward the boss gap as soon as the siren fires.'
  if (combo.value >= 10) return 'Combo king. Keep weaving for x2 score on near misses.'
  return 'Solid run. Try a different theme — they each handle a touch differently.'
})
function heatScale(v: number) {
  const max = Math.max(1, ...laneHeat.value); return v / max
}

// ============================
// Mount
// ============================
onMounted(() => {
  loadSettings()
  reset()
  prevTs = performance.now(); rafId = requestAnimationFrame(loop)
  if (useTilt.value) try { window.addEventListener('deviceorientation', tiltHandler) } catch { /* ignore */ }
  root.value?.focus()
})
onUnmounted(() => { cancelAnimationFrame(rafId); window.removeEventListener('deviceorientation', tiltHandler) })

watch(() => props.running, (r) => { if (r && !alive.value) reset() })
watch(useTilt, (v) => {
  try {
    if (v) window.addEventListener('deviceorientation', tiltHandler)
    else window.removeEventListener('deviceorientation', tiltHandler)
  } catch { /* ignore */ }
})
</script>

<style scoped>
.wrap { position: relative; width: 100%; height: 100%; background: #020617; outline: none; overflow: hidden; touch-action: none; }
.cv { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.topbar { position: absolute; top: 8px; left: 8px; right: 8px; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.hud-pill { background: rgba(0, 0, 0, 0.55); color: #fff; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; backdrop-filter: blur(4px); }
.pwr { margin-right: 6px; }
.settings { margin-left: auto; background: rgba(0, 0, 0, 0.55); border: none; color: #fff; width: 32px; height: 32px; border-radius: 50%; font-size: 16px; cursor: pointer; }
.hint { position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%); color: rgba(255, 255, 255, 0.85); background: rgba(0, 0, 0, 0.55); padding: 6px 14px; border-radius: 12px; font-size: 12px; pointer-events: none; }
.edgeglow { position: absolute; inset: 0; pointer-events: none; box-shadow: inset 0 0 0 0 rgba(252, 211, 77, 0); transition: box-shadow 0.18s; }
.edgeglow.on { box-shadow: inset 0 0 80px 20px rgba(252, 211, 77, 0.45); }
.voice { position: absolute; bottom: 64px; left: 50%; transform: translateX(-50%); background: rgba(15, 23, 42, 0.85); color: #fff; padding: 8px 14px; border-radius: 18px; font-weight: 600; font-size: 13px; letter-spacing: 0.5px; }
.voice-enter-from, .voice-leave-to { opacity: 0; transform: translate(-50%, 6px); }
.overlay { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.7); display: flex; align-items: center; justify-content: center; z-index: 5; padding: 16px; }
.card { background: #0f172a; color: #fff; border-radius: 18px; padding: 14px; max-width: 520px; width: 100%; max-height: 90vh; overflow: auto; }
.card.analytics { max-width: 600px; }
.card-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 15px; }
.x { background: transparent; border: none; color: #fff; font-size: 22px; cursor: pointer; }
.grp { margin: 12px 0; }
.grp.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.grp.two > div { min-width: 0; }
label { font-size: 12px; color: rgba(255, 255, 255, 0.75); display: block; margin-bottom: 4px; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chip { background: rgba(255, 255, 255, 0.06); color: #fff; border: none; padding: 5px 10px; border-radius: 14px; font-size: 11px; cursor: pointer; }
.chip.on { background: linear-gradient(90deg, #14b8a6, #6366f1); font-weight: 700; }
.chip.locked { opacity: 0.4; }
.chip.wide { display: block; width: 100%; margin: 4px 0; padding: 8px; }
.chip.primary { background: linear-gradient(90deg, #f59e0b, #ef4444); font-weight: 700; }
.bar { background: rgba(255, 255, 255, 0.08); border-radius: 4px; height: 8px; overflow: hidden; margin: 4px 0; }
.fill { height: 100%; background: linear-gradient(90deg, #34d399, #f59e0b, #ef4444); }
.muted { color: rgba(255, 255, 255, 0.55); font-size: 11px; }
.row { display: flex; gap: 8px; margin: 10px 0; flex-wrap: wrap; }
.row > .grow { flex: 1; min-width: 200px; }
.tips { background: rgba(56, 189, 248, 0.15); border-radius: 8px; padding: 8px; font-size: 12px; }
.unlocks { background: rgba(20, 184, 166, 0.2); border-radius: 8px; padding: 8px; color: #a7f3d0; font-weight: 700; }
.stat { background: rgba(255, 255, 255, 0.06); border-radius: 8px; padding: 6px 10px; flex: 1; min-width: 80px; text-align: center; }
.stat label { font-size: 10px; color: rgba(255, 255, 255, 0.6); }
.stat b { font-size: 14px; }
.heat { display: flex; gap: 4px; align-items: end; height: 70px; padding: 4px; background: rgba(255, 255, 255, 0.04); border-radius: 8px; }
.heatcol { flex: 1; background: linear-gradient(0deg, #38bdf8, #f472b6); border-radius: 4px 4px 0 0; min-height: 4px; position: relative; transition: height 0.4s; }
.heatcol span { position: absolute; bottom: -16px; left: 0; right: 0; text-align: center; font-size: 10px; color: rgba(255, 255, 255, 0.6); }
.reactsvg { width: 100%; height: 80px; background: rgba(255, 255, 255, 0.04); border-radius: 8px; }
.crash { margin-top: 10px; }
.crash summary { cursor: pointer; color: rgba(255, 255, 255, 0.7); font-size: 11px; }
.crash pre { background: #020617; padding: 8px; border-radius: 6px; font-size: 11px; overflow: auto; }
.cal-pad { background: #1e293b; border-radius: 12px; height: 180px; display: flex; align-items: center; justify-content: center; color: #fff; touch-action: none; user-select: none; cursor: crosshair; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.18s; }
</style>
