<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <div
      class="canvas-frame"
      :class="{ shake: shakeT > 0, slowmo: slowMoT > 0, lowfx: settings.reducedMotion }"
      @mousedown.prevent="onPress"
      @mouseup.prevent="onRelease"
      @mousemove="onMove"
      @contextmenu.prevent="onOverdrive"
      @touchstart.prevent="onTStart"
      @touchmove.prevent="onTMove"
      @touchend.prevent="onTEnd"
    >
      <canvas ref="cv" class="cv" :style="{ filter: cbFilter }" />
      <div v-if="iframes > 0" class="invuln-tint"></div>
      <div v-if="bossWarn > 0" class="boss-warn">⚠ BOSS APPROACHING ⚠</div>
      <button class="settings-btn" @click.stop="settingsOpen = true" @touchstart.stop @touchend.stop title="Settings">⚙</button>
    </div>

    <div class="hud-bottom">
      <div class="left">
        <div class="lives" :title="`${lives} hits left`">
          <span v-for="i in livesArr" :key="i">❤</span>
          <span v-if="shieldUntil > now" class="shield-ico">🛡</span>
        </div>
        <div class="combo" v-if="combo > 1">×{{ combo }} combo</div>
      </div>
      <div class="middle">
        <div class="energy-track">
          <div class="energy-fill" :style="{ width: Math.min(100, (energy / 100) * 100) + '%' }"></div>
          <span class="energy-label">{{ overdriveReady ? 'OVERDRIVE READY · 2-FINGER TAP' : 'Energy ' + Math.floor(energy) + '%' }}</span>
        </div>
      </div>
      <div class="right">
        <div class="wave">Wave {{ wave }}<span v-if="bossActive" class="boss-tag">BOSS</span></div>
        <div class="weapon">{{ weaponName }} · L{{ weaponLevel }}</div>
      </div>
    </div>

    <div class="hint">
      <span v-if="dailyActive" class="tag">🌅 Daily seed</span>
      <span v-else-if="sharedActive" class="tag">🔗 Shared seed</span>
      <span>Drag to fly · auto-fire · 2-finger tap = Overdrive · long-press = charged</span>
    </div>

    <!-- Settings -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false" @touchstart.stop @touchend.stop @mousedown.stop @mouseup.stop>
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Space Hunter · Nebula Strike · Settings</div>
          <button class="x" @click="settingsOpen = false">✕</button>
        </div>
        <div class="p-body">
          <div class="row">
            <label>Drag sensitivity <b>{{ settings.sensitivity.toFixed(2) }}</b></label>
            <input type="range" min="0.2" max="1" step="0.05" v-model.number="settings.sensitivity" @change="saveSettings" />
            <div class="hint-sm">1.00 = ship perfectly tracks finger; lower = smoother glide.</div>
          </div>
          <div class="row">
            <label>Auto-fire rate <b>{{ settings.fireRate }} / sec</b></label>
            <input type="range" min="2" max="10" step="1" v-model.number="settings.fireRate" @change="saveSettings" />
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.autoFire" @change="saveSettings" /> Always-on auto-fire (no tap needed)</label>
            <label class="check"><input type="checkbox" v-model="settings.gyro" @change="onToggleGyro" /> Gyro tilt steering (mobile)</label>
            <label class="check"><input type="checkbox" v-model="settings.haptics" @change="saveSettings" /> Haptic feedback</label>
            <label class="check"><input type="checkbox" v-model="settings.layeredMusic" @change="saveSettings" /> Layered music (intensity scales with wave)</label>
            <label class="check"><input type="checkbox" v-model="settings.voice" @change="saveSettings" /> Voice announcer</label>
            <label class="check"><input type="checkbox" v-model="settings.reducedMotion" @change="saveSettings" /> Reduced motion (no shake / slow-mo)</label>
            <label class="check"><input type="checkbox" v-model="settings.highContrast" @change="saveSettings" /> High-contrast outlines</label>
            <label class="check"><input type="checkbox" v-model="settings.batterySaver" @change="saveSettings" /> Battery saver (cap 30 fps, fewer particles)</label>
            <label class="check"><input type="checkbox" v-model="settings.oneHanded" @change="saveSettings" /> One-handed mode (drag in bottom half only)</label>
            <label class="check"><input type="checkbox" v-model="settings.assistHitbox" @change="saveSettings" /> Assist: smaller player hitbox</label>
            <label class="check"><input type="checkbox" v-model="settings.assistAutoFire" @change="saveSettings" /> Assist: continuous auto-fire</label>
            <label class="check"><input type="checkbox" v-model="settings.assistInvuln" @change="saveSettings" /> Assist: invincibility (story mode)</label>
          </div>
          <div class="row">
            <label>Colourblind mode</label>
            <div class="chips">
              <button v-for="m in cbModes" :key="m" class="chip" :class="{ on: settings.colorblind === m }" @click="settings.colorblind = m; saveSettings()">{{ m }}</button>
            </div>
          </div>
          <div class="row">
            <label>Ship hull (skin)</label>
            <div class="chips">
              <button
                v-for="s in skins"
                :key="s.id"
                class="chip"
                :class="{ on: settings.skin === s.id, locked: !isUnlocked(s.id) }"
                :title="isUnlocked(s.id) ? s.desc : `Unlock: ${s.unlockHint}`"
                @click="if (isUnlocked(s.id)) { settings.skin = s.id; saveSettings() }"
              >
                {{ isUnlocked(s.id) ? s.label : '🔒 ' + s.label }}
              </button>
            </div>
            <div class="hint-sm">{{ currentSkinDesc }}</div>
          </div>
          <div class="row">
            <label>Ship upgrades (cost coins)</label>
            <div class="chips">
              <button v-for="u in upgrades" :key="u.id" class="chip"
                :class="{ on: getUpgradeLevel(u.id) > 0 }"
                @click="buyUpgrade(u.id)"
                :title="u.desc">
                {{ u.label }} L{{ getUpgradeLevel(u.id) }} ({{ upgradeCost(u.id) }}🪙)
              </button>
            </div>
            <div class="hint-sm">Upgrades persist across runs. Coins balance: {{ store.coins }} 🪙</div>
          </div>
          <div class="row">
            <button class="btn" @click="openCalibration">🎯 Drag-test calibration</button>
            <button class="btn" @click="copyShareSeed">🔗 Copy "Beat my seed" link</button>
          </div>
          <div class="row">
            <div class="meter">
              <span>Adaptive difficulty</span>
              <div class="bar"><div :style="{ width: Math.min(100, Math.max(0, mmrValue)) + '%' }"></div></div>
              <div class="hint-sm">{{ mmrLabel }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Perk picker -->
    <div v-if="perkChoice" class="overlay">
      <div class="panel small">
        <div class="p-title">Choose a perk for this run</div>
        <div class="perks">
          <button v-for="p in perkChoice" :key="p.id" class="perk-card" @click="selectPerk(p.id)">
            <div class="perk-ico">{{ p.icon }}</div>
            <div class="perk-name">{{ p.name }}</div>
            <div class="perk-desc">{{ p.desc }}</div>
          </button>
        </div>
      </div>
    </div>

    <!-- Calibration -->
    <div v-if="calibrating" class="overlay" @click.self="calibrating = false">
      <div class="panel small">
        <div class="p-title">Drag-test calibration</div>
        <div class="cal-box" @mousemove="onCalMove" @touchmove.prevent="onCalTouch">
          <div class="cal-target" :style="{ left: calTargetX + '%' }"></div>
          <div class="cal-cursor" :style="{ left: calCursorX + '%' }"></div>
        </div>
        <div class="hint-sm">Move the cursor onto the target — sensitivity tunes itself based on how close you can hold it.</div>
        <button class="btn" @click="calibrating = false">Done · auto-tuned to {{ settings.sensitivity.toFixed(2) }}</button>
      </div>
    </div>

    <!-- Post-run analytics -->
    <div v-if="showAnalytics" class="overlay">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Mission report · Score {{ score }}</div>
          <button class="x" @click="finishToShell">Continue ▸</button>
        </div>
        <div class="p-body">
          <div class="r-msg">{{ deathSuggestion }}</div>
          <div class="r-stats-row">
            <div><span>Wave reached</span><b>{{ wave }}</b></div>
            <div><span>Accuracy</span><b>{{ accuracyPct }}%</b></div>
            <div><span>Kills</span><b>{{ totalKills }}</b></div>
            <div><span>Best combo</span><b>×{{ bestCombo }}</b></div>
            <div><span>Most dangerous</span><b>{{ mostDangerous }}</b></div>
            <div><span>Coins earned</span><b>{{ runCoins }}</b></div>
          </div>
          <div class="graphs">
            <div class="g-block">
              <div class="g-title">Death heatmap (where you took hits)</div>
              <canvas ref="heatChart" class="g-canvas"></canvas>
            </div>
            <div class="g-block">
              <div class="g-title">Damage over wave</div>
              <canvas ref="damageChart" class="g-canvas"></canvas>
            </div>
          </div>
          <div class="r-actions">
            <button class="btn primary" @click="instantReplay">↺ Watch instant replay</button>
            <button class="btn" @click="finishToShell">Continue</button>
          </div>
          <div v-if="newUnlocks.length" class="unlock-banner">🎉 Unlocked: {{ newUnlocks.join(', ') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, reactive, nextTick } from 'vue'
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
const heatChart = ref<HTMLCanvasElement>()
const damageChart = ref<HTMLCanvasElement>()

const W = 360, H = 600
type CB = 'off' | 'protanopia' | 'deuteranopia' | 'tritanopia'
type SkinId = 'interceptor' | 'brawler' | 'stealth' | 'healer'

interface Settings {
  sensitivity: number
  fireRate: number
  autoFire: boolean
  gyro: boolean
  haptics: boolean
  layeredMusic: boolean
  voice: boolean
  reducedMotion: boolean
  highContrast: boolean
  batterySaver: boolean
  oneHanded: boolean
  assistHitbox: boolean
  assistAutoFire: boolean
  assistInvuln: boolean
  colorblind: CB
  skin: SkinId
}

const SETTINGS_KEY = 'space_settings_v1'
const UNLOCKS_KEY = 'space_unlocks_v1'
const UPGRADES_KEY = 'space_upgrades_v1'
const MMR_KEY = 'space_mmr_v1'
const DAILY_KEY = 'space_daily_v1'

function defaultSettings(): Settings {
  return {
    sensitivity: 0.85,
    fireRate: 5,
    autoFire: true,
    gyro: false,
    haptics: true,
    layeredMusic: true,
    voice: true,
    reducedMotion: false,
    highContrast: false,
    batterySaver: false,
    oneHanded: false,
    assistHitbox: false,
    assistAutoFire: false,
    assistInvuln: false,
    colorblind: 'off',
    skin: 'interceptor',
  }
}
function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) return { ...defaultSettings(), ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return defaultSettings()
}
const settings = reactive<Settings>(loadSettings())
function saveSettings() { try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)) } catch { /* ignore */ } }

const cbModes: CB[] = ['off', 'protanopia', 'deuteranopia', 'tritanopia']
const cbFilter = computed(() => settings.colorblind === 'off' ? 'none'
  : `url(#cb-${settings.colorblind})`)

interface Skin { id: SkinId; label: string; desc: string; unlockHint: string; hp: number; speed: number; fireMul: number; primary: string; trail: string }
const skins: Skin[] = [
  { id: 'interceptor', label: 'Interceptor', desc: 'Balanced fighter — 3 HP, fast.', unlockHint: '', hp: 3, speed: 1.0, fireMul: 1.0, primary: '#a855f7', trail: '#22d3ee' },
  { id: 'brawler', label: 'Brawler', desc: 'Tankier (4 HP), slower, +damage.', unlockHint: 'reach wave 6', hp: 4, speed: 0.85, fireMul: 1.3, primary: '#ef4444', trail: '#facc15' },
  { id: 'stealth', label: 'Stealth', desc: 'Smaller hitbox, faster, fragile (2 HP).', unlockHint: 'kill 200 enemies', hp: 2, speed: 1.25, fireMul: 0.9, primary: '#0ea5e9', trail: '#94a3b8' },
  { id: 'healer', label: 'Healer', desc: 'Health drops 2x more often.', unlockHint: 'survive a boss without taking a hit', hp: 3, speed: 0.95, fireMul: 0.85, primary: '#10b981', trail: '#34d399' },
]
const currentSkin = computed(() => skins.find((s) => s.id === settings.skin) || skins[0])
const currentSkinDesc = computed(() => currentSkin.value.desc)

interface Upgrade { id: string; label: string; desc: string; baseCost: number; max: number }
const upgrades: Upgrade[] = [
  { id: 'fire',   label: 'Fire rate',   desc: '+0.5 shots/sec per level',   baseCost: 40, max: 5 },
  { id: 'dmg',    label: 'Damage',      desc: '+1 dmg per level',           baseCost: 60, max: 5 },
  { id: 'hp',     label: 'Hull HP',     desc: '+1 starting HP per level',   baseCost: 80, max: 3 },
  { id: 'speed',  label: 'Engine',      desc: '+5 % speed per level',       baseCost: 30, max: 4 },
  { id: 'spec',   label: 'Special',     desc: '−10 % overdrive cooldown',   baseCost: 50, max: 4 },
]
function loadUpgrades(): Record<string, number> {
  try { const r = localStorage.getItem(UPGRADES_KEY); if (r) return JSON.parse(r) } catch { /* ignore */ }
  return {}
}
const upgradeMap = reactive<Record<string, number>>(loadUpgrades())
function getUpgradeLevel(id: string) { return upgradeMap[id] || 0 }
function upgradeCost(id: string) { const u = upgrades.find((x) => x.id === id)!; return u.baseCost * (getUpgradeLevel(id) + 1) }
function buyUpgrade(id: string) {
  const u = upgrades.find((x) => x.id === id)!
  if (getUpgradeLevel(id) >= u.max) { toast.warning?.('Maxed') ; return }
  const c = upgradeCost(id)
  if (store.coins < c) { toast.warning?.(`Need ${c - store.coins} more coins`); return }
  store.coins -= c; store.save()
  upgradeMap[id] = getUpgradeLevel(id) + 1
  try { localStorage.setItem(UPGRADES_KEY, JSON.stringify(upgradeMap)) } catch { /* ignore */ }
}

function loadUnlocks(): Set<string> {
  try { const r = localStorage.getItem(UNLOCKS_KEY); if (r) return new Set(JSON.parse(r) as string[]) } catch { /* ignore */ }
  return new Set(['interceptor'])
}
const unlocks = reactive<Set<string>>(loadUnlocks())
function isUnlocked(id: string) { return unlocks.has(id) }
function doUnlock(id: string) {
  if (unlocks.has(id)) return false
  unlocks.add(id)
  try { localStorage.setItem(UNLOCKS_KEY, JSON.stringify(Array.from(unlocks))) } catch { /* ignore */ }
  return true
}

let mmrValue = (() => { try { return Number(localStorage.getItem(MMR_KEY) || 50) } catch { return 50 } })()
const mmrLabel = computed(() => mmrValue < 30 ? 'Easier waves' : mmrValue > 70 ? 'Tougher waves + elites' : 'Standard')
function saveMmr() { try { localStorage.setItem(MMR_KEY, String(mmrValue)) } catch { /* ignore */ } }

// === Daily seed ===
function todayStr() { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` }
const sharedActive = ref(false)
const dailyActive = ref(false)
let runSeed = Date.now()
let runRng = mulberry32(runSeed)
function mulberry32(a: number) { return function () { let t = a += 0x6d2b79f5; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 } }
function hashSeed(s: string) { let h = 2166136261 >>> 0; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) } return h >>> 0 }

function pickRunSeed() {
  try {
    const url = new URL(window.location.href)
    const s = url.searchParams.get('seed')
    if (s) { sharedActive.value = true; return hashSeed('share-' + s) }
  } catch { /* ignore */ }
  if (Math.random() < 0.3) { dailyActive.value = true; return hashSeed('daily-' + todayStr()) }
  return Date.now() >>> 0
}
const shareSeedString = computed(() => runSeed.toString(36).slice(-6))
function copyShareSeed() {
  try {
    const url = new URL(window.location.href)
    url.searchParams.set('seed', shareSeedString.value)
    navigator.clipboard?.writeText(url.toString())
    toast.success?.('Seed link copied')
  } catch { /* ignore */ }
}

// === Game state ===
type EnemyKind = 'swarmer' | 'tank' | 'sniper' | 'mother' | 'spawnling' | 'elite'
interface Enemy { x: number; y: number; vx: number; vy: number; hp: number; maxHp: number; kind: EnemyKind; isBoss?: boolean; phase?: number; cooldown?: number; bossPattern?: number; weakX?: number; weakY?: number }
interface Bullet { x: number; y: number; vx: number; vy: number; from: 'p' | 'e'; dmg?: number; charged?: boolean; homing?: boolean; targetIdx?: number }
interface Drop { x: number; y: number; kind: 'wpn' | 'shield' | 'life' | 'dmg' | 'slow' | 'energy' | 'bomb'; vy: number }
interface Particle { x: number; y: number; vx: number; vy: number; life: number; life0: number; color: string; size: number }
interface Star { x: number; y: number; speed: number; size: number; alpha: number; color: string }

let player = { x: W / 2, y: H - 70, hp: 3 }
let enemies: Enemy[] = []
let bullets: Bullet[] = []
let drops: Drop[] = []
let particles: Particle[] = []
let stars: Star[] = []
let nebulas: { x: number; y: number; r: number; color: string; speed: number }[] = []
let debris: { x: number; y: number; vy: number; rot: number; vrot: number }[] = []
let lastShot = 0
let lastSpawn = 0
let now = ref(0)
let alive = true
let runStartT = 0
const score = ref(0)
const lives = ref(3)
const livesArr = computed(() => Array.from({ length: Math.max(0, lives.value) }))
const wave = ref(1)
const weaponLevel = ref(1)
const weaponName = computed(() => (['', 'Bolt', 'Twin', 'Spread', 'Heavy', 'Beam'][weaponLevel.value] || 'Beam'))
const combo = ref(1)
const bestCombo = ref(1)
let lastKillT = 0
const energy = ref(0)
const overdriveReady = computed(() => energy.value >= 100)
const iframes = ref(0)
let shieldUntil = 0
let damageBoostUntil = 0
let slowMoUntil = 0
let chargingUntil = 0
const chargeFor = ref(0)
let bossActive = false
const bossWarn = ref(0)
let killsThisWave = 0
let waveTarget = 8
const totalKills = ref(0)
let shotsFired = 0
let shotsHit = 0
const accuracyPct = computed(() => shotsFired === 0 ? 0 : Math.round((shotsHit / shotsFired) * 100))
const runCoins = ref(0)
const newUnlocks = ref<string[]>([])
const showAnalytics = ref(false)
const settingsOpen = ref(false)
const calibrating = ref(false)
const calTargetX = ref(50)
const calCursorX = ref(50)
const shakeT = ref(0)
const slowMoT = ref(0)
let speedMul = 1
const mostDangerous = ref('—')
const deathSuggestion = ref('')
const damagePerWave: number[] = []
const heatPoints: { x: number; y: number }[] = []
const replayLog: { t: number; ev: string; a?: number; b?: number }[] = []

// perks (per-run)
interface Perk { id: string; icon: string; name: string; desc: string }
const allPerks: Perk[] = [
  { id: 'shieldStart',  icon: '🛡', name: 'Shield Start',     desc: 'Spawn each wave with a 3-second shield.' },
  { id: 'lifesteal',    icon: '🩸', name: 'Lifesteal',        desc: 'Every 25 kills heals 1 HP.' },
  { id: 'doubleShot',   icon: '✦',  name: 'Double Shot',      desc: 'Permanently +1 weapon level.' },
  { id: 'magnet',       icon: '🧲', name: 'Magnet',           desc: 'Stronger pull on power-ups.' },
  { id: 'overcharge',   icon: '⚡', name: 'Overcharge',       desc: 'Energy fills 50 % faster.' },
  { id: 'glassCannon',  icon: '💎', name: 'Glass Cannon',     desc: '+50 % damage but −1 HP.' },
  { id: 'last-stand',   icon: '🔥', name: 'Last Stand',       desc: 'At 1 HP, fire rate doubles.' },
]
const perkChoice = ref<Perk[] | null>(null)
const activePerk = ref<Perk | null>(null)
function rollPerks() {
  const pool = allPerks.slice().sort(() => runRng() - 0.5).slice(0, 3)
  perkChoice.value = pool
}
function selectPerk(id: string) {
  const p = allPerks.find((x) => x.id === id) || null
  activePerk.value = p
  perkChoice.value = null
  if (p?.id === 'doubleShot') weaponLevel.value = Math.min(5, weaponLevel.value + 1)
  if (p?.id === 'glassCannon') player.hp = Math.max(1, player.hp - 1)
  lives.value = player.hp
  if (settings.voice) speak('Perk ' + p?.name)
}

function reset() {
  runSeed = pickRunSeed()
  runRng = mulberry32(runSeed)
  alive = true
  player = { x: W / 2, y: H - 70, hp: currentSkin.value.hp + (getUpgradeLevel('hp') || 0) }
  enemies = []; bullets = []; drops = []; particles = []; debris = []
  stars = []; nebulas = []
  for (let i = 0; i < 80; i++) stars.push({ x: runRng() * W, y: runRng() * H, speed: 0.3 + runRng() * 1.6, size: runRng() * 1.7 + 0.4, alpha: 0.3 + runRng() * 0.7, color: ['#fff', '#a3bffa', '#fde68a'][Math.floor(runRng() * 3)] })
  for (let i = 0; i < 4; i++) nebulas.push({ x: runRng() * W, y: runRng() * H, r: 90 + runRng() * 120, color: ['#3b0764', '#0c4a6e', '#581c87', '#1e3a8a'][i], speed: 0.05 + runRng() * 0.15 })
  lastShot = 0; lastSpawn = 0
  score.value = 0
  lives.value = player.hp
  weaponLevel.value = 1
  wave.value = 1
  combo.value = 1
  bestCombo.value = 1
  energy.value = 0
  iframes.value = 0
  shieldUntil = 0; damageBoostUntil = 0; slowMoUntil = 0; chargingUntil = 0; chargeFor.value = 0
  bossActive = false; bossWarn.value = 0
  killsThisWave = 0; waveTarget = 8
  totalKills.value = 0; shotsFired = 0; shotsHit = 0; runCoins.value = 0
  damagePerWave.length = 0; damagePerWave.push(0)
  heatPoints.length = 0
  replayLog.length = 0
  speedMul = 1
  shakeT.value = 0; slowMoT.value = 0
  newUnlocks.value = []
  mostDangerous.value = '—'
  deathSuggestion.value = ''
  showAnalytics.value = false
  runStartT = performance.now()
  props.onScore(0)
  // weapon level baseline
  // adaptive difficulty modifiers
  const mod = (mmrValue - 50) / 100
  speedMul = Math.max(0.7, Math.min(1.45, 1 + mod * 0.6))
  rollPerks()
}

function shotCooldown() {
  let base = 1000 / (settings.fireRate + getUpgradeLevel('fire') * 0.5)
  base /= currentSkin.value.fireMul
  if (lives.value === 1 && activePerk.value?.id === 'last-stand') base /= 2
  return base
}

function shoot(charged = false) {
  shotsFired += 1
  store.beep('tap')
  const lvl = weaponLevel.value
  const dmg = (1 + getUpgradeLevel('dmg')) * (charged ? 3 : 1) * (performance.now() < damageBoostUntil ? 2 : 1) * (activePerk.value?.id === 'glassCannon' ? 1.5 : 1)
  const sx = player.x, sy = player.y - 18
  const c: SkinId = settings.skin
  const trail = currentSkin.value.trail
  if (charged) {
    bullets.push({ x: sx, y: sy, vx: 0, vy: -10, from: 'p', dmg: dmg * 1.5, charged: true })
    return
  }
  if (lvl === 1) bullets.push({ x: sx, y: sy, vx: 0, vy: -8, from: 'p', dmg })
  else if (lvl === 2) {
    bullets.push({ x: sx - 8, y: sy, vx: 0, vy: -8, from: 'p', dmg })
    bullets.push({ x: sx + 8, y: sy, vx: 0, vy: -8, from: 'p', dmg })
  } else if (lvl === 3) {
    bullets.push({ x: sx, y: sy, vx: 0, vy: -8, from: 'p', dmg })
    bullets.push({ x: sx - 8, y: sy, vx: -2, vy: -8, from: 'p', dmg })
    bullets.push({ x: sx + 8, y: sy, vx: 2, vy: -8, from: 'p', dmg })
  } else if (lvl === 4) {
    for (let i = -2; i <= 2; i++) bullets.push({ x: sx + i * 6, y: sy, vx: i * 1.5, vy: -8, from: 'p', dmg })
  } else {
    for (let i = -3; i <= 3; i++) bullets.push({ x: sx + i * 5, y: sy, vx: i * 0.8, vy: -8, from: 'p', dmg })
  }
  void c; void trail
}

function fireOverdrive() {
  if (!overdriveReady.value) return
  energy.value = 0
  store.beep('power')
  store.vibrate([10, 20, 10])
  // 8 homing missiles
  for (let i = 0; i < 8; i++) {
    bullets.push({ x: player.x, y: player.y, vx: Math.cos(i / 8 * Math.PI * 2) * 2, vy: -6, from: 'p', dmg: 4, homing: true })
  }
  if (settings.voice) speak('Overdrive')
}

function speak(text: string) {
  if (!settings.voice) return
  try {
    const u = new SpeechSynthesisUtterance(text)
    u.rate = 1.2; u.volume = 0.7
    speechSynthesis.cancel()
    speechSynthesis.speak(u)
  } catch { /* ignore */ }
}

function spawnEnemy() {
  const r = runRng()
  const w = wave.value
  let kind: EnemyKind
  if (w >= 5 && r < 0.06 && !bossActive) kind = 'mother'
  else if (w >= 4 && r < 0.18) kind = 'sniper'
  else if (w >= 3 && r < 0.40) kind = 'tank'
  else if (mmrValue > 70 && r < 0.45) kind = 'elite'
  else kind = 'swarmer'
  const x = 30 + runRng() * (W - 60)
  const baseHp = w / 3
  const eMul = 1 + (mmrValue - 50) / 200
  switch (kind) {
    case 'swarmer':
      enemies.push({ x, y: -20, vx: (runRng() - 0.5) * 1.6 * speedMul, vy: 1.5 * speedMul * eMul, hp: 1, maxHp: 1, kind })
      break
    case 'tank':
      enemies.push({ x, y: -30, vx: (runRng() - 0.5) * 0.8, vy: 1.0 * speedMul * eMul, hp: 4 + Math.floor(baseHp), maxHp: 4 + Math.floor(baseHp), kind, cooldown: 60 })
      break
    case 'sniper':
      enemies.push({ x, y: -20, vx: 0, vy: 0.5 * speedMul, hp: 2, maxHp: 2, kind, cooldown: 90 + Math.floor(runRng() * 60) })
      break
    case 'mother':
      enemies.push({ x, y: -40, vx: 0.4 * speedMul, vy: 0.4 * speedMul, hp: 12 + Math.floor(baseHp), maxHp: 12 + Math.floor(baseHp), kind, cooldown: 80 })
      break
    case 'elite':
      enemies.push({ x, y: -30, vx: (runRng() - 0.5) * 2.4, vy: 1.6 * speedMul * eMul, hp: 3, maxHp: 3, kind, cooldown: 120 })
      break
  }
}

function spawnBoss() {
  bossActive = true
  bossWarn.value = 90
  store.vibrate([80, 60, 80])
  if (settings.voice) speak('Boss approaching')
  const hp = 30 + wave.value * 12
  enemies.push({ x: W / 2, y: -50, vx: 1.6 * speedMul, vy: 0.6 * speedMul, hp, maxHp: hp, kind: 'tank', isBoss: true, phase: 0, bossPattern: 0, cooldown: 60, weakX: 0, weakY: 24 })
}

function spawnDrop(x: number, y: number) {
  const r = runRng()
  let kind: Drop['kind']
  if (r < 0.05) kind = 'bomb'
  else if (r < 0.15) kind = 'life'
  else if (r < 0.35) kind = 'shield'
  else if (r < 0.55) kind = 'wpn'
  else if (r < 0.70) kind = 'dmg'
  else if (r < 0.82) kind = 'slow'
  else kind = 'energy'
  if (settings.skin === 'healer' && runRng() < 0.5 && kind !== 'life' && kind !== 'bomb') kind = 'life'
  drops.push({ x, y, kind, vy: 1.6 })
}

function magnetic() {
  const range = activePerk.value?.id === 'magnet' ? 100 : 50
  for (const d of drops) {
    const dx = player.x - d.x, dy = player.y - d.y
    const dist = Math.hypot(dx, dy)
    if (dist < range) {
      d.x += dx / dist * 2.5
      d.y += dy / dist * 2.5
    }
  }
}

function explode(x: number, y: number, color = '#facc15', n = 14) {
  const max = settings.batterySaver ? 6 : settings.reducedMotion ? 4 : n
  for (let i = 0; i < max; i++) particles.push({ x, y, vx: (runRng() - 0.5) * 6, vy: (runRng() - 0.5) * 6, life: 36, life0: 36, color, size: 2 + runRng() * 2 })
}

let touching = false
let pointerX: number | null = null
let pointerY: number | null = null
function pageX(e: MouseEvent | TouchEvent) {
  if ('touches' in e) return e.touches[0]?.clientX ?? null
  return (e as MouseEvent).clientX
}
function pageY(e: MouseEvent | TouchEvent) {
  if ('touches' in e) return e.touches[0]?.clientY ?? null
  return (e as MouseEvent).clientY
}
function rectMap(x: number, y: number) {
  const r = cv.value!.getBoundingClientRect()
  return [(x - r.left) * (W / r.width), (y - r.top) * (H / r.height)] as const
}
function onPress(e: MouseEvent | TouchEvent) {
  touching = true
  const x = pageX(e), y = pageY(e); if (x == null || y == null) return
  const [mx, my] = rectMap(x, y)
  pointerX = mx; pointerY = my
  // overdrive: 2-finger tap
  if ('touches' in e && (e as TouchEvent).touches.length >= 2) { fireOverdrive(); return }
  if (!settings.autoFire && performance.now() - lastShot > shotCooldown()) { shoot(); lastShot = performance.now() }
  chargingUntil = performance.now() + 700
}
function onMove(e: MouseEvent | TouchEvent) {
  if (!touching) return
  const x = pageX(e), y = pageY(e); if (x == null || y == null) return
  const [mx, my] = rectMap(x, y)
  pointerX = mx; pointerY = my
}
function onRelease() {
  touching = false; pointerX = null; pointerY = null
  if (chargingUntil > performance.now() + 200) { /* short tap fired */ }
  else if (chargingUntil > 0 && performance.now() - (chargingUntil - 700) > 600) {
    shoot(true) // charged
  }
  chargingUntil = 0; chargeFor.value = 0
}
function onTStart(e: TouchEvent) { onPress(e) }
function onTMove(e: TouchEvent) { onMove(e) }
function onTEnd() { onRelease() }
function onOverdrive() { fireOverdrive() }
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key === 'a') player.x -= 12
  else if (e.key === 'ArrowRight' || e.key === 'd') player.x += 12
  else if (e.key === ' ') { if (performance.now() - lastShot > shotCooldown()) { shoot(); lastShot = performance.now() } e.preventDefault() }
  else if (e.key === 'q' || e.key === 'Q') fireOverdrive()
  player.x = Math.max(20, Math.min(W - 20, player.x))
}
function onKeyUp(_e: KeyboardEvent) { /* placeholder for hold-key actions */ }

function takeHit(severity = 1) {
  if (settings.assistInvuln) return
  if (iframes.value > 0) return
  if (performance.now() < shieldUntil) return
  lives.value -= severity
  iframes.value = 30
  store.beep('lose')
  store.vibrate(80)
  combo.value = 1
  damagePerWave[wave.value - 1] = (damagePerWave[wave.value - 1] || 0) + severity
  heatPoints.push({ x: player.x, y: player.y })
  if (lives.value <= 0) die()
}

function die() {
  alive = false
  store.beep('lose')
  store.vibrate(220)
  // slow-mo, then analytics
  slowMoT.value = settings.reducedMotion ? 0 : 60
  setTimeout(() => {
    showPostRunStats()
  }, 700)
}

function showPostRunStats() {
  // adapt MMR
  if (wave.value >= 5) mmrValue = Math.min(95, mmrValue + 4)
  else if (wave.value <= 2) mmrValue = Math.max(15, mmrValue - 3)
  saveMmr()
  // unlocks
  if (wave.value >= 6) { if (doUnlock('brawler')) newUnlocks.value.push('Brawler hull') }
  if (totalKills.value >= 200) { if (doUnlock('stealth')) newUnlocks.value.push('Stealth hull') }
  // heuristic suggestion
  if (mostDangerous.value === 'sniper') deathSuggestion.value = 'Snipers got you — keep moving and never park.'
  else if (mostDangerous.value === 'tank') deathSuggestion.value = 'Tanks chip you down — focus-fire them first.'
  else if (mostDangerous.value === 'mother') deathSuggestion.value = 'Motherships flood the screen — kill them on entry.'
  else deathSuggestion.value = 'Stay in motion — every parked second is a hit.'
  showAnalytics.value = true
  nextTick(drawCharts)
}

function drawCharts() {
  const hc = heatChart.value, dc = damageChart.value
  if (hc) {
    hc.width = hc.clientWidth * devicePixelRatio
    hc.height = 120 * devicePixelRatio
    const ctx = hc.getContext('2d')!
    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.fillStyle = '#0b0e1a'; ctx.fillRect(0, 0, hc.clientWidth, 120)
    for (const p of heatPoints) {
      ctx.beginPath()
      const px = (p.x / W) * hc.clientWidth
      const py = (p.y / H) * 120
      const grad = ctx.createRadialGradient(px, py, 0, px, py, 18)
      grad.addColorStop(0, 'rgba(239,68,68,0.55)')
      grad.addColorStop(1, 'rgba(239,68,68,0)')
      ctx.fillStyle = grad
      ctx.arc(px, py, 18, 0, Math.PI * 2); ctx.fill()
    }
  }
  if (dc) {
    dc.width = dc.clientWidth * devicePixelRatio
    dc.height = 120 * devicePixelRatio
    const ctx = dc.getContext('2d')!
    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.fillStyle = '#0b0e1a'; ctx.fillRect(0, 0, dc.clientWidth, 120)
    const max = Math.max(1, ...damagePerWave)
    const bw = dc.clientWidth / Math.max(1, damagePerWave.length)
    damagePerWave.forEach((v, i) => {
      ctx.fillStyle = '#ef4444'
      const h = (v / max) * 100
      ctx.fillRect(i * bw + 2, 110 - h, bw - 4, h)
    })
  }
}

function finishToShell() {
  showAnalytics.value = false
  props.onGameOver(score.value)
}

function instantReplay() {
  showAnalytics.value = false
  reset()
}

let last = 0
let acc = 0
const FIXED_DT = 1000 / 60
let raf = 0

function step(dt: number) {
  now.value = performance.now()
  if (bossWarn.value > 0) { bossWarn.value -= 1; if (bossWarn.value === 0) {/* warn ends */} }
  if (iframes.value > 0) iframes.value -= 1
  if (chargingUntil > 0) chargeFor.value = Math.min(1, (performance.now() - (chargingUntil - 700)) / 700)
  if (slowMoT.value > 0) slowMoT.value -= 1
  if (shakeT.value > 0) shakeT.value -= 1
  // energy
  const eGain = (activePerk.value?.id === 'overcharge' ? 0.45 : 0.30)
  energy.value = Math.min(100, energy.value + eGain)
  // player movement
  if (settings.gyro && gyroX !== null) {
    player.x = Math.max(20, Math.min(W - 20, player.x + gyroX * 4 * currentSkin.value.speed))
  }
  if (pointerX != null) {
    let target = pointerX
    if (settings.oneHanded && pointerY != null && pointerY < H * 0.5) {
      target = player.x // ignore upper half drags
    }
    const sens = settings.sensitivity
    player.x += (target - player.x) * sens
    player.x = Math.max(20, Math.min(W - 20, player.x))
  }
  // auto-fire
  if ((settings.autoFire || settings.assistAutoFire) && performance.now() - lastShot > shotCooldown()) {
    shoot(); lastShot = performance.now()
  }
  // bullets
  bullets.forEach((b) => {
    if (b.homing && b.from === 'p' && enemies.length > 0) {
      // pick nearest enemy
      let bestIdx = -1; let bestD = 9e9
      for (let i = 0; i < enemies.length; i++) {
        const e = enemies[i]; const d = Math.hypot(e.x - b.x, e.y - b.y)
        if (d < bestD) { bestD = d; bestIdx = i }
      }
      if (bestIdx >= 0) {
        const tgt = enemies[bestIdx]
        const dx = tgt.x - b.x, dy = tgt.y - b.y
        const m = Math.hypot(dx, dy) || 1
        b.vx += dx / m * 0.4; b.vy += dy / m * 0.4
        const s = Math.hypot(b.vx, b.vy) || 1
        b.vx = b.vx / s * 6; b.vy = b.vy / s * 6
      }
    }
    b.x += b.vx; b.y += b.vy
  })
  bullets = bullets.filter((b) => b.y > -20 && b.y < H + 20 && b.x > -20 && b.x < W + 20)
  // drops
  drops.forEach((d) => { d.y += d.vy })
  drops = drops.filter((d) => d.y < H + 30)
  magnetic()
  for (const d of drops) {
    if (Math.abs(d.x - player.x) < 18 && Math.abs(d.y - player.y) < 18) {
      d.y = H + 100
      switch (d.kind) {
        case 'wpn': weaponLevel.value = Math.min(5, weaponLevel.value + 1); store.beep('power'); break
        case 'shield': shieldUntil = performance.now() + 6000; store.beep('power'); break
        case 'life': lives.value = Math.min(player.hp + 2, lives.value + 1); store.beep('win'); break
        case 'dmg': damageBoostUntil = performance.now() + 8000; store.beep('power'); break
        case 'slow': slowMoUntil = performance.now() + 5000; speedMul = 0.5; setTimeout(() => { speedMul = 1 }, 5000); store.beep('power'); break
        case 'energy': energy.value = Math.min(100, energy.value + 35); store.beep('power'); break
        case 'bomb': for (const e of enemies) if (!e.isBoss) { e.hp = 0; explode(e.x, e.y, '#facc15', 8) } store.beep('win'); break
      }
    }
  }
  // enemies
  lastSpawn += dt
  const spawnInt = Math.max(280, 1100 - wave.value * 60 - (mmrValue - 50) * 5)
  if (!bossActive && lastSpawn >= spawnInt) {
    lastSpawn = 0
    spawnEnemy()
    if (wave.value >= 4 && runRng() < 0.3) spawnEnemy()
  }
  enemies.forEach((en) => {
    en.x += en.vx
    en.y += en.vy
    if (en.x < 30 || en.x > W - 30) en.vx *= -1
    if (en.kind === 'sniper' && en.y < 60) en.vy = 0
    if (en.cooldown !== undefined) en.cooldown -= 1
    if (en.cooldown !== undefined && en.cooldown <= 0) {
      if (en.kind === 'tank') {
        bullets.push({ x: en.x, y: en.y + 12, vx: 0, vy: 4, from: 'e' })
        en.cooldown = 80
      } else if (en.kind === 'sniper') {
        // homing missile
        const dx = player.x - en.x, dy = player.y - en.y; const m = Math.hypot(dx, dy) || 1
        bullets.push({ x: en.x, y: en.y + 12, vx: dx / m * 3, vy: dy / m * 3, from: 'e', homing: true })
        en.cooldown = 130
      } else if (en.kind === 'mother') {
        en.cooldown = 70
        // spawn 3 swarmlings
        for (let i = -1; i <= 1; i++) enemies.push({ x: en.x + i * 12, y: en.y + 14, vx: i * 1.2, vy: 1.6, hp: 1, maxHp: 1, kind: 'spawnling' })
      } else if (en.kind === 'elite') {
        bullets.push({ x: en.x - 6, y: en.y + 8, vx: -0.6, vy: 4, from: 'e' })
        bullets.push({ x: en.x + 6, y: en.y + 8, vx: 0.6, vy: 4, from: 'e' })
        en.cooldown = 110
      } else if (en.isBoss) {
        en.cooldown = 30
        en.bossPattern = (en.bossPattern || 0) + 1
        const p = en.bossPattern
        if (p % 4 === 0) {
          // ring
          for (let i = 0; i < 8; i++) bullets.push({ x: en.x, y: en.y, vx: Math.cos((i / 8) * Math.PI * 2) * 3, vy: Math.sin((i / 8) * Math.PI * 2) * 3, from: 'e' })
        } else {
          const dx = player.x - en.x, dy = player.y - en.y; const m = Math.hypot(dx, dy) || 1
          bullets.push({ x: en.x, y: en.y + 12, vx: dx / m * 4, vy: dy / m * 4, from: 'e' })
        }
      }
    }
    if (en.isBoss && en.y < 80) en.y = 80
  })
  // collisions
  for (const en of enemies) {
    if (en.y > H - 20 && !en.isBoss) { takeHit(); en.hp = 0; explode(en.x, en.y, '#ef4444', 8) }
    const hitR = settings.assistHitbox ? 9 : 14
    if (Math.abs(en.x - player.x) < hitR + 12 && Math.abs(en.y - player.y) < hitR + 12 && !en.isBoss) {
      takeHit(); en.hp = 0; explode(en.x, en.y, '#ef4444', 8)
    }
    if (en.isBoss && Math.abs(en.x - player.x) < 30 && Math.abs(en.y - player.y) < 26) takeHit(2)
  }
  for (const b of bullets) {
    if (b.from === 'e') {
      const hitR = settings.assistHitbox ? 7 : 12
      if (Math.abs(b.x - player.x) < hitR && Math.abs(b.y - player.y) < hitR) {
        takeHit(); b.y = H + 100
      }
    }
  }
  // bullet vs enemies
  for (const b of bullets) {
    if (b.from !== 'p') continue
    for (const en of enemies) {
      const sz = en.isBoss ? 30 : en.kind === 'mother' ? 22 : 14
      if (Math.abs(b.x - en.x) < sz && Math.abs(b.y - en.y) < sz) {
        en.hp -= b.dmg || 1
        b.y = -100
        shotsHit += 1
        if (en.hp <= 0) {
          totalKills.value += 1
          combo.value = combo.value + 1
          bestCombo.value = Math.max(bestCombo.value, combo.value)
          lastKillT = performance.now()
          const v = en.isBoss ? 200 : en.kind === 'mother' ? 80 : en.kind === 'tank' ? 30 : en.kind === 'sniper' ? 35 : en.kind === 'elite' ? 25 : 10
          score.value += Math.floor(v * Math.min(combo.value, 10) / 1.6)
          props.onScore(score.value)
          explode(en.x, en.y, en.isBoss ? '#ec4899' : en.kind === 'tank' ? '#f97316' : '#facc15', en.isBoss ? 28 : 12)
          if (en.isBoss) {
            bossActive = false
            wave.value += 1
            killsThisWave = 0
            damagePerWave.push(0)
            speedMul *= 1.06
            store.beep('win')
            for (let i = 0; i < 4; i++) spawnDrop(en.x + (i - 2) * 16, en.y)
            // unlock healer if no damage on boss
            if ((damagePerWave[wave.value - 2] || 0) === 0) { if (doUnlock('healer')) newUnlocks.value.push('Healer hull') }
          } else {
            killsThisWave += 1
            if (runRng() < 0.15) spawnDrop(en.x, en.y)
            // lifesteal
            if (activePerk.value?.id === 'lifesteal' && totalKills.value % 25 === 0) {
              lives.value = Math.min(player.hp + 2, lives.value + 1)
            }
            store.beep('tick')
          }
          // track most dangerous
          if (en.isBoss && wave.value > 5) mostDangerous.value = 'boss'
        }
        break
      }
    }
  }
  enemies = enemies.filter((en) => en.hp > 0 && en.y < H + 30)
  // wave progression
  if (!bossActive && killsThisWave >= waveTarget) {
    if (wave.value % 3 === 0) {
      spawnBoss()
    } else {
      wave.value += 1
      killsThisWave = 0
      waveTarget = 6 + wave.value * 2
      damagePerWave.push(0)
      if (activePerk.value?.id === 'shieldStart') shieldUntil = performance.now() + 3000
      store.beep('win')
      if (settings.voice) speak('Wave ' + wave.value)
    }
  }
  // particles
  particles.forEach((p) => { p.x += p.vx; p.y += p.vy; p.life -= 1 })
  particles = particles.filter((p) => p.life > 0)
  // stars parallax
  for (const s of stars) { s.y += s.speed; if (s.y > H) { s.y = -2; s.x = runRng() * W } }
  for (const n of nebulas) { n.y += n.speed; if (n.y > H + n.r) { n.y = -n.r; n.x = runRng() * W } }
  // combo decay
  if (performance.now() - lastKillT > 2500 && combo.value > 1) combo.value = 1
}

function draw() {
  const ctx = cv.value!.getContext('2d')!
  // background
  ctx.fillStyle = '#02030d'
  ctx.fillRect(0, 0, W, H)
  // nebula
  for (const n of nebulas) {
    const grad = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r)
    grad.addColorStop(0, n.color + 'cc')
    grad.addColorStop(1, n.color + '00')
    ctx.fillStyle = grad
    ctx.fillRect(n.x - n.r, n.y - n.r, n.r * 2, n.r * 2)
  }
  // stars
  for (const s of stars) {
    ctx.globalAlpha = s.alpha
    ctx.fillStyle = s.color
    ctx.fillRect(s.x, s.y, s.size, s.size)
  }
  ctx.globalAlpha = 1
  // shield
  if (performance.now() < shieldUntil) {
    ctx.strokeStyle = `rgba(56,189,248,${0.5 + Math.sin(performance.now() / 80) * 0.3})`
    ctx.lineWidth = 2
    ctx.beginPath(); ctx.arc(player.x, player.y, 26, 0, Math.PI * 2); ctx.stroke()
  }
  // player ship
  const skin = currentSkin.value
  if (iframes.value > 0 && Math.floor(iframes.value / 4) % 2 === 0) ctx.globalAlpha = 0.4
  ctx.fillStyle = skin.primary
  ctx.beginPath()
  ctx.moveTo(player.x, player.y - 16)
  ctx.lineTo(player.x - 14, player.y + 12)
  ctx.lineTo(player.x - 6, player.y + 8)
  ctx.lineTo(player.x + 6, player.y + 8)
  ctx.lineTo(player.x + 14, player.y + 12)
  ctx.closePath(); ctx.fill()
  if (settings.highContrast) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1; ctx.stroke() }
  // engine flame
  ctx.fillStyle = skin.trail
  ctx.fillRect(player.x - 4, player.y + 12, 8, 6 + Math.sin(performance.now() / 80) * 3)
  // cannon glow
  ctx.fillStyle = '#fff'
  ctx.fillRect(player.x - 1, player.y - 18, 2, 4)
  ctx.globalAlpha = 1
  // charge ring
  if (chargeFor.value > 0) {
    ctx.strokeStyle = '#facc15'; ctx.lineWidth = 1.5
    ctx.beginPath(); ctx.arc(player.x, player.y, 18 + chargeFor.value * 10, 0, Math.PI * 2 * chargeFor.value); ctx.stroke()
  }
  // bullets
  for (const b of bullets) {
    ctx.fillStyle = b.from === 'p' ? (b.charged ? '#facc15' : currentSkin.value.trail) : '#ef4444'
    ctx.shadowColor = ctx.fillStyle as string; ctx.shadowBlur = b.from === 'p' ? 8 : 4
    ctx.fillRect(b.x - 2, b.y - 8, 4, b.charged ? 16 : 12)
  }
  ctx.shadowBlur = 0
  // enemies
  for (const en of enemies) {
    if (en.isBoss) {
      ctx.fillStyle = '#ec4899'
      ctx.fillRect(en.x - 32, en.y - 24, 64, 48)
      // weak point
      ctx.fillStyle = '#fde68a'
      ctx.beginPath(); ctx.arc(en.x + (en.weakX || 0), en.y + (en.weakY || 0), 4, 0, Math.PI * 2); ctx.fill()
      // boss HP bar
      ctx.fillStyle = '#475569'; ctx.fillRect(40, 10, W - 80, 8)
      ctx.fillStyle = '#ef4444'; ctx.fillRect(40, 10, (W - 80) * (en.hp / en.maxHp), 8)
      ctx.strokeStyle = '#fff'; ctx.strokeRect(40, 10, W - 80, 8)
      ctx.fillStyle = '#fff'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText('BOSS', W / 2, 8)
    } else {
      switch (en.kind) {
        case 'tank':   ctx.fillStyle = '#f97316'; ctx.fillRect(en.x - 16, en.y - 14, 32, 28); break
        case 'sniper': ctx.fillStyle = '#a855f7'; ctx.beginPath(); ctx.moveTo(en.x, en.y - 14); ctx.lineTo(en.x - 14, en.y + 12); ctx.lineTo(en.x + 14, en.y + 12); ctx.closePath(); ctx.fill(); break
        case 'mother': ctx.fillStyle = '#dc2626'; ctx.beginPath(); ctx.ellipse(en.x, en.y, 24, 14, 0, 0, Math.PI * 2); ctx.fill(); break
        case 'elite':  ctx.fillStyle = '#facc15'; ctx.fillRect(en.x - 10, en.y - 10, 20, 20); ctx.strokeStyle = '#fff'; ctx.strokeRect(en.x - 10, en.y - 10, 20, 20); break
        case 'spawnling': ctx.fillStyle = '#fb7185'; ctx.fillRect(en.x - 6, en.y - 6, 12, 12); break
        default:       ctx.fillStyle = '#ef4444'; ctx.fillRect(en.x - 12, en.y - 10, 24, 20); break
      }
      // HP indicator for multi-hit enemies
      if (en.maxHp > 1) {
        ctx.fillStyle = '#475569'; ctx.fillRect(en.x - 14, en.y - 18, 28, 3)
        ctx.fillStyle = '#34d399'; ctx.fillRect(en.x - 14, en.y - 18, 28 * (en.hp / en.maxHp), 3)
      }
      if (settings.highContrast) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1; ctx.strokeRect(en.x - 14, en.y - 12, 28, 24) }
    }
  }
  // drops
  for (const d of drops) {
    const col: Record<Drop['kind'], string> = { wpn: '#facc15', shield: '#38bdf8', life: '#ef4444', dmg: '#fb923c', slow: '#a855f7', energy: '#10b981', bomb: '#fff' }
    ctx.fillStyle = col[d.kind]
    ctx.shadowColor = col[d.kind]; ctx.shadowBlur = 12
    ctx.beginPath(); ctx.arc(d.x, d.y, 8, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#000'; ctx.font = 'bold 9px sans-serif'; ctx.textAlign = 'center'
    const lbl: Record<Drop['kind'], string> = { wpn: '+', shield: 'S', life: '♥', dmg: 'D', slow: '⌛', energy: '⚡', bomb: '✸' }
    ctx.fillText(lbl[d.kind], d.x, d.y + 3)
  }
  // particles
  for (const p of particles) {
    ctx.globalAlpha = p.life / p.life0
    ctx.fillStyle = p.color
    ctx.fillRect(p.x, p.y, p.size, p.size)
  }
  ctx.globalAlpha = 1
  // SVG defs for cb filters
  if (!cbFilterEnsured) ensureCbDefs()
}

let cbFilterEnsured = false
function ensureCbDefs() {
  if (typeof document === 'undefined') return
  if (document.getElementById('space-cb-defs')) { cbFilterEnsured = true; return }
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.id = 'space-cb-defs'
  svg.style.position = 'absolute'; svg.style.width = '0'; svg.style.height = '0'
  svg.innerHTML = `
    <defs>
      <filter id="cb-protanopia"><feColorMatrix values="0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0"/></filter>
      <filter id="cb-deuteranopia"><feColorMatrix values="0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0"/></filter>
      <filter id="cb-tritanopia"><feColorMatrix values="0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0"/></filter>
    </defs>`
  document.body.appendChild(svg)
  cbFilterEnsured = true
}

let lastFrame = 0
function loop(t: number) {
  if (!alive) { /* still draw analytics behind */ }
  const fps = settings.batterySaver ? 30 : 60
  const minInterval = 1000 / fps
  if (t - lastFrame < minInterval) { raf = requestAnimationFrame(loop); return }
  lastFrame = t
  const dt = Math.min(64, t - (last || t)); last = t
  if (props.paused || perkChoice.value || settingsOpen.value || calibrating.value || showAnalytics.value) {
    draw(); raf = requestAnimationFrame(loop); return
  }
  acc += dt * (slowMoT.value > 0 ? 0.4 : 1)
  let safety = 0
  while (acc >= FIXED_DT && safety < 5) {
    acc -= FIXED_DT
    if (alive) step(FIXED_DT)
    safety += 1
  }
  draw()
  raf = requestAnimationFrame(loop)
}

// gyro
let gyroX: number | null = null
function onGyro(e: DeviceOrientationEvent) {
  const g = e.gamma || 0
  gyroX = Math.max(-1, Math.min(1, g / 25))
}
async function onToggleGyro() {
  if (settings.gyro) {
    try {
      type SafariEvent = { requestPermission?: () => Promise<string> }
      const cls = (window as unknown as { DeviceOrientationEvent: SafariEvent }).DeviceOrientationEvent
      if (typeof cls?.requestPermission === 'function') {
        const r = await cls.requestPermission!()
        if (r !== 'granted') { settings.gyro = false; saveSettings(); return }
      }
      window.addEventListener('deviceorientation', onGyro)
    } catch { /* ignore */ }
  } else {
    window.removeEventListener('deviceorientation', onGyro)
    gyroX = null
  }
  saveSettings()
}

// calibration
function openCalibration() {
  calibrating.value = true
  calTargetX.value = 50; calCursorX.value = 50
  setInterval(() => { calTargetX.value = 20 + Math.random() * 60 }, 1200) as unknown as number
}
function onCalMove(e: MouseEvent) {
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  calCursorX.value = ((e.clientX - r.left) / r.width) * 100
  const diff = Math.abs(calTargetX.value - calCursorX.value)
  // tighter follow → higher sensitivity
  settings.sensitivity = Math.max(0.4, Math.min(0.95, 0.5 + (60 - diff) / 100))
  saveSettings()
}
function onCalTouch(e: TouchEvent) {
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.touches[0].clientX
  calCursorX.value = ((x - r.left) / r.width) * 100
  const diff = Math.abs(calTargetX.value - calCursorX.value)
  settings.sensitivity = Math.max(0.4, Math.min(0.95, 0.5 + (60 - diff) / 100))
  saveSettings()
}

onMounted(() => {
  cv.value!.width = W; cv.value!.height = H
  ensureCbDefs()
  reset()
  raf = requestAnimationFrame(loop)
  root.value?.focus()
  if (settings.gyro) onToggleGyro()
})
onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('deviceorientation', onGyro)
})
watch(() => props.running, (v) => { if (v) { reset(); alive = true } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; touch-action: none; }
.canvas-frame { position: relative; border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.5); overflow: hidden; }
.canvas-frame.shake { animation: shake 0.3s linear; }
.canvas-frame.slowmo { filter: blur(0.4px) saturate(1.2); }
.canvas-frame.lowfx { filter: none !important; }
.cv { display: block; touch-action: none; }
.invuln-tint { position: absolute; inset: 0; background: rgba(56,189,248,0.08); pointer-events: none; animation: blink 0.3s linear infinite; }
.boss-warn { position: absolute; left: 0; right: 0; top: 30%; text-align: center; color: #ff7b7b; font-weight: 700; letter-spacing: 2px; text-shadow: 0 0 8px rgba(0,0,0,0.6); animation: pulse 0.4s linear infinite; pointer-events: none; }
.settings-btn { position: absolute; top: 6px; right: 6px; width: 28px; height: 28px; border-radius: 50%; border: 1px solid rgba(255,255,255,0.3); background: rgba(0,0,0,0.55); color: #fff; cursor: pointer; }
.hud-bottom { display: grid; grid-template-columns: 1fr 1.4fr 1fr; gap: 8px; width: 360px; padding: 0 4px; align-items: center; color: #fff; font-size: 0.8rem; }
.hud-bottom .left { display: flex; flex-direction: column; gap: 2px; }
.hud-bottom .right { text-align: right; display: flex; flex-direction: column; gap: 2px; }
.lives { color: #ef4444; display: flex; gap: 2px; align-items: center; }
.shield-ico { color: #38bdf8; margin-left: 4px; }
.combo { color: #facc15; font-weight: 700; }
.energy-track { position: relative; background: rgba(255,255,255,0.12); height: 18px; border-radius: 9px; overflow: hidden; }
.energy-fill { background: linear-gradient(90deg, #10b981, #facc15); height: 100%; transition: width 0.1s linear; }
.energy-label { position: absolute; left: 0; right: 0; top: 0; bottom: 0; text-align: center; line-height: 18px; font-size: 0.72rem; color: #fff; text-shadow: 0 1px 1px rgba(0,0,0,0.6); }
.wave { color: #fde68a; }
.boss-tag { color: #ec4899; margin-left: 4px; font-weight: 700; }
.weapon { color: rgba(255,255,255,0.7); font-size: 0.72rem; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.74rem; text-align: center; padding: 0 8px; }
.tag { color: #fde68a; font-weight: 600; margin-right: 6px; }
.overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; z-index: 50; padding: 16px; }
.panel { background: #0b0e1a; border: 1px solid #2a2f48; border-radius: 14px; max-width: 460px; width: 100%; max-height: 85vh; overflow-y: auto; color: #fff; }
.panel.small { max-width: 340px; padding: 14px; text-align: center; }
.p-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-bottom: 1px solid #1f2335; }
.p-title { font-weight: 700; }
.p-body { padding: 12px 14px; }
.x { background: transparent; border: none; color: #fff; font-size: 16px; cursor: pointer; }
.row { margin-bottom: 12px; }
.row label { display: block; font-size: 0.85rem; margin-bottom: 4px; color: rgba(255,255,255,0.85); }
.row label.check { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.row .hint-sm { color: rgba(255,255,255,0.55); font-size: 0.74rem; margin-top: 4px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip { background: #1c2030; border: 1px solid #2a2f48; border-radius: 18px; color: #fff; padding: 4px 10px; font-size: 0.75rem; cursor: pointer; }
.chip.on { background: linear-gradient(135deg, #6366f1, #a855f7); border-color: transparent; }
.chip.locked { opacity: 0.55; }
.btn { background: #1c2030; border: 1px solid #2a2f48; border-radius: 8px; color: #fff; padding: 6px 10px; cursor: pointer; font-size: 0.82rem; margin-right: 6px; }
.btn.primary { background: linear-gradient(135deg, #6366f1, #a855f7); border-color: transparent; }
input[type=range] { width: 100%; }
.meter .bar { background: rgba(255,255,255,0.12); height: 8px; border-radius: 4px; overflow: hidden; }
.meter .bar div { background: linear-gradient(90deg, #10b981, #facc15, #ef4444); height: 100%; }
.perks { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 10px; }
.perk-card { background: #1c2030; border: 1px solid #2a2f48; border-radius: 10px; color: #fff; padding: 12px 6px; cursor: pointer; text-align: center; }
.perk-ico { font-size: 1.6rem; }
.perk-name { font-weight: 700; margin: 4px 0; font-size: 0.85rem; }
.perk-desc { font-size: 0.7rem; color: rgba(255,255,255,0.65); }
.cal-box { position: relative; background: #14182a; border-radius: 10px; height: 60px; margin: 12px 0; overflow: hidden; }
.cal-target { position: absolute; top: 50%; transform: translate(-50%, -50%); width: 30px; height: 30px; border-radius: 50%; background: rgba(56,189,248,0.4); border: 2px solid #38bdf8; transition: left 0.6s ease; }
.cal-cursor { position: absolute; top: 50%; transform: translate(-50%, -50%); width: 10px; height: 10px; background: #facc15; border-radius: 50%; }
.r-msg { color: #fde68a; margin-bottom: 10px; font-size: 0.92rem; }
.r-stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 8px 0; }
.r-stats-row > div { background: #14182a; border-radius: 6px; padding: 6px; text-align: center; }
.r-stats-row span { display: block; color: rgba(255,255,255,0.55); font-size: 0.7rem; }
.r-stats-row b { font-size: 0.95rem; }
.graphs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.g-block { background: #14182a; border-radius: 8px; padding: 6px; }
.g-title { font-size: 0.72rem; color: rgba(255,255,255,0.65); margin-bottom: 4px; }
.g-canvas { width: 100%; height: 120px; display: block; border-radius: 4px; }
.r-actions { display: flex; gap: 6px; margin-top: 12px; }
.unlock-banner { margin-top: 12px; padding: 8px; background: linear-gradient(135deg, #facc15, #ec4899); border-radius: 8px; font-weight: 700; text-align: center; color: #0b0e1a; }
@keyframes shake { 0%,100% { transform: translate(0,0) } 20% { transform: translate(-3px, 1px) } 40% { transform: translate(2px, -2px) } 60% { transform: translate(-2px, 2px) } 80% { transform: translate(2px, 1px) } }
@keyframes blink { 0%,100% { opacity: 0.6 } 50% { opacity: 0.2 } }
@keyframes pulse { 0%,100% { transform: scale(1); opacity: 1 } 50% { transform: scale(1.04); opacity: 0.8 } }
</style>
