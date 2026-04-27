<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <canvas ref="cv" class="cv" :style="cbFilter"
      @pointerdown="pdown" @pointermove="pmove" @pointerup="pup" @pointercancel="pup"
    />
    <div class="topbar">
      <div class="hud-pill">{{ Math.floor(distance) }}m · {{ themeName }}</div>
      <div class="hud-pill" v-if="combo > 1">x{{ combo }} combo</div>
      <div class="hud-pill" v-if="powerActive">{{ powerActive }} {{ Math.ceil(powerLeft/60) }}s</div>
      <button class="settings" @click="settingsOpen = true" title="Settings">⚙</button>
    </div>
    <div class="hint" v-if="!started && alive">Tap / Space = jump · double-tap = double jump · ↓ / swipe down = crouch</div>

    <!-- Settings overlay -->
    <transition name="fade">
      <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
        <div class="card">
          <div class="card-h">
            <b>Dino Dash · Settings</b>
            <button class="x" @click="settingsOpen = false">×</button>
          </div>
          <div class="grp">
            <label>Mode</label>
            <div class="chips">
              <button v-for="m in modes" :key="m" class="chip" :class="{ on: gameMode === m }" @click="setMode(m)">{{ m }}</button>
            </div>
          </div>
          <div class="grp">
            <label>Theme</label>
            <div class="chips">
              <button v-for="t in themes" :key="t.id" class="chip" :class="{ on: theme === t.id }" @click="theme = t.id; persist()">{{ t.name }}</button>
            </div>
          </div>
          <div class="grp">
            <label>Skin</label>
            <div class="chips">
              <button v-for="s in skinList" :key="s.id" class="chip" :class="{ on: skin === s.id, locked: !s.unlocked }"
                :disabled="!s.unlocked" @click="pickSkin(s.id)">
                {{ s.unlocked ? s.name : '🔒 ' + s.name }}
              </button>
            </div>
          </div>
          <div class="grp two">
            <div>
              <label>Sensitivity / jump-buffer (ms): {{ jumpBuffer }}</label>
              <input type="range" min="0" max="200" step="10" v-model.number="jumpBuffer" @change="persist" />
            </div>
            <div>
              <label>Crouch swipe length (px): {{ swipeMin }}</label>
              <input type="range" min="10" max="80" step="2" v-model.number="swipeMin" @change="persist" />
            </div>
          </div>
          <div class="grp two">
            <label class="row"><input type="checkbox" v-model="haptics" @change="persist" /> Haptics</label>
            <label class="row"><input type="checkbox" v-model="reducedMotion" @change="persist" /> Reduced motion</label>
            <label class="row"><input type="checkbox" v-model="highContrast" @change="persist" /> High contrast</label>
            <label class="row"><input type="checkbox" v-model="oneHanded" @change="persist" /> One-handed mode</label>
            <label class="row"><input type="checkbox" v-model="batterySaver" @change="persist" /> Battery saver (30 fps)</label>
            <label class="row"><input type="checkbox" v-model="announcer" @change="persist" /> Voice announcer</label>
          </div>
          <div class="grp two">
            <label class="row"><input type="checkbox" v-model="assistAutoJump" @change="persist" /> Assist: auto-jump</label>
            <label class="row"><input type="checkbox" v-model="assistBigBox" @change="persist" /> Assist: forgiveness hitbox</label>
            <label class="row"><input type="checkbox" v-model="assistInvincible" @change="persist" /> Practice (invincible)</label>
          </div>
          <div class="grp">
            <label>Colourblind mode</label>
            <div class="chips">
              <button v-for="c in ['off','protanopia','deuteranopia','tritanopia']" :key="c"
                class="chip" :class="{ on: colorblind === c }" @click="colorblind = c; persist()">{{ c }}</button>
            </div>
          </div>
          <div class="grp">
            <label>Permanent upgrades (coins: {{ store.coins }})</label>
            <div class="chips">
              <button class="chip" :class="{ on: upgradeDoubleJump }" :disabled="upgradeDoubleJump || store.coins < 50"
                @click="buy('doubleJump', 50)">Double jump · 50</button>
              <button class="chip" :class="{ on: upgradeShield > 0 }" :disabled="upgradeShield >= 3 || store.coins < 30"
                @click="buy('shield', 30)">Shield Lv {{ upgradeShield }}/3 · 30</button>
              <button class="chip" :class="{ on: upgradeMagnet > 0 }" :disabled="upgradeMagnet >= 3 || store.coins < 30"
                @click="buy('magnet', 30)">Magnet Lv {{ upgradeMagnet }}/3 · 30</button>
            </div>
          </div>
          <div class="grp">
            <label>Calibration · Jump test</label>
            <button class="btn" @click="startCalibration">Run jump test (5 obstacles)</button>
            <div class="muted" v-if="calMessage">{{ calMessage }}</div>
          </div>
          <div class="grp">
            <label>Daily seed: <code>{{ todaySeed() }}</code></label>
            <button class="btn small" @click="copyShare">Copy seed link</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Post-run analytics -->
    <transition name="fade">
      <div v-if="showAnalytics" class="overlay" @click.self="showAnalytics = false">
        <div class="card analytics">
          <div class="card-h"><b>Run analysis · {{ Math.floor(distance) }} m</b><button class="x" @click="showAnalytics = false">×</button></div>
          <div class="tip">{{ runTip }}</div>
          <div class="row2">
            <div class="stat"><div class="lbl">Distance</div><div class="val">{{ Math.floor(distance) }}m</div></div>
            <div class="stat"><div class="lbl">Coins</div><div class="val">+{{ runCoins }}</div></div>
            <div class="stat"><div class="lbl">Jumps</div><div class="val">{{ stats.jumps }}</div></div>
            <div class="stat"><div class="lbl">Combo max</div><div class="val">{{ stats.maxCombo }}</div></div>
          </div>
          <div class="sec-title">Death heatmap</div>
          <div class="heat">
            <div v-for="(v, k) in deathBuckets" :key="k" class="heat-row">
              <span class="heat-lbl">{{ k }}</span>
              <span class="heat-bar" :style="{ width: ((v / Math.max(1, deathTotal)) * 100) + '%' }">{{ v }}</span>
            </div>
          </div>
          <div class="sec-title">Reaction time (obstacle spawn → jump)</div>
          <svg class="chart" viewBox="0 0 320 60" preserveAspectRatio="none">
            <polyline :points="reactionPolyline" fill="none" stroke="#60a5fa" stroke-width="1.6" />
          </svg>
          <div v-if="unlockedNow.length" class="unlocks">🎉 Unlocked: {{ unlockedNow.join(', ') }}</div>
          <div class="actions">
            <button class="btn primary" @click="instantReplay">↺ Instant replay</button>
            <button class="btn" @click="watchGhostNext = true; showAnalytics = false; reset()">👻 Ghost-race best</button>
            <button class="btn" @click="showAnalytics = false; props.onGameOver(Math.floor(distance))">Continue</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
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

// ------- persisted settings -------
const SAVE_KEY = 'dino_dash_v1'
const jumpBuffer = ref(100)
const swipeMin = ref(20)
const haptics = ref(true)
const reducedMotion = ref(false)
const highContrast = ref(false)
const oneHanded = ref(false)
const batterySaver = ref(false)
const announcer = ref(true)
const assistAutoJump = ref(false)
const assistBigBox = ref(false)
const assistInvincible = ref(false)
const colorblind = ref<'off' | 'protanopia' | 'deuteranopia' | 'tritanopia'>('off')
const skin = ref<'classic' | 'cyber' | 'bone' | 'lava' | 'ice' | 'gold'>('classic')
const theme = ref<'desert' | 'jungle' | 'volcano' | 'ice' | 'moon'>('desert')
const upgradeDoubleJump = ref(false)
const upgradeShield = ref(0)
const upgradeMagnet = ref(0)
const adaptive = reactive({ recentDeaths: [] as number[] /* distances of last 5 runs */ })
const stats = reactive({ longest: 0, totalJumps: 0, jumps: 0, maxCombo: 0 })
const unlocks = reactive({ cyber: false, bone: false, lava: false, ice: false, gold: false })

function loadSettings() {
  try {
    const j = JSON.parse(localStorage.getItem(SAVE_KEY) || '{}')
    if (j.jumpBuffer != null) jumpBuffer.value = j.jumpBuffer
    if (j.swipeMin != null) swipeMin.value = j.swipeMin
    if (j.haptics != null) haptics.value = j.haptics
    if (j.reducedMotion != null) reducedMotion.value = j.reducedMotion
    if (j.highContrast != null) highContrast.value = j.highContrast
    if (j.oneHanded != null) oneHanded.value = j.oneHanded
    if (j.batterySaver != null) batterySaver.value = j.batterySaver
    if (j.announcer != null) announcer.value = j.announcer
    if (j.assistAutoJump != null) assistAutoJump.value = j.assistAutoJump
    if (j.assistBigBox != null) assistBigBox.value = j.assistBigBox
    if (j.assistInvincible != null) assistInvincible.value = j.assistInvincible
    if (j.colorblind) colorblind.value = j.colorblind
    if (j.skin) skin.value = j.skin
    if (j.theme) theme.value = j.theme
    if (j.upgradeDoubleJump != null) upgradeDoubleJump.value = j.upgradeDoubleJump
    if (j.upgradeShield != null) upgradeShield.value = j.upgradeShield
    if (j.upgradeMagnet != null) upgradeMagnet.value = j.upgradeMagnet
    if (j.adaptive) adaptive.recentDeaths = j.adaptive.recentDeaths || []
    if (j.stats) Object.assign(stats, j.stats)
    if (j.unlocks) Object.assign(unlocks, j.unlocks)
  } catch (e) { /* ignore */ }
}
function persist() {
  try {
    localStorage.setItem(SAVE_KEY, JSON.stringify({
      jumpBuffer: jumpBuffer.value, swipeMin: swipeMin.value, haptics: haptics.value,
      reducedMotion: reducedMotion.value, highContrast: highContrast.value, oneHanded: oneHanded.value,
      batterySaver: batterySaver.value, announcer: announcer.value,
      assistAutoJump: assistAutoJump.value, assistBigBox: assistBigBox.value, assistInvincible: assistInvincible.value,
      colorblind: colorblind.value, skin: skin.value, theme: theme.value,
      upgradeDoubleJump: upgradeDoubleJump.value, upgradeShield: upgradeShield.value, upgradeMagnet: upgradeMagnet.value,
      adaptive: { recentDeaths: adaptive.recentDeaths },
      stats: { longest: stats.longest, totalJumps: stats.totalJumps },
      unlocks,
    }))
  } catch (e) { /* ignore */ }
}

const themes = [
  { id: 'desert' as const, name: 'Desert' },
  { id: 'jungle' as const, name: 'Jungle' },
  { id: 'volcano' as const, name: 'Volcano' },
  { id: 'ice' as const, name: 'Ice Age' },
  { id: 'moon' as const, name: 'Moon' },
]
const themeName = computed(() => themes.find(t => t.id === theme.value)?.name || '')
const skinList = computed(() => [
  { id: 'classic', name: 'Classic', unlocked: true },
  { id: 'cyber', name: 'Cyber', unlocked: unlocks.cyber },
  { id: 'bone', name: 'Bone', unlocked: unlocks.bone },
  { id: 'lava', name: 'Lava', unlocked: unlocks.lava },
  { id: 'ice', name: 'Ice', unlocked: unlocks.ice },
  { id: 'gold', name: 'Gold', unlocked: unlocks.gold },
])
const modes = ['classic', 'time-trial', 'boss-run', 'mission'] as const
type Mode = typeof modes[number]
const gameMode = ref<Mode>('classic')
function setMode(m: Mode) { gameMode.value = m; reset() }
watch(() => props.mode, (m) => { if (m && (modes as readonly string[]).includes(m)) gameMode.value = m as Mode })

const cbFilter = computed(() => {
  if (colorblind.value === 'off') return ''
  const map: Record<string, string> = {
    protanopia: 'url(#dd-cb-pro)', deuteranopia: 'url(#dd-cb-deu)', tritanopia: 'url(#dd-cb-tri)',
  }
  return { filter: map[colorblind.value] || '' }
})

// ------- canvas / world -------
const W = 480, H = 260
const ground = H - 40
type Obs = { x: number; y: number; w: number; h: number; type: 'cactus' | 'rock' | 'ptero' | 'log'; passed: boolean; spawnAt: number }
type Coin = { x: number; y: number; got: boolean; phase: number }
type Particle = { x: number; y: number; vx: number; vy: number; life: number; max: number; col: string; size: number }
const obstacles: Obs[] = []
const coins: Coin[] = []
const particles: Particle[] = []
const player = reactive({ y: ground, vy: 0, sliding: false, slideT: 0, jumpsUsed: 0, lastLand: 0 })
const distance = ref(0)
const runCoins = ref(0)
const combo = ref(0)
const speed = ref(5)
const baseSpeed = ref(5)
const maxSpeed = ref(13)
const alive = ref(true)
const started = ref(false)
const settingsOpen = ref(false)
const showAnalytics = ref(false)
const calibrationActive = ref(false)
const calCount = ref(0)
const calReactions = ref<number[]>([])
const calMessage = ref('')
const watchGhostNext = ref(false)
const ghostShow = ref(false)
const bestRun = ref<{ frames: { y: number; sliding: boolean }[] } | null>(null)
const ghostFrameIdx = ref(0)
const recordedFrames: { y: number; sliding: boolean }[] = []
const tapBuffer = ref(0)
const reactions = ref<number[]>([])
const deathBuckets = reactive<Record<string, number>>({ cactus: 0, ptero: 0, rock: 0, log: 0 })
const runTip = ref('')
const unlockedNow = ref<string[]>([])
const powerActive = ref<string>('')
const powerLeft = ref(0)
const dayTime = ref(0)
const nightT = computed(() => (Math.sin(dayTime.value / 500) + 1) / 2)

// Power-ups granted from coins
let shieldCharges = 0
let magnetTimer = 0
let slowMoTimer = 0
let doubleCoinTimer = 0

// ------- seeded RNG (daily challenges) -------
function todaySeed(): string {
  const d = new Date(); return `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, '0')}${String(d.getUTCDate()).padStart(2, '0')}`
}
function makeRng(seed: number) { let x = seed >>> 0; return () => { x = (x * 1664525 + 1013904223) >>> 0; return x / 4294967296 } }
let rng = makeRng(Date.now())
function readSeedFromUrl(): number | null {
  const m = location.hash.match(/dinoSeed=([\w-]+)/); return m ? Array.from(m[1]).reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0 : null
}

// ------- mode/diff base config -------
function configure() {
  const diffMul = ({ easy: 0.85, medium: 1, hard: 1.18, insane: 1.35 } as any)[props.difficulty] || 1
  baseSpeed.value = 4.4 * diffMul
  maxSpeed.value = 11.5 * diffMul
  speed.value = baseSpeed.value
  // adaptive: many short deaths → easier ramp
  const recent = adaptive.recentDeaths
  const shortDeaths = recent.filter(d => d < 500).length
  const longRuns = recent.filter(d => d > 2000).length
  if (shortDeaths >= 3) { baseSpeed.value *= 0.9; speed.value *= 0.9 }
  if (longRuns >= 3) { baseSpeed.value *= 1.05; maxSpeed.value *= 1.08 }
  // mission/time-trial seeded daily
  if (gameMode.value === 'mission') {
    const seedNum = Array.from(todaySeed()).reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0
    rng = makeRng(seedNum)
  } else if (readSeedFromUrl() != null) {
    rng = makeRng(readSeedFromUrl()!)
  } else {
    rng = makeRng(Date.now() & 0xffffffff)
  }
}

function reset() {
  configure()
  obstacles.length = 0; coins.length = 0; particles.length = 0
  player.y = ground; player.vy = 0; player.sliding = false; player.slideT = 0; player.jumpsUsed = 0
  distance.value = 0; runCoins.value = 0; combo.value = 0
  alive.value = true; started.value = false
  spawnAcc = 0; bossSpawned = false
  shieldCharges = upgradeShield.value
  magnetTimer = 0; slowMoTimer = 0; doubleCoinTimer = 0
  powerActive.value = ''; powerLeft.value = 0
  dayTime.value = 0
  reactions.value = []
  recordedFrames.length = 0
  ghostFrameIdx.value = 0
  ghostShow.value = watchGhostNext.value && bestRun.value != null
  watchGhostNext.value = false
  stats.jumps = 0; stats.maxCombo = 0
  unlockedNow.value = []
  props.onScore(0)
  if (gameMode.value === 'time-trial') timeTrialEnd = Date.now() + 60000
}
let timeTrialEnd = 0
let bossSpawned = false
let spawnAcc = 0

// ------- input handling -------
let lastTapTime = 0
function jump(now = performance.now()) {
  if (!alive.value) return
  if (!started.value) started.value = true
  const onGround = player.y >= ground - 0.5
  const canDouble = upgradeDoubleJump.value && !onGround && player.jumpsUsed < 2
  if (onGround) {
    player.vy = -11.6; player.jumpsUsed = 1
    spawnDust(80, ground)
    if (haptics.value && navigator.vibrate) navigator.vibrate(8)
    store.beep('tap')
    stats.jumps++; stats.totalJumps++
    // Reaction time
    const next = obstacles.find(o => !o.passed)
    if (next) reactions.value.push(performance.now() - next.spawnAt)
  } else if (canDouble) {
    player.vy = -10.5; player.jumpsUsed = 2
    if (haptics.value && navigator.vibrate) navigator.vibrate(14)
    store.beep('power')
    spawnBurst(80, player.y - 14, '#a78bfa')
    stats.jumps++; stats.totalJumps++
  } else {
    tapBuffer.value = now + jumpBuffer.value
  }
}
function crouch(start: boolean) {
  if (!alive.value) return
  if (start && player.y >= ground - 0.5) {
    player.sliding = true; player.slideT = 30
    if (haptics.value && navigator.vibrate) navigator.vibrate(4)
  } else if (!start) {
    player.sliding = false; player.slideT = 0
  }
}
function onKey(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'w') { e.preventDefault(); jump() }
  if (e.key === 'ArrowDown' || e.key === 's') { e.preventDefault(); crouch(true) }
  if (e.key === 'p' || e.key === 'P') { settingsOpen.value = !settingsOpen.value }
}
function onKeyUp(e: KeyboardEvent) { if (e.key === 'ArrowDown' || e.key === 's') crouch(false) }

let pStartX = 0, pStartY = 0, pStartT = 0, pId = -1, didSlide = false
function pdown(e: PointerEvent) {
  pId = e.pointerId
  pStartX = e.clientX; pStartY = e.clientY; pStartT = performance.now(); didSlide = false
  // edge protection - ignore very-close-to-edge taps
  const rect = (e.target as HTMLElement).getBoundingClientRect()
  if (e.clientX - rect.left < 8 || rect.right - e.clientX < 8) return
  // one-handed: ignore taps in upper half if enabled
  if (oneHanded.value && (e.clientY - rect.top) < rect.height / 2) return
  // double-tap detection (200ms)
  const now = performance.now()
  if (now - lastTapTime < 220 && upgradeDoubleJump.value) { jump(now); jump(now); lastTapTime = 0; return }
  lastTapTime = now
}
function pmove(e: PointerEvent) {
  if (e.pointerId !== pId) return
  const dy = e.clientY - pStartY
  if (dy > swipeMin.value && !didSlide) { crouch(true); didSlide = true }
  if (dy < -swipeMin.value) { jump(); didSlide = true; pId = -1 }
}
function pup(e: PointerEvent) {
  if (e.pointerId !== pId) return
  const dy = e.clientY - pStartY
  const dx = e.clientX - pStartX
  if (didSlide) { crouch(false); pId = -1; return }
  if (Math.abs(dx) < swipeMin.value && Math.abs(dy) < swipeMin.value) jump()
  pId = -1
}

// ------- particle helpers -------
function spawnDust(x: number, y: number) {
  if (reducedMotion.value) return
  for (let i = 0; i < 5; i++) {
    particles.push({ x, y, vx: (rng() - 0.5) * 1.5, vy: -rng() * 1.2, life: 24, max: 24, col: '#cbd5e1', size: 2 })
  }
}
function spawnBurst(x: number, y: number, col = '#fde68a') {
  if (reducedMotion.value) return
  for (let i = 0; i < 12; i++) {
    const a = rng() * Math.PI * 2, v = 1 + rng() * 2
    particles.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 30, max: 30, col, size: 2 })
  }
}

// ------- power-ups & coins logic -------
function maybeGivePower() {
  // every ~250m chance to give a power
  if (rng() < 0.012) {
    const choices: string[] = []
    if (upgradeShield.value > 0) choices.push('shield')
    if (upgradeMagnet.value > 0) choices.push('magnet')
    choices.push('slowmo', 'double')
    const pick = choices[Math.floor(rng() * choices.length)]
    if (pick === 'shield') { shieldCharges = Math.min(shieldCharges + 1, 1 + upgradeShield.value); powerActive.value = 'Shield'; powerLeft.value = 60 * 6 }
    if (pick === 'magnet') { magnetTimer = 60 * 8; powerActive.value = 'Magnet'; powerLeft.value = magnetTimer }
    if (pick === 'slowmo') { slowMoTimer = 60 * 2; powerActive.value = 'Slow-mo'; powerLeft.value = slowMoTimer }
    if (pick === 'double') { doubleCoinTimer = 60 * 8; powerActive.value = '2x Coins'; powerLeft.value = doubleCoinTimer }
    say(pick === 'shield' ? 'Shielded' : pick === 'magnet' ? 'Magnet on' : pick === 'slowmo' ? 'Slow motion' : 'Double coins')
  }
}
function buy(kind: 'doubleJump' | 'shield' | 'magnet', cost: number) {
  if (store.coins < cost) return
  store.coins -= cost
  if (kind === 'doubleJump') upgradeDoubleJump.value = true
  if (kind === 'shield') upgradeShield.value = Math.min(3, upgradeShield.value + 1)
  if (kind === 'magnet') upgradeMagnet.value = Math.min(3, upgradeMagnet.value + 1)
  persist(); store.beep('win')
}

// ------- speech announcer -------
function say(text: string) {
  if (!announcer.value) return
  try {
    const u = new SpeechSynthesisUtterance(text); u.rate = 1.1; u.volume = 0.6; speechSynthesis.cancel(); speechSynthesis.speak(u)
  } catch { /* ignore */ }
}

// ------- calibration -------
function pickSkin(id: typeof skin.value) {
  const found = skinList.value.find(s => s.id === id)
  if (found && found.unlocked) { skin.value = id; persist() }
}

function startCalibration() {
  calibrationActive.value = true; calCount.value = 0; calReactions.value = []
  calMessage.value = 'Calibration: jump 5 obstacles…'
  settingsOpen.value = false
  reset()
}
function finishCalibration() {
  calibrationActive.value = false
  if (calReactions.value.length) {
    const avg = calReactions.value.reduce((a, b) => a + b, 0) / calReactions.value.length
    jumpBuffer.value = Math.max(40, Math.min(180, Math.round(avg * 0.6)))
    calMessage.value = `Avg reaction ${Math.round(avg)}ms → buffer set to ${jumpBuffer.value}ms`
    persist()
  } else {
    calMessage.value = 'Calibration aborted.'
  }
}

// ------- main step -------
function step() {
  if (!alive.value || !started.value) {
    // pre-start idle: just bob the player
    player.y = ground - Math.abs(Math.sin(performance.now() / 250)) * 4
    return
  }
  if (props.paused || settingsOpen.value || showAnalytics.value) return
  const slowFactor = slowMoTimer > 0 ? 0.5 : 1
  if (slowMoTimer > 0) slowMoTimer--
  if (magnetTimer > 0) magnetTimer--
  if (doubleCoinTimer > 0) doubleCoinTimer--
  if (powerLeft.value > 0) powerLeft.value--
  if (powerLeft.value <= 0) powerActive.value = ''

  // physics
  player.vy += 0.62 * slowFactor
  player.y += player.vy * slowFactor
  if (player.y >= ground) {
    if (player.vy > 4) spawnDust(80, ground)
    player.y = ground; player.vy = 0; player.jumpsUsed = 0
    // consume buffered jump
    if (tapBuffer.value > performance.now()) { jump(); tapBuffer.value = 0 }
  }
  if (player.sliding) {
    player.slideT--; if (player.slideT <= 0) player.sliding = false
  }
  // assist auto-jump
  if (assistAutoJump.value) {
    const next = obstacles.find(o => o.x > 80 && o.x < 80 + 80 && !o.passed)
    if (next && next.type !== 'ptero' && player.y >= ground - 0.5) jump()
  }

  // speed curve
  const maxFor = gameMode.value === 'boss-run' ? maxSpeed.value * 1.05 : maxSpeed.value
  speed.value += (maxFor - speed.value) * 0.0008
  distance.value += (speed.value * slowFactor) / 7
  dayTime.value += speed.value * slowFactor

  // spawn
  spawnAcc += speed.value * slowFactor
  const gap = Math.max(110, 180 - speed.value * 4)
  if (spawnAcc > gap + rng() * 90) {
    spawnAcc = 0
    if (gameMode.value === 'boss-run' && distance.value > 0 && Math.floor(distance.value / 2000) > (bossSpawned ? 0 : -1) && !bossSpawned && (distance.value % 2000) < 50) {
      // Boss: giant pterodactyl spawned in a wave
      bossSpawned = true
      say('Boss incoming')
      for (let i = 0; i < 5; i++) obstacles.push({ x: W + i * 90, y: ground - 50 - (i % 2) * 30, w: 32, h: 24, type: 'ptero', passed: false, spawnAt: performance.now() })
    } else {
      const r = rng()
      if (r < 0.35) obstacles.push({ x: W, y: ground - 26, w: 18, h: 26, type: 'cactus', passed: false, spawnAt: performance.now() })
      else if (r < 0.6) obstacles.push({ x: W, y: ground - 14, w: 22, h: 14, type: 'rock', passed: false, spawnAt: performance.now() })
      else if (r < 0.85) {
        const high = rng() < 0.5
        obstacles.push({ x: W, y: high ? ground - 70 : ground - 40, w: 28, h: 18, type: 'ptero', passed: false, spawnAt: performance.now() })
      } else if (theme.value === 'jungle') {
        obstacles.push({ x: W, y: ground - 40, w: 22, h: 40, type: 'log', passed: false, spawnAt: performance.now() })
      } else {
        obstacles.push({ x: W, y: ground - 22, w: 18, h: 22, type: 'cactus', passed: false, spawnAt: performance.now() })
      }
      if (rng() < 0.5) coins.push({ x: W + 60 + rng() * 40, y: ground - 60 - rng() * 30, got: false, phase: rng() * 6 })
    }
    maybeGivePower()
  }

  // move
  for (const o of obstacles) o.x -= speed.value * slowFactor
  for (const c of coins) {
    c.x -= speed.value * slowFactor; c.phase += 0.15
    if (magnetTimer > 0) {
      const dx = 80 - c.x, dy = (player.y - 16) - c.y
      const d = Math.hypot(dx, dy)
      const range = 60 + upgradeMagnet.value * 25
      if (d < range) { c.x += (dx / d) * 3; c.y += (dy / d) * 3 }
    }
  }
  for (const p of particles) { p.x += p.vx; p.y += p.vy; p.vy += 0.05; p.life-- }

  // collision
  const px = 80, pwid = 26
  const hitForgive = assistBigBox.value ? -4 : 0
  let pYTop = player.y - 30 - hitForgive, pYBot = player.y + hitForgive
  if (player.sliding) pYTop = player.y - 14
  for (const o of obstacles) {
    const oTop = o.y, oBot = o.y + o.h
    const inX = o.x < px + pwid + hitForgive && o.x + o.w > px - hitForgive
    if (inX && oBot > pYTop && oTop < pYBot) {
      if (assistInvincible.value) { o.x = -100; continue }
      if (shieldCharges > 0) {
        shieldCharges--; o.x = -100; spawnBurst(80, player.y - 14, '#60a5fa'); say('Shielded')
        if (haptics.value && navigator.vibrate) navigator.vibrate(20)
        if (shieldCharges <= 0 && powerActive.value === 'Shield') powerActive.value = ''
        continue
      }
      die(o.type)
      return
    }
    if (!o.passed && o.x + o.w < px) {
      o.passed = true
      combo.value++
      stats.maxCombo = Math.max(stats.maxCombo, combo.value)
      props.onScore(Math.floor(distance.value) + runCoins.value * 5)
      store.beep('tick')
      if (calibrationActive.value) {
        calCount.value++
        if (calCount.value >= 5) finishCalibration()
      }
      // near-miss detection
      const passClear = Math.abs(player.y - o.y - o.h / 2)
      if (passClear < 12) say('Perfect')
      else if (passClear < 22) {/* near miss flash */ flashGlow.value = 18 }
    }
  }
  for (const c of coins) {
    if (c.got) continue
    if (Math.abs(c.x - 80) < 14 && Math.abs(c.y - (player.y - 16)) < 22) {
      c.got = true
      const inc = (1 + (doubleCoinTimer > 0 ? 1 : 0))
      runCoins.value += inc
      spawnBurst(c.x, c.y, '#facc15')
      store.beep('tick')
      if (haptics.value && navigator.vibrate) navigator.vibrate(6)
    }
  }
  // milestone
  if (Math.floor(distance.value) > 0 && Math.floor(distance.value) % 1000 === 0 && lastMilestone !== Math.floor(distance.value)) {
    lastMilestone = Math.floor(distance.value)
    say('Good')
    if (haptics.value && navigator.vibrate) navigator.vibrate([8, 30, 8])
  }
  // record frames for ghost
  recordedFrames.push({ y: player.y, sliding: player.sliding })
  ghostFrameIdx.value++
  // cleanup
  for (let i = obstacles.length - 1; i >= 0; i--) if (obstacles[i].x < -50) obstacles.splice(i, 1)
  for (let i = coins.length - 1; i >= 0; i--) if (coins[i].x < -50 || coins[i].got) coins.splice(i, 1)
  for (let i = particles.length - 1; i >= 0; i--) if (particles[i].life <= 0) particles.splice(i, 1)
  if (flashGlow.value > 0) flashGlow.value--

  // time-trial end
  if (gameMode.value === 'time-trial' && Date.now() > timeTrialEnd) die('rock')
}
let lastMilestone = -1
const flashGlow = ref(0)

function die(kind: 'cactus' | 'ptero' | 'rock' | 'log') {
  if (!alive.value) return
  alive.value = false
  deathBuckets[kind]++
  if (haptics.value && navigator.vibrate) navigator.vibrate(80)
  store.beep('lose')
  spawnBurst(80, player.y - 14, '#ef4444')
  // adaptive book-keeping
  adaptive.recentDeaths.push(distance.value)
  if (adaptive.recentDeaths.length > 5) adaptive.recentDeaths.shift()
  // unlocks
  const d = distance.value
  if (!unlocks.cyber && d >= 500) { unlocks.cyber = true; unlockedNow.value.push('Cyber skin') }
  if (!unlocks.bone && d >= 1000) { unlocks.bone = true; unlockedNow.value.push('Bone skin') }
  if (!unlocks.lava && d >= 1500) { unlocks.lava = true; unlockedNow.value.push('Lava skin') }
  if (!unlocks.ice && stats.maxCombo >= 25) { unlocks.ice = true; unlockedNow.value.push('Ice skin') }
  if (!unlocks.gold && stats.totalJumps >= 500) { unlocks.gold = true; unlockedNow.value.push('Gold skin') }
  if (d > stats.longest) { stats.longest = d; say('New record') }
  // award coins to global wallet
  store.addCoins(runCoins.value)
  // tip generator
  const total = Object.values(deathBuckets).reduce((a, b) => a + b, 0)
  const top = Object.entries(deathBuckets).sort((a, b) => b[1] - a[1])[0]
  if (kind === 'ptero') runTip.value = 'You hit a high pterodactyl — try crouching instead of jumping over.'
  else if (kind === 'cactus') runTip.value = 'You jumped too late — try hitting jump ~0.1s earlier or shorten your buffer.'
  else if (kind === 'log') runTip.value = 'Logs need a tall jump — full press, not a tap.'
  else runTip.value = `Most of your deaths come from ${top?.[0] || 'obstacles'}. Slow down and look ahead.`
  if (recordedFrames.length > 60 && d > stats.longest * 0.7) bestRun.value = { frames: recordedFrames.slice() }
  persist()
  setTimeout(() => { showAnalytics.value = true }, reducedMotion.value ? 200 : 600)
}

function instantReplay() {
  showAnalytics.value = false
  // brief flash
  reset()
}
function copyShare() {
  const url = `${location.origin}${location.pathname}#dinoSeed=${todaySeed()}`
  try { navigator.clipboard.writeText(url); calMessage.value = 'Seed link copied!' } catch { calMessage.value = url }
}

// ------- per-run reaction polyline for analytics -------
const reactionPolyline = computed(() => {
  const arr = reactions.value.slice(-32)
  if (!arr.length) return ''
  const max = Math.max(...arr, 1)
  return arr.map((v, i) => `${(i / (arr.length - 1 || 1)) * 320},${60 - (v / max) * 56}`).join(' ')
})
const deathTotal = computed(() => Object.values(deathBuckets).reduce((a, b) => a + b, 0))

// ------- rendering -------
function themeBg(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const t = nightT.value
  const interp = (a: [number, number, number], b: [number, number, number]) =>
    `rgb(${Math.round(a[0] + (b[0] - a[0]) * t)},${Math.round(a[1] + (b[1] - a[1]) * t)},${Math.round(a[2] + (b[2] - a[2]) * t)})`
  let dayTop: [number, number, number], dayBot: [number, number, number], nightTop: [number, number, number], nightBot: [number, number, number], gnd: string
  switch (theme.value) {
    case 'jungle':  dayTop = [134, 239, 172]; dayBot = [16, 94, 56]; nightTop = [9, 30, 32]; nightBot = [4, 18, 24]; gnd = '#14532d'; break
    case 'volcano': dayTop = [251, 146, 60]; dayBot = [127, 29, 29]; nightTop = [38, 5, 5]; nightBot = [12, 0, 0]; gnd = '#7f1d1d'; break
    case 'ice':     dayTop = [186, 230, 253]; dayBot = [100, 116, 139]; nightTop = [12, 35, 60]; nightBot = [4, 12, 30]; gnd = '#cbd5e1'; break
    case 'moon':    dayTop = [148, 163, 184]; dayBot = [30, 41, 59]; nightTop = [10, 12, 24]; nightBot = [0, 0, 8]; gnd = '#475569'; break
    default:        dayTop = [253, 230, 138]; dayBot = [251, 146, 60]; nightTop = [12, 18, 50]; nightBot = [3, 5, 18]; gnd = '#92400e'
  }
  const top = interp(dayTop, nightTop), bot = interp(dayBot, nightBot)
  const g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, top); g.addColorStop(1, bot)
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
  // far parallax - mountains/clouds
  ctx.fillStyle = highContrast.value ? '#fff' : 'rgba(255,255,255,0.3)'
  for (let i = 0; i < 5; i++) {
    const cx = ((i * 90 - distance.value * 0.4) % (w + 60) + w + 60) % (w + 60) - 30
    ctx.beginPath(); ctx.arc(cx, 50 + (i % 2) * 12, 14, 0, Math.PI * 2); ctx.fill()
  }
  // ground
  ctx.fillStyle = gnd; ctx.fillRect(0, ground, w, h - ground)
  // ground details
  ctx.fillStyle = 'rgba(0,0,0,0.18)'
  for (let x = -((distance.value * 4) % 22); x < w; x += 22) ctx.fillRect(x, ground + 8, 12, 3)
  // moon stars when night
  if (t > 0.6) {
    ctx.fillStyle = 'rgba(255,255,255,0.6)'
    for (let i = 0; i < 22; i++) {
      const sx = ((i * 53 + distance.value * 0.2) % w)
      const sy = (i * 17) % (ground - 20)
      ctx.fillRect(sx, sy, 1.2, 1.2)
    }
  }
}

function drawObstacle(ctx: CanvasRenderingContext2D, o: Obs) {
  const fillFor = (def: string) => highContrast.value ? '#fff' : def
  if (o.type === 'cactus') {
    ctx.fillStyle = fillFor(theme.value === 'volcano' ? '#dc2626' : theme.value === 'ice' ? '#7dd3fc' : '#16a34a')
    ctx.fillRect(o.x, o.y, o.w, o.h)
    ctx.fillRect(o.x - 4, o.y + 4, 4, 10); ctx.fillRect(o.x + o.w, o.y + 8, 4, 10)
  } else if (o.type === 'rock') {
    ctx.fillStyle = fillFor('#9ca3af'); ctx.beginPath()
    ctx.ellipse(o.x + o.w / 2, o.y + o.h / 2, o.w / 2, o.h / 2, 0, 0, Math.PI * 2); ctx.fill()
  } else if (o.type === 'ptero') {
    ctx.fillStyle = fillFor('#a855f7')
    const wing = (Math.sin(distance.value / 5 + o.x) + 1) * 6
    ctx.fillRect(o.x, o.y, o.w, o.h)
    ctx.beginPath(); ctx.moveTo(o.x, o.y + o.h / 2); ctx.lineTo(o.x - 10, o.y + o.h / 2 - wing); ctx.lineTo(o.x, o.y + o.h / 2 - 2); ctx.fill()
    ctx.beginPath(); ctx.moveTo(o.x + o.w, o.y + o.h / 2); ctx.lineTo(o.x + o.w + 10, o.y + o.h / 2 - wing); ctx.lineTo(o.x + o.w, o.y + o.h / 2 - 2); ctx.fill()
    // shadow on ground
    ctx.fillStyle = 'rgba(0,0,0,0.25)'
    ctx.fillRect(o.x - 2, ground - 2, o.w + 4, 2)
  } else { // log
    ctx.fillStyle = fillFor('#92400e'); ctx.fillRect(o.x, o.y, o.w, o.h)
    ctx.fillStyle = 'rgba(0,0,0,0.3)'; ctx.fillRect(o.x + 4, o.y + 4, 4, o.h - 8)
  }
}

function skinColors() {
  switch (skin.value) {
    case 'cyber': return { body: '#22d3ee', accent: '#a78bfa', eye: '#fff' }
    case 'bone': return { body: '#e5e7eb', accent: '#f3f4f6', eye: '#000' }
    case 'lava': return { body: '#f97316', accent: '#facc15', eye: '#fff' }
    case 'ice': return { body: '#bae6fd', accent: '#7dd3fc', eye: '#000' }
    case 'gold': return { body: '#facc15', accent: '#fde68a', eye: '#000' }
    default: return { body: '#facc15', accent: '#f59e0b', eye: '#000' }
  }
}

function drawDino(ctx: CanvasRenderingContext2D, x: number, y: number, sliding: boolean) {
  const c = skinColors()
  ctx.fillStyle = c.body
  if (sliding) {
    ctx.fillRect(x - 14, y - 14, 28, 14)
    ctx.fillStyle = c.accent; ctx.fillRect(x + 4, y - 14, 10, 4)
    ctx.fillStyle = c.eye; ctx.fillRect(x + 8, y - 11, 3, 3)
  } else {
    // body
    ctx.fillRect(x - 12, y - 30, 24, 30)
    // tail
    ctx.fillRect(x - 18, y - 24, 6, 8)
    // head
    ctx.fillStyle = c.accent
    ctx.fillRect(x + 6, y - 36, 12, 12)
    ctx.fillStyle = c.eye; ctx.fillRect(x + 13, y - 33, 3, 3)
    // legs (animation)
    const leg = (Math.floor(performance.now() / 90) % 2) === 0
    ctx.fillStyle = c.body
    ctx.fillRect(x - 8, y - 6, 5, 6 + (leg ? 0 : 2))
    ctx.fillRect(x + 3, y - 6, 5, 6 + (leg ? 2 : 0))
  }
  // shield ring
  if (shieldCharges > 0) {
    ctx.strokeStyle = 'rgba(96,165,250,0.7)'; ctx.lineWidth = 2
    ctx.beginPath(); ctx.arc(x, y - 16, 22, 0, Math.PI * 2); ctx.stroke()
  }
}

function drawHud(ctx: CanvasRenderingContext2D) {
  ctx.fillStyle = '#fff'; ctx.font = 'bold 14px sans-serif'
  ctx.fillText(`${Math.floor(distance.value)}m`, 10, 20)
  ctx.fillStyle = '#facc15'
  ctx.fillText(`◎ ${runCoins.value}`, 70, 20)
  if (combo.value > 1) { ctx.fillStyle = '#22d3ee'; ctx.fillText(`x${combo.value}`, 130, 20) }
  if (gameMode.value === 'time-trial') {
    const remain = Math.max(0, Math.ceil((timeTrialEnd - Date.now()) / 1000))
    ctx.fillStyle = '#fb7185'; ctx.fillText(`${remain}s`, W - 50, 20)
  }
  // speed lines
  if (!reducedMotion.value && speed.value / maxSpeed.value > 0.8) {
    ctx.strokeStyle = 'rgba(255,255,255,0.3)'; ctx.lineWidth = 1
    for (let i = 0; i < 6; i++) {
      const ly = 60 + i * 30; const off = (distance.value * 6 + i * 17) % 80
      ctx.beginPath(); ctx.moveTo(W - off, ly); ctx.lineTo(W - off - 30, ly); ctx.stroke()
    }
  }
  if (flashGlow.value > 0) {
    ctx.strokeStyle = `rgba(96,165,250,${flashGlow.value / 18})`; ctx.lineWidth = 6
    ctx.strokeRect(0, 0, W, H)
  }
  if (calibrationActive.value) {
    ctx.fillStyle = 'rgba(0,0,0,0.55)'; ctx.fillRect(W / 2 - 80, H - 36, 160, 22)
    ctx.fillStyle = '#fff'; ctx.fillText(`Calibration ${calCount.value}/5`, W / 2 - 60, H - 20)
  }
}

function draw() {
  const ctx = cv.value!.getContext('2d')!
  themeBg(ctx, W, H)
  // ghost
  if (ghostShow.value && bestRun.value) {
    const f = bestRun.value.frames[Math.min(bestRun.value.frames.length - 1, ghostFrameIdx.value)]
    if (f) { ctx.globalAlpha = 0.35; drawDino(ctx, 80, f.y, f.sliding); ctx.globalAlpha = 1 }
  }
  // coins
  for (const c of coins) {
    if (c.got) continue
    ctx.fillStyle = '#fbbf24'; ctx.beginPath(); ctx.arc(c.x, c.y, 6 + Math.sin(c.phase) * 1.2, 0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 1; ctx.stroke()
  }
  for (const o of obstacles) drawObstacle(ctx, o)
  // particles
  for (const p of particles) {
    ctx.fillStyle = p.col; ctx.globalAlpha = Math.max(0, p.life / p.max)
    ctx.fillRect(p.x, p.y, p.size, p.size); ctx.globalAlpha = 1
  }
  drawDino(ctx, 80, player.y, player.sliding)
  drawHud(ctx)
}

// ------- main loop with battery-saver fps cap -------
let raf = 0; let last = 0; let acc = 0
const FIXED_DT = 1000 / 60
function loop(now: number) {
  raf = requestAnimationFrame(loop)
  const dt = Math.min(64, now - (last || now)); last = now
  acc += dt
  let safety = 0
  while (acc >= FIXED_DT && safety < 5) { acc -= FIXED_DT; step(); safety++ }
  if (batterySaver.value) {
    if (now - lastDraw < 33) return
    lastDraw = now
  }
  draw()
}
let lastDraw = 0

onMounted(() => {
  loadSettings()
  cv.value!.width = W; cv.value!.height = H
  // inject SVG cb filters once
  if (!document.getElementById('dd-cb-filters')) {
    const div = document.createElement('div')
    div.id = 'dd-cb-filters'
    div.style.cssText = 'position:absolute;width:0;height:0;pointer-events:none'
    div.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg"><filter id="dd-cb-pro"><feColorMatrix values="0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0"/></filter>
      <filter id="dd-cb-deu"><feColorMatrix values="0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0"/></filter>
      <filter id="dd-cb-tri"><feColorMatrix values="0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0"/></filter></svg>`
    document.body.appendChild(div)
  }
  reset(); raf = requestAnimationFrame(loop); root.value?.focus()
})
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) reset() })
watch(() => props.difficulty, () => reset())
</script>

<style scoped>
.wrap { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; width: 100%; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; touch-action: none; background: #0f172a; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.78rem; text-align: center; }
.topbar { position: absolute; top: 6px; left: 6px; right: 6px; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; pointer-events: none; }
.hud-pill { background: rgba(0,0,0,0.55); color: #fff; padding: 3px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; pointer-events: auto; }
.settings { margin-left: auto; background: rgba(0,0,0,0.55); color: #fff; border: 0; border-radius: 999px; width: 26px; height: 26px; cursor: pointer; pointer-events: auto; }
.overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.65); display: flex; align-items: center; justify-content: center; z-index: 5; padding: 10px; overflow: auto; }
.card { background: #0f172a; color: #fff; border-radius: 14px; padding: 14px; width: min(440px, 100%); max-height: 92vh; overflow: auto; box-shadow: 0 18px 40px rgba(0,0,0,0.6); }
.card-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.x { background: transparent; color: #fff; border: 0; font-size: 20px; cursor: pointer; }
.grp { margin-top: 10px; }
.grp label { display: block; font-size: 12px; color: rgba(255,255,255,0.7); margin-bottom: 4px; }
.grp.two { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.row { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #fff; }
input[type="range"] { width: 100%; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chip { background: rgba(255,255,255,0.08); color: #fff; border: 0; border-radius: 999px; padding: 5px 10px; font-size: 11px; cursor: pointer; }
.chip.on { background: #6366f1; }
.chip.locked, .chip:disabled { opacity: 0.5; cursor: not-allowed; }
.btn { background: #6366f1; color: #fff; border: 0; border-radius: 8px; padding: 8px 12px; font-size: 12px; cursor: pointer; font-weight: 600; }
.btn.small { padding: 4px 10px; font-size: 11px; }
.btn.primary { background: #f59e0b; }
.muted { color: rgba(255,255,255,0.6); font-size: 11px; margin-top: 4px; }
.tip { background: rgba(96,165,250,0.2); padding: 8px; border-radius: 8px; font-size: 12px; color: #fff; }
.row2 { display: grid; grid-template-columns: repeat(4,1fr); gap: 6px; margin-top: 10px; }
.stat { background: rgba(255,255,255,0.08); padding: 6px; border-radius: 8px; text-align: center; }
.stat .lbl { font-size: 10px; color: rgba(255,255,255,0.6); }
.stat .val { font-size: 14px; font-weight: 700; color: #fff; }
.sec-title { margin-top: 10px; font-size: 11px; color: rgba(255,255,255,0.6); }
.heat { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.heat-row { display: flex; align-items: center; gap: 6px; }
.heat-lbl { width: 60px; font-size: 11px; color: #fff; }
.heat-bar { background: linear-gradient(90deg,#fb7185,#f59e0b); color: #000; font-weight: 700; padding: 2px 6px; border-radius: 4px; font-size: 11px; }
.chart { width: 100%; height: 60px; background: rgba(255,255,255,0.06); border-radius: 8px; margin-top: 4px; }
.unlocks { background: rgba(250,204,21,0.25); color: #fde68a; padding: 6px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; margin-top: 8px; }
.actions { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
