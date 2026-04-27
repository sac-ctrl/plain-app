<template>
  <div class="m2048" :class="['theme-' + settings.theme, 'skin-' + settings.skin, { rmo: settings.reducedMotion, hc: settings.highContrast, oh: settings.oneHanded, cb: settings.colorblind !== 'off' }]" tabindex="0" ref="root" @keydown="onKey">
    <div class="hud">
      <div class="left">
        <div class="chip"><span class="lbl">SCORE</span><strong>{{ score }}</strong></div>
        <div class="chip"><span class="lbl">BEST</span><strong>{{ best }}</strong></div>
        <div class="chip"><span class="lbl">MOVES</span><strong>{{ moves }}</strong></div>
        <div v-if="mode === 'timed'" class="chip warn"><span class="lbl">TIME</span><strong>{{ Math.max(0, timeRemain) }}s</strong></div>
        <div v-if="mode === 'challenge'" class="chip"><span class="lbl">LVL</span><strong>{{ challengeLvl + 1 }}/{{ challengeBoards.length }}</strong></div>
      </div>
      <div class="right">
        <button class="pill" :disabled="!history.length || undosLeft <= 0" @click="undo" :title="`Undo (${undosLeft} left)`">↶ {{ undosLeft }}</button>
        <button class="pill" :disabled="powerups.shuffle <= 0" @click="usePower('shuffle')" title="Shuffle">🔀 {{ powerups.shuffle }}</button>
        <button class="pill" :disabled="powerups.clear <= 0" @click="usePower('clear')" title="Remove the lowest tile">✂ {{ powerups.clear }}</button>
        <button class="pill" @click="settingsOpen = true" title="Settings">⚙</button>
      </div>
    </div>

    <div class="board"
      ref="boardEl"
      @touchstart.passive="ts"
      @touchend.passive="te"
      @mousedown="md"
      @mouseup="mu"
    >
      <div class="cells">
        <div v-for="i in size * size" :key="'c' + i" class="cell" />
      </div>
      <transition-group name="tile" tag="div" class="tiles">
        <div v-for="t in tiles" :key="t.id"
          class="tile"
          :class="['v' + Math.min(t.v, 32768), { merged: t.justMerged, spawned: t.justSpawned }]"
          :style="tileStyle(t)"
        >
          <span class="num">{{ t.v }}</span>
          <span v-if="settings.colorblind !== 'off'" class="cb">{{ cbBadge(t.v) }}</span>
        </div>
      </transition-group>
      <div v-if="dead" class="overlay">
        <div class="end">
          <h3>Game over</h3>
          <p>Score {{ score }} · Best tile {{ bestTile }}</p>
          <button class="pill primary" @click="reset()">New game</button>
        </div>
      </div>
      <div v-if="won && !contunue" class="overlay">
        <div class="end win">
          <h3>You hit 2048! 🎉</h3>
          <button class="pill primary" @click="contunue = true">Continue forever</button>
          <button class="pill" @click="reset()">New game</button>
        </div>
      </div>
    </div>

    <div class="armrow" v-if="settings.arrows">
      <div class="ar-row"><button class="ar" @click="move('U')">▲</button></div>
      <div class="ar-row">
        <button class="ar" @click="move('L')">◀</button>
        <button class="ar mid" @click="move('D')">▼</button>
        <button class="ar" @click="move('R')">▶</button>
      </div>
    </div>

    <div class="footer">
      <span class="hint">Swipe (or arrow keys / WASD) to merge tiles. Reach <b>2048</b>.</span>
      <span class="hint">Mode: <b>{{ mode }}</b> · Grid: <b>{{ size }}×{{ size }}</b> · Today’s seed: <b>#{{ daily.seed }}</b></span>
    </div>

    <!-- Settings sheet -->
    <div v-if="settingsOpen" class="sheet" @click.self="settingsOpen = false">
      <div class="card">
        <h3>2048 · Merger Ascension</h3>
        <section>
          <h4>Mode</h4>
          <div class="chips">
            <button v-for="m in ['classic','timed','endless','challenge','daily']" :key="m" class="chip-btn" :class="{ on: mode === m }" @click="setMode(m)">{{ m }}</button>
          </div>
        </section>
        <section>
          <h4>Grid size</h4>
          <div class="chips">
            <button v-for="s in [4,5,6]" :key="s" class="chip-btn" :class="{ on: size === s, locked: !unlocks.grids.includes(s) }" @click="setSize(s)">{{ s }}×{{ s }} <span v-if="!unlocks.grids.includes(s)">🔒</span></button>
          </div>
          <p class="hint" v-if="!unlocks.grids.includes(5)">Reach 1024 to unlock 5×5 · Reach 2048 to unlock 6×6.</p>
        </section>
        <section>
          <h4>Theme</h4>
          <div class="chips">
            <button v-for="t in ['neon','paper','glass','dark']" :key="t" class="chip-btn" :class="{ on: settings.theme === t }" @click="settings.theme = t; persist()">{{ t }}</button>
          </div>
        </section>
        <section>
          <h4>Tile skin</h4>
          <div class="chips">
            <button v-for="s in ['classic','digital','neon','marble','wood','crystal']" :key="s" class="chip-btn" :class="{ on: settings.skin === s, locked: !unlocks.skins.includes(s) }" @click="unlocks.skins.includes(s) && (settings.skin = s, persist())">{{ s }} <span v-if="!unlocks.skins.includes(s)">🔒</span></button>
          </div>
        </section>
        <section>
          <h4>Sound &amp; haptics</h4>
          <label><input type="checkbox" v-model="settings.haptics" @change="persist()"/> Haptics</label>
          <label><input type="checkbox" v-model="settings.sound" @change="persist()"/> Sound effects</label>
          <label><input type="checkbox" v-model="settings.voice" @change="persist()"/> Voice announcer</label>
          <label><input type="checkbox" v-model="settings.dynamicMusic" @change="persist()"/> Dynamic background music</label>
        </section>
        <section>
          <h4>Controls</h4>
          <label>Swipe distance threshold: {{ settings.swipeThreshold }}px<input type="range" min="10" max="80" v-model.number="settings.swipeThreshold" @change="persist()"/></label>
          <label><input type="checkbox" v-model="settings.tilt" @change="setupTilt()"/> Tilt to slide (gyro)</label>
          <label><input type="checkbox" v-model="settings.arrows" @change="persist()"/> On-screen arrow buttons</label>
          <label><input type="checkbox" v-model="settings.bouncy" @change="persist()"/> Overshoot bounce on slide</label>
        </section>
        <section>
          <h4>Accessibility</h4>
          <label><input type="checkbox" v-model="settings.reducedMotion" @change="persist()"/> Reduced motion</label>
          <label><input type="checkbox" v-model="settings.highContrast" @change="persist()"/> High contrast</label>
          <label><input type="checkbox" v-model="settings.oneHanded" @change="persist()"/> One-handed mode (limit swipe zone)</label>
          <label><input type="checkbox" v-model="settings.batterySaver" @change="persist()"/> Battery saver (30 fps)</label>
          <label>Colourblind:
            <select v-model="settings.colorblind" @change="persist()">
              <option value="off">Off</option><option value="protanopia">Protanopia</option><option value="deuteranopia">Deuteranopia</option><option value="tritanopia">Tritanopia</option>
            </select>
          </label>
          <label>Font scale: {{ settings.fontScale.toFixed(2) }}<input type="range" min="0.85" max="1.5" step="0.05" v-model.number="settings.fontScale" @change="persist()"/></label>
        </section>
        <section>
          <h4>Assists</h4>
          <label><input type="checkbox" v-model="settings.assistHints" @change="persist()"/> Show merge hints (glow on mergeable tiles)</label>
          <label><input type="checkbox" v-model="settings.assistAutoUndo" @change="persist()"/> Auto-undo if a swipe would end the game</label>
          <label><input type="checkbox" v-model="settings.assistSlow" @change="persist()"/> Slower animations</label>
          <label><input type="checkbox" v-model="settings.previewNext" @change="persist()"/> Show next-tile preview</label>
        </section>
        <section>
          <h4>Calibration</h4>
          <p class="hint">Swipe across the strip below to auto-tune the swipe threshold.</p>
          <div class="cal" @touchstart.passive="calStart" @touchend.passive="calEnd" @mousedown="calStart" @mouseup="calEnd">Drag here</div>
        </section>
        <section>
          <h4>Stats</h4>
          <div class="stats">
            <div><b>{{ stats.totalMerges }}</b><span>Total merges</span></div>
            <div><b>{{ stats.bestTile }}</b><span>Most-merged tile</span></div>
            <div><b>{{ stats.totalScore }}</b><span>Total points</span></div>
            <div><b>{{ stats.totalMoves }}</b><span>Total moves</span></div>
          </div>
        </section>
        <section>
          <h4>Share / friend challenge</h4>
          <button class="pill" @click="copyShare">{{ shareLabel }}</button>
        </section>
        <button class="pill primary" @click="settingsOpen = false">Close</button>
      </div>
    </div>

    <!-- Post-game analytics -->
    <div v-if="analyticsOpen" class="sheet" @click.self="analyticsOpen = false">
      <div class="card">
        <h3>Post-game report</h3>
        <p><b>Score</b> {{ score }} · <b>Best tile</b> {{ bestTile }} · <b>Moves</b> {{ moves }} · <b>Efficiency</b> {{ moves ? (score / moves).toFixed(1) : 0 }} pts/move</p>
        <h4>Cell heatmap (placement frequency)</h4>
        <div class="heat" :style="{ gridTemplateColumns: `repeat(${size}, 1fr)` }">
          <div v-for="(c, i) in heat" :key="i" class="hc" :style="{ background: heatColor(c) }">{{ c }}</div>
        </div>
        <h4>Tile peaks over time</h4>
        <svg :viewBox="`0 0 ${peakHistory.length || 1} 100`" preserveAspectRatio="none" class="chart">
          <polyline :points="peakSvg" fill="none" stroke="#facc15" stroke-width="1.5"/>
        </svg>
        <p class="tip">{{ tip }}</p>
        <h4>Replay</h4>
        <input type="range" :min="0" :max="replay.length - 1" v-model.number="replayIdx" @input="seekReplay()" />
        <p class="hint">Move {{ replayIdx + 1 }} / {{ replay.length }}</p>
        <button class="pill primary" @click="analyticsOpen = false">Close</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: string
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const boardEl = ref<HTMLDivElement>()

// ---------- persistent settings ----------
type Mode = 'classic' | 'timed' | 'endless' | 'challenge' | 'daily'
const SK = 'm2048_settings_v1', UK = 'm2048_unlocks_v1', PK = 'm2048_powerups_v1', SS = 'm2048_stats_v1', BK = 'm2048_best_v1'
const settings = reactive({
  theme: 'neon',
  skin: 'classic',
  haptics: true,
  sound: true,
  voice: false,
  dynamicMusic: true,
  swipeThreshold: 28,
  tilt: false,
  arrows: false,
  bouncy: true,
  reducedMotion: false,
  highContrast: false,
  oneHanded: false,
  batterySaver: false,
  colorblind: 'off',
  fontScale: 1,
  assistHints: false,
  assistAutoUndo: false,
  assistSlow: false,
  previewNext: false,
  losses: 0,
  wins: 0,
})
const unlocks = reactive({
  skins: ['classic'] as string[],
  grids: [4] as number[],
  themes: ['neon','dark'] as string[],
})
const powerups = reactive({ shuffle: 1, clear: 1, undoExtra: 0, lastRefresh: '' })
const stats = reactive({ totalMerges: 0, bestTile: 0, totalScore: 0, totalMoves: 0 })
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
  // unique
  unlocks.skins = Array.from(new Set(unlocks.skins))
  unlocks.grids = Array.from(new Set(unlocks.grids))
}
load()

// daily refill of powerups
function todayKey() { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` }
if (powerups.lastRefresh !== todayKey()) {
  powerups.shuffle = Math.min(3, powerups.shuffle + 1)
  powerups.clear = Math.min(3, powerups.clear + 1)
  powerups.undoExtra = Math.min(5, powerups.undoExtra + 3)
  powerups.lastRefresh = todayKey()
  persist()
}

// ---------- daily seed (mulberry32) ----------
function mulberry32(s: number) { return () => { s |= 0; s = (s + 0x6D2B79F5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 } }
const dailyHash = (() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate() })()
const daily = reactive({ seed: dailyHash, rng: mulberry32(dailyHash) })

// ---------- board state ----------
type Tile = { id: number; r: number; c: number; v: number; justMerged?: boolean; justSpawned?: boolean }
const tiles = ref<Tile[]>([])
let tileIdSeq = 1
const size = ref(4)
const score = ref(0)
const moves = ref(0)
const bestTile = ref(0)
const undosLeft = ref(5)
const history = ref<{ tiles: Tile[]; score: number; moves: number; undosLeft: number }[]>([])
const replay = ref<{ tiles: Tile[]; dir: string; score: number }[]>([])
const replayIdx = ref(0)
const heat = ref<number[]>([])
const peakHistory = ref<number[]>([])
const dead = ref(false)
const won = ref(false)
const contunue = ref(false)
const settingsOpen = ref(false)
const analyticsOpen = ref(false)
const mode = ref<Mode>(((props.mode as Mode) === 'time' ? 'timed' : (props.mode as Mode)) || 'classic')
const timeRemain = ref(60)
let timerId: number | null = null
let useDaily = false

// challenge boards: pre-set boards with obstacles (negative -1 = blocked)
const challengeBoards = [
  // [size, prefilled tiles {r,c,v}, blocked cells {r,c}]
  { size: 4, fixed: [{ r: 0, c: 0, v: 2 }, { r: 0, c: 3, v: 2 }, { r: 3, c: 0, v: 4 }, { r: 3, c: 3, v: 4 }], blocked: [{ r: 1, c: 1 }, { r: 2, c: 2 }] },
  { size: 4, fixed: [{ r: 0, c: 0, v: 8 }, { r: 0, c: 1, v: 4 }, { r: 0, c: 2, v: 2 }], blocked: [{ r: 3, c: 0 }, { r: 3, c: 3 }] },
  { size: 5, fixed: [{ r: 2, c: 2, v: 16 }, { r: 0, c: 0, v: 4 }, { r: 0, c: 4, v: 4 }, { r: 4, c: 0, v: 4 }, { r: 4, c: 4, v: 4 }], blocked: [] },
]
const challengeLvl = ref(0)
const blocked = ref<Set<string>>(new Set())

function setSize(s: number) { if (!unlocks.grids.includes(s)) return; size.value = s; reset(); persist() }
function setMode(m: Mode) {
  mode.value = m; useDaily = m === 'daily'
  reset(); persist()
}

function blockKey(r: number, c: number) { return `${r},${c}` }
function emptyCells(): Array<{ r: number; c: number }> {
  const out: Array<{ r: number; c: number }> = []
  const occ = new Set(tiles.value.map(t => blockKey(t.r, t.c)))
  for (let r = 0; r < size.value; r++) for (let c = 0; c < size.value; c++) {
    const k = blockKey(r, c)
    if (!occ.has(k) && !blocked.value.has(k)) out.push({ r, c })
  }
  return out
}
function rng(): number { return useDaily ? daily.rng() : Math.random() }
function spawn(forceVal?: number) {
  const empty = emptyCells()
  if (!empty.length) return null
  const cell = empty[Math.floor(rng() * empty.length)]
  const four = settings.losses >= 3 ? 0.05 : settings.wins >= 3 ? 0.2 : 0.1
  const v = forceVal ?? (rng() < (1 - four) ? 2 : 4)
  const t: Tile = { id: tileIdSeq++, r: cell.r, c: cell.c, v, justSpawned: true }
  tiles.value.push(t)
  return t
}

function reset() {
  if (timerId) { clearInterval(timerId); timerId = null }
  tiles.value = []
  blocked.value = new Set()
  history.value = []
  replay.value = []
  replayIdx.value = 0
  heat.value = Array(size.value * size.value).fill(0)
  peakHistory.value = []
  score.value = 0; moves.value = 0; bestTile.value = 0
  dead.value = false; won.value = false; contunue.value = false
  undosLeft.value = 5 + powerups.undoExtra
  if (useDaily) daily.rng = mulberry32(daily.seed)
  if (mode.value === 'challenge') {
    const b = challengeBoards[challengeLvl.value % challengeBoards.length]
    size.value = b.size
    heat.value = Array(size.value * size.value).fill(0)
    blocked.value = new Set(b.blocked.map(p => blockKey(p.r, p.c)))
    for (const f of b.fixed) tiles.value.push({ id: tileIdSeq++, r: f.r, c: f.c, v: f.v, justSpawned: true })
  } else {
    spawn(); spawn()
  }
  if (mode.value === 'timed') {
    timeRemain.value = 60
    timerId = window.setInterval(() => {
      timeRemain.value -= 1
      if (timeRemain.value <= 0) { endGame() }
    }, 1000)
  }
  props.onScore(0)
}

// ---------- core merge ----------
function move(dir: 'L' | 'R' | 'U' | 'D') {
  if (dead.value || (won.value && !contunue.value)) return
  // snapshot
  const snap = JSON.parse(JSON.stringify({ tiles: tiles.value, score: score.value, moves: moves.value, undosLeft: undosLeft.value }))
  const dxV = dir === 'L' ? -1 : dir === 'R' ? 1 : 0
  const dyV = dir === 'U' ? -1 : dir === 'D' ? 1 : 0
  const grid: (Tile | null)[][] = Array.from({ length: size.value }, () => Array(size.value).fill(null))
  for (const t of tiles.value) grid[t.r][t.c] = t
  const order: Array<{ r: number; c: number }> = []
  for (let r = 0; r < size.value; r++) for (let c = 0; c < size.value; c++) order.push({ r, c })
  if (dir === 'R') order.sort((a, b) => b.c - a.c)
  if (dir === 'D') order.sort((a, b) => b.r - a.r)
  let moved = false
  let gained = 0
  let mergedThisMove = 0
  const newDead: number[] = []
  for (const { r, c } of order) {
    const t = grid[r][c]; if (!t) continue
    let nr = r, nc = c
    while (true) {
      const tr = nr + dyV, tc = nc + dxV
      if (tr < 0 || tr >= size.value || tc < 0 || tc >= size.value) break
      if (blocked.value.has(blockKey(tr, tc))) break
      const occ = grid[tr][tc]
      if (!occ) { grid[tr][tc] = t; grid[nr][nc] = null; nr = tr; nc = tc; continue }
      if (occ.v === t.v && !(occ as any).__merged) {
        occ.v *= 2; (occ as any).__merged = true; occ.justMerged = true
        gained += occ.v; mergedThisMove += 1
        bestTile.value = Math.max(bestTile.value, occ.v)
        if (occ.v >= 2048 && !won.value) { won.value = true; store.beep('win'); if (settings.voice) speak('Two thousand forty eight!') }
        if (settings.haptics) store.vibrate(occ.v >= 1024 ? 36 : occ.v >= 512 ? 22 : 14)
        if (settings.sound) bleep(440 + Math.log2(occ.v) * 60, 0.06)
        newDead.push(t.id)
        grid[nr][nc] = null
        nr = tr; nc = tc
        break
      }
      break
    }
    if (nr !== r || nc !== c) { moved = true; t.r = nr; t.c = nc }
  }
  if (!moved) {
    if (settings.haptics) store.vibrate([6, 8, 6])
    return
  }
  // remove dead
  tiles.value = tiles.value.filter(t => !newDead.includes(t.id))
  for (const t of tiles.value) { delete (t as any).__merged; t.justSpawned = false }
  setTimeout(() => { for (const t of tiles.value) t.justMerged = false }, 220)
  score.value += gained
  if (gained > 0) props.onScore(score.value)
  moves.value += 1
  stats.totalMerges += mergedThisMove
  stats.totalMoves += 1
  stats.bestTile = Math.max(stats.bestTile, bestTile.value)
  if (mergedThisMove > 0) {
    if (settings.dynamicMusic) intensity.value = Math.min(1, Math.log2(bestTile.value) / 12)
  }
  // spawn after move (skip for challenge static feel? still spawn but limited)
  if (mode.value !== 'challenge') spawn()
  else if (Math.random() < 0.4) spawn()
  // heat update
  for (const t of tiles.value) heat.value[t.r * size.value + t.c] = (heat.value[t.r * size.value + t.c] || 0) + 1
  peakHistory.value.push(bestTile.value)
  // replay
  replay.value.push({ tiles: JSON.parse(JSON.stringify(tiles.value)), dir, score: score.value })
  // history
  history.value.push(snap)
  if (history.value.length > 30) history.value.shift()
  // game-over check (or assist auto-undo)
  if (isStuck()) {
    if (settings.assistAutoUndo && history.value.length) { undo(true); return }
    endGame()
    return
  }
  // unlocks
  if (bestTile.value >= 1024 && !unlocks.grids.includes(5)) unlocks.grids.push(5)
  if (bestTile.value >= 2048 && !unlocks.grids.includes(6)) unlocks.grids.push(6)
  if (mergedThisMove >= 3 && !unlocks.skins.includes('digital')) unlocks.skins.push('digital')
  if (bestTile.value >= 512 && !unlocks.skins.includes('neon')) unlocks.skins.push('neon')
  if (bestTile.value >= 1024 && !unlocks.skins.includes('marble')) unlocks.skins.push('marble')
  if (bestTile.value >= 2048 && !unlocks.skins.includes('crystal')) unlocks.skins.push('crystal')
  if (stats.totalMerges >= 100 && !unlocks.skins.includes('wood')) unlocks.skins.push('wood')
  // endless: at 2048, expand grid
  if (mode.value === 'endless' && bestTile.value >= 2048 && size.value < 6) { size.value += 1; heat.value = heat.value.concat(Array(size.value * size.value - heat.value.length).fill(0)); spawn() }
  if (mode.value === 'challenge' && tiles.value.every(t => t.v >= 16) && tiles.value.length >= 4) {
    challengeLvl.value = (challengeLvl.value + 1) % challengeBoards.length
    setTimeout(() => reset(), 600)
  }
  persist()
}

function isStuck(): boolean {
  if (emptyCells().length) return false
  const grid: (Tile | null)[][] = Array.from({ length: size.value }, () => Array(size.value).fill(null))
  for (const t of tiles.value) grid[t.r][t.c] = t
  for (let r = 0; r < size.value; r++) for (let c = 0; c < size.value; c++) {
    const t = grid[r][c]; if (!t) continue
    if (c + 1 < size.value && grid[r][c + 1]?.v === t.v) return false
    if (r + 1 < size.value && grid[r + 1][c]?.v === t.v) return false
  }
  return true
}

function undo(silent = false) {
  if (!history.value.length || undosLeft.value <= 0) return
  const h = history.value.pop()!
  tiles.value = h.tiles
  score.value = h.score
  moves.value = h.moves
  if (!silent) undosLeft.value -= 1
  props.onScore(score.value)
  if (settings.haptics) store.vibrate(20)
}

function usePower(kind: 'shuffle' | 'clear') {
  if (powerups[kind] <= 0) return
  if (kind === 'shuffle') {
    const cells = emptyCells().concat(tiles.value.map(t => ({ r: t.r, c: t.c })))
    const shuffled = [...cells].sort(() => Math.random() - 0.5)
    tiles.value.forEach((t, i) => { t.r = shuffled[i].r; t.c = shuffled[i].c })
  }
  if (kind === 'clear') {
    if (!tiles.value.length) return
    let lo = tiles.value[0]
    for (const t of tiles.value) if (t.v < lo.v) lo = t
    tiles.value = tiles.value.filter(t => t.id !== lo.id)
  }
  powerups[kind] -= 1
  persist()
  if (settings.haptics) store.vibrate(40)
}

function endGame() {
  if (timerId) { clearInterval(timerId); timerId = null }
  dead.value = true
  if (score.value > best.value) best.value = score.value
  stats.totalScore += score.value
  if (won.value) settings.wins += 1; else settings.losses += 1
  // tip generation
  const c0 = (heat.value[0] || 0) + (heat.value[size.value - 1] || 0) + (heat.value[size.value * (size.value - 1)] || 0) + (heat.value[size.value * size.value - 1] || 0)
  const total = heat.value.reduce((s, n) => s + (n || 0), 0) || 1
  if (c0 / total > 0.5) tip.value = 'Great corner discipline — you locked your highest tiles in corners.'
  else if (mergedRunMax < 3) tip.value = 'Try to chain merges in one swipe — line up equal tiles before pushing.'
  else tip.value = 'Solid run. Pre-stack a row before reaching for the boss tile.'
  analyticsOpen.value = true
  persist()
  store.beep('lose')
  props.onGameOver(score.value)
}

const tip = ref('')
const mergedRunMax = 0
const peakSvg = computed(() => peakHistory.value.map((v, i) => `${i},${100 - Math.min(100, Math.log2(Math.max(2, v)) * 7)}`).join(' '))
function heatColor(c: number) { const max = Math.max(...heat.value, 1); const a = c / max; return `rgba(250, 204, 21, ${0.1 + a * 0.7})` }
function cbBadge(v: number): string { const b = Math.log2(v); return ['◇','◯','△','▽','◊','▣','✦','✪','✱','✜','❖','⬢','⬣','⬡','◐','◑'][Math.max(0, Math.min(15, b - 1))] }

// ---------- inputs ----------
let sx = 0, sy = 0, mvDown = false
function ts(e: TouchEvent) { const t = e.touches[0]; sx = t.clientX; sy = t.clientY }
function te(e: TouchEvent) {
  const t = e.changedTouches[0]
  if (settings.oneHanded && t.clientY < window.innerHeight * 0.4) return
  swipe(t.clientX - sx, t.clientY - sy)
}
function md(e: MouseEvent) { sx = e.clientX; sy = e.clientY; mvDown = true }
function mu(e: MouseEvent) { if (!mvDown) return; mvDown = false; swipe(e.clientX - sx, e.clientY - sy) }
function swipe(dx: number, dy: number) {
  const th = settings.swipeThreshold
  if (Math.abs(dx) < th && Math.abs(dy) < th) {
    // dead zone
    if (Math.abs(dx) > th * 0.4 || Math.abs(dy) > th * 0.4) {
      // ambiguous diagonal → ignore
    }
    return
  }
  // swipe buffer
  if (animating) { queued = nextDir(dx, dy); return }
  move(nextDir(dx, dy))
}
function nextDir(dx: number, dy: number): 'L' | 'R' | 'U' | 'D' {
  return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'R' : 'L') : (dy > 0 ? 'D' : 'U')
}
let animating = false, queued: 'L'|'R'|'U'|'D'|null = null
function onKey(e: KeyboardEvent) {
  const m: Record<string, 'L' | 'R' | 'U' | 'D'> = { ArrowLeft: 'L', a: 'L', ArrowRight: 'R', d: 'R', ArrowUp: 'U', w: 'U', ArrowDown: 'D', s: 'D' }
  if (m[e.key]) { e.preventDefault(); move(m[e.key]) }
  if (e.key === 'z' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); undo() }
}
// gyro tilt
let lastTiltMove = 0, tiltHandler: ((e: DeviceMotionEvent) => void) | null = null
function setupTilt() {
  persist()
  if (!settings.tilt) {
    if (tiltHandler) window.removeEventListener('devicemotion', tiltHandler); tiltHandler = null; return
  }
  tiltHandler = (e: DeviceMotionEvent) => {
    const g = e.accelerationIncludingGravity; if (!g) return
    if (Date.now() - lastTiltMove < 350) return
    if (Math.abs(g.x ?? 0) > 5) { lastTiltMove = Date.now(); move((g.x ?? 0) > 0 ? 'L' : 'R') }
    else if (Math.abs(g.y ?? 0) > 5) { lastTiltMove = Date.now(); move((g.y ?? 0) > 0 ? 'D' : 'U') }
  }
  window.addEventListener('devicemotion', tiltHandler)
}

// calibration
let calStartT = 0, calStartX = 0, calStartY = 0
function calStart(e: TouchEvent | MouseEvent) {
  calStartT = Date.now()
  if ('touches' in e) { calStartX = e.touches[0].clientX; calStartY = e.touches[0].clientY }
  else { calStartX = (e as MouseEvent).clientX; calStartY = (e as MouseEvent).clientY }
}
function calEnd(e: TouchEvent | MouseEvent) {
  const dur = Date.now() - calStartT
  let x = calStartX, y = calStartY
  if ('changedTouches' in e) { x = e.changedTouches[0].clientX; y = e.changedTouches[0].clientY }
  else { x = (e as MouseEvent).clientX; y = (e as MouseEvent).clientY }
  const d = Math.hypot(x - calStartX, y - calStartY)
  if (d < 8) return
  settings.swipeThreshold = Math.max(12, Math.min(72, Math.round(d * 0.6)))
  persist()
}

// ---------- audio ----------
let ac: AudioContext | null = null
let musicG: GainNode | null = null
const intensity = ref(0)
function audio() { if (!ac) ac = new (window.AudioContext || (window as any).webkitAudioContext)(); return ac! }
function bleep(freq: number, dur: number) {
  if (!settings.sound) return
  const c = audio(); const o = c.createOscillator(); const g = c.createGain()
  o.type = 'triangle'; o.frequency.value = freq; o.connect(g); g.connect(c.destination)
  g.gain.setValueAtTime(0.18, c.currentTime); g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + dur)
  o.start(); o.stop(c.currentTime + dur)
}
function speak(s: string) { try { const u = new SpeechSynthesisUtterance(s); u.rate = 1.05; u.volume = 0.6; speechSynthesis.speak(u) } catch {} }
function startMusic() {
  if (!settings.dynamicMusic || !settings.sound) return
  const c = audio()
  if (musicG) return
  musicG = c.createGain(); musicG.gain.value = 0.025; musicG.connect(c.destination)
  ;[110, 138.6, 165, 220].forEach((f, i) => {
    const o = c.createOscillator(); o.frequency.value = f; o.type = i % 2 ? 'sine' : 'triangle'
    const og = c.createGain(); og.gain.value = i === 0 ? 1 : 0
    o.connect(og); og.connect(musicG!)
    o.start()
    setInterval(() => { if (musicG) og.gain.value = i / 4 < intensity.value ? 1 : 0 }, 800)
  })
}

// ---------- share ----------
const shareLabel = ref('Copy daily seed link')
function copyShare() {
  const url = `${location.origin}${location.pathname}?game=2048&seed=${daily.seed}`
  navigator.clipboard?.writeText(url).then(() => { shareLabel.value = 'Copied!'; setTimeout(() => shareLabel.value = 'Copy daily seed link', 1200) })
}

// ---------- positioning ----------
function tileStyle(t: Tile): Record<string, string> {
  const cellPct = 100 / size.value
  const dur = settings.assistSlow ? 220 : settings.reducedMotion ? 60 : 130
  return {
    left: `calc(${t.c * cellPct}% + 4px)`,
    top: `calc(${t.r * cellPct}% + 4px)`,
    width: `calc(${cellPct}% - 8px)`,
    height: `calc(${cellPct}% - 8px)`,
    transition: `left ${dur}ms ${settings.bouncy && !settings.reducedMotion ? 'cubic-bezier(0.34,1.56,0.64,1)' : 'ease-out'}, top ${dur}ms ease-out`,
    fontSize: `${Math.max(0.85, settings.fontScale - Math.min(0.5, Math.log10(t.v) * 0.18))}rem`,
  }
}

// ---------- lifecycle ----------
onMounted(() => {
  reset()
  setupTilt()
  startMusic()
  root.value?.focus()
})
onBeforeUnmount(() => {
  if (timerId) clearInterval(timerId)
  if (tiltHandler) window.removeEventListener('devicemotion', tiltHandler)
})
watch(() => props.running, (v) => v && reset())

function seekReplay() {
  const r = replay.value[replayIdx.value]; if (!r) return
  tiles.value = JSON.parse(JSON.stringify(r.tiles))
}
</script>

<style scoped>
.m2048 { display: flex; flex-direction: column; align-items: center; gap: 10px; outline: none; color: #fff; --gap: 6px; }
.hud { width: min(440px, 92vw); display: flex; justify-content: space-between; gap: 6px; flex-wrap: wrap; }
.left, .right { display: flex; gap: 4px; flex-wrap: wrap; }
.chip { background: rgba(255,255,255,0.06); padding: 4px 10px; border-radius: 999px; display: inline-flex; gap: 6px; align-items: center; font-size: 12px; }
.chip.warn { color: #fcd34d; }
.lbl { opacity: 0.6; font-size: 10px; letter-spacing: 1px; }
.pill { background: rgba(255,255,255,0.1); color: #fff; border: 1px solid rgba(255,255,255,0.2); padding: 6px 12px; border-radius: 999px; cursor: pointer; font-size: 12px; }
.pill:disabled { opacity: 0.35; cursor: not-allowed; }
.pill.primary { background: linear-gradient(135deg, #ec4899, #a855f7); border-color: transparent; }
.board { position: relative; width: min(440px, 92vw); aspect-ratio: 1; background: rgba(20, 18, 40, 0.6); border-radius: 14px; overflow: hidden; padding: 4px; touch-action: none; }
.cells { position: absolute; inset: 4px; display: grid; grid-template-columns: repeat(v-bind(size), 1fr); grid-auto-rows: 1fr; gap: 8px; }
.cell { background: rgba(255,255,255,0.04); border-radius: 10px; }
.tiles { position: absolute; inset: 4px; }
.tile { position: absolute; display: flex; align-items: center; justify-content: center; border-radius: 10px; font-weight: 800; box-shadow: 0 2px 6px rgba(0,0,0,0.3); will-change: transform, left, top; }
.tile.merged { animation: pop 220ms ease-out; }
.tile.spawned { animation: drop 200ms ease-out; }
@keyframes pop { 0% { transform: scale(1); } 50% { transform: scale(1.15); } 100% { transform: scale(1); } }
@keyframes drop { 0% { transform: scale(0.4); opacity: 0; } 100% { transform: scale(1); opacity: 1; } }
.tile .num { color: #fff; }
.tile.v2 { background: #eee4da; } .tile.v2 .num { color: #776e65; }
.tile.v4 { background: #ede0c8; } .tile.v4 .num { color: #776e65; }
.tile.v8 { background: #f2b179; } .tile.v16 { background: #f59563; } .tile.v32 { background: #f67c5f; } .tile.v64 { background: #f65e3b; }
.tile.v128 { background: #edcf72; } .tile.v256 { background: #edcc61; } .tile.v512 { background: #edc850; }
.tile.v1024 { background: #2563eb; } .tile.v2048 { background: #1d4ed8; } .tile.v4096 { background: #7c3aed; } .tile.v8192 { background: #c026d3; } .tile.v16384, .tile.v32768 { background: linear-gradient(135deg, #06b6d4, #a855f7, #ec4899); }
.cb { font-size: 0.5em; opacity: 0.65; margin-left: 4px; }
.theme-paper { background: #f5f1e8; }
.theme-paper .board { background: #fff; }
.theme-paper .chip { background: #00000010; color: #1a1a1a; }
.theme-glass .board { background: rgba(255,255,255,0.06); backdrop-filter: blur(14px); border: 1px solid rgba(255,255,255,0.2); }
.theme-dark { background: #000; }
.skin-digital .tile .num { font-family: 'Courier New', monospace; }
.skin-neon .tile { box-shadow: 0 0 16px currentColor inset; }
.skin-marble .tile { background-blend-mode: overlay; }
.skin-crystal .tile { backdrop-filter: blur(6px); }
.armrow { display: flex; flex-direction: column; align-items: center; gap: 4px; margin-top: 4px; }
.ar-row { display: flex; gap: 4px; }
.ar { width: 50px; height: 36px; border-radius: 8px; background: rgba(255,255,255,0.08); color: #fff; border: none; cursor: pointer; }
.ar.mid { width: 64px; }
.footer { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.hint { font-size: 12px; opacity: 0.65; }
.overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.65); display: flex; align-items: center; justify-content: center; }
.end { background: #1a1a2e; padding: 18px; border-radius: 14px; text-align: center; }
.sheet { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 14px; overflow: auto; }
.card { background: #0b0e1a; padding: 16px; border-radius: 14px; max-width: 460px; width: 100%; max-height: 90vh; overflow: auto; }
.card h3 { margin: 0 0 8px 0; }
.card h4 { margin: 12px 0 6px 0; font-size: 13px; opacity: 0.85; }
.card label { display: block; font-size: 12px; margin: 4px 0; opacity: 0.85; }
.chips { display: flex; gap: 4px; flex-wrap: wrap; }
.chip-btn { background: rgba(255,255,255,0.06); color: #fff; border: 1px solid rgba(255,255,255,0.15); padding: 4px 10px; border-radius: 999px; cursor: pointer; font-size: 11px; text-transform: capitalize; }
.chip-btn.on { background: linear-gradient(135deg, #ec4899, #a855f7); border-color: transparent; }
.chip-btn.locked { opacity: 0.45; }
.cal { background: rgba(255,255,255,0.05); padding: 16px; text-align: center; border-radius: 8px; touch-action: none; user-select: none; cursor: ew-resize; }
.stats { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; }
.stats > div { background: rgba(255,255,255,0.04); padding: 6px; border-radius: 6px; display: flex; flex-direction: column; }
.stats b { font-size: 16px; }
.stats span { font-size: 10px; opacity: 0.6; }
.heat { display: grid; gap: 2px; margin: 4px 0 8px; }
.hc { padding: 8px; text-align: center; border-radius: 4px; font-size: 11px; color: #1a1a2e; font-weight: 600; }
.chart { width: 100%; height: 80px; background: rgba(255,255,255,0.04); border-radius: 6px; }
.tip { background: rgba(250, 204, 21, 0.15); padding: 8px; border-left: 3px solid #facc15; font-size: 12px; margin: 8px 0; border-radius: 4px; }
.rmo .tile { transition: none !important; animation: none !important; }
.hc .tile { border: 2px solid #fff; }
.cb .tile { box-shadow: 0 0 0 2px #fff inset; }
@media (max-width: 480px) {
  .board { width: 96vw; }
  .ar { width: 44px; height: 32px; }
}
</style>
