<template>
  <div class="pulse" :class="['theme-' + settings.theme, { rmo: settings.reducedMotion, hc: settings.highContrast, oh: settings.oneHanded, cb: settings.colorblind !== 'off' }]" tabindex="0" @keydown="onKey">
    <div class="hud">
      <div class="left">
        <div class="chip"><span class="lbl">SCORE</span><strong>{{ score }}</strong></div>
        <div class="chip"><span class="lbl">BEST</span><strong>{{ best }}</strong></div>
        <div class="chip"><span class="lbl">COMBO</span><strong>×{{ combo }}</strong></div>
        <div class="chip"><span class="lbl">ACC</span><strong>{{ acc }}%</strong></div>
        <div class="chip" v-if="lives < maxLives"><span class="lbl">❤</span><strong>{{ lives }}/{{ maxLives }}</strong></div>
      </div>
      <div class="right">
        <button class="pill" :disabled="powerups.hint <= 0" @click="usePower('hint')">💡 {{ powerups.hint }}</button>
        <button class="pill" :disabled="powerups.slow <= 0" @click="usePower('slow')">⏱ {{ powerups.slow }}</button>
        <button class="pill" :disabled="powerups.life <= 0" @click="usePower('life')">❤ {{ powerups.life }}</button>
        <button class="pill" @click="settingsOpen = true">⚙</button>
      </div>
    </div>

    <div class="status" :class="judge?.kind">
      <span v-if="state === 'show'">Watch · sequence {{ seq.length }}</span>
      <span v-else-if="state === 'input'">Tap the pattern · {{ idx + 1 }}/{{ seq.length }}</span>
      <span v-else-if="state === 'rhythm'">Tap on the beat</span>
      <span v-else-if="state === 'cascade'">Tap tiles in order before they hit the bottom</span>
      <span v-else-if="state === 'shape'">Trace the shape</span>
      <span v-else-if="state === 'done'">{{ won ? 'Sequence cleared!' : 'Game over' }}</span>
      <span v-else>Get ready…</span>
    </div>

    <!-- Sequence / Shape mode pads grid -->
    <div v-if="settings.gameMode !== 'cascade' && settings.gameMode !== 'rhythm'" class="board" :class="['pads-' + numPads]" :style="{ gridTemplateColumns: 'repeat(' + Math.ceil(Math.sqrt(numPads)) + ', 1fr)' }">
      <button v-for="i in numPads" :key="i"
        class="pad"
        :style="{ background: padColors[(i-1) % padColors.length], boxShadow: lit === (i-1) ? '0 0 40px ' + padColors[(i-1) % padColors.length] : '' }"
        :class="['p' + ((i-1) % padColors.length), { lit: lit === (i-1), hint: hintIdx === (i-1), shape: settings.gameMode === 'shape' }]"
        :disabled="state !== 'input'"
        @pointerdown="press(i-1)"
      >
        <span v-if="settings.colorblind !== 'off'" class="cb">{{ shapeFor(i-1) }}</span>
        <span v-if="settings.gameMode === 'shape' && shapePath.includes(i-1)" class="dot">{{ shapePath.indexOf(i-1) + 1 }}</span>
      </button>
    </div>

    <!-- Rhythm tap mode: scrolling beat targets -->
    <div v-if="settings.gameMode === 'rhythm'" class="rhythm" ref="rhythmEl">
      <div class="lane" v-for="i in 4" :key="i" @pointerdown="rhythmPress(i-1)">
        <span class="lane-bg" :style="{ background: padColors[(i-1) % padColors.length] }"/>
      </div>
      <div class="targets">
        <div v-for="t in rhythmTargets" :key="t.id" class="target" :class="['p' + (t.lane % padColors.length)]" :style="{ left: (t.lane * 25 + 12.5) + '%', top: t.y + '%' }"/>
      </div>
      <div class="hitline"/>
    </div>

    <!-- Speed cascade mode: tiles falling, tap in order -->
    <div v-if="settings.gameMode === 'cascade'" class="cascade" ref="cascEl">
      <div v-for="t in cascTiles" :key="t.id"
        class="ctile" :class="{ next: t.order === cascNext }"
        :style="{ left: t.x + '%', top: t.y + '%', background: padColors[t.color % padColors.length] }"
        @pointerdown="cascadePress(t)"
      >{{ t.order + 1 }}</div>
    </div>

    <!-- Calibration -->
    <div v-if="calibrating" class="cal-bar">
      <p>Tap to the beat ×8</p>
      <div class="metro" :class="{ pulse: metroOn }"/>
      <p class="hint">Latency: {{ calLatency }}ms · Window: {{ settings.timingMs }}ms</p>
    </div>

    <div class="footer">
      <span class="hint">Mode: <b>{{ settings.gameMode }}</b> · Pads: <b>{{ numPads }}</b> · Theme: <b>{{ settings.theme }}</b> · Sound pack: <b>{{ settings.soundPack }}</b></span>
      <span class="hint" v-if="settings.gameMode === 'rhythm'">Beat synced to music. Today’s seed: <b>#{{ seed }}</b></span>
    </div>

    <!-- Settings sheet -->
    <div v-if="settingsOpen" class="sheet" @click.self="settingsOpen = false">
      <div class="card">
        <h3>Pattern Pulse · Settings</h3>
        <section>
          <h4>Mode</h4>
          <div class="chips">
            <button v-for="m in ['sequence','rhythm','shape','cascade']" :key="m" class="chip-btn" :class="{ on: settings.gameMode === m }" @click="settings.gameMode = m; persist(); reset()">{{ m }}</button>
          </div>
        </section>
        <section>
          <h4>Pads</h4>
          <div class="chips">
            <button v-for="n in [4, 6, 9]" :key="n" class="chip-btn" :class="{ on: numPadsSetting === n }" @click="numPadsSetting = n; persist(); reset()">{{ n }} pads</button>
          </div>
        </section>
        <section>
          <h4>Theme</h4>
          <div class="chips">
            <button v-for="t in ['synth','particles','dark','nature']" :key="t" class="chip-btn" :class="{ on: settings.theme === t }" @click="settings.theme = t; persist()">{{ t }}</button>
          </div>
        </section>
        <section>
          <h4>Sound pack</h4>
          <div class="chips">
            <button v-for="s in ['piano','drums','synth','8bit']" :key="s" class="chip-btn" :class="{ on: settings.soundPack === s, locked: !unlocks.soundPacks.includes(s) }" @click="unlocks.soundPacks.includes(s) && (settings.soundPack = s, persist())">{{ s }} <span v-if="!unlocks.soundPacks.includes(s)">🔒</span></button>
          </div>
        </section>
        <section>
          <h4>Tap effects</h4>
          <div class="chips">
            <button v-for="t in ['ripple','sparks','stars','splash']" :key="t" class="chip-btn" :class="{ on: settings.tapEffect === t, locked: !unlocks.effects.includes(t) }" @click="unlocks.effects.includes(t) && (settings.tapEffect = t, persist())">{{ t }} <span v-if="!unlocks.effects.includes(t)">🔒</span></button>
          </div>
        </section>
        <section>
          <h4>Timing &amp; difficulty</h4>
          <label>Timing window: ±{{ settings.timingMs }}ms<input type="range" min="40" max="220" step="5" v-model.number="settings.timingMs" @change="persist()"/></label>
          <label>Tap radius padding: {{ settings.tapPad }}px<input type="range" min="0" max="40" v-model.number="settings.tapPad" @change="persist()"/></label>
          <label><input type="checkbox" v-model="settings.adaptive" @change="persist()"/> Adaptive difficulty</label>
          <label><input type="checkbox" v-model="settings.multitouch" @change="persist()"/> Allow two-finger pads</label>
        </section>
        <section>
          <h4>Sound &amp; haptics</h4>
          <label><input type="checkbox" v-model="settings.haptics" @change="persist()"/> Haptics</label>
          <label><input type="checkbox" v-model="settings.audio" @change="persist()"/> Sound</label>
          <label><input type="checkbox" v-model="settings.voice" @change="persist()"/> Voice announcer</label>
          <label><input type="checkbox" v-model="settings.dynamicMusic" @change="persist()"/> Dynamic music layers</label>
        </section>
        <section>
          <h4>Accessibility</h4>
          <label><input type="checkbox" v-model="settings.reducedMotion" @change="persist()"/> Reduced motion</label>
          <label><input type="checkbox" v-model="settings.highContrast" @change="persist()"/> High contrast</label>
          <label><input type="checkbox" v-model="settings.oneHanded" @change="persist()"/> One-handed (pads on bottom-right)</label>
          <label><input type="checkbox" v-model="settings.batterySaver" @change="persist()"/> Battery saver</label>
          <label>Colourblind:
            <select v-model="settings.colorblind" @change="persist()">
              <option value="off">Off</option><option value="protanopia">Protanopia</option><option value="deuteranopia">Deuteranopia</option><option value="tritanopia">Tritanopia</option>
            </select>
          </label>
        </section>
        <section>
          <h4>Assists</h4>
          <label><input type="checkbox" v-model="settings.assistHint" @change="persist()"/> Always glow next pad</label>
          <label><input type="checkbox" v-model="settings.assistRadius" @change="persist()"/> Larger tap radius</label>
          <label><input type="checkbox" v-model="settings.assistTiming" @change="persist()"/> Wider timing window</label>
        </section>
        <section>
          <h4>Calibration</h4>
          <p class="hint">Tap to the metronome 8 times to auto-tune your timing window.</p>
          <button class="pill" @click="startCalibration">{{ calibrating ? 'Cancel' : 'Start rhythm test' }}</button>
        </section>
        <section>
          <h4>Stats</h4>
          <div class="stats">
            <div><b>{{ stats.bestStreak }}</b><span>Longest streak</span></div>
            <div><b>{{ stats.bestAccuracy }}%</b><span>Best accuracy</span></div>
            <div><b>{{ stats.totalSeq }}</b><span>Sequences cleared</span></div>
            <div><b>{{ stats.totalTaps }}</b><span>Total taps</span></div>
          </div>
        </section>
        <section>
          <h4>Share</h4>
          <button class="pill" @click="copyShare">{{ shareLabel }}</button>
        </section>
        <button class="pill primary" @click="settingsOpen = false">Close</button>
      </div>
    </div>

    <!-- Post-game analytics -->
    <div v-if="analyticsOpen" class="sheet" @click.self="analyticsOpen = false">
      <div class="card">
        <h3>Post-game</h3>
        <p><b>Streak</b> {{ score / 10 }} · <b>Accuracy</b> {{ acc }}% · <b>Longest combo</b> {{ bestCombo }}</p>
        <h4>Pad heatmap (timing)</h4>
        <div class="heat" :style="{ gridTemplateColumns: 'repeat(' + Math.ceil(Math.sqrt(numPads)) + ', 1fr)' }">
          <div v-for="(p, i) in padTiming" :key="i" class="hc" :style="{ background: heatColor(p.avgMs) }">
            <span class="hc-pad" :style="{ background: padColors[i % padColors.length] }"/>{{ p.taps ? Math.round(p.avgMs) : '–' }}ms
          </div>
        </div>
        <h4>Timing histogram (early ↔ late)</h4>
        <svg viewBox="0 -50 200 100" preserveAspectRatio="none" class="chart">
          <line x1="100" y1="-50" x2="100" y2="50" stroke="#fff" stroke-width="0.4"/>
          <rect v-for="(b, i) in histo" :key="i" :x="i * (200 / histo.length)" :y="-b" :width="200 / histo.length - 0.5" :height="b" fill="#22d3ee"/>
        </svg>
        <p class="tip">{{ tip }}</p>
        <button class="pill primary" @click="analyticsOpen = false">Close</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: string
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const SK = 'pulse_settings_v1', UK = 'pulse_unlocks_v1', PK = 'pulse_powerups_v1', SS = 'pulse_stats_v1', BK = 'pulse_best_v1'

const settings = reactive({
  gameMode: 'sequence' as 'sequence' | 'rhythm' | 'shape' | 'cascade',
  theme: 'synth',
  soundPack: 'piano',
  tapEffect: 'ripple',
  timingMs: 100,
  tapPad: 8,
  adaptive: true,
  multitouch: false,
  haptics: true,
  audio: true,
  voice: false,
  dynamicMusic: true,
  reducedMotion: false,
  highContrast: false,
  oneHanded: false,
  batterySaver: false,
  colorblind: 'off',
  assistHint: false,
  assistRadius: false,
  assistTiming: false,
  recentMisses: 0,
  recentCorrect: 0,
  avgReactMs: 400,
})
const unlocks = reactive({
  soundPacks: ['piano'] as string[],
  effects: ['ripple'] as string[],
  themes: ['synth','dark'] as string[],
})
const powerups = reactive({ hint: 2, slow: 2, life: 1, lastRefresh: '' })
const stats = reactive({ bestStreak: 0, bestAccuracy: 0, totalSeq: 0, totalTaps: 0 })
const best = ref(0)
function persist() {
  localStorage.setItem(SK, JSON.stringify(settings))
  localStorage.setItem(UK, JSON.stringify(unlocks))
  localStorage.setItem(PK, JSON.stringify(powerups))
  localStorage.setItem(SS, JSON.stringify(stats))
  localStorage.setItem(BK, String(best.value))
}
function load() {
  try { Object.assign(settings, JSON.parse(localStorage.getItem(SK) || '{}')) } catch {}
  try { Object.assign(unlocks, JSON.parse(localStorage.getItem(UK) || '{}')) } catch {}
  try { Object.assign(powerups, JSON.parse(localStorage.getItem(PK) || '{}')) } catch {}
  try { Object.assign(stats, JSON.parse(localStorage.getItem(SS) || '{}')) } catch {}
  best.value = Number(localStorage.getItem(BK) || 0)
  unlocks.soundPacks = Array.from(new Set(unlocks.soundPacks))
  unlocks.effects = Array.from(new Set(unlocks.effects))
}
load()

function todayKey() { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` }
if (powerups.lastRefresh !== todayKey()) {
  powerups.hint = Math.min(5, powerups.hint + 2)
  powerups.slow = Math.min(5, powerups.slow + 1)
  powerups.life = Math.min(3, powerups.life + 1)
  powerups.lastRefresh = todayKey()
  persist()
}

// daily seed (shared rhythm sequence)
function mulberry32(s: number) { return () => { s |= 0; s = (s + 0x6D2B79F5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 } }
const seed = (() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate() })()
const dailyRng = mulberry32(seed)

// pads
const numPadsSetting = ref(4)
const numPads = computed(() => settings.gameMode === 'rhythm' ? 4 : numPadsSetting.value)
const padColors = ['#ef4444', '#22c55e', '#3b82f6', '#facc15', '#a855f7', '#06b6d4', '#f97316', '#ec4899', '#14b8a6']
function shapeFor(i: number): string { return ['●','■','▲','◆','★','♥','♣','♠','✦'][i % 9] }

// state
const state = ref<'show' | 'input' | 'rhythm' | 'cascade' | 'shape' | 'done' | 'idle'>('idle')
const seq = ref<number[]>([])
const idx = ref(0)
const lit = ref(-1)
const hintIdx = ref(-1)
const score = ref(0)
const combo = ref(0)
const bestCombo = ref(0)
const lives = ref(1)
const maxLives = ref(1)
const judge = ref<{ kind: 'perfect' | 'good' | 'miss'; ms: number } | null>(null)
const won = ref(false)
const settingsOpen = ref(false)
const analyticsOpen = ref(false)

// shape path for shape mode
const shapePath = ref<number[]>([])
const shapeStep = ref(0)

// rhythm mode
const rhythmEl = ref<HTMLDivElement>()
type RTarget = { id: number; lane: number; spawnT: number; y: number; hit?: boolean }
const rhythmTargets = ref<RTarget[]>([])
let rId = 1
let rhythmStart = 0
let bpm = 100
const beatMs = computed(() => 60000 / bpm)
const fallMs = 1500

// cascade mode
const cascEl = ref<HTMLDivElement>()
type CTile = { id: number; x: number; y: number; vy: number; order: number; color: number; tapped?: boolean }
const cascTiles = ref<CTile[]>([])
let cId = 1
const cascNext = ref(0)
const cascRound = ref(0)

// stats per game
const padTiming = ref<{ taps: number; sumMs: number; avgMs: number }[]>([])
const histo = ref<number[]>(Array(20).fill(0))
const tip = ref('')

// calibration
const calibrating = ref(false)
const metroOn = ref(false)
const calLatency = ref(0)
let calTaps: number[] = []
let calMetroId: number | null = null

let toRefs: number[] = []
function clearTo() { toRefs.forEach(t => clearTimeout(t)); toRefs = [] }

function reset() {
  clearTo()
  seq.value = []; idx.value = 0; lit.value = -1; hintIdx.value = -1
  score.value = 0; combo.value = 0; bestCombo.value = 0; judge.value = null; won.value = false
  cascTiles.value = []; cascNext.value = 0; cascRound.value = 0
  rhythmTargets.value = []
  shapePath.value = []; shapeStep.value = 0
  padTiming.value = Array(numPads.value).fill(0).map(() => ({ taps: 0, sumMs: 0, avgMs: 0 }))
  histo.value = Array(20).fill(0)
  lives.value = 1 + powerups.life
  maxLives.value = lives.value
  // adaptive: longer timing window after misses
  // assistance overrides
  state.value = 'show'
  if (settings.gameMode === 'sequence') startSequence()
  else if (settings.gameMode === 'rhythm') startRhythm()
  else if (settings.gameMode === 'cascade') startCascade()
  else if (settings.gameMode === 'shape') startShape()
}

// ---------- sequence mode ----------
function speedForSeq() {
  let base = props.difficulty === 'easy' ? 700 : props.difficulty === 'hard' ? 360 : props.difficulty === 'insane' ? 240 : 500
  if (settings.adaptive && settings.recentMisses >= 2) base += 120
  if (settings.adaptive && settings.recentCorrect >= 5) base = Math.max(180, base - 60)
  return base
}
function startSequence() {
  const v = Math.floor(dailyRng() * numPads.value)
  seq.value.push(v)
  idx.value = 0; state.value = 'show'
  showSeq()
}
function showSeq() {
  const sp = speedForSeq()
  let i = 0
  function step() {
    if (i >= seq.value.length) { state.value = 'input'; return }
    lit.value = seq.value[i]
    sound(seq.value[i], 'show')
    toRefs.push(window.setTimeout(() => { lit.value = -1; i += 1; toRefs.push(window.setTimeout(step, 150)) }, sp))
  }
  toRefs.push(window.setTimeout(step, 350))
}

// ---------- shape mode ----------
function startShape() {
  const shapes: number[][] = [
    // L-shape, zigzag, square, star...
    [0, numPads.value === 4 ? 2 : 3, numPads.value - 1],
    [0, 1, numPads.value - 2, numPads.value - 1],
    Array.from({ length: numPads.value }, (_, i) => i),
  ]
  shapePath.value = shapes[Math.floor(dailyRng() * shapes.length)]
  shapeStep.value = 0; state.value = 'input'
}

// ---------- rhythm mode ----------
function startRhythm() {
  state.value = 'rhythm'
  rhythmStart = performance.now()
  bpm = 90 + Math.floor(dailyRng() * 60) + (props.difficulty === 'hard' ? 20 : 0) + (props.difficulty === 'insane' ? 40 : 0)
  spawnRhythm(0)
  rhythmLoop()
}
function spawnRhythm(beat: number) {
  for (let i = 0; i < 24; i++) {
    const lane = Math.floor(dailyRng() * 4)
    rhythmTargets.value.push({ id: rId++, lane, spawnT: rhythmStart + (beat + i + 2) * beatMs.value, y: -10 })
  }
}
let rRaf: number | null = null
function rhythmLoop() {
  if (state.value !== 'rhythm') return
  const now = performance.now()
  for (const t of rhythmTargets.value) {
    const elapsed = t.spawnT - now
    t.y = 100 - (elapsed / fallMs) * 100
  }
  // missed targets
  for (const t of rhythmTargets.value) {
    if (!t.hit && t.y > 100 + 6) { t.hit = true; combo.value = 0; settings.recentMisses += 1; lives.value -= 1 }
  }
  if (lives.value <= 0) { state.value = 'done'; endGame(); return }
  rhythmTargets.value = rhythmTargets.value.filter(t => !t.hit || t.y < 110)
  if (rhythmTargets.value.length < 8) spawnRhythm(Math.round((now - rhythmStart) / beatMs.value))
  rRaf = requestAnimationFrame(rhythmLoop)
}

// ---------- cascade mode ----------
function startCascade() {
  state.value = 'cascade'
  cascNext.value = 0; cascRound.value = 0
  spawnCascadeRound()
  cascLoop()
}
function spawnCascadeRound() {
  const n = 4 + Math.floor(cascRound.value * 0.7)
  cascNext.value = 0
  for (let i = 0; i < n; i++) {
    cascTiles.value.push({ id: cId++, x: 5 + Math.floor(dailyRng() * 9) * 10, y: -i * 12, vy: 0.4 + cascRound.value * 0.05, order: i, color: Math.floor(dailyRng() * 4) })
  }
}
let cRaf: number | null = null
function cascLoop() {
  if (state.value !== 'cascade') return
  for (const t of cascTiles.value) if (!t.tapped) t.y += t.vy
  for (const t of cascTiles.value) {
    if (!t.tapped && t.y > 100) { t.tapped = true; combo.value = 0; settings.recentMisses += 1; lives.value -= 1 }
  }
  if (lives.value <= 0) { state.value = 'done'; endGame(); return }
  cascTiles.value = cascTiles.value.filter(t => !t.tapped || t.y < 110)
  if (cascTiles.value.every(t => t.tapped)) {
    cascRound.value += 1; spawnCascadeRound()
  }
  cRaf = requestAnimationFrame(cascLoop)
}

// ---------- input ----------
function press(p: number) {
  if (settings.gameMode === 'sequence') {
    if (state.value !== 'input') return
    lit.value = p
    sound(p, 'tap')
    if (settings.haptics) store.vibrate(8)
    setTimeout(() => (lit.value = -1), 120)
    if (p !== seq.value[idx.value]) {
      // miss
      lives.value -= 1; combo.value = 0; settings.recentMisses += 1
      if (settings.haptics) store.vibrate([12, 8, 12])
      if (lives.value <= 0) { state.value = 'done'; endGame(); return }
      // restart current sequence from beginning
      idx.value = 0
      state.value = 'show'
      toRefs.push(window.setTimeout(showSeq, 600))
      return
    }
    idx.value += 1
    combo.value += 1; bestCombo.value = Math.max(bestCombo.value, combo.value)
    if (idx.value === seq.value.length) {
      const bonus = seq.value.length * 10 * Math.max(1, Math.floor(combo.value / 5))
      score.value += bonus; props.onScore(score.value)
      stats.totalSeq += 1; settings.recentCorrect += 1
      if (combo.value >= 10 && !unlocks.effects.includes('sparks')) unlocks.effects.push('sparks')
      if (combo.value >= 25 && !unlocks.soundPacks.includes('synth')) unlocks.soundPacks.push('synth')
      if (combo.value >= 50 && !unlocks.effects.includes('stars')) unlocks.effects.push('stars')
      if (settings.voice && combo.value % 10 === 0) speak(`Combo ${combo.value}`)
      toRefs.push(window.setTimeout(() => { startSequence() }, 500))
    }
  } else if (settings.gameMode === 'shape') {
    if (state.value !== 'input') return
    lit.value = p; sound(p, 'tap')
    setTimeout(() => (lit.value = -1), 120)
    if (p !== shapePath.value[shapeStep.value]) { lives.value -= 1; combo.value = 0; if (lives.value <= 0) { state.value = 'done'; endGame() }; return }
    shapeStep.value += 1; combo.value += 1; bestCombo.value = Math.max(bestCombo.value, combo.value)
    if (shapeStep.value === shapePath.value.length) {
      score.value += shapePath.value.length * 12; props.onScore(score.value)
      toRefs.push(window.setTimeout(startShape, 400))
    }
  }
  stats.totalTaps += 1
}
function rhythmPress(lane: number) {
  if (state.value !== 'rhythm') return
  const now = performance.now()
  // find closest target in lane
  let best: RTarget | null = null
  let bestDt = Infinity
  for (const t of rhythmTargets.value) {
    if (t.hit || t.lane !== lane) continue
    const dt = Math.abs(t.spawnT - now)
    if (dt < bestDt) { bestDt = dt; best = t }
  }
  if (!best) return
  const w = settings.timingMs * (settings.assistTiming ? 1.5 : 1)
  if (bestDt > w * 1.5) { combo.value = 0; lives.value -= 1; if (settings.haptics) store.vibrate([12, 8, 12]); return }
  best.hit = true
  const j: 'perfect' | 'good' | 'miss' = bestDt < w * 0.5 ? 'perfect' : bestDt < w ? 'good' : 'miss'
  judge.value = { kind: j, ms: best.spawnT - now }
  setTimeout(() => (judge.value = null), 400)
  combo.value = j === 'miss' ? 0 : combo.value + 1
  bestCombo.value = Math.max(bestCombo.value, combo.value)
  const pts = j === 'perfect' ? 50 : j === 'good' ? 25 : 0
  score.value += pts * Math.max(1, Math.floor(combo.value / 5))
  props.onScore(score.value)
  if (j === 'perfect' && settings.haptics) store.vibrate(20)
  // analytics
  const bin = Math.max(0, Math.min(histo.value.length - 1, Math.floor(((best.spawnT - now) + w) / (2 * w / histo.value.length))))
  histo.value[bin] = Math.min(50, histo.value[bin] + 4)
  const pt = padTiming.value[lane]; pt.taps += 1; pt.sumMs += Math.abs(best.spawnT - now); pt.avgMs = pt.sumMs / pt.taps
  sound(lane, 'tap')
  stats.totalTaps += 1
}
function cascadePress(t: CTile) {
  if (state.value !== 'cascade') return
  if (t.order !== cascNext.value) { combo.value = 0; lives.value -= 1; if (settings.haptics) store.vibrate([12, 8, 12]); return }
  t.tapped = true; cascNext.value += 1; combo.value += 1
  bestCombo.value = Math.max(bestCombo.value, combo.value)
  score.value += 20 + combo.value; props.onScore(score.value)
  sound(t.color, 'tap')
  stats.totalTaps += 1
}

// ---------- power-ups ----------
function usePower(kind: 'hint' | 'slow' | 'life') {
  if (powerups[kind] <= 0) return
  if (kind === 'hint') {
    if (settings.gameMode === 'sequence') hintIdx.value = seq.value[idx.value]
    if (settings.gameMode === 'shape') hintIdx.value = shapePath.value[shapeStep.value]
    setTimeout(() => (hintIdx.value = -1), 1200)
  }
  if (kind === 'slow') {
    if (settings.gameMode === 'rhythm') for (const t of rhythmTargets.value) t.spawnT += 1500
    if (settings.gameMode === 'cascade') for (const t of cascTiles.value) t.vy *= 0.5
    setTimeout(() => { if (settings.gameMode === 'cascade') for (const t of cascTiles.value) t.vy *= 2 }, 4000)
  }
  if (kind === 'life') { lives.value += 1; maxLives.value = Math.max(maxLives.value, lives.value) }
  powerups[kind] -= 1
  persist()
  if (settings.haptics) store.vibrate(40)
}

// ---------- audio ----------
let ac: AudioContext | null = null
function audio() { if (!ac) ac = new (window.AudioContext || (window as any).webkitAudioContext)(); return ac! }
function sound(pad: number, kind: 'show' | 'tap') {
  if (!settings.audio) return
  const c = audio()
  const o = c.createOscillator(); const g = c.createGain()
  const baseFreq = 220 * Math.pow(2, pad / 12)
  o.frequency.value = baseFreq
  o.type = settings.soundPack === '8bit' ? 'square' : settings.soundPack === 'drums' ? 'sawtooth' : settings.soundPack === 'synth' ? 'triangle' : 'sine'
  o.connect(g); g.connect(c.destination)
  g.gain.setValueAtTime(kind === 'tap' ? 0.18 : 0.12, c.currentTime)
  g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + 0.18)
  o.start(); o.stop(c.currentTime + 0.18)
}
function speak(s: string) { try { const u = new SpeechSynthesisUtterance(s); u.rate = 1.05; u.volume = 0.6; speechSynthesis.speak(u) } catch {} }

// ---------- calibration ----------
function startCalibration() {
  if (calibrating.value) { cancelCal(); return }
  calibrating.value = true; calTaps = []
  let beat = 0
  calMetroId = window.setInterval(() => {
    metroOn.value = true; sound(0, 'show')
    setTimeout(() => (metroOn.value = false), 80)
    beat += 1
    if (beat >= 8) cancelCal()
  }, 600)
  window.addEventListener('keydown', calKey)
  window.addEventListener('pointerdown', calTap)
}
function cancelCal() {
  if (calMetroId) clearInterval(calMetroId); calMetroId = null
  window.removeEventListener('keydown', calKey)
  window.removeEventListener('pointerdown', calTap)
  if (calTaps.length >= 4) {
    const dts = calTaps.slice(1).map((t, i) => t - calTaps[i])
    const avg = dts.reduce((a, b) => a + b, 0) / dts.length
    const variance = dts.reduce((s, v) => s + (v - avg) ** 2, 0) / dts.length
    settings.timingMs = Math.max(50, Math.min(220, Math.round(Math.sqrt(variance) + 60)))
    calLatency.value = Math.round(Math.sqrt(variance))
    persist()
  }
  calibrating.value = false
}
function calKey(e: KeyboardEvent) { if (e.code === 'Space') calTaps.push(performance.now()) }
function calTap() { calTaps.push(performance.now()) }

// ---------- end ----------
function endGame() {
  if (rRaf) cancelAnimationFrame(rRaf); rRaf = null
  if (cRaf) cancelAnimationFrame(cRaf); cRaf = null
  if (score.value > best.value) best.value = score.value
  stats.bestStreak = Math.max(stats.bestStreak, bestCombo.value)
  stats.bestAccuracy = Math.max(stats.bestAccuracy, parseInt(acc.value as any) || 0)
  // tip
  if (judge.value?.ms && judge.value.ms < -30) tip.value = `You taped ~${Math.abs(judge.value.ms).toFixed(0)}ms late — anticipate the beat.`
  else if (judge.value?.ms && judge.value.ms > 30) tip.value = `You tapped ${judge.value.ms.toFixed(0)}ms early — wait for the beat.`
  else tip.value = 'Strong rhythm — try increasing the BPM.'
  analyticsOpen.value = true
  persist()
  store.beep('lose')
  props.onGameOver(score.value)
}

const acc = computed(() => {
  const total = stats.totalTaps + 1
  return Math.round((bestCombo.value / Math.max(1, total)) * 100)
})

// share
const shareLabel = ref('Copy daily seed link')
function copyShare() {
  const url = `${location.origin}${location.pathname}?game=tap&seed=${seed}`
  navigator.clipboard?.writeText(url).then(() => { shareLabel.value = 'Copied!'; setTimeout(() => shareLabel.value = 'Copy daily seed link', 1200) })
}

function heatColor(ms: number) { return `rgba(34, 211, 238, ${Math.max(0.1, Math.min(0.9, 1 - ms / 200))})` }

function onKey(e: KeyboardEvent) {
  if (state.value === 'input') {
    const n = numPads.value
    if (e.key >= '1' && e.key <= '9' && +e.key <= n) press(+e.key - 1)
  }
}

onMounted(() => { reset() })
onBeforeUnmount(() => {
  clearTo()
  if (rRaf) cancelAnimationFrame(rRaf)
  if (cRaf) cancelAnimationFrame(cRaf)
  if (calMetroId) clearInterval(calMetroId)
})
watch(() => props.running, (v) => v && reset())
watch(() => props.mode, (m) => { if (m === 'time') { settings.gameMode = 'rhythm'; reset() } })
</script>

<style scoped>
.pulse { display: flex; flex-direction: column; align-items: center; gap: 10px; outline: none; color: #fff; }
.hud { width: min(440px, 92vw); display: flex; justify-content: space-between; gap: 6px; flex-wrap: wrap; }
.left, .right { display: flex; gap: 4px; flex-wrap: wrap; }
.chip { background: rgba(255,255,255,0.08); padding: 4px 10px; border-radius: 999px; display: inline-flex; gap: 6px; align-items: center; font-size: 12px; }
.lbl { opacity: 0.6; font-size: 10px; letter-spacing: 1px; }
.pill { background: rgba(255,255,255,0.1); color: #fff; border: 1px solid rgba(255,255,255,0.2); padding: 6px 12px; border-radius: 999px; cursor: pointer; font-size: 12px; }
.pill:disabled { opacity: 0.4; cursor: not-allowed; }
.pill.primary { background: linear-gradient(135deg, #ec4899, #a855f7); border-color: transparent; }
.status { font-size: 14px; min-height: 1.5em; transition: color 0.2s; opacity: 0.9; }
.status.perfect { color: #fcd34d; font-weight: 700; }
.status.good { color: #22c55e; }
.status.miss { color: #ef4444; }
.board { display: grid; gap: 12px; width: min(420px, 90vw); aspect-ratio: 1; }
.pad { aspect-ratio: 1; border: 2px solid rgba(255,255,255,0.15); border-radius: 16px; cursor: pointer; transition: filter 0.1s, transform 0.1s; position: relative; color: #fff; font-size: 18px; font-weight: 700; }
.pad:disabled { cursor: not-allowed; }
.pad.lit { filter: brightness(1.7); transform: scale(0.95); }
.pad.hint { box-shadow: 0 0 24px #fcd34d, inset 0 0 12px #fcd34d; }
.pad.shape .dot { background: rgba(255,255,255,0.3); border-radius: 50%; padding: 4px 8px; font-size: 12px; }
.cb { font-size: 24px; opacity: 0.9; }
.rhythm { width: min(420px, 92vw); height: 60vh; max-height: 420px; position: relative; background: rgba(0,0,0,0.4); border-radius: 14px; overflow: hidden; display: flex; }
.lane { flex: 1; opacity: 0.15; transition: opacity 0.1s; cursor: pointer; }
.lane-bg { display: block; width: 100%; height: 100%; }
.targets { position: absolute; inset: 0; pointer-events: none; }
.target { position: absolute; transform: translate(-50%, 0); width: 16%; aspect-ratio: 1; border-radius: 50%; box-shadow: 0 0 12px currentColor; }
.target.p0 { background: #ef4444; color: #ef4444; } .target.p1 { background: #22c55e; color: #22c55e; } .target.p2 { background: #3b82f6; color: #3b82f6; } .target.p3 { background: #facc15; color: #facc15; }
.hitline { position: absolute; bottom: 8%; left: 0; right: 0; height: 2px; background: rgba(255,255,255,0.5); box-shadow: 0 0 8px #fff; }
.cascade { width: min(420px, 92vw); height: 60vh; max-height: 420px; position: relative; background: rgba(0,0,0,0.4); border-radius: 14px; overflow: hidden; }
.ctile { position: absolute; width: 14%; aspect-ratio: 1; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #1a1a2e; font-weight: 800; transition: opacity 0.15s; }
.ctile.next { box-shadow: 0 0 16px #fcd34d; }
.cal-bar { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 8px; }
.metro { width: 14px; height: 14px; border-radius: 50%; background: rgba(255,255,255,0.2); transition: background 0.06s; }
.metro.pulse { background: #fcd34d; box-shadow: 0 0 16px #fcd34d; }
.footer { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.hint { font-size: 12px; opacity: 0.65; }
.theme-particles { background: radial-gradient(circle at 30% 20%, #1e1b4b, #000); }
.theme-dark { background: #000; }
.theme-nature { background: linear-gradient(135deg, #064e3b, #022c22); }
.theme-synth { background: linear-gradient(135deg, #4c1d95, #1e1b4b); }
.sheet { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 14px; overflow: auto; }
.card { background: #0b0e1a; padding: 16px; border-radius: 14px; max-width: 460px; width: 100%; max-height: 90vh; overflow: auto; }
.card h3 { margin: 0 0 8px 0; }
.card h4 { margin: 12px 0 6px 0; font-size: 13px; opacity: 0.85; }
.card label { display: block; font-size: 12px; margin: 4px 0; opacity: 0.85; }
.chips { display: flex; gap: 4px; flex-wrap: wrap; }
.chip-btn { background: rgba(255,255,255,0.06); color: #fff; border: 1px solid rgba(255,255,255,0.15); padding: 4px 10px; border-radius: 999px; cursor: pointer; font-size: 11px; text-transform: capitalize; }
.chip-btn.on { background: linear-gradient(135deg, #ec4899, #a855f7); border-color: transparent; }
.chip-btn.locked { opacity: 0.45; }
.stats { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; }
.stats > div { background: rgba(255,255,255,0.04); padding: 6px; border-radius: 6px; display: flex; flex-direction: column; }
.stats b { font-size: 16px; }
.stats span { font-size: 10px; opacity: 0.6; }
.heat { display: grid; gap: 2px; margin: 4px 0 8px; }
.hc { padding: 8px; text-align: center; border-radius: 4px; font-size: 11px; color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 6px; }
.hc-pad { width: 8px; height: 8px; border-radius: 50%; }
.chart { width: 100%; height: 80px; background: rgba(255,255,255,0.04); border-radius: 6px; }
.tip { background: rgba(250, 204, 21, 0.15); padding: 8px; border-left: 3px solid #facc15; font-size: 12px; margin: 8px 0; border-radius: 4px; }
.rmo .pad, .rmo .target, .rmo .ctile { transition: none !important; animation: none !important; }
.hc .pad { border-width: 4px; }
.oh .board { margin-left: auto; }
</style>
