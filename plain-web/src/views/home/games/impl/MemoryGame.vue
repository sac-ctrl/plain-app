<template>
  <div class="wrap" :class="{ onehand: settings.oneHanded, lowfx: settings.reducedMotion, hicon: settings.highContrast }">
    <div class="topbar">
      <div class="info">
        <div title="pairs"><span class="lab">Pairs</span> <b>{{ matched / 2 }}/{{ cards.length / 2 }}</b></div>
        <div title="moves"><span class="lab">Moves</span> <b>{{ moves }}</b></div>
        <div v-if="settings.timed" title="time"><span class="lab">Time</span> <b>{{ time }}s</b></div>
        <div v-if="!settings.timed && timer > 0" title="time"><span class="lab">Time</span> <b>{{ time }}s</b></div>
        <div title="streak"><span class="lab">Streak</span> <b>×{{ streak }}</b></div>
      </div>
      <div class="tools">
        <button class="ico-btn" :disabled="powerups.reveal <= 0" @click="usePowerup('reveal')" title="Reveal one pair">👁 {{ powerups.reveal }}</button>
        <button class="ico-btn" :disabled="powerups.shuffle <= 0" @click="usePowerup('shuffle')" title="Shuffle remaining">🔀 {{ powerups.shuffle }}</button>
        <button class="ico-btn" :disabled="powerups.freeze <= 0 || !settings.timed" @click="usePowerup('freeze')" title="Freeze timer 5s">❄ {{ powerups.freeze }}</button>
        <button class="ico-btn" @click="settingsOpen = true" title="Settings">⚙</button>
      </div>
    </div>

    <div v-if="settings.timed" class="time-bar">
      <div class="time-fill" :style="{ width: ((timeRemain / settings.timedSec) * 100) + '%' }"></div>
    </div>

    <div
      class="board"
      :class="{ ['theme-' + settings.theme]: true }"
      :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)`, maxWidth: boardWidth + 'px' }"
    >
      <button
        v-for="(c, i) in cards"
        :key="c.uid"
        class="card"
        :class="{ flipped: c.flipped || c.found, found: c.found, hint: hintIdx === i, ['back-' + settings.cardBack]: true }"
        :disabled="c.found || c.flipped || lock || frozen"
        @click="onClick(i)"
        @contextmenu.prevent
        @mousedown="onMouseDown(i)"
        @mouseup="onMouseUp(i)"
        @touchstart.prevent="onTouchStart(i)"
        @touchend.prevent="onTouchEnd(i)"
      >
        <span class="back" aria-hidden="true">
          <span class="bg-pattern"></span>
        </span>
        <span class="front" :class="{ shape: settings.colorblindShapes }">
          <span v-if="settings.colorblindShapes" class="shape-tag">{{ shapeFor(c.pid) }}</span>
          <span class="symbol">{{ c.v }}</span>
        </span>
      </button>
    </div>

    <div class="hint-row">
      <span v-if="dailyActive" class="tag">🌅 Daily seed</span>
      <span v-else-if="sharedActive" class="tag">🔗 Shared seed</span>
      <span class="tip">{{ statusTip }}</span>
    </div>

    <!-- Win overlay -->
    <div v-if="showWin" class="overlay">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Cleared!</div>
          <button class="x" @click="finishToShell">Continue ▸</button>
        </div>
        <div class="p-body">
          <div class="r-msg">{{ winSubtitle }}</div>
          <div class="stars">
            <span v-for="i in 3" :key="i" :class="{ on: i <= starsEarned }">★</span>
          </div>
          <div class="r-stats-row">
            <div><span>Moves</span><b>{{ moves }}</b></div>
            <div><span>Time</span><b>{{ time }}s</b></div>
            <div><span>Mismatches</span><b>{{ mismatches }}</b></div>
            <div><span>Efficiency</span><b>{{ efficiencyPct }}%</b></div>
            <div><span>Streak best</span><b>×{{ bestStreak }}</b></div>
            <div><span>Coins</span><b>+{{ coinsEarned }}</b></div>
          </div>
          <div class="graphs">
            <div class="g-block">
              <div class="g-title">Card revisit heatmap (red = thought twice)</div>
              <canvas ref="heatChart" class="g-canvas"></canvas>
            </div>
            <div class="g-block">
              <div class="g-title">Time per match (seconds)</div>
              <canvas ref="paceChart" class="g-canvas"></canvas>
            </div>
          </div>
          <div class="r-tip">{{ tipText }}</div>
          <div class="r-actions">
            <button class="btn primary" @click="instantReplay">↺ Watch flip-replay</button>
            <button class="btn" @click="resetAndPlay">🔁 Play again</button>
            <button class="btn" @click="finishToShell">Continue</button>
          </div>
          <div v-if="newUnlocks.length" class="unlock-banner">🎉 Unlocked: {{ newUnlocks.join(', ') }}</div>
        </div>
      </div>
    </div>

    <!-- Settings -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Recall · Twin Echo · Settings</div>
          <button class="x" @click="settingsOpen = false">✕</button>
        </div>
        <div class="p-body">
          <div class="row">
            <label>Game mode</label>
            <div class="chips">
              <button v-for="m in modes" :key="m.id" class="chip" :class="{ on: settings.gameMode === m.id }" @click="settings.gameMode = m.id; saveSettings(); resetAndPlay()">
                {{ m.label }}
              </button>
            </div>
            <div class="hint-sm">{{ modeDesc }}</div>
          </div>
          <div class="row">
            <label>Grid size <b>{{ settings.rows }}×{{ settings.cols }}</b></label>
            <div class="chips">
              <button v-for="g in gridChoices" :key="g.id" class="chip" :class="{ on: settings.rows === g.r && settings.cols === g.c }" @click="setGrid(g.r, g.c)">
                {{ g.r }}×{{ g.c }}
              </button>
            </div>
          </div>
          <div class="row">
            <label>Theme</label>
            <div class="chips">
              <button v-for="t in themes" :key="t.id" class="chip" :class="{ on: settings.theme === t.id }" @click="settings.theme = t.id; saveSettings(); resetAndPlay()">
                {{ t.label }}
              </button>
            </div>
            <div class="hint-sm">{{ themeDesc }}</div>
          </div>
          <div class="row">
            <label>Card back</label>
            <div class="chips">
              <button v-for="b in cardBacks" :key="b.id" class="chip"
                :class="{ on: settings.cardBack === b.id, locked: !isUnlocked('back-' + b.id) }"
                :title="isUnlocked('back-' + b.id) ? b.label : 'Locked: ' + b.unlockHint"
                @click="if (isUnlocked('back-' + b.id)) { settings.cardBack = b.id; saveSettings() }">
                {{ isUnlocked('back-' + b.id) ? b.label : '🔒 ' + b.label }}
              </button>
            </div>
          </div>
          <div class="row">
            <label class="check"><input type="checkbox" v-model="settings.timed" @change="saveSettings(); resetAndPlay()" /> Timed (60s base, +5s per match)</label>
            <label class="check"><input type="checkbox" v-model="settings.haptics" @change="saveSettings" /> Haptic feedback</label>
            <label class="check"><input type="checkbox" v-model="settings.audio" @change="saveSettings" /> Audio cues</label>
            <label class="check"><input type="checkbox" v-model="settings.voice" @change="saveSettings" /> Voice announcer</label>
            <label class="check"><input type="checkbox" v-model="settings.adaptive" @change="saveSettings" /> Adaptive difficulty (hints / regrouping)</label>
            <label class="check"><input type="checkbox" v-model="settings.flip3d" @change="saveSettings" /> 3D flip animation</label>
            <label class="check"><input type="checkbox" v-model="settings.reducedMotion" @change="saveSettings" /> Reduced motion</label>
            <label class="check"><input type="checkbox" v-model="settings.highContrast" @change="saveSettings" /> High-contrast borders</label>
            <label class="check"><input type="checkbox" v-model="settings.colorblindShapes" @change="saveSettings" /> Colourblind: shape tags on each card</label>
            <label class="check"><input type="checkbox" v-model="settings.oneHanded" @change="saveSettings" /> One-handed mode</label>
            <label class="check"><input type="checkbox" v-model="settings.batterySaver" @change="saveSettings" /> Battery saver (no flip animation, less juice)</label>
            <label class="check"><input type="checkbox" v-model="settings.assistAutoMatch" @change="saveSettings" /> Assist: auto-match after two wrong flips</label>
            <label class="check"><input type="checkbox" v-model="settings.assistLongerReveal" @change="saveSettings" /> Assist: longer mismatch reveal (1.6s)</label>
            <label class="check"><input type="checkbox" v-model="settings.assistLargeTouch" @change="saveSettings" /> Assist: larger touch zones</label>
            <label class="check"><input type="checkbox" v-model="settings.confirmLongPress" @change="saveSettings" /> Accessibility: confirm flip via long press (motor)</label>
          </div>
          <div class="row">
            <label>Flip animation speed <b>{{ settings.flipSpeed.toFixed(2) }}×</b></label>
            <input type="range" min="0.5" max="2" step="0.1" v-model.number="settings.flipSpeed" @change="saveSettings" />
          </div>
          <div class="row">
            <label>Mismatch reveal time <b>{{ settings.mismatchMs }} ms</b></label>
            <input type="range" min="400" max="2400" step="100" v-model.number="settings.mismatchMs" @change="saveSettings" />
          </div>
          <div class="row">
            <label>UI font scale <b>{{ settings.fontScale.toFixed(2) }}×</b></label>
            <input type="range" min="0.8" max="1.6" step="0.05" v-model.number="settings.fontScale" @change="saveSettings" />
          </div>
          <div class="row">
            <button class="btn" @click="openCalibration">🧠 Memory test (suggest grid)</button>
            <button class="btn" @click="copyShareSeed">🔗 Copy "Beat my seed" link</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Calibration -->
    <div v-if="calibrating" class="overlay" @click.self="calibrating = false">
      <div class="panel small">
        <div class="p-title">Memory span test</div>
        <div class="cal-stage">
          <div v-if="calStep < calSequence.length" class="cal-prompt">
            <div>Watch the symbol order ({{ calStep + 1 }}/{{ calSequence.length }}):</div>
            <div class="cal-symbol">{{ calSequence[calStep] }}</div>
          </div>
          <div v-else-if="!calDone" class="cal-prompt">
            <div>Now tap them in the same order:</div>
            <div class="cal-grid">
              <button v-for="s in calOptions" :key="s" class="chip" @click="calChoose(s)">{{ s }}</button>
            </div>
          </div>
          <div v-else class="cal-prompt">
            <div>Span = <b>{{ calScore }}</b>. Suggested grid: <b>{{ suggestedGrid.r }}×{{ suggestedGrid.c }}</b></div>
            <button class="btn primary" @click="acceptSuggested">Use this grid</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Replay -->
    <div v-if="replaying" class="overlay" @click.self="replaying = false">
      <div class="panel">
        <div class="p-head">
          <div class="p-title">Flip replay</div>
          <button class="x" @click="replaying = false">✕</button>
        </div>
        <div class="p-body">
          <div class="r-msg">Replay step {{ replayIdx + 1 }} / {{ replayLog.length }}</div>
          <div class="board mini" :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)` }">
            <div v-for="(_, i) in cards" :key="i" class="cardm"
              :class="{ on: replayLog[replayIdx]?.idx === i }">{{ i }}</div>
          </div>
          <div class="r-actions">
            <button class="btn" @click="replayIdx = Math.max(0, replayIdx - 1)">◀</button>
            <button class="btn primary" @click="replayPlay">{{ replayPlaying ? '⏸ Pause' : '▶ Play' }}</button>
            <button class="btn" @click="replayIdx = Math.min(replayLog.length - 1, replayIdx + 1)">▶</button>
          </div>
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
  mode?: 'classic' | 'time'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()

type ThemeId = 'animals' | 'space' | 'food' | 'geometry' | 'fantasy' | 'nostalgia'
type CardBackId = 'classic' | 'cyber' | 'pastel' | 'galaxy' | 'forest' | 'inkwell'
type GameMode = 'classic' | 'timed' | 'memorylane' | 'mismatch' | 'zen'

interface Settings {
  rows: number
  cols: number
  theme: ThemeId
  cardBack: CardBackId
  timed: boolean
  timedSec: number
  haptics: boolean
  audio: boolean
  voice: boolean
  adaptive: boolean
  flip3d: boolean
  reducedMotion: boolean
  highContrast: boolean
  colorblindShapes: boolean
  oneHanded: boolean
  batterySaver: boolean
  assistAutoMatch: boolean
  assistLongerReveal: boolean
  assistLargeTouch: boolean
  confirmLongPress: boolean
  gameMode: GameMode
  flipSpeed: number
  mismatchMs: number
  fontScale: number
}

const SETTINGS_KEY = 'memory_settings_v1'
const UNLOCKS_KEY = 'memory_unlocks_v1'
const POWERUPS_KEY = 'memory_powerups_v1'
const POWERUPS_DAILY_KEY = 'memory_powerups_daily_v1'

function defaultSettings(): Settings {
  return {
    rows: 4, cols: 4,
    theme: 'animals', cardBack: 'classic',
    timed: false, timedSec: 60,
    haptics: true, audio: true, voice: true, adaptive: true,
    flip3d: true, reducedMotion: false, highContrast: false,
    colorblindShapes: false, oneHanded: false, batterySaver: false,
    assistAutoMatch: false, assistLongerReveal: false, assistLargeTouch: false,
    confirmLongPress: false,
    gameMode: 'classic',
    flipSpeed: 1.0,
    mismatchMs: 800,
    fontScale: 1.0,
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

const themes: { id: ThemeId; label: string; symbols: string[]; desc: string }[] = [
  { id: 'animals', label: '🐱 Animals', desc: 'Cute pairs', symbols: ['🐱','🐶','🦊','🐼','🐯','🐸','🐵','🐰','🦁','🐨','🐮','🐷','🐔','🐧','🦄','🐻'] },
  { id: 'space',   label: '🚀 Space',   desc: 'Planets, stars, astronauts', symbols: ['🚀','🌟','🌍','🪐','🌙','☄','👽','🛸','🌞','☀','🌠','⭐','🌑','🌌','🛰','🌒'] },
  { id: 'food',    label: '🍕 Food',    desc: 'Sushi, pizza, fruit', symbols: ['🍕','🍔','🍟','🌭','🥗','🍣','🍜','🍩','🍦','🍓','🍇','🍎','🥭','🍑','🌶','🥑'] },
  { id: 'geometry',label: '◇ Geometry', desc: 'Abstract shapes', symbols: ['◇','◯','△','▽','◊','▣','✦','✪','✱','✜','❖','⬢','⬣','⬡','◐','◑'] },
  { id: 'fantasy', label: '🐉 Fantasy', desc: 'Dragons, gems, swords', symbols: ['🐉','💎','🗡','🛡','🏰','🧙','🧝','🧚','🐲','⚔','📜','🔮','🪄','💍','👑','🪙'] },
  { id: 'nostalgia',label: '♠ Nostalgia',desc: 'Classic cards', symbols: ['♠','♣','♥','♦','J','Q','K','A','♢','♤','♧','♡','★','☆','✶','✷'] },
]
const themeDesc = computed(() => themes.find((t) => t.id === settings.theme)?.desc || '')
const shapeMap = ['●','■','▲','◆','★','♥','♣','♠','✦','✚','◐','◔','◓','◑','◒','✸']
function shapeFor(pid: number) { return shapeMap[pid % shapeMap.length] }

const cardBacks: { id: CardBackId; label: string; unlockHint: string }[] = [
  { id: 'classic', label: 'Classic',  unlockHint: '' },
  { id: 'cyber',   label: 'Cyber',    unlockHint: 'Win a 4×4' },
  { id: 'pastel',  label: 'Pastel',   unlockHint: 'Win a Timed game' },
  { id: 'galaxy',  label: 'Galaxy',   unlockHint: 'Beat 5×5 in under 90s' },
  { id: 'forest',  label: 'Forest',   unlockHint: 'No mismatches in any 2×2' },
  { id: 'inkwell', label: 'Inkwell',  unlockHint: 'Daily seed clear' },
]
const modes: { id: GameMode; label: string }[] = [
  { id: 'classic',    label: 'Classic' },
  { id: 'timed',      label: 'Timed' },
  { id: 'memorylane', label: 'Memory Lane' },
  { id: 'mismatch',   label: 'Mismatch Penalty' },
  { id: 'zen',        label: 'Zen' },
]
const modeDescs: Record<GameMode, string> = {
  classic: 'Standard match-pair game. Move counter and optional timer.',
  timed: 'Beat the clock — every match adds +5 s.',
  memorylane: 'Grid expands by one row after every clear.',
  mismatch: 'Hardcore — every wrong match adds two more cards.',
  zen: 'No timer, no penalty, no power-ups. Pure relaxation.',
}
const modeDesc = computed(() => modeDescs[settings.gameMode])
const gridChoices = [
  { id: '2x2', r: 2, c: 2 },
  { id: '2x3', r: 2, c: 3 },
  { id: '4x3', r: 4, c: 3 },
  { id: '4x4', r: 4, c: 4 },
  { id: '4x5', r: 4, c: 5 },
  { id: '5x4', r: 5, c: 4 },
  { id: '4x6', r: 4, c: 6 },
  { id: '6x6', r: 6, c: 6 },
]
function setGrid(r: number, c: number) {
  if ((r * c) % 2 !== 0) c += 1
  settings.rows = r; settings.cols = c
  saveSettings(); resetAndPlay()
}

// unlocks
function loadUnlocks(): Set<string> {
  try { const r = localStorage.getItem(UNLOCKS_KEY); if (r) return new Set(JSON.parse(r) as string[]) } catch { /* ignore */ }
  return new Set(['back-classic'])
}
const unlocks = reactive<Set<string>>(loadUnlocks())
const newUnlocks = ref<string[]>([])
function isUnlocked(id: string) { return unlocks.has(id) }
function doUnlock(id: string, label: string) {
  if (unlocks.has(id)) return false
  unlocks.add(id)
  try { localStorage.setItem(UNLOCKS_KEY, JSON.stringify(Array.from(unlocks))) } catch { /* ignore */ }
  newUnlocks.value.push(label)
  return true
}

// power-ups stash, refilled daily
function todayStr() { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` }
function loadPowerups() {
  try {
    const raw = localStorage.getItem(POWERUPS_KEY)
    const lastDay = localStorage.getItem(POWERUPS_DAILY_KEY)
    const t = todayStr()
    let p = raw ? JSON.parse(raw) : { reveal: 2, shuffle: 1, freeze: 1 }
    if (lastDay !== t) {
      p = { reveal: Math.min(5, p.reveal + 2), shuffle: Math.min(3, p.shuffle + 1), freeze: Math.min(3, p.freeze + 1) }
      localStorage.setItem(POWERUPS_DAILY_KEY, t)
      localStorage.setItem(POWERUPS_KEY, JSON.stringify(p))
    }
    return p
  } catch { /* ignore */ }
  return { reveal: 2, shuffle: 1, freeze: 1 }
}
const powerups = reactive(loadPowerups())
function persistPowerups() { try { localStorage.setItem(POWERUPS_KEY, JSON.stringify(powerups)) } catch { /* ignore */ } }

// daily seed / shared seed
const dailyActive = ref(false)
const sharedActive = ref(false)
let runSeed = Date.now()
function mulberry32(a: number) { return function () { let t = a += 0x6d2b79f5; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 } }
let runRng = mulberry32(runSeed)
function hashSeed(s: string) { let h = 2166136261 >>> 0; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) } return h >>> 0 }
function pickSeed() {
  try {
    const url = new URL(window.location.href)
    const s = url.searchParams.get('mseed')
    if (s) { sharedActive.value = true; return hashSeed('share-' + s) }
  } catch { /* ignore */ }
  if (Math.random() < 0.25) { dailyActive.value = true; return hashSeed('daily-' + todayStr()) }
  return Date.now() >>> 0
}
function copyShareSeed() {
  try {
    const url = new URL(window.location.href)
    url.searchParams.set('mseed', runSeed.toString(36).slice(-6))
    navigator.clipboard?.writeText(url.toString())
    toast.success?.('Seed link copied')
  } catch { /* ignore */ }
}

// adapt difficulty from previous best
const sizes: Record<string, [number, number]> = { easy: [3, 4], medium: [4, 4], hard: [4, 5], insane: [5, 6] }
function applyDifficulty() {
  if (props.difficulty && !sessionStorage.getItem('mem_diff_applied')) {
    const [r, c] = sizes[props.difficulty] || [4, 4]
    settings.rows = r; settings.cols = c
    sessionStorage.setItem('mem_diff_applied', '1')
    saveSettings()
  }
}

// === game state ===
interface Card { v: string; flipped: boolean; found: boolean; pid: number; uid: number; revisits: number }
const cards = ref<Card[]>([])
const cols = computed(() => settings.cols)
const matched = ref(0)
const moves = ref(0)
const lock = ref(false)
const time = ref(0)
const timeRemain = ref(60)
let timer = 0
let frozen = false
let frozenTimer = 0
const sel = ref<number[]>([])
const mismatches = ref(0)
const consecutiveMatches = ref(0)
const consecutiveMisses = ref(0)
const streak = ref(0)
const bestStreak = ref(0)
const hintIdx = ref<number | null>(null)
const settingsOpen = ref(false)
const calibrating = ref(false)
const showWin = ref(false)
const replaying = ref(false)
const replayIdx = ref(0)
const replayPlaying = ref(false)
const replayLog: { t: number; idx: number; pid: number; matched: boolean }[] = []
let runStart = 0
const matchTimes: number[] = []
let lastMatchAt = 0
const coinsEarned = ref(0)
const starsEarned = ref(0)
const tipText = ref('')
const winSubtitle = ref('')
const efficiencyPct = computed(() => moves.value === 0 ? 100 : Math.round(((cards.value.length / 2) / moves.value) * 100))
const idealMoves = computed(() => cards.value.length / 2)
const boardWidth = computed(() => Math.min(520, settings.cols * 80))

// status tip
const statusTip = computed(() => {
  if (settings.gameMode === 'zen') return 'Zen mode — relax'
  if (settings.gameMode === 'memorylane') return 'Memory Lane — grid grows after each clear'
  if (settings.gameMode === 'mismatch') return 'Mismatch Penalty — every miss adds two cards'
  if (settings.gameMode === 'timed') return 'Timed — match all pairs before the clock empties'
  return 'Match every pair'
})

// === core actions ===
function reset() {
  applyDifficulty()
  runSeed = pickSeed(); runRng = mulberry32(runSeed)
  const t = themes.find((x) => x.id === settings.theme)!
  let r = settings.rows, c = settings.cols
  if ((r * c) % 2 !== 0) c = c + 1
  const total = r * c
  const pairs = total / 2
  const sym = t.symbols.slice(0, pairs)
  const arr: Card[] = []
  let uid = 1
  sym.forEach((s, i) => {
    arr.push({ v: s, flipped: false, found: false, pid: i, uid: uid++, revisits: 0 })
    arr.push({ v: s, flipped: false, found: false, pid: i, uid: uid++, revisits: 0 })
  })
  // shuffle (Fisher–Yates with seeded RNG)
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(runRng() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]]
  }
  cards.value = arr
  matched.value = 0
  moves.value = 0
  lock.value = false
  sel.value = []
  mismatches.value = 0
  consecutiveMatches.value = 0
  consecutiveMisses.value = 0
  streak.value = 0
  bestStreak.value = 0
  hintIdx.value = null
  matchTimes.length = 0
  replayLog.length = 0
  runStart = Date.now()
  lastMatchAt = runStart
  time.value = 0
  newUnlocks.value = []
  coinsEarned.value = 0
  starsEarned.value = 0
  showWin.value = false
  // timer
  clearInterval(timer); frozen = false; frozenTimer = 0
  if (settings.gameMode === 'timed') {
    settings.timed = true
    timeRemain.value = settings.timedSec
  } else {
    timeRemain.value = settings.timedSec
  }
  timer = window.setInterval(tick, 1000)
  props.onScore(0)
}

function tick() {
  if (props.paused || showWin.value || replaying.value) return
  if (frozen) {
    frozenTimer -= 1
    if (frozenTimer <= 0) frozen = false
    updateScore()
    return
  }
  time.value += 1
  if (settings.timed || settings.gameMode === 'timed') {
    timeRemain.value -= 1
    if (timeRemain.value <= 0) {
      clearInterval(timer)
      // game over due to timeout
      finishLose()
    }
  }
  updateScore()
}

function updateScore() {
  const base = matched.value * 60
  let bonus = 0
  if (settings.gameMode === 'zen') bonus = 0
  else if (settings.timed || settings.gameMode === 'timed') bonus = Math.max(0, timeRemain.value * 4)
  else bonus = Math.max(0, 300 - time.value * 2)
  bonus -= mismatches.value * 10
  const final = Math.max(0, base + bonus + streak.value * 8)
  props.onScore(final)
  return final
}

let pressTimer: number | null = null
let pressIdx = -1

function onClick(i: number) {
  if (settings.confirmLongPress) return // require long press in this mode
  flip(i)
}
function onMouseDown(i: number) {
  if (!settings.confirmLongPress) return
  pressIdx = i
  pressTimer = window.setTimeout(() => { flip(i); pressIdx = -1 }, 400)
}
function onMouseUp(_i: number) {
  if (pressTimer != null) { clearTimeout(pressTimer); pressTimer = null }
}
function onTouchStart(i: number) { onMouseDown(i) }
function onTouchEnd(i: number) { onMouseUp(i) }

function flip(i: number) {
  const card = cards.value[i]
  if (!card) return
  if (card.found || card.flipped || lock.value || frozen) return
  card.flipped = true
  card.revisits += 1
  sel.value.push(i)
  beep('tick')
  vibrate(10)
  replayLog.push({ t: Date.now() - runStart, idx: i, pid: card.pid, matched: false })
  if (sel.value.length === 2) {
    moves.value += 1
    lock.value = true
    const [a, b] = sel.value
    const A = cards.value[a], B = cards.value[b]
    if (A.pid === B.pid) {
      A.found = true; B.found = true
      matched.value += 2
      consecutiveMatches.value += 1
      consecutiveMisses.value = 0
      streak.value += 1
      bestStreak.value = Math.max(bestStreak.value, streak.value)
      sel.value = []
      lock.value = false
      beep('win')
      vibrate(40)
      replayLog[replayLog.length - 1].matched = true
      const dt = Date.now() - lastMatchAt
      lastMatchAt = Date.now()
      matchTimes.push(dt / 1000)
      if (settings.timed || settings.gameMode === 'timed') timeRemain.value += 5
      // adaptive: 5 consecutive matches
      if (settings.adaptive && consecutiveMatches.value >= 5 && settings.gameMode === 'memorylane') {
        toast.info?.('Streak! Grid expands next round.')
      }
      if (matched.value === cards.value.length) {
        clearInterval(timer)
        // memory lane: expand next time
        if (settings.gameMode === 'memorylane') {
          setTimeout(() => {
            settings.rows = Math.min(6, settings.rows + 1)
            saveSettings()
            finishWin()
          }, 400)
        } else {
          setTimeout(finishWin, 400)
        }
      }
    } else {
      mismatches.value += 1
      consecutiveMisses.value += 1
      consecutiveMatches.value = 0
      streak.value = 0
      beep('lose')
      vibrate(60)
      // adaptive hint after 3 in a row
      if (settings.adaptive && consecutiveMisses.value >= 3) {
        showHint()
        consecutiveMisses.value = 0
      }
      // mismatch penalty mode: add 2 cards
      if (settings.gameMode === 'mismatch') {
        addPair()
      }
      // assist auto-match after 2 wrong on same pair
      if (settings.assistAutoMatch && consecutiveMisses.value >= 2) {
        autoMatchHint()
      }
      const delay = settings.assistLongerReveal ? 1600 : settings.mismatchMs
      setTimeout(() => {
        if (cards.value[a]) cards.value[a].flipped = false
        if (cards.value[b]) cards.value[b].flipped = false
        sel.value = []
        lock.value = false
      }, delay)
    }
  }
  updateScore()
}

function addPair() {
  const t = themes.find((x) => x.id === settings.theme)!
  const usedPids = new Set(cards.value.map((c) => c.pid))
  const nextPid = Array.from({ length: t.symbols.length }, (_, i) => i).find((p) => !usedPids.has(p))
  if (nextPid === undefined) return
  const sym = t.symbols[nextPid]
  let uid = (cards.value.reduce((m, c) => Math.max(m, c.uid), 0) || 0) + 1
  const a: Card = { v: sym, flipped: false, found: false, pid: nextPid, uid: uid++, revisits: 0 }
  const b: Card = { v: sym, flipped: false, found: false, pid: nextPid, uid: uid++, revisits: 0 }
  cards.value.push(a, b)
  // re-shuffle the unfound cards
  const open = cards.value.filter((c) => !c.found)
  for (let i = open.length - 1; i > 0; i--) {
    const j = Math.floor(runRng() * (i + 1));
    [open[i], open[j]] = [open[j], open[i]]
  }
  const found = cards.value.filter((c) => c.found)
  cards.value = [...found, ...open]
}

function showHint() {
  // pick a random matching pair that's not yet found
  const groups: Record<number, number[]> = {}
  cards.value.forEach((c, i) => { if (!c.found) (groups[c.pid] = groups[c.pid] || []).push(i) })
  const candidates = Object.values(groups).filter((g) => g.length === 2)
  if (candidates.length === 0) return
  const pick = candidates[Math.floor(runRng() * candidates.length)]
  hintIdx.value = pick[0]
  setTimeout(() => { hintIdx.value = null }, 1200)
}

function autoMatchHint() {
  const groups: Record<number, number[]> = {}
  cards.value.forEach((c, i) => { if (!c.found) (groups[c.pid] = groups[c.pid] || []).push(i) })
  const candidates = Object.values(groups).filter((g) => g.length === 2)
  if (candidates.length === 0) return
  const pair = candidates[0]
  setTimeout(() => {
    cards.value[pair[0]].flipped = true
    cards.value[pair[1]].flipped = true
    setTimeout(() => {
      cards.value[pair[0]].found = true; cards.value[pair[1]].found = true
      matched.value += 2
    }, 700)
  }, 700)
}

function usePowerup(kind: 'reveal' | 'shuffle' | 'freeze') {
  if (powerups[kind] <= 0) return
  if (kind === 'reveal') {
    showHint()
    // also briefly flash both cards of the pair
    const groups: Record<number, number[]> = {}
    cards.value.forEach((c, i) => { if (!c.found) (groups[c.pid] = groups[c.pid] || []).push(i) })
    const candidates = Object.values(groups).filter((g) => g.length === 2)
    if (candidates.length > 0) {
      const pick = candidates[Math.floor(runRng() * candidates.length)]
      cards.value[pick[0]].flipped = true; cards.value[pick[1]].flipped = true
      setTimeout(() => {
        if (!cards.value[pick[0]].found) cards.value[pick[0]].flipped = false
        if (!cards.value[pick[1]].found) cards.value[pick[1]].flipped = false
      }, 900)
    }
  } else if (kind === 'shuffle') {
    const open = cards.value.filter((c) => !c.found)
    for (let i = open.length - 1; i > 0; i--) {
      const j = Math.floor(runRng() * (i + 1));
      [open[i].uid, open[j].uid] = [open[j].uid, open[i].uid]
      // swap their content
      const A = open[i], B = open[j]
      const tv = A.v, tp = A.pid; A.v = B.v; A.pid = B.pid; B.v = tv; B.pid = tp
    }
  } else if (kind === 'freeze') {
    frozen = true; frozenTimer = 5
  }
  powerups[kind] -= 1
  persistPowerups()
  beep('power')
}

function vibrate(ms: number) { if (settings.haptics) store.vibrate(ms) }
function beep(k: 'tick' | 'win' | 'lose' | 'tap' | 'power') { if (settings.audio) store.beep(k) }

function finishWin() {
  showWin.value = true
  // stars
  const ideal = idealMoves.value
  const ratio = ideal / Math.max(1, moves.value)
  const stars = ratio > 0.85 ? 3 : ratio > 0.6 ? 2 : 1
  starsEarned.value = stars
  const final = updateScore()
  coinsEarned.value = Math.max(2, Math.floor(final / 12))
  store.coins += coinsEarned.value
  store.save()
  // unlock cardbacks
  if (settings.rows * settings.cols >= 16) doUnlock('back-cyber', 'Cyber back')
  if (settings.timed) doUnlock('back-pastel', 'Pastel back')
  if ((settings.rows * settings.cols) >= 25 && time.value < 90) doUnlock('back-galaxy', 'Galaxy back')
  if (mismatches.value === 0) doUnlock('back-forest', 'Forest back')
  if (dailyActive.value) doUnlock('back-inkwell', 'Inkwell back')
  // tip
  const revisitTotal = cards.value.reduce((s, c) => s + c.revisits, 0)
  const inefficiency = revisitTotal / cards.value.length
  if (inefficiency > 2.4) tipText.value = 'You revisited many cards — try to map positions on first sweep.'
  else if (mismatches.value === 0) tipText.value = 'Flawless run! Now beat your time.'
  else tipText.value = 'Spread your first flips across the board to build a fast mental map.'
  if (stars === 3) winSubtitle.value = 'Perfect memory!'
  else if (stars === 2) winSubtitle.value = 'Great recall.'
  else winSubtitle.value = 'Keep training that memory.'
  if (settings.voice) { try { const u = new SpeechSynthesisUtterance('Match cleared'); u.rate = 1.2; u.volume = 0.7; speechSynthesis.cancel(); speechSynthesis.speak(u) } catch { /* ignore */ } }
  nextTick(drawCharts)
}

function finishLose() {
  // treat as ending — show win panel with 0 stars
  showWin.value = true
  starsEarned.value = 0
  const final = updateScore()
  coinsEarned.value = Math.max(1, Math.floor(final / 20))
  store.coins += coinsEarned.value
  store.save()
  tipText.value = 'Time ran out. Try a smaller grid or use Reveal earlier.'
  winSubtitle.value = 'Out of time'
  nextTick(drawCharts)
}

function finishToShell() {
  showWin.value = false
  props.onGameOver(updateScore())
}

function resetAndPlay() {
  showWin.value = false
  reset()
}

function instantReplay() {
  if (replayLog.length === 0) { toast.info?.('Nothing to replay'); return }
  replaying.value = true; replayIdx.value = 0; replayPlaying.value = false
}
let replayInterval: number | null = null
function replayPlay() {
  if (replayPlaying.value) {
    replayPlaying.value = false
    if (replayInterval != null) { clearInterval(replayInterval); replayInterval = null }
  } else {
    replayPlaying.value = true
    replayInterval = window.setInterval(() => {
      if (replayIdx.value >= replayLog.length - 1) {
        replayPlaying.value = false
        if (replayInterval != null) { clearInterval(replayInterval); replayInterval = null }
        return
      }
      replayIdx.value += 1
    }, 600)
  }
}

const heatChart = ref<HTMLCanvasElement>()
const paceChart = ref<HTMLCanvasElement>()
function drawCharts() {
  const hc = heatChart.value, pc = paceChart.value
  if (hc) {
    hc.width = hc.clientWidth * devicePixelRatio
    hc.height = 120 * devicePixelRatio
    const ctx = hc.getContext('2d')!
    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.fillStyle = '#0b0e1a'; ctx.fillRect(0, 0, hc.clientWidth, 120)
    const r = settings.rows, c = settings.cols
    const cw = hc.clientWidth / c, chh = 120 / r
    cards.value.forEach((card, i) => {
      const cx = (i % c) * cw, cy = Math.floor(i / c) * chh
      const heat = Math.min(1, card.revisits / 4)
      ctx.fillStyle = `rgba(239, 68, 68, ${0.15 + heat * 0.6})`
      ctx.fillRect(cx + 1, cy + 1, cw - 2, chh - 2)
      ctx.fillStyle = '#fff'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(String(card.revisits), cx + cw / 2, cy + chh / 2 + 3)
    })
  }
  if (pc) {
    pc.width = pc.clientWidth * devicePixelRatio
    pc.height = 120 * devicePixelRatio
    const ctx = pc.getContext('2d')!
    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.fillStyle = '#0b0e1a'; ctx.fillRect(0, 0, pc.clientWidth, 120)
    const max = Math.max(1, ...matchTimes)
    const bw = pc.clientWidth / Math.max(1, matchTimes.length)
    matchTimes.forEach((v, i) => {
      ctx.fillStyle = '#10b981'
      const h = (v / max) * 100
      ctx.fillRect(i * bw + 2, 110 - h, bw - 4, h)
    })
  }
}

// calibration
const calSequence = ref<string[]>([])
const calOptions = ref<string[]>([])
const calStep = ref(0)
const calDone = ref(false)
const calScore = ref(0)
const calInputs = ref<string[]>([])
function openCalibration() {
  calibrating.value = true
  calStep.value = 0; calDone.value = false; calScore.value = 0; calInputs.value = []
  const t = themes.find((x) => x.id === settings.theme)!
  calOptions.value = t.symbols.slice(0, 6)
  calSequence.value = Array.from({ length: 5 }, () => calOptions.value[Math.floor(Math.random() * calOptions.value.length)])
  let i = 0
  const ti = setInterval(() => { i += 1; if (i > calSequence.value.length) { clearInterval(ti); calStep.value = i; return }; calStep.value = i }, 900)
}
function calChoose(s: string) {
  calInputs.value.push(s)
  if (calInputs.value.length === calSequence.value.length) {
    let correct = 0
    for (let i = 0; i < calSequence.value.length; i++) if (calInputs.value[i] === calSequence.value[i]) correct += 1
    calScore.value = correct
    calDone.value = true
  }
}
const suggestedGrid = computed(() => {
  const s = calScore.value
  if (s <= 2) return { r: 2, c: 3 }
  if (s <= 3) return { r: 3, c: 4 }
  if (s <= 4) return { r: 4, c: 4 }
  return { r: 5, c: 6 }
})
function acceptSuggested() {
  setGrid(suggestedGrid.value.r, suggestedGrid.value.c)
  calibrating.value = false
}

onMounted(() => {
  reset()
})
onUnmounted(() => {
  clearInterval(timer)
  if (replayInterval != null) clearInterval(replayInterval)
})
watch(() => props.running, (v) => { if (v) reset() })
watch(() => settings.fontScale, (v) => { document.documentElement.style.setProperty('--mem-font', String(v)) })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 8px; font-size: calc(1rem * var(--mem-font, 1)); }
.wrap.onehand { padding-left: 0; padding-right: 0; padding-bottom: 60px; }
.topbar { display: flex; gap: 10px; align-items: center; justify-content: space-between; width: 100%; max-width: 520px; padding: 0 6px; }
.info { display: flex; gap: 12px; color: #fff; font-weight: 600; font-size: 0.86rem; flex-wrap: wrap; }
.info .lab { color: rgba(255,255,255,0.55); font-weight: 500; margin-right: 2px; }
.tools { display: flex; gap: 4px; }
.ico-btn { background: #1c2030; border: 1px solid #2a2f48; border-radius: 8px; color: #fff; padding: 4px 8px; cursor: pointer; font-size: 0.8rem; }
.ico-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.time-bar { width: 100%; max-width: 520px; height: 6px; background: rgba(255,255,255,0.12); border-radius: 3px; overflow: hidden; }
.time-fill { background: linear-gradient(90deg, #10b981, #facc15, #ef4444); height: 100%; transition: width 0.5s linear; }
.board { display: grid; gap: 8px; width: 100%; }
.board.mini { gap: 2px; max-width: 240px; }
.cardm { background: #1c2030; border-radius: 4px; aspect-ratio: 1; display: flex; align-items: center; justify-content: center; color: rgba(255,255,255,0.45); font-size: 0.6rem; }
.cardm.on { background: #facc15; color: #000; font-weight: 700; }
.card { aspect-ratio: 1; border-radius: 12px; border: none; background: linear-gradient(135deg, #6366f1, #a855f7); color: #fff; cursor: pointer; perspective: 600px; position: relative; font-size: 1.7rem; transition: transform 0.18s; }
.card:active { transform: scale(0.96); }
.card.found { background: linear-gradient(135deg, #10b981, #14b8a6); }
.card.hint { animation: hintPulse 1s ease-in-out 2; }
.front, .back { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; backface-visibility: hidden; transition: transform calc(0.4s / var(--flipSpeed, 1)); border-radius: 12px; }
.front { transform: rotateY(180deg); flex-direction: column; gap: 2px; }
.front .symbol { font-size: 1.7rem; }
.front .shape-tag { color: rgba(255,255,255,0.85); font-size: 0.9rem; line-height: 1; }
.card.flipped .back { transform: rotateY(180deg); }
.card.flipped .front { transform: rotateY(360deg); }
.back .bg-pattern { width: 60%; height: 60%; border-radius: 8px; background: rgba(255,255,255,0.18); position: relative; }
.back-cyber .bg-pattern { background: repeating-linear-gradient(45deg, rgba(34,211,238,0.18) 0 6px, transparent 6px 12px); }
.back-pastel .bg-pattern { background: radial-gradient(circle, rgba(252,231,243,0.5), rgba(165,180,252,0.3)); }
.back-galaxy .bg-pattern { background: radial-gradient(ellipse, rgba(168,85,247,0.7) 0%, rgba(0,0,0,0.6) 60%); }
.back-forest .bg-pattern { background: linear-gradient(135deg, #166534, #14532d); }
.back-inkwell .bg-pattern { background: repeating-linear-gradient(0deg, rgba(255,255,255,0.18) 0 2px, transparent 2px 8px); }
.theme-space .card { background: linear-gradient(135deg, #1e1b4b, #6d28d9); }
.theme-food .card { background: linear-gradient(135deg, #ea580c, #facc15); }
.theme-geometry .card { background: linear-gradient(135deg, #1f2937, #111827); }
.theme-fantasy .card { background: linear-gradient(135deg, #7c2d12, #a21caf); }
.theme-nostalgia .card { background: linear-gradient(135deg, #fafafa, #d4d4d4); color: #1c1917; }
.hicon .card { outline: 2px solid #fff; }
.lowfx .front, .lowfx .back { transition: none; }
.lowfx .card.flipped .back, .lowfx .card.flipped .front { transition: none; }
.hint-row { display: flex; gap: 10px; align-items: center; color: rgba(255,255,255,0.65); font-size: 0.78rem; }
.tag { color: #fde68a; font-weight: 600; }
.overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; z-index: 50; padding: 16px; }
.panel { background: #0b0e1a; border: 1px solid #2a2f48; border-radius: 14px; max-width: 520px; width: 100%; max-height: 88vh; overflow-y: auto; color: #fff; }
.panel.small { max-width: 360px; padding: 14px; text-align: center; }
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
.r-msg { color: #fde68a; margin-bottom: 10px; }
.r-stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 8px 0; }
.r-stats-row > div { background: #14182a; border-radius: 6px; padding: 6px; text-align: center; }
.r-stats-row span { display: block; color: rgba(255,255,255,0.55); font-size: 0.7rem; }
.r-stats-row b { font-size: 0.95rem; }
.graphs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.g-block { background: #14182a; border-radius: 8px; padding: 6px; }
.g-title { font-size: 0.72rem; color: rgba(255,255,255,0.65); margin-bottom: 4px; }
.g-canvas { width: 100%; height: 120px; display: block; border-radius: 4px; }
.r-tip { margin-top: 10px; color: rgba(255,255,255,0.75); font-size: 0.84rem; padding: 8px; background: #14182a; border-radius: 6px; }
.r-actions { display: flex; gap: 6px; margin-top: 12px; flex-wrap: wrap; }
.unlock-banner { margin-top: 12px; padding: 8px; background: linear-gradient(135deg, #facc15, #ec4899); border-radius: 8px; font-weight: 700; text-align: center; color: #0b0e1a; }
.stars { font-size: 2rem; color: rgba(255,255,255,0.18); display: flex; gap: 8px; justify-content: center; }
.stars span.on { color: #facc15; text-shadow: 0 0 12px rgba(250,204,21,0.6); }
.cal-stage { padding: 14px; }
.cal-symbol { font-size: 3.5rem; margin: 12px 0; }
.cal-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 10px; }
@keyframes hintPulse { 0%,100% { box-shadow: 0 0 0 0 rgba(250,204,21,0.0) } 50% { box-shadow: 0 0 0 6px rgba(250,204,21,0.6) } }
</style>
