<template>
  <div class="sh-root" :class="[`cb-${cfg.colorblind}`, { hc: cfg.highContrast, big: cfg.bigHud }]" :style="rootStyle">
    <div class="hud top">
      <div class="pill"><span>SCORE</span><b>{{ score }}</b></div>
      <div class="pill"><span>WAVE</span><b>{{ act }}-{{ wave }}</b></div>
      <div class="pill"><span>×</span><b>{{ comboMul.toFixed(1) }}</b></div>
      <div class="pill grow"><span>HP</span><div class="hp">
        <span v-for="i in 8" :key="i" class="dot" :class="{ on: i <= hp }"></span>
      </div></div>
      <div class="pill"><span>ACC</span><b>{{ accuracy }}%</b></div>
      <div class="pill" v-if="cfg.daily"><span>DAILY</span><b>{{ dailyCode }}</b></div>
    </div>

    <canvas ref="cv" class="cv" @pointerdown="onDown" @pointermove="onMove"
            @pointerup="onUp" @pointercancel="onUp" @pointerleave="onUp"
            @touchstart.prevent @touchmove.prevent @dblclick="overdrive"></canvas>

    <div class="hud bot" :class="{ oh: cfg.oneHanded }">
      <button class="chip" @click="showSettings = true">⚙ Settings</button>
      <button class="chip" @click="showShop = true">⚡ Shop</button>
      <button class="chip" @click="showHull = true">🚀 Hull</button>
      <button class="chip" @click="showPerks = true">✨ Perks</button>
      <button class="chip" @click="showCalibrate = true">🎯 Calibrate</button>
      <button class="chip" @click="overdrive" :disabled="overdriveCharge < 100">💥 Overdrive ({{ Math.floor(overdriveCharge) }}%)</button>
      <button class="chip" v-if="!started" @click="startRun">▶ Start</button>
      <button class="chip" v-else @click="restartRun">↻ Restart</button>
    </div>

    <div class="powers" v-if="started">
      <span v-if="shieldT > 0" class="pwr sh">SHIELD {{ Math.ceil(shieldT) }}s</span>
      <span v-if="slowT > 0" class="pwr sl">SLOW {{ Math.ceil(slowT) }}s</span>
      <span v-if="magnetT > 0" class="pwr mg">MAGNET {{ Math.ceil(magnetT) }}s</span>
      <span v-if="doubleT > 0" class="pwr db">×2 DMG {{ Math.ceil(doubleT) }}s</span>
      <span v-if="ifr > 0" class="pwr ifr">IFRAME</span>
    </div>

    <div v-if="!started && !showAnalytics" class="splash" @click="startRun">
      <div class="title">SPACE HUNTER</div>
      <div class="sub">NEBULA STRIKE</div>
      <div class="hint">Drag to fly · double-tap overdrive · long press railgun</div>
      <div class="hint small">Mode: {{ mode }} · Hull: {{ hullDef.label }} · Streak: {{ stats.loginStreak }}d</div>
      <div class="hint go">Tap to launch</div>
    </div>

    <!-- Settings -->
    <div v-if="showSettings" class="sheet" @click.self="showSettings = false">
      <div class="card">
        <h3>Settings</h3>
        <div class="grp">
          <label><input type="checkbox" v-model="cfg.sound" @change="save()"> Sound</label>
          <label><input type="checkbox" v-model="cfg.music" @change="save()"> Music</label>
          <label><input type="checkbox" v-model="cfg.voice" @change="save()"> Voice announcer</label>
          <label><input type="checkbox" v-model="cfg.haptics" @change="save()"> Haptics</label>
          <label><input type="checkbox" v-model="cfg.tilt" @change="save()"> Tilt steering (gyro)</label>
          <label><input type="checkbox" v-model="cfg.tapHalves" @change="save()"> Tap halves to step</label>
          <label><input type="checkbox" v-model="cfg.doubleTapOd" @change="save()"> Double-tap overdrive</label>
          <label><input type="checkbox" v-model="cfg.longPressRail" @change="save()"> Long-press railgun</label>
          <label><input type="checkbox" v-model="cfg.autoFireAssist" @change="save()"> Always-on auto-fire</label>
          <label><input type="checkbox" v-model="cfg.bigHitbox" @change="save()"> Larger pickup hitbox</label>
          <label><input type="checkbox" v-model="cfg.smallEnemyHitbox" @change="save()"> Smaller enemy bullets</label>
          <label><input type="checkbox" v-model="cfg.invinc" @change="save()"> Invincibility (story only)</label>
          <label><input type="checkbox" v-model="cfg.oneHanded" @change="save()"> One-handed mode</label>
          <label><input type="checkbox" v-model="cfg.bigHud" @change="save()"> Big HUD</label>
          <label><input type="checkbox" v-model="cfg.reducedMotion" @change="save()"> Reduced motion</label>
          <label><input type="checkbox" v-model="cfg.highContrast" @change="save()"> High contrast outlines</label>
          <label><input type="checkbox" v-model="cfg.battery" @change="save()"> Battery saver (30 fps)</label>
          <label><input type="checkbox" v-model="cfg.daily" @change="save()"> Daily seeded run</label>
          <label><input type="checkbox" v-model="cfg.ghost" @change="save()"> Ghost overlay (best run)</label>
          <label><input type="checkbox" v-model="cfg.replay" @change="save()"> Save replay</label>
        </div>
        <div class="row">
          <label>Drag sensitivity {{ Math.round(cfg.dragSens * 100) }}%</label>
          <input type="range" min="0" max="100" :value="cfg.dragSens * 100" @input="cfg.dragSens = +($event.target as HTMLInputElement).value/100; save()">
        </div>
        <div class="row">
          <label>Edge dead-zone {{ Math.round(cfg.edgeDead * 100) }}%</label>
          <input type="range" min="0" max="20" :value="cfg.edgeDead * 100" @input="cfg.edgeDead = +($event.target as HTMLInputElement).value/100; save()">
        </div>
        <div class="row">
          <label>Tilt sensitivity {{ Math.round(cfg.tiltSens * 100) }}%</label>
          <input type="range" min="0" max="100" :value="cfg.tiltSens * 100" @input="cfg.tiltSens = +($event.target as HTMLInputElement).value/100; save()">
        </div>
        <div class="row">
          <label>HUD opacity {{ Math.round(cfg.hudOpacity * 100) }}%</label>
          <input type="range" min="20" max="100" :value="cfg.hudOpacity * 100" @input="cfg.hudOpacity = +($event.target as HTMLInputElement).value/100; save()">
        </div>
        <div class="row">
          <label>Font scale {{ Math.round(cfg.fontScale * 100) }}%</label>
          <input type="range" min="80" max="160" :value="cfg.fontScale * 100" @input="cfg.fontScale = +($event.target as HTMLInputElement).value/100; save()">
        </div>
        <div class="row">
          <label>Colorblind</label>
          <select v-model="cfg.colorblind" @change="save()">
            <option value="none">None</option>
            <option value="deutan">Deuteranopia</option>
            <option value="protan">Protanopia</option>
            <option value="tritan">Tritanopia</option>
          </select>
        </div>
        <div class="row"><button class="chip" @click="showSettings = false">Close</button></div>
      </div>
    </div>

    <!-- Hull picker -->
    <div v-if="showHull" class="sheet" @click.self="showHull = false">
      <div class="card">
        <h3>Hull · own {{ stats.unlockedHulls.length }}/4</h3>
        <div v-for="h in HULLS" :key="h.id" class="hullRow">
          <div class="dot" :style="{ background: h.color }"></div>
          <div class="grow">
            <div class="name">{{ h.label }}</div>
            <div class="meta">FR {{ h.fire }} · DMG {{ h.dmg }} · HP {{ h.hp }} · SPD {{ h.spd }}</div>
            <div class="desc">{{ h.desc }}</div>
          </div>
          <button v-if="!stats.unlockedHulls.includes(h.id)" class="chip" :disabled="store.coins < h.price" @click="buyHull(h)">Buy {{ h.price }}c</button>
          <button v-else-if="cfg.hull !== h.id" class="chip on" @click="cfg.hull = h.id; save()">Use</button>
          <span v-else class="chip eq">Equipped</span>
        </div>
        <button class="chip" @click="showHull = false">Close</button>
      </div>
    </div>

    <!-- Shop / upgrades -->
    <div v-if="showShop" class="sheet" @click.self="showShop = false">
      <div class="card">
        <h3>Permanent upgrades · coins {{ store.coins }}</h3>
        <div v-for="u in UPGRADES" :key="u.id" class="upRow">
          <div class="grow">
            <div class="name">{{ u.label }}</div>
            <div class="meta">Lv {{ stats.upgrades[u.id] || 0 }}/5 · {{ u.desc }}</div>
          </div>
          <button class="chip" :disabled="(stats.upgrades[u.id]||0) >= 5 || store.coins < u.cost * ((stats.upgrades[u.id]||0)+1)"
                  @click="buyUpgrade(u)">+1 ({{ u.cost * ((stats.upgrades[u.id]||0)+1) }}c)</button>
        </div>
        <button class="chip" @click="showShop = false">Close</button>
      </div>
    </div>

    <!-- Perks -->
    <div v-if="showPerks" class="sheet" @click.self="showPerks = false">
      <div class="card">
        <h3>Pick a perk for this run</h3>
        <div v-for="p in PERKS" :key="p.id" class="hullRow">
          <div class="grow">
            <div class="name">{{ p.label }}</div>
            <div class="desc">{{ p.desc }}</div>
          </div>
          <button class="chip" :class="{ on: cfg.perk === p.id }" @click="cfg.perk = p.id; save()">{{ cfg.perk === p.id ? '✓' : 'Pick' }}</button>
        </div>
        <button class="chip" @click="showPerks = false">Close</button>
      </div>
    </div>

    <!-- Calibration -->
    <div v-if="showCalibrate" class="sheet" @click.self="showCalibrate = false">
      <div class="card">
        <h3>Live calibration</h3>
        <div class="cal">
          <div class="bar"><div class="fill" :style="{ width: calProgress + '%' }"></div></div>
          <div class="hint" v-if="!calStarted">Touch the box and drag from far left to far right.</div>
          <div class="hint" v-else>Tracked range: {{ Math.round(calRange) }} px → sensitivity {{ Math.round(cfg.dragSens * 100) }}%</div>
        </div>
        <div class="row">
          <button class="chip" @click="startCal">Start drag test</button>
          <button class="chip" v-if="cfg.tilt" @click="calibrateTilt">Calibrate tilt</button>
          <button class="chip" @click="showCalibrate = false">Done</button>
        </div>
      </div>
    </div>

    <!-- Analytics -->
    <div v-if="showAnalytics" class="sheet">
      <div class="card wide">
        <h3>Run report · Score {{ score }}</h3>
        <div class="kpis">
          <div class="kpi"><span>Wave reached</span><b>{{ act }}-{{ wave }}</b></div>
          <div class="kpi"><span>Accuracy</span><b>{{ accuracy }}%</b></div>
          <div class="kpi"><span>Kills</span><b>{{ kills }}</b></div>
          <div class="kpi"><span>Coins</span><b>{{ runCoins }}</b></div>
          <div class="kpi"><span>Bosses beat</span><b>{{ runBosses }}</b></div>
          <div class="kpi"><span>Perfect waves</span><b>{{ perfectWaves }}</b></div>
        </div>
        <h4>Death heatmap</h4>
        <svg :viewBox="`0 0 ${heatW} ${heatH}`" class="heat">
          <rect x="0" y="0" :width="heatW" :height="heatH" fill="#0b1220" />
          <g v-for="(c, i) in heatCells" :key="i">
            <rect :x="c.x" :y="c.y" :width="c.w" :height="c.h" :fill="c.color" />
          </g>
          <circle v-for="(d, i) in deaths" :key="'d'+i" :cx="d.x * heatW" :cy="d.y * heatH" r="6" fill="#ef4444" stroke="#fff" />
        </svg>
        <h4>Most dangerous</h4>
        <div class="dangers">
          <div v-for="(n, k) in dangerCounts" :key="k" class="bar2">
            <span class="lbl">{{ k }}</span>
            <div class="track"><div class="fill" :style="{ width: (n / dangerMax * 100) + '%' }"></div></div>
            <b>{{ n }}</b>
          </div>
        </div>
        <h4>Tip</h4>
        <div class="tip">{{ runTip }}</div>
        <h4 v-if="cfg.replay">Replay (last 5 sec)</h4>
        <div v-if="cfg.replay" class="replay">{{ replayFrames.length }} frames captured</div>
        <div class="row">
          <button class="chip" @click="watchReplay" v-if="cfg.replay">▶ Watch replay (slow-mo)</button>
          <button class="chip" @click="copyChallenge">📋 Share run code</button>
          <button class="chip" @click="reseed">↻ Replay run</button>
          <button class="chip on" @click="showAnalytics = false; props.onGameOver(score)">Done</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: string
  mode: string
  paused: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
  accent?: string
}>()

const store = useGamesStore()

// ----------------------------- Settings ---------------------------------
type Cfg = {
  sound: boolean; music: boolean; voice: boolean; haptics: boolean
  tilt: boolean; tapHalves: boolean; doubleTapOd: boolean; longPressRail: boolean
  autoFireAssist: boolean; bigHitbox: boolean; smallEnemyHitbox: boolean; invinc: boolean
  oneHanded: boolean; bigHud: boolean; reducedMotion: boolean; highContrast: boolean
  battery: boolean; daily: boolean; ghost: boolean; replay: boolean
  dragSens: number; edgeDead: number; tiltSens: number; hudOpacity: number; fontScale: number
  colorblind: 'none' | 'deutan' | 'protan' | 'tritan'
  hull: string; perk: string
  tiltOffset: number
}
const DEFAULTS: Cfg = {
  sound: true, music: true, voice: true, haptics: true,
  tilt: false, tapHalves: true, doubleTapOd: true, longPressRail: true,
  autoFireAssist: true, bigHitbox: false, smallEnemyHitbox: false, invinc: false,
  oneHanded: false, bigHud: false, reducedMotion: false, highContrast: false,
  battery: false, daily: false, ghost: true, replay: true,
  dragSens: 1, edgeDead: 0.04, tiltSens: 0.5, hudOpacity: 0.9, fontScale: 1,
  colorblind: 'none', hull: 'interceptor', perk: 'shield',
  tiltOffset: 0,
}
const cfg = reactive<Cfg>({ ...DEFAULTS })
const stats = reactive({
  unlockedHulls: ['interceptor'] as string[],
  upgrades: {} as Record<string, number>,
  loginStreak: 1, lastLoginDay: '',
  bestScore: 0, bestWave: 0, totalKills: 0, totalRuns: 0, totalCoins: 0,
})
const SAVEKEY = 'space_hunter_settings_v1'
function load() {
  try {
    const raw = localStorage.getItem(SAVEKEY)
    if (raw) {
      const o = JSON.parse(raw)
      Object.assign(cfg, o.cfg || {})
      Object.assign(stats, o.stats || {})
    }
  } catch (_) {}
  // login streak
  const today = new Date().toISOString().slice(0, 10)
  if (stats.lastLoginDay !== today) {
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10)
    stats.loginStreak = stats.lastLoginDay === yesterday ? stats.loginStreak + 1 : 1
    stats.lastLoginDay = today
    if (stats.loginStreak > 1) store.addCoins(10 * Math.min(stats.loginStreak, 7))
  }
}
function save() { localStorage.setItem(SAVEKEY, JSON.stringify({ cfg, stats })) }
load()

// ---------------------------- Catalogs ----------------------------------
const HULLS = [
  { id: 'interceptor', label: 'Interceptor', color: '#7dd3fc', fire: 6, dmg: 1, hp: 3, spd: 1.2, price: 0,
    desc: 'Balanced ship — fast fire, average HP.' },
  { id: 'brawler', label: 'Brawler', color: '#ef4444', fire: 4, dmg: 2, hp: 5, spd: 0.9, price: 250,
    desc: 'Tankier and hits harder, slower fire.' },
  { id: 'stealth', label: 'Stealth', color: '#a78bfa', fire: 7, dmg: 1, hp: 2, spd: 1.4, price: 350,
    desc: 'Smaller hitbox, fast fire, fragile.' },
  { id: 'healer', label: 'Healer', color: '#34d399', fire: 5, dmg: 1, hp: 4, spd: 1.1, price: 500,
    desc: 'Regens 1 HP every 4 waves cleared.' },
] as const
const hullDef = computed(() => HULLS.find(h => h.id === cfg.hull) || HULLS[0])

const UPGRADES = [
  { id: 'fire', label: 'Fire rate', cost: 80, desc: '+10% per level' },
  { id: 'dmg', label: 'Damage', cost: 100, desc: '+1 per 2 levels' },
  { id: 'hp', label: 'Max HP', cost: 120, desc: '+1 HP per level' },
  { id: 'spd', label: 'Move speed', cost: 60, desc: '+8% per level' },
  { id: 'spec', label: 'Special recharge', cost: 90, desc: 'Overdrive recharges 15% faster' },
] as const

const PERKS = [
  { id: 'shield', label: 'Shield on wave start', desc: 'Free 2-second shield each wave.' },
  { id: 'lifesteal', label: 'Lifesteal', desc: '5% chance to heal +1 HP on kill.' },
  { id: 'double', label: 'Double shot', desc: 'Fires two parallel bolts.' },
  { id: 'magnet', label: 'Magnet aura', desc: 'Pickups drift toward you.' },
  { id: 'slowonhit', label: 'Slow on hit', desc: '0.5s slow-time after taking damage.' },
] as const

type EnemyKind = 'swarmer' | 'tank' | 'sniper' | 'mothership' | 'drone' | 'mine' | 'bomber' | 'weaver' | 'splitter' | 'shielder' | 'kamikaze' | 'turret' | 'elite' | 'miniboss' | 'boss'
type PowerKind = 'shield' | 'slow' | 'magnet' | 'life' | 'double' | 'nuke' | 'repair' | 'overcharge'

interface Bullet { x: number; y: number; vx: number; vy: number; r: number; dmg: number; pierce?: number; from: 'p' | 'e'; color: string }
interface Enemy { x: number; y: number; vx: number; vy: number; kind: EnemyKind; hp: number; max: number; r: number; t: number; phase?: number; cooldown?: number; shield?: number; data?: Record<string, number> }
interface Power { x: number; y: number; vx: number; vy: number; kind: PowerKind; t: number }
interface Particle { x: number; y: number; vx: number; vy: number; life: number; color: string; r: number }

// ----------------------------- State ------------------------------------
const cv = ref<HTMLCanvasElement>()
const score = ref(0)
const hp = ref(3)
const maxHp = computed(() => hullDef.value.hp + (stats.upgrades.hp || 0))
const act = ref(1)
const wave = ref(1)
const comboMul = ref(1)
const overdriveCharge = ref(0)
const shieldT = ref(0); const slowT = ref(0); const magnetT = ref(0); const doubleT = ref(0); const ifr = ref(0)
const started = ref(false); const dead = ref(false)
const kills = ref(0); const shotsFired = ref(0); const shotsHit = ref(0); const runCoins = ref(0)
const runBosses = ref(0); const perfectWaves = ref(0)
const accuracy = computed(() => shotsFired.value ? Math.round(100 * shotsHit.value / shotsFired.value) : 0)

const showSettings = ref(false); const showShop = ref(false); const showHull = ref(false)
const showPerks = ref(false); const showCalibrate = ref(false); const showAnalytics = ref(false)

const calStarted = ref(false); const calRange = ref(0); const calProgress = ref(0); const calMin = ref(Infinity); const calMax = ref(-Infinity)

const dailyCode = computed(() => {
  const d = new Date()
  const seed = d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate()
  return seed.toString(36).toUpperCase()
})

const rootStyle = computed(() => ({
  '--accent': props.accent || '#7CE7FF',
  '--hud-op': cfg.hudOpacity,
  '--font-scale': cfg.fontScale,
}) as Record<string, string | number>)

// player
let pos = { x: 0, y: 0 }
let target = { x: 0, y: 0 }
let pointerActive = false
let pointerDownAt = 0
let pointerDownPos = { x: 0, y: 0 }
let lastTapAt = 0
let chargeT = 0
let chargeReady = false

// world
let bullets: Bullet[] = []
let enemies: Enemy[] = []
let powers: Power[] = []
let particles: Particle[] = []
let stars: { x: number; y: number; r: number; v: number }[] = []
let nebula: { x: number; y: number; r: number; c: string }[] = []
let waveT = 0
let waveSpawned = 0
let waveQueue: { kind: EnemyKind; delay: number }[] = []
let bossActive = false
let waveDeathCount = 0
let recentDeaths = 0
let recentWins = 0
let frameNo = 0
let lastTime = 0
let raf: number | null = null
let lastFireAt = 0
let mode = props.mode
let timeScale = 1
let killedBy: Record<string, number> = {}
let deaths: { x: number; y: number; cause: string }[] = []
let replayFrames: { x: number; y: number; e: number; b: number }[] = []

const heatW = 240, heatH = 360
const heatCells = computed(() => {
  const cells: { x: number; y: number; w: number; h: number; color: string }[] = []
  if (!deaths.length) return cells
  const cols = 6, rows = 9
  const cw = heatW / cols, ch = heatH / rows
  const counts = new Array(cols * rows).fill(0)
  deaths.forEach(d => {
    const c = Math.min(cols - 1, Math.max(0, Math.floor(d.x * cols)))
    const r = Math.min(rows - 1, Math.max(0, Math.floor(d.y * rows)))
    counts[r * cols + c]++
  })
  const m = Math.max(1, ...counts)
  for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) {
    const v = counts[r * cols + c] / m
    cells.push({ x: c * cw, y: r * ch, w: cw, h: ch, color: `rgba(239,68,68,${0.05 + v * 0.5})` })
  }
  return cells
})
const dangerCounts = computed(() => killedBy)
const dangerMax = computed(() => Math.max(1, ...Object.values(killedBy)))
const runTip = computed(() => {
  const top = Object.entries(killedBy).sort((a, b) => b[1] - a[1])[0]
  if (!top) return 'Smooth run! Try a higher mode for the leaderboard climb.'
  if (top[0] === 'sniper') return 'Snipers tracked you — never park, keep weaving.'
  if (top[0] === 'kamikaze') return 'Kamikazes closed in — kite them across the screen.'
  if (top[0] === 'tank') return 'Tanks soaked your damage — buy double shot or upgrade Damage.'
  if (top[0] === 'boss') return 'Boss caught you in phase 2 — save overdrive for the second phase.'
  return `Watch out for ${top[0]} — they killed you most.`
})

// PRNG
let rngState = 0
function srand(seed: number) { rngState = (seed >>> 0) || 1 }
function rand() { rngState = (rngState * 1664525 + 1013904223) >>> 0; return rngState / 0xFFFFFFFF }

// audio
let toneCtx: AudioContext | null = null
function tone(kind: 'shot' | 'kill' | 'hit' | 'pwr' | 'boss' | 'wave') {
  if (!cfg.sound) return
  try {
    if (!toneCtx) toneCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
    const o = toneCtx.createOscillator(); const g = toneCtx.createGain()
    o.connect(g); g.connect(toneCtx.destination)
    const f: Record<string, [number, number]> = {
      shot: [880, 0.04], kill: [330, 0.12], hit: [120, 0.18], pwr: [660, 0.18], boss: [80, 0.5], wave: [520, 0.2],
    }
    const [hz, dur] = f[kind]
    o.type = kind === 'hit' ? 'sawtooth' : 'sine'
    o.frequency.value = hz
    g.gain.value = 0.05
    o.start(); o.stop(toneCtx.currentTime + dur)
  } catch (_) {}
}
let lastSpoke = 0
function speak(s: string) {
  if (!cfg.voice || !cfg.sound) return
  const now = Date.now(); if (now - lastSpoke < 800) return; lastSpoke = now
  try { const u = new SpeechSynthesisUtterance(s); u.volume = 0.7; u.rate = 1.1; window.speechSynthesis.speak(u) } catch (_) {}
}
function haptic(ms: number | number[]) {
  if (!cfg.haptics) return
  if ('vibrate' in navigator) { try { navigator.vibrate(ms as number) } catch (_) {} }
}

// ----------------------------- Setup ------------------------------------
let tiltHandler: ((e: DeviceOrientationEvent) => void) | null = null

onMounted(() => {
  const c = cv.value!; const ctx = c.getContext('2d')!
  function resize() {
    const dpr = Math.max(1, window.devicePixelRatio || 1)
    const r = c.getBoundingClientRect()
    c.width = Math.floor(r.width * dpr); c.height = Math.floor(r.height * dpr)
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    pos.x = r.width / 2; pos.y = r.height * 0.85
    target.x = pos.x; target.y = pos.y
  }
  resize()
  window.addEventListener('resize', resize)
  window.addEventListener('keydown', onKey)
  if (cfg.tilt && 'DeviceOrientationEvent' in window) {
    tiltHandler = (e: DeviceOrientationEvent) => {
      const g = (e.gamma || 0) - cfg.tiltOffset
      const w = c.getBoundingClientRect().width
      target.x = (w / 2) + (g / 45) * w * cfg.tiltSens
    }
    window.addEventListener('deviceorientation', tiltHandler)
  }
  // initial stars
  const r = c.getBoundingClientRect()
  for (let i = 0; i < 80; i++) stars.push({ x: Math.random() * r.width, y: Math.random() * r.height, r: Math.random() * 1.5 + 0.4, v: Math.random() * 30 + 10 })
  for (let i = 0; i < 5; i++) nebula.push({ x: Math.random() * r.width, y: Math.random() * r.height, r: Math.random() * 200 + 100, c: ['#312e81', '#581c87', '#0f766e'][i % 3] })

  raf = requestAnimationFrame(loop)
})
onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
  window.removeEventListener('resize', () => {})
  window.removeEventListener('keydown', onKey)
  if (tiltHandler) window.removeEventListener('deviceorientation', tiltHandler)
})

// ----------------------------- Input ------------------------------------
function onDown(e: PointerEvent) {
  pointerActive = true
  pointerDownAt = performance.now()
  const r = cv.value!.getBoundingClientRect()
  const x = e.clientX - r.left, y = e.clientY - r.top
  pointerDownPos = { x, y }
  if (started.value) {
    target.x = x; if (!cfg.oneHanded) target.y = y
  }
  // tap halves
  if (cfg.tapHalves && started.value) {
    if (x < r.width * 0.2) target.x -= 30
    else if (x > r.width * 0.8) target.x += 30
  }
  chargeT = 0; chargeReady = false
}
function onMove(e: PointerEvent) {
  if (!pointerActive) return
  const r = cv.value!.getBoundingClientRect()
  const x = e.clientX - r.left, y = e.clientY - r.top
  const ed = r.width * cfg.edgeDead
  target.x = Math.max(ed, Math.min(r.width - ed, x))
  if (!cfg.oneHanded) target.y = Math.max(r.height * 0.5, Math.min(r.height - 20, y))
  // sensitivity scaling around centre
  if (cfg.dragSens < 1) {
    const dx = (x - r.width / 2) * cfg.dragSens
    target.x = r.width / 2 + dx
  }
}
function onUp() {
  if (pointerActive) {
    const dt = performance.now() - pointerDownAt
    if (dt < 220) {
      // potential double-tap for overdrive
      const since = performance.now() - lastTapAt
      if (cfg.doubleTapOd && since < 280) overdrive()
      lastTapAt = performance.now()
    } else if (cfg.longPressRail && chargeReady) {
      fireRailgun()
    }
  }
  pointerActive = false
  chargeT = 0; chargeReady = false
}
function onKey(e: KeyboardEvent) {
  if (!started.value && e.key === 'Enter') { startRun(); return }
  if (!started.value) return
  const r = cv.value!.getBoundingClientRect()
  if (e.key === 'ArrowLeft' || e.key === 'a' || e.key === 'A') target.x = Math.max(20, target.x - 24)
  else if (e.key === 'ArrowRight' || e.key === 'd' || e.key === 'D') target.x = Math.min(r.width - 20, target.x + 24)
  else if (e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W') target.y = Math.max(r.height * 0.5, target.y - 24)
  else if (e.key === 'ArrowDown' || e.key === 's' || e.key === 'S') target.y = Math.min(r.height - 20, target.y + 24)
  else if (e.key === ' ') { e.preventDefault(); overdrive() }
  else if (e.key === 'Shift') fireRailgun()
}

// ------------------------ Spawning helpers ------------------------------
function spawnEnemy(kind: EnemyKind, x: number, y: number): Enemy {
  const base: Record<EnemyKind, { hp: number; r: number; vx: number; vy: number }> = {
    swarmer: { hp: 1, r: 12, vx: 0, vy: 60 },
    tank: { hp: 6, r: 22, vx: 0, vy: 30 },
    sniper: { hp: 3, r: 14, vx: 0, vy: 0 },
    mothership: { hp: 12, r: 30, vx: 0, vy: 18 },
    drone: { hp: 1, r: 10, vx: 0, vy: 80 },
    mine: { hp: 1, r: 14, vx: 0, vy: 18 },
    bomber: { hp: 2, r: 16, vx: 0, vy: 70 },
    weaver: { hp: 2, r: 14, vx: 60, vy: 50 },
    splitter: { hp: 3, r: 18, vx: 0, vy: 50 },
    shielder: { hp: 2, r: 18, vx: 0, vy: 40 },
    kamikaze: { hp: 1, r: 12, vx: 0, vy: 100 },
    turret: { hp: 4, r: 18, vx: 0, vy: 0 },
    elite: { hp: 8, r: 20, vx: 0, vy: 50 },
    miniboss: { hp: 25, r: 36, vx: 0, vy: 25 },
    boss: { hp: 80 + act.value * 30, hp2: 0, r: 60, vx: 80, vy: 10 } as any,
  }
  const b = base[kind]
  const e: Enemy = { x, y, vx: b.vx, vy: b.vy, kind, hp: b.hp, max: b.hp, r: b.r, t: 0, phase: kind === 'boss' ? 1 : 0, cooldown: 0,
    shield: kind === 'shielder' ? 3 : 0, data: {} }
  return e
}

function buildWave() {
  waveQueue = []
  const w = wave.value
  const a = act.value
  const diffMul = props.difficulty === 'easy' ? 0.7 : props.difficulty === 'hard' ? 1.4 : props.difficulty === 'expert' ? 1.7 : 1
  const adapt = (1 + recentWins * 0.06 - recentDeaths * 0.04)
  const count = Math.floor((6 + w * 1.4 + a * 2) * diffMul * adapt)
  if (mode === 'BossRush') { waveQueue.push({ kind: 'boss', delay: 1 }); return }
  if (w === 10) {
    waveQueue.push({ kind: 'boss', delay: 1.5 }); return
  }
  for (let i = 0; i < count; i++) {
    const r = rand()
    let kind: EnemyKind = 'swarmer'
    if (a >= 5) {
      if (r < 0.18) kind = 'swarmer'; else if (r < 0.32) kind = 'weaver'; else if (r < 0.45) kind = 'tank'
      else if (r < 0.55) kind = 'sniper'; else if (r < 0.65) kind = 'shielder'; else if (r < 0.75) kind = 'splitter'
      else if (r < 0.85) kind = 'elite'; else if (r < 0.92) kind = 'turret'; else kind = 'mothership'
    } else if (a >= 3) {
      if (r < 0.3) kind = 'swarmer'; else if (r < 0.5) kind = 'weaver'; else if (r < 0.62) kind = 'tank'
      else if (r < 0.72) kind = 'sniper'; else if (r < 0.8) kind = 'shielder'; else if (r < 0.9) kind = 'kamikaze'
      else if (r < 0.96) kind = 'bomber'; else kind = 'mothership'
    } else if (a >= 2) {
      if (r < 0.4) kind = 'swarmer'; else if (r < 0.6) kind = 'weaver'; else if (r < 0.75) kind = 'tank'
      else if (r < 0.88) kind = 'drone'; else if (r < 0.96) kind = 'kamikaze'; else kind = 'sniper'
    } else {
      if (r < 0.6) kind = 'swarmer'; else if (r < 0.8) kind = 'drone'
      else if (r < 0.92) kind = 'mine'; else kind = 'tank'
    }
    if (w === 5 || w === 8) waveQueue.push({ kind: 'miniboss', delay: i * 0.05 })
    waveQueue.push({ kind, delay: 0.4 + i * 0.5 })
  }
}

function spawnFromQueue(dt: number) {
  if (!waveQueue.length) return
  const next = waveQueue[0]
  next.delay -= dt
  if (next.delay <= 0) {
    waveQueue.shift()
    const r = cv.value!.getBoundingClientRect()
    const x = 30 + rand() * (r.width - 60)
    enemies.push(spawnEnemy(next.kind, x, -30))
    waveSpawned++
    if (next.kind === 'boss') {
      bossActive = true
      speak('Boss approaching')
      tone('boss')
      haptic([60, 40, 60, 40, 60])
      runBosses.value++
    }
    if (next.kind === 'miniboss') speak('Mini boss')
  }
}

// ----------------------------- Loop -------------------------------------
function loop(now: number) {
  raf = requestAnimationFrame(loop)
  if (props.paused || !started.value || dead.value) { lastTime = now; return }
  let dt = Math.min(0.05, (now - lastTime) / 1000); if (!lastTime) dt = 0.016; lastTime = now
  if (cfg.battery && frameNo % 2 === 0) {} // throttle to 30fps
  if (cfg.battery) dt = Math.min(dt, 1 / 30)
  timeScale = slowT.value > 0 ? 0.5 : 1
  dt *= timeScale
  frameNo++

  // smooth move
  const r = cv.value!.getBoundingClientRect()
  pos.x += (target.x - pos.x) * Math.min(1, hullDef.value.spd * (1 + (stats.upgrades.spd || 0) * 0.08) * 18 * dt)
  pos.y += (target.y - pos.y) * Math.min(1, hullDef.value.spd * 18 * dt)
  pos.x = Math.max(20, Math.min(r.width - 20, pos.x))
  pos.y = Math.max(r.height * 0.5, Math.min(r.height - 20, pos.y))

  if (cfg.replay && frameNo % 4 === 0) {
    replayFrames.push({ x: pos.x / r.width, y: pos.y / r.height, e: enemies.length, b: bullets.length })
    if (replayFrames.length > 600) replayFrames.shift()
  }

  // charge
  if (pointerActive && cfg.longPressRail) {
    chargeT += dt; if (chargeT > 0.6) chargeReady = true
  }

  // timers
  shieldT.value = Math.max(0, shieldT.value - dt)
  slowT.value = Math.max(0, slowT.value - dt)
  magnetT.value = Math.max(0, magnetT.value - dt)
  doubleT.value = Math.max(0, doubleT.value - dt)
  ifr.value = Math.max(0, ifr.value - dt)
  overdriveCharge.value = Math.min(100, overdriveCharge.value + dt * (5 + (stats.upgrades.spec || 0) * 0.75))

  // auto-fire
  const fireRate = (hullDef.value.fire + (stats.upgrades.fire || 0) * 0.6) * (1 + (cfg.perk === 'double' ? 0 : 0))
  if (cfg.autoFireAssist || pointerActive) {
    if (now - lastFireAt > 1000 / fireRate) {
      fireBullet(); lastFireAt = now
    }
  }

  // spawn / wave
  spawnFromQueue(dt)
  if (!waveQueue.length && enemies.length === 0) {
    nextWave()
  }

  // ENEMIES
  for (const e of enemies) {
    e.t += dt
    e.cooldown = (e.cooldown || 0) - dt
    switch (e.kind) {
      case 'swarmer': e.x += Math.sin(e.t * 3) * 60 * dt; e.y += e.vy * dt; break
      case 'drone': e.y += e.vy * dt; if (rand() < 0.005) e.vx = (rand() - 0.5) * 80; e.x += (e.vx || 0) * dt; break
      case 'mine': e.y += e.vy * dt; if (Math.hypot(e.x - pos.x, e.y - pos.y) < 80 && (e.cooldown || 0) <= 0) {
        explodeEnemy(e); }
        break
      case 'weaver': e.x += Math.sin(e.t * 2) * 120 * dt; e.y += e.vy * dt; break
      case 'tank': e.y += e.vy * dt; if ((e.cooldown || 0) <= 0 && e.y > 60) { fireEnemyBullet(e, pos.x, pos.y, 200, 'tank'); e.cooldown = 1.4 }; break
      case 'sniper': if (e.y < r.height * 0.18) e.y += 20 * dt; else if ((e.cooldown || 0) <= 0) { fireEnemyBullet(e, pos.x, pos.y, 240, 'sniper', true); e.cooldown = 2 }; break
      case 'mothership': e.y += e.vy * dt; e.x += Math.sin(e.t) * 30 * dt; if ((e.cooldown || 0) <= 0 && e.y > 50) { enemies.push(spawnEnemy('drone', e.x, e.y)); e.cooldown = 2.5 }; break
      case 'bomber': e.y += e.vy * dt; if ((e.cooldown || 0) <= 0) { fireEnemyBullet(e, e.x, e.y + 30, 220, 'bomber'); e.cooldown = 0.7 }; break
      case 'splitter': e.y += e.vy * dt; break
      case 'shielder': e.y += e.vy * dt; e.x += Math.cos(e.t * 1.4) * 40 * dt; break
      case 'kamikaze': {
        const dx = pos.x - e.x, dy = pos.y - e.y; const m = Math.hypot(dx, dy) || 1
        e.x += (dx / m) * 220 * dt; e.y += (dy / m) * 220 * dt; break
      }
      case 'turret': if ((e.cooldown || 0) <= 0) { for (let k = 0; k < 6; k++) { const a = (Math.PI * 2 * k / 6) + e.t; const ang = Math.cos(a); fireEnemyBulletAng(e, ang, Math.sin(a), 180) } e.cooldown = 1.6 }; break
      case 'elite': e.y += e.vy * dt; if ((e.cooldown || 0) <= 0) { for (let k = -1; k <= 1; k++) fireEnemyBulletAng(e, k * 0.3, 1, 220); e.cooldown = 1.2 }; break
      case 'miniboss': e.y += e.vy * dt * 0.5; e.x += Math.sin(e.t * 0.8) * 80 * dt; if ((e.cooldown || 0) <= 0) { for (let k = -2; k <= 2; k++) fireEnemyBulletAng(e, k * 0.25, 1, 200); e.cooldown = 1 }; break
      case 'boss': bossUpdate(e, dt, r); break
    }
    // power-up drop on screen exit
    if (e.y > r.height + 40) { enemies = enemies.filter(x => x !== e) }
  }

  // BULLETS
  for (const b of bullets) { b.x += b.vx * dt; b.y += b.vy * dt }
  bullets = bullets.filter(b => b.x > -10 && b.x < r.width + 10 && b.y > -10 && b.y < r.height + 10)

  // collisions
  // player bullets vs enemies
  for (const b of bullets) {
    if (b.from !== 'p') continue
    for (const e of enemies) {
      const d = Math.hypot(b.x - e.x, b.y - e.y)
      if (d < e.r + b.r) {
        if (e.shield && e.shield > 0) { e.shield--; bullets = bullets.filter(x => x !== b); break }
        e.hp -= b.dmg * (doubleT.value > 0 ? 2 : 1)
        shotsHit.value++
        spawn(b.x, b.y, e.kind === 'boss' ? '#fbbf24' : '#fff', 6)
        if ((b.pierce || 0) > 0) { b.pierce!--; } else { bullets = bullets.filter(x => x !== b) }
        if (e.hp <= 0) {
          killEnemy(e); 
        }
        break
      }
    }
  }
  // enemy bullets vs player
  if (!cfg.invinc) {
    for (const b of bullets) {
      if (b.from !== 'e') continue
      const pr = (cfg.smallEnemyHitbox ? 0.7 : 1) * (hullDef.value.id === 'stealth' ? 7 : 10)
      if (Math.hypot(b.x - pos.x, b.y - pos.y) < pr + b.r) {
        bullets = bullets.filter(x => x !== b)
        if (ifr.value <= 0 && shieldT.value <= 0) damagePlayer('bullet')
        else if (shieldT.value > 0) { shieldT.value = 0; spawn(pos.x, pos.y, '#38bdf8', 20) }
      }
    }
    // enemy body
    for (const e of enemies) {
      if (Math.hypot(e.x - pos.x, e.y - pos.y) < e.r + 10) {
        if (ifr.value <= 0 && shieldT.value <= 0) damagePlayer(e.kind)
        else if (shieldT.value > 0) { shieldT.value = 0; spawn(pos.x, pos.y, '#38bdf8', 20) }
        if (e.kind === 'kamikaze' || e.kind === 'mine') { explodeEnemy(e) }
      }
    }
  }

  // power-ups
  for (const p of powers) {
    p.t += dt
    p.y += p.vy * dt
    if (magnetT.value > 0 || cfg.perk === 'magnet') {
      const dx = pos.x - p.x, dy = pos.y - p.y; const m = Math.hypot(dx, dy) || 1
      p.x += (dx / m) * 220 * dt; p.y += (dy / m) * 220 * dt
    }
    const pr = cfg.bigHitbox ? 28 : 18
    if (Math.hypot(p.x - pos.x, p.y - pos.y) < pr) { applyPower(p.kind); powers = powers.filter(x => x !== p) }
  }
  powers = powers.filter(p => p.y < r.height + 30)

  // particles
  for (const pp of particles) { pp.x += pp.vx * dt; pp.y += pp.vy * dt; pp.life -= dt }
  particles = particles.filter(p => p.life > 0)

  // healer regen
  if (hullDef.value.id === 'healer' && perfectWaves.value > 0 && perfectWaves.value % 4 === 0 && hp.value < maxHp.value) {
    hp.value++; perfectWaves.value++
  }

  draw()
  props.onScore(score.value)
}

function nextWave() {
  if (mode === 'Endless' || mode === 'Daily') {
    wave.value++
    if (wave.value > 999) wave.value = 1
  } else if (mode === 'BossRush') {
    act.value++; if (act.value > 5) finishRun(true); return
  } else {
    if (wave.value === 10) { act.value++; wave.value = 1; recentWins++; recentDeaths = Math.max(0, recentDeaths - 1)
      if (act.value > 5) { finishRun(true); return }
      speak(`Act ${act.value}`)
    } else {
      wave.value++
    }
  }
  // perk: shield on wave start
  if (cfg.perk === 'shield') shieldT.value = Math.max(shieldT.value, 2)
  // adaptive
  if (recentDeaths > 2) recentDeaths = 0
  perfectWaves.value++
  speak(`Wave ${act.value}-${wave.value}`)
  tone('wave')
  buildWave()
}

function bossUpdate(e: Enemy, dt: number, r: DOMRect) {
  e.x += e.vx * dt
  if (e.x < 60 || e.x > r.width - 60) e.vx = -e.vx
  e.y = Math.min(r.height * 0.18, e.y + 30 * dt)
  // phases: 1) sweep + spread, 2) summon + aimed, 3) bullet hell
  const ratio = e.hp / e.max
  const newPhase = ratio > 0.66 ? 1 : ratio > 0.33 ? 2 : 3
  if (newPhase !== e.phase) { e.phase = newPhase; e.cooldown = 0; speak(`Boss phase ${newPhase}`); haptic([40, 30, 40]) }
  e.cooldown = (e.cooldown || 0) - dt
  if ((e.cooldown || 0) <= 0) {
    if (e.phase === 1) {
      for (let k = -3; k <= 3; k++) fireEnemyBulletAng(e, k * 0.18, 1, 200)
      e.cooldown = 1.0
    } else if (e.phase === 2) {
      fireEnemyBullet(e, pos.x, pos.y, 260, 'boss', true)
      if (Math.random() < 0.5) enemies.push(spawnEnemy('drone', e.x + (rand() - 0.5) * 60, e.y + 30))
      e.cooldown = 0.7
    } else {
      for (let k = 0; k < 16; k++) {
        const ang = (Math.PI * 2 * k / 16) + e.t * 0.5
        fireEnemyBulletAng(e, Math.cos(ang), Math.sin(ang), 180)
      }
      e.cooldown = 0.8
    }
  }
}

function killEnemy(e: Enemy) {
  enemies = enemies.filter(x => x !== e)
  kills.value++
  comboMul.value = Math.min(10, comboMul.value + 0.1)
  const base = e.kind === 'boss' ? 1500 : e.kind === 'miniboss' ? 400 : e.kind === 'elite' ? 120 : e.kind === 'tank' ? 60 : 30
  score.value += Math.floor(base * comboMul.value)
  spawn(e.x, e.y, '#fbbf24', e.kind === 'boss' ? 80 : 18)
  tone('kill'); haptic(e.kind === 'boss' ? [60, 30, 80] : 12)
  if (e.kind === 'splitter') { for (let k = 0; k < 2; k++) enemies.push(spawnEnemy('swarmer', e.x + (k === 0 ? -20 : 20), e.y)) }
  if (cfg.perk === 'lifesteal' && Math.random() < 0.05 && hp.value < maxHp.value) hp.value++
  if (Math.random() < (e.kind === 'boss' ? 1 : e.kind === 'miniboss' ? 0.7 : 0.12)) dropPower(e.x, e.y)
  if (e.kind === 'boss') { bossActive = false; runBosses.value = Math.max(runBosses.value, runBosses.value); recentWins++; speak('Boss down') }
  runCoins.value += Math.max(1, Math.floor(base / 30))
}

function explodeEnemy(e: Enemy) {
  spawn(e.x, e.y, '#ef4444', 40)
  enemies = enemies.filter(x => x !== e)
  for (let k = 0; k < 8; k++) {
    const a = (Math.PI * 2 * k / 8)
    fireEnemyBulletAng(e, Math.cos(a), Math.sin(a), 160)
  }
  tone('hit'); haptic(40)
}

function damagePlayer(cause: string) {
  if (cfg.invinc) return
  hp.value--
  ifr.value = 0.6
  comboMul.value = 1
  killedBy[cause] = (killedBy[cause] || 0) + 1
  if (cfg.perk === 'slowonhit') slowT.value = Math.max(slowT.value, 0.5)
  spawn(pos.x, pos.y, '#ef4444', 24)
  tone('hit'); haptic([30, 20, 50])
  perfectWaves.value = 0
  if (hp.value <= 2) speak('Critical health')
  if (hp.value <= 0) { recordDeath(cause); finishRun(false) }
}

function recordDeath(cause: string) {
  const r = cv.value!.getBoundingClientRect()
  deaths.push({ x: pos.x / r.width, y: pos.y / r.height, cause })
  recentDeaths++
}

function fireBullet() {
  const dmg = hullDef.value.dmg + Math.floor((stats.upgrades.dmg || 0) / 2)
  const color = hullDef.value.color
  shotsFired.value++
  if (cfg.perk === 'double') {
    bullets.push({ x: pos.x - 6, y: pos.y - 16, vx: 0, vy: -640, r: 3, dmg, from: 'p', color })
    bullets.push({ x: pos.x + 6, y: pos.y - 16, vx: 0, vy: -640, r: 3, dmg, from: 'p', color })
  } else {
    bullets.push({ x: pos.x, y: pos.y - 18, vx: 0, vy: -640, r: 3, dmg, from: 'p', color })
  }
  tone('shot'); haptic(5)
}

function fireRailgun() {
  if (!chargeReady) return
  chargeReady = false
  const dmg = (hullDef.value.dmg + Math.floor((stats.upgrades.dmg || 0) / 2)) * 4
  bullets.push({ x: pos.x, y: pos.y - 18, vx: 0, vy: -900, r: 6, dmg, pierce: 6, from: 'p', color: '#fbbf24' })
  tone('pwr'); haptic(20)
}

function fireEnemyBullet(e: Enemy, tx: number, ty: number, sp: number, _label: string, homing = false) {
  const dx = tx - e.x, dy = ty - e.y; const m = Math.hypot(dx, dy) || 1
  bullets.push({ x: e.x, y: e.y + 10, vx: (dx / m) * sp, vy: (dy / m) * sp, r: cfg.smallEnemyHitbox ? 3 : 4, dmg: 1, from: 'e', color: homing ? '#a78bfa' : '#f43f5e' })
}
function fireEnemyBulletAng(e: Enemy, dx: number, dy: number, sp: number) {
  const m = Math.hypot(dx, dy) || 1
  bullets.push({ x: e.x, y: e.y + 10, vx: (dx / m) * sp, vy: (dy / m) * sp, r: 4, dmg: 1, from: 'e', color: '#f43f5e' })
}

function dropPower(x: number, y: number) {
  const kinds: PowerKind[] = ['shield', 'slow', 'magnet', 'life', 'double', 'nuke', 'repair', 'overcharge']
  const w = [0.18, 0.12, 0.14, 0.08, 0.14, 0.06, 0.16, 0.12]
  let r = Math.random(), acc = 0, k: PowerKind = 'shield'
  for (let i = 0; i < kinds.length; i++) { acc += w[i]; if (r < acc) { k = kinds[i]; break } }
  powers.push({ x, y, vx: 0, vy: 60, kind: k, t: 0 })
}

function applyPower(k: PowerKind) {
  switch (k) {
    case 'shield': shieldT.value = 8; speak('Shield'); break
    case 'slow': slowT.value = 4; speak('Slow time'); break
    case 'magnet': magnetT.value = 6; speak('Magnet'); break
    case 'life': hp.value = Math.min(maxHp.value, hp.value + 1); speak('Plus one life'); break
    case 'double': doubleT.value = 8; speak('Double damage'); break
    case 'nuke': enemies.forEach(e => { if (e.kind !== 'boss') e.hp = 0; }); enemies.filter(e => e.hp <= 0).forEach(killEnemy); speak('Nuke'); haptic(80); break
    case 'repair': hp.value = maxHp.value; speak('Repaired'); break
    case 'overcharge': overdriveCharge.value = 100; speak('Overcharge'); break
  }
  tone('pwr'); haptic(20)
}

function overdrive() {
  if (overdriveCharge.value < 100) return
  overdriveCharge.value = 0
  const r = cv.value!.getBoundingClientRect()
  for (let k = -8; k <= 8; k++) {
    bullets.push({ x: pos.x, y: pos.y - 18, vx: k * 60, vy: -700, r: 4, dmg: 3, from: 'p', color: '#fde047', pierce: 2 })
  }
  // damage all on screen
  enemies.forEach(e => { if (e.kind !== 'boss') e.hp -= 4; else e.hp -= 30 })
  enemies.filter(e => e.hp <= 0).forEach(killEnemy)
  spawn(r.width / 2, r.height / 2, '#fde047', 100)
  speak('Overdrive'); tone('pwr'); haptic([20, 30, 60])
}

function spawn(x: number, y: number, color: string, n: number) {
  if (cfg.reducedMotion) n = Math.min(n, 4)
  for (let i = 0; i < n; i++) {
    particles.push({ x, y, vx: (Math.random() - 0.5) * 240, vy: (Math.random() - 0.5) * 240, life: 0.5 + Math.random() * 0.5, color, r: 1 + Math.random() * 2 })
  }
}

// Drawing
function draw() {
  const c = cv.value!; const ctx = c.getContext('2d')!
  const r = c.getBoundingClientRect()
  ctx.clearRect(0, 0, r.width, r.height)
  // background
  const grad = ctx.createLinearGradient(0, 0, 0, r.height)
  grad.addColorStop(0, '#020617'); grad.addColorStop(1, '#02123A')
  ctx.fillStyle = grad; ctx.fillRect(0, 0, r.width, r.height)
  // nebula
  if (!cfg.reducedMotion) for (const n of nebula) {
    const g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r)
    g.addColorStop(0, n.c + '99'); g.addColorStop(1, '#00000000')
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2); ctx.fill()
  }
  // stars
  ctx.fillStyle = '#fff'
  for (const s of stars) { s.y += s.v * 0.016 * (cfg.battery ? 0.5 : 1); if (s.y > r.height) { s.y = -2; s.x = Math.random() * r.width }; ctx.fillRect(s.x, s.y, s.r, s.r) }
  // power bar
  ctx.fillStyle = '#fde047'; ctx.fillRect(0, 0, r.width * (overdriveCharge.value / 100), 3)
  // ghost
  if (cfg.ghost) drawGhost(ctx)
  // particles
  for (const p of particles) { ctx.fillStyle = p.color; ctx.globalAlpha = Math.max(0, p.life); ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill() }
  ctx.globalAlpha = 1
  // bullets
  for (const b of bullets) {
    ctx.fillStyle = b.color
    if (b.from === 'p') { ctx.fillRect(b.x - 1.5, b.y - 8, 3, 14); ctx.shadowColor = b.color; ctx.shadowBlur = 8; ctx.fillRect(b.x - 1, b.y - 6, 2, 10); ctx.shadowBlur = 0 }
    else { ctx.beginPath(); ctx.arc(b.x, b.y, b.r, 0, Math.PI * 2); ctx.fill() }
  }
  // enemies
  for (const e of enemies) drawEnemy(ctx, e)
  // power-ups
  for (const p of powers) drawPower(ctx, p)
  // ship
  drawShip(ctx, pos.x, pos.y)
  // charge ring
  if (chargeT > 0 && chargeT < 0.6) { ctx.strokeStyle = '#fde047'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(pos.x, pos.y, 16 + chargeT * 30, 0, Math.PI * 2); ctx.stroke() }
  if (chargeReady) { ctx.strokeStyle = '#fde047'; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(pos.x, pos.y, 24, 0, Math.PI * 2); ctx.stroke() }
}

function drawShip(ctx: CanvasRenderingContext2D, x: number, y: number) {
  const h = hullDef.value
  ctx.save(); ctx.translate(x, y)
  // exhaust
  if (!cfg.reducedMotion) {
    const eg = ctx.createLinearGradient(0, 0, 0, 30)
    eg.addColorStop(0, '#fcd34d'); eg.addColorStop(1, '#00000000')
    ctx.fillStyle = eg; ctx.fillRect(-4, 8, 8, 22 + Math.sin(frameNo * 0.3) * 6)
  }
  // body
  ctx.fillStyle = h.color
  ctx.beginPath(); ctx.moveTo(0, -16); ctx.lineTo(12, 12); ctx.lineTo(-12, 12); ctx.closePath(); ctx.fill()
  ctx.strokeStyle = cfg.highContrast ? '#fff' : '#0008'; ctx.lineWidth = 1.5; ctx.stroke()
  // canopy
  ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(0, -2, 4, 0, Math.PI * 2); ctx.fill()
  if (shieldT.value > 0) { ctx.strokeStyle = '#38bdf8'; ctx.globalAlpha = 0.7; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, 22, 0, Math.PI * 2); ctx.stroke(); ctx.globalAlpha = 1 }
  if (ifr.value > 0 && Math.floor(frameNo / 4) % 2 === 0) { ctx.globalAlpha = 0.3; ctx.fillRect(-12, -16, 24, 28); ctx.globalAlpha = 1 }
  ctx.restore()
}

function drawEnemy(ctx: CanvasRenderingContext2D, e: Enemy) {
  ctx.save(); ctx.translate(e.x, e.y)
  const colors: Record<EnemyKind, string> = {
    swarmer: '#ef4444', tank: '#1e3a8a', sniper: '#a78bfa', mothership: '#7c3aed',
    drone: '#22c55e', mine: '#eab308', bomber: '#fb923c', weaver: '#ec4899',
    splitter: '#0ea5e9', shielder: '#14b8a6', kamikaze: '#ef4444', turret: '#6b7280',
    elite: '#a855f7', miniboss: '#fb7185', boss: '#111827',
  }
  ctx.fillStyle = colors[e.kind]
  if (cfg.highContrast) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5 }
  if (e.kind === 'mine') {
    ctx.beginPath(); ctx.arc(0, 0, e.r, 0, Math.PI * 2); ctx.fill()
    for (let k = 0; k < 8; k++) { const a = Math.PI * 2 * k / 8; ctx.fillRect(Math.cos(a) * e.r, Math.sin(a) * e.r - 2, 4, 4) }
  } else if (e.kind === 'boss') {
    ctx.fillRect(-e.r, -e.r * 0.6, e.r * 2, e.r * 1.2)
    ctx.fillStyle = '#dc2626'; ctx.fillRect(-e.r * 0.4, -e.r * 0.6, e.r * 0.8, 6) // weak point
    if (cfg.highContrast) ctx.strokeRect(-e.r, -e.r * 0.6, e.r * 2, e.r * 1.2)
  } else if (e.kind === 'shielder') {
    ctx.beginPath(); ctx.arc(0, 0, e.r, 0, Math.PI * 2); ctx.fill()
    if ((e.shield || 0) > 0) { ctx.strokeStyle = '#34d399'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, e.r + 4, 0, Math.PI * 2); ctx.stroke() }
  } else {
    ctx.beginPath(); ctx.moveTo(0, -e.r); ctx.lineTo(e.r, e.r); ctx.lineTo(-e.r, e.r); ctx.closePath(); ctx.fill()
    if (cfg.highContrast) ctx.stroke()
  }
  // hp bar
  if (e.max > 1 && e.hp < e.max) {
    ctx.fillStyle = '#000'; ctx.fillRect(-e.r, -e.r - 8, e.r * 2, 4)
    ctx.fillStyle = '#10b981'; ctx.fillRect(-e.r, -e.r - 8, e.r * 2 * (e.hp / e.max), 4)
  }
  ctx.restore()
}

function drawPower(ctx: CanvasRenderingContext2D, p: Power) {
  const map: Record<PowerKind, [string, string]> = {
    shield: ['🛡', '#38bdf8'], slow: ['⏳', '#60a5fa'], magnet: ['🧲', '#ef4444'],
    life: ['❤', '#ec4899'], double: ['×2', '#facc15'], nuke: ['☢', '#84cc16'],
    repair: ['+', '#34d399'], overcharge: ['⚡', '#fde047'],
  }
  const [icon, color] = map[p.kind]
  ctx.save(); ctx.translate(p.x, p.y)
  ctx.fillStyle = color + 'cc'; ctx.beginPath(); ctx.arc(0, 0, 14, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#000'; ctx.font = '14px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
  ctx.fillText(icon, 0, 0)
  ctx.restore()
}

function drawGhost(ctx: CanvasRenderingContext2D) {
  const g = (store.getGhost as unknown as (id: string) => { frames?: { x: number; y: number }[] } | null)('shooter')
  if (!g || !g.frames) return
  const r = cv.value!.getBoundingClientRect()
  const i = frameNo % g.frames.length
  const f = g.frames[i]
  if (!f) return
  ctx.save(); ctx.globalAlpha = 0.35
  ctx.fillStyle = '#fbbf24'
  ctx.beginPath(); ctx.moveTo(f.x * r.width, f.y * r.height - 14); ctx.lineTo(f.x * r.width + 10, f.y * r.height + 10); ctx.lineTo(f.x * r.width - 10, f.y * r.height + 10); ctx.closePath(); ctx.fill()
  ctx.restore()
}

// ----------------------------- Run flow ---------------------------------
function startRun() {
  resetRun(); started.value = true; lastTime = 0
  if (cfg.daily) srand(parseInt(dailyCode.value, 36))
  else srand(Date.now())
  buildWave()
  speak(`Act ${act.value}, wave ${wave.value}`)
}
function restartRun() { resetRun(); started.value = true; buildWave() }
function reseed() { showAnalytics.value = false; restartRun() }
function resetRun() {
  score.value = 0; act.value = 1; wave.value = 1; comboMul.value = 1
  hp.value = maxHp.value; overdriveCharge.value = 0
  shieldT.value = 0; slowT.value = 0; magnetT.value = 0; doubleT.value = 0; ifr.value = 0
  bullets = []; enemies = []; powers = []; particles = []
  waveQueue = []; waveT = 0; waveSpawned = 0; bossActive = false
  kills.value = 0; shotsFired.value = 0; shotsHit.value = 0; runCoins.value = 0
  runBosses.value = 0; perfectWaves.value = 0
  killedBy = {}; deaths = []; replayFrames = []
  recentDeaths = 0; recentWins = 0
  dead.value = false; showAnalytics.value = false
  mode = props.mode
}

function finishRun(_won: boolean) {
  dead.value = true
  if (score.value > stats.bestScore) {
    stats.bestScore = score.value
    if (cfg.replay) store.saveGhost('shooter', { frames: replayFrames.slice() })
  }
  stats.bestWave = Math.max(stats.bestWave, act.value * 10 + wave.value)
  stats.totalKills += kills.value; stats.totalRuns++; stats.totalCoins += runCoins.value
  store.addCoins(runCoins.value + Math.floor(score.value / 200))
  save()
  setTimeout(() => { showAnalytics.value = true }, 400)
}

function buyHull(h: typeof HULLS[number]) {
  if (store.coins < h.price) return
  store.coins -= h.price
  if (!stats.unlockedHulls.includes(h.id)) stats.unlockedHulls.push(h.id)
  cfg.hull = h.id; save()
}
function buyUpgrade(u: typeof UPGRADES[number]) {
  const lv = stats.upgrades[u.id] || 0; if (lv >= 5) return
  const cost = u.cost * (lv + 1); if (store.coins < cost) return
  store.coins -= cost; stats.upgrades[u.id] = lv + 1; save()
}

function startCal() {
  calStarted.value = true; calMin.value = Infinity; calMax.value = -Infinity; calProgress.value = 0
  const c = cv.value!
  const handler = (e: PointerEvent) => {
    const r = c.getBoundingClientRect(); const x = e.clientX - r.left
    calMin.value = Math.min(calMin.value, x); calMax.value = Math.max(calMax.value, x)
    calRange.value = calMax.value - calMin.value
    calProgress.value = Math.min(100, calRange.value / r.width * 100)
    if (calProgress.value > 95) {
      cfg.dragSens = Math.max(0.3, Math.min(1, calRange.value / r.width)); save()
      c.removeEventListener('pointermove', handler)
    }
  }
  c.addEventListener('pointermove', handler)
}
function calibrateTilt() {
  const handler = (e: DeviceOrientationEvent) => {
    cfg.tiltOffset = e.gamma || 0; save(); window.removeEventListener('deviceorientation', handler)
  }
  window.addEventListener('deviceorientation', handler, { once: true })
}

function copyChallenge() {
  const code = `SH-${dailyCode.value}-${score.value}-${act.value}${wave.value}`
  try { navigator.clipboard.writeText(code) } catch (_) {}
}

let replayPlaying = false
function watchReplay() {
  if (!replayFrames.length || replayPlaying) return
  replayPlaying = true
  showAnalytics.value = false
  let i = 0
  const id = setInterval(() => {
    const r = cv.value!.getBoundingClientRect()
    const f = replayFrames[i++]
    if (!f) { clearInterval(id); replayPlaying = false; showAnalytics.value = true; return }
    pos.x = f.x * r.width; pos.y = f.y * r.height; draw()
  }, 60)
}

watch(() => props.mode, (m) => { mode = m })
</script>

<style scoped>
.sh-root { position: relative; width: 100%; height: 100%; min-height: 480px; background: #02123A; color: #fff; overflow: hidden; font-size: calc(13px * var(--font-scale, 1)); }
.cv { position: absolute; inset: 0; touch-action: none; cursor: crosshair; }
.hud { position: absolute; left: 0; right: 0; padding: 8px; display: flex; flex-wrap: wrap; gap: 6px; opacity: var(--hud-op, 0.9); pointer-events: none; }
.hud .pill { background: #0008; border: 1px solid #fff2; border-radius: 8px; padding: 4px 8px; display: flex; gap: 4px; align-items: center; pointer-events: all; font-size: 11px; }
.big .hud .pill { font-size: 13px; padding: 6px 10px; }
.hud .pill span { opacity: 0.7; }
.hud .pill b { color: var(--accent); }
.hud .pill.grow { flex: 1; min-width: 100px; }
.hp { display: inline-flex; gap: 2px; }
.hp .dot { width: 8px; height: 8px; background: #fff2; border-radius: 50%; }
.hp .dot.on { background: #34d399; }
.hud.top { top: 0; }
.hud.bot { bottom: 0; flex-direction: row; }
.hud.bot.oh { bottom: 0; flex-wrap: wrap; }
.chip { background: #0009; color: #fff; border: 1px solid #fff3; border-radius: 8px; padding: 6px 10px; cursor: pointer; font-size: 12px; pointer-events: all; }
.chip:hover { border-color: var(--accent); }
.chip.on { background: var(--accent); color: #000; }
.chip.eq { background: #34d399; color: #000; }
.chip:disabled { opacity: 0.4; cursor: not-allowed; }
.powers { position: absolute; top: 50px; right: 8px; display: flex; flex-direction: column; gap: 4px; pointer-events: none; }
.pwr { background: #000a; padding: 4px 8px; border-radius: 6px; font-size: 10px; font-weight: bold; }
.pwr.sh { color: #38bdf8; } .pwr.sl { color: #60a5fa; } .pwr.mg { color: #ef4444; } .pwr.db { color: #facc15; } .pwr.ifr { color: #fde047; }
.splash { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; align-items: center; background: #000a; cursor: pointer; }
.splash .title { font-size: 36px; font-weight: 900; letter-spacing: 4px; color: var(--accent); }
.splash .sub { font-size: 16px; color: #fff8; letter-spacing: 6px; margin-bottom: 12px; }
.splash .hint { font-size: 13px; color: #fff8; }
.splash .hint.small { font-size: 11px; opacity: 0.7; margin-top: 4px; }
.splash .hint.go { color: #fde047; margin-top: 12px; font-weight: bold; }
.sheet { position: absolute; inset: 0; background: #000c; display: flex; align-items: center; justify-content: center; z-index: 10; padding: 12px; }
.card { background: #111827; padding: 16px; border-radius: 14px; max-width: 460px; width: 100%; max-height: 86vh; overflow-y: auto; }
.card.wide { max-width: 600px; }
.card h3 { margin: 0 0 8px; color: var(--accent); }
.card h4 { margin: 12px 0 6px; color: #fbbf24; font-size: 13px; }
.grp { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 8px; }
.grp label { font-size: 12px; display: flex; gap: 4px; align-items: center; }
.row { margin-top: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.row label { flex: 1; font-size: 12px; }
.hullRow, .upRow { display: flex; gap: 8px; align-items: center; padding: 6px 0; border-bottom: 1px solid #fff1; }
.hullRow .dot { width: 22px; height: 22px; border-radius: 50%; }
.hullRow .grow, .upRow .grow { flex: 1; }
.hullRow .name, .upRow .name { font-weight: bold; font-size: 13px; }
.hullRow .meta, .upRow .meta { font-size: 11px; opacity: 0.7; }
.hullRow .desc { font-size: 11px; opacity: 0.6; }
.cal { margin: 12px 0; }
.cal .bar { background: #fff2; height: 16px; border-radius: 8px; overflow: hidden; }
.cal .bar .fill { background: var(--accent); height: 100%; transition: width 0.1s; }
.kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 8px; }
.kpi { background: #fff1; padding: 6px; border-radius: 6px; font-size: 11px; display: flex; flex-direction: column; }
.kpi span { opacity: 0.7; }
.kpi b { font-size: 16px; color: var(--accent); }
.heat { width: 100%; max-width: 240px; border-radius: 8px; }
.dangers { display: flex; flex-direction: column; gap: 4px; }
.bar2 { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.bar2 .lbl { width: 80px; text-transform: capitalize; }
.bar2 .track { flex: 1; background: #fff2; height: 8px; border-radius: 4px; overflow: hidden; }
.bar2 .track .fill { background: #ef4444; height: 100%; }
.tip { background: #fff1; padding: 8px; border-radius: 6px; font-size: 12px; }
.replay { font-size: 11px; opacity: 0.7; }

/* colorblind filters */
.cb-deutan .cv { filter: hue-rotate(20deg) saturate(0.85); }
.cb-protan .cv { filter: hue-rotate(-30deg) saturate(0.9); }
.cb-tritan .cv { filter: hue-rotate(60deg) saturate(0.85); }
.hc .cv { filter: contrast(1.3); }
</style>
