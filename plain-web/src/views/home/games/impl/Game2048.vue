<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <div class="board"
      @touchstart.passive="ts"
      @touchend.passive="te"
    >
      <div v-for="(row, ri) in board" :key="ri" class="row">
        <div v-for="(v, ci) in row" :key="ci" class="cell" :data-v="v">
          <span v-if="v">{{ v }}</span>
        </div>
      </div>
    </div>
    <div class="controls">
      <button class="undo" :disabled="!history.length" @click="undo"><i-lucide:rotate-ccw /> Undo</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const SIZE = 4
const board = ref<number[][]>([])
const history = ref<{ board: number[][]; score: number }[]>([])
let score = 0

function blank(): number[][] {
  return Array.from({ length: SIZE }, () => Array(SIZE).fill(0))
}

function add() {
  const empty: [number, number][] = []
  for (let r = 0; r < SIZE; r++) for (let c = 0; c < SIZE; c++) if (!board.value[r][c]) empty.push([r, c])
  if (!empty.length) return
  const [r, c] = empty[Math.floor(Math.random() * empty.length)]
  board.value[r][c] = Math.random() < 0.9 ? 2 : 4
}

function reset() {
  board.value = blank()
  score = 0
  history.value = []
  add(); add()
  props.onScore(0)
}

function snapshot() {
  history.value.push({ board: board.value.map((r) => r.slice()), score })
  if (history.value.length > 20) history.value.shift()
}

function undo() {
  const h = history.value.pop()
  if (!h) return
  board.value = h.board
  score = h.score
  props.onScore(score)
}

function rotate(b: number[][]): number[][] {
  const n: number[][] = blank()
  for (let r = 0; r < SIZE; r++) for (let c = 0; c < SIZE; c++) n[c][SIZE - 1 - r] = b[r][c]
  return n
}

function compress(row: number[]): { row: number[]; gained: number } {
  let r = row.filter((v) => v)
  let gained = 0
  for (let i = 0; i < r.length - 1; i++) {
    if (r[i] === r[i + 1]) {
      r[i] *= 2
      gained += r[i]
      r.splice(i + 1, 1)
    }
  }
  while (r.length < SIZE) r.push(0)
  return { row: r, gained }
}

function moveLeft(): boolean {
  let moved = false
  let gained = 0
  const newBoard: number[][] = []
  for (const row of board.value) {
    const before = row.join(',')
    const { row: nr, gained: g } = compress(row)
    if (nr.join(',') !== before) moved = true
    gained += g
    newBoard.push(nr)
  }
  if (moved) {
    board.value = newBoard
    score += gained
    props.onScore(score)
    if (gained > 0) store.beep('tick')
  }
  return moved
}

function move(dir: 'L' | 'R' | 'U' | 'D') {
  snapshot()
  let b = board.value.map((r) => r.slice())
  const rot = { L: 0, U: 1, R: 2, D: 3 }[dir]
  for (let i = 0; i < rot; i++) b = rotate(b)
  board.value = b
  const moved = moveLeft()
  b = board.value
  for (let i = 0; i < (4 - rot) % 4; i++) b = rotate(b)
  board.value = b
  if (!moved) { history.value.pop(); return }
  add()
  if (gameOver()) {
    props.onGameOver()
  }
}

function gameOver(): boolean {
  for (let r = 0; r < SIZE; r++) for (let c = 0; c < SIZE; c++) {
    if (!board.value[r][c]) return false
    if (c + 1 < SIZE && board.value[r][c] === board.value[r][c + 1]) return false
    if (r + 1 < SIZE && board.value[r][c] === board.value[r + 1][c]) return false
  }
  return true
}

function onKey(e: KeyboardEvent) {
  const m: Record<string, 'L' | 'R' | 'U' | 'D'> = {
    ArrowLeft: 'L', a: 'L',
    ArrowRight: 'R', d: 'R',
    ArrowUp: 'U', w: 'U',
    ArrowDown: 'D', s: 'D',
  }
  if (m[e.key]) { e.preventDefault(); move(m[e.key]) }
}

let sx = 0, sy = 0
function ts(e: TouchEvent) { const t = e.touches[0]; sx = t.clientX; sy = t.clientY }
function te(e: TouchEvent) {
  const t = e.changedTouches[0]
  const dx = t.clientX - sx, dy = t.clientY - sy
  if (Math.abs(dx) < 20 && Math.abs(dy) < 20) return
  if (Math.abs(dx) > Math.abs(dy)) move(dx > 0 ? 'R' : 'L')
  else move(dy > 0 ? 'D' : 'U')
}

onMounted(() => { reset(); root.value?.focus() })
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 14px; outline: none; }
.board { background: #1f1b3b; padding: 8px; border-radius: 14px; touch-action: none; }
.row { display: flex; }
.cell { width: 64px; height: 64px; margin: 4px; border-radius: 10px; background: rgba(255,255,255,0.06); display: flex; align-items: center; justify-content: center; font-weight: 800; color: #fff; font-size: 1.3rem; transition: background 0.15s; }
.cell[data-v="2"] { background: #eee4da; color: #776e65; }
.cell[data-v="4"] { background: #ede0c8; color: #776e65; }
.cell[data-v="8"] { background: #f2b179; }
.cell[data-v="16"] { background: #f59563; }
.cell[data-v="32"] { background: #f67c5f; }
.cell[data-v="64"] { background: #f65e3b; }
.cell[data-v="128"] { background: #edcf72; font-size: 1.1rem; }
.cell[data-v="256"] { background: #edcc61; font-size: 1.1rem; }
.cell[data-v="512"] { background: #edc850; font-size: 1.1rem; }
.cell[data-v="1024"] { background: #edc53f; font-size: 0.95rem; }
.cell[data-v="2048"] { background: #edc22e; font-size: 0.95rem; }
.controls { display: flex; gap: 8px; }
.undo { background: rgba(255,255,255,0.1); color: #fff; border: 1px solid rgba(255,255,255,0.2); padding: 8px 16px; border-radius: 999px; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.undo:disabled { opacity: 0.4; cursor: not-allowed; }
@media (max-width: 480px) {
  .cell { width: 56px; height: 56px; }
}
</style>
