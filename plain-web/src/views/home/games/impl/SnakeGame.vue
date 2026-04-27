<template>
  <div class="wrap" tabindex="0" ref="rootRef" @keydown="onKey">
    <canvas ref="canvasRef" class="cv" />
    <div class="powers" v-if="running">
      <div v-for="p in activePowers" :key="p.kind" class="power-pill" :class="p.kind">
        <span>{{ powerLabel(p.kind) }}</span>
        <b>{{ Math.ceil(p.timeLeft / 1000) }}s</b>
      </div>
    </div>
    <div class="dpad" v-if="running">
      <button @click="set(0, -1)"><i-lucide:chevron-up /></button>
      <div class="row">
        <button @click="set(-1, 0)"><i-lucide:chevron-left /></button>
        <button @click="set(1, 0)"><i-lucide:chevron-right /></button>
      </div>
      <button @click="set(0, 1)"><i-lucide:chevron-down /></button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

type PowerKind = 'slow' | 'double' | 'shrink'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'classic' | 'time' | 'survival' | 'challenge'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const canvasRef = ref<HTMLCanvasElement>()
const rootRef = ref<HTMLDivElement>()
const SIZE = 20
let cells = 22
let snake: { x: number; y: number }[] = []
let dir = { x: 1, y: 0 }
let pendingDir = { x: 1, y: 0 }
let food = { x: 10, y: 10 }
let powerUp: { x: number; y: number; kind: PowerKind } | null = null
let powerSpawnT = 0
let walls: { x: number; y: number }[] = []
let timer = 0
let raf = 0
let last = 0
const baseSpeed: Record<string, number> = { easy: 140, medium: 100, hard: 75, insane: 55 }
let speed = 100
let alive = true
let score = 0
let multiplier = 1
const activePowers = reactive<{ kind: PowerKind; timeLeft: number }[]>([])
let timeLeftMs = 0

function powerLabel(k: PowerKind) {
  return ({ slow: '⏳ Slow', double: '✨ x2', shrink: '✂ Shrink' } as Record<PowerKind, string>)[k]
}

function reset() {
  alive = true
  snake = [{ x: 10, y: 11 }, { x: 9, y: 11 }, { x: 8, y: 11 }]
  dir = { x: 1, y: 0 }
  pendingDir = { x: 1, y: 0 }
  speed = baseSpeed[props.difficulty] || 100
  score = 0
  multiplier = 1
  activePowers.splice(0)
  walls = []
  if (props.mode === 'challenge') buildArena()
  placeFood()
  powerUp = null
  powerSpawnT = 0
  timeLeftMs = props.mode === 'time' ? 60000 : 0
  props.onScore(0)
  last = performance.now()
  timer = 0
}

function buildArena() {
  const n = 6 + Math.floor(Math.random() * 4)
  for (let i = 0; i < n; i++) {
    const w = { x: Math.floor(Math.random() * (cells - 2)) + 1, y: Math.floor(Math.random() * (cells - 2)) + 1 }
    if (Math.abs(w.x - 10) < 2 && Math.abs(w.y - 11) < 2) continue
    walls.push(w)
  }
}

function isFree(x: number, y: number): boolean {
  if (snake.find((s) => s.x === x && s.y === y)) return false
  if (walls.find((w) => w.x === x && w.y === y)) return false
  return true
}

function placeFood() {
  for (let i = 0; i < 200; i++) {
    const f = { x: Math.floor(Math.random() * cells), y: Math.floor(Math.random() * cells) }
    if (isFree(f.x, f.y) && !(powerUp && powerUp.x === f.x && powerUp.y === f.y)) { food = f; return }
  }
}

function spawnPowerUp() {
  const kinds: PowerKind[] = ['slow', 'double', 'shrink']
  for (let i = 0; i < 100; i++) {
    const f = { x: Math.floor(Math.random() * cells), y: Math.floor(Math.random() * cells) }
    if (isFree(f.x, f.y) && !(food.x === f.x && food.y === f.y)) {
      powerUp = { ...f, kind: kinds[Math.floor(Math.random() * kinds.length)] }
      return
    }
  }
}

function set(x: number, y: number) {
  if (snake.length > 1 && dir.x === -x && dir.y === -y) return
  pendingDir = { x, y }
}
function onKey(e: KeyboardEvent) {
  const m: Record<string, [number, number]> = {
    ArrowUp: [0, -1], w: [0, -1], ArrowDown: [0, 1], s: [0, 1],
    ArrowLeft: [-1, 0], a: [-1, 0], ArrowRight: [1, 0], d: [1, 0],
  }
  const v = m[e.key]
  if (v) { e.preventDefault(); set(v[0], v[1]) }
}

function applyPower(kind: PowerKind) {
  store.beep('power')
  store.vibrate(60)
  if (kind === 'slow') {
    speed = Math.min(220, speed * 1.6)
    activePowers.push({ kind, timeLeft: 6000 })
  } else if (kind === 'double') {
    multiplier = 2
    activePowers.push({ kind, timeLeft: 8000 })
  } else if (kind === 'shrink') {
    snake = snake.slice(0, Math.max(3, Math.floor(snake.length / 2)))
    activePowers.push({ kind, timeLeft: 1500 })
  }
}

function step() {
  dir = pendingDir
  const head = { x: snake[0].x + dir.x, y: snake[0].y + dir.y }
  if (head.x < 0 || head.x >= cells || head.y < 0 || head.y >= cells) return die()
  if (snake.find((s) => s.x === head.x && s.y === head.y)) return die()
  if (walls.find((w) => w.x === head.x && w.y === head.y)) return die()
  snake.unshift(head)
  if (head.x === food.x && head.y === food.y) {
    score += 10 * multiplier
    props.onScore(score)
    store.beep('tick')
    placeFood()
    if (speed > 35) speed = Math.max(35, speed - 2)
  } else {
    snake.pop()
  }
  if (powerUp && head.x === powerUp.x && head.y === powerUp.y) {
    applyPower(powerUp.kind)
    powerUp = null
  }
}

function die() {
  alive = false
  cancelAnimationFrame(raf)
  props.onGameOver(score)
}

function draw() {
  const c = canvasRef.value!
  const ctx = c.getContext('2d')!
  ctx.fillStyle = '#0b0e1f'
  ctx.fillRect(0, 0, c.width, c.height)
  ctx.strokeStyle = 'rgba(255,255,255,0.05)'
  for (let i = 0; i <= cells; i++) {
    ctx.beginPath(); ctx.moveTo(i * SIZE, 0); ctx.lineTo(i * SIZE, c.height); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(0, i * SIZE); ctx.lineTo(c.width, i * SIZE); ctx.stroke()
  }
  // walls
  for (const w of walls) {
    ctx.fillStyle = '#475569'
    ctx.fillRect(w.x * SIZE + 2, w.y * SIZE + 2, SIZE - 4, SIZE - 4)
  }
  // food
  ctx.fillStyle = '#ec4899'; ctx.shadowColor = '#ec4899'; ctx.shadowBlur = 12
  ctx.fillRect(food.x * SIZE + 3, food.y * SIZE + 3, SIZE - 6, SIZE - 6)
  ctx.shadowBlur = 0
  // power-up
  if (powerUp) {
    const pcol = powerUp.kind === 'slow' ? '#38bdf8' : powerUp.kind === 'double' ? '#facc15' : '#22c55e'
    ctx.fillStyle = pcol; ctx.shadowColor = pcol; ctx.shadowBlur = 16
    ctx.beginPath()
    ctx.arc(powerUp.x * SIZE + SIZE / 2, powerUp.y * SIZE + SIZE / 2, SIZE / 2 - 2, 0, Math.PI * 2)
    ctx.fill()
    ctx.shadowBlur = 0
  }
  // snake
  snake.forEach((s, i) => {
    const t = i / Math.max(1, snake.length)
    ctx.fillStyle = i === 0 ? '#a855f7' : `rgba(99, 102, 241, ${1 - t * 0.5})`
    ctx.shadowColor = multiplier > 1 ? '#facc15' : '#a855f7'
    ctx.shadowBlur = i === 0 ? 14 : 6
    ctx.fillRect(s.x * SIZE + 1, s.y * SIZE + 1, SIZE - 2, SIZE - 2)
  })
  ctx.shadowBlur = 0
  // hud
  ctx.fillStyle = '#fff'; ctx.font = 'bold 14px sans-serif'
  if (props.mode === 'time') ctx.fillText('⏱ ' + Math.max(0, Math.ceil(timeLeftMs / 1000)) + 's', 8, 18)
}

function loop(now: number) {
  if (!alive) return
  const dt = now - last
  last = now
  if (props.paused) { raf = requestAnimationFrame(loop); return }
  timer += dt
  // power timers
  for (let i = activePowers.length - 1; i >= 0; i--) {
    activePowers[i].timeLeft -= dt
    if (activePowers[i].timeLeft <= 0) {
      const k = activePowers[i].kind
      if (k === 'slow') speed = baseSpeed[props.difficulty] || 100
      if (k === 'double') multiplier = 1
      activePowers.splice(i, 1)
    }
  }
  // power spawn
  powerSpawnT += dt
  if (!powerUp && powerSpawnT > 8000) { spawnPowerUp(); powerSpawnT = 0 }
  // time mode
  if (props.mode === 'time') {
    timeLeftMs -= dt
    if (timeLeftMs <= 0) return die()
  }
  while (timer >= speed) { step(); timer -= speed; if (!alive) return }
  draw()
  raf = requestAnimationFrame(loop)
}

function fit() {
  const c = canvasRef.value!
  const w = Math.min(rootRef.value?.clientWidth || 320, 460)
  cells = Math.floor((w - 20) / SIZE)
  c.width = cells * SIZE
  c.height = cells * SIZE
}

onMounted(() => {
  fit()
  reset()
  raf = requestAnimationFrame(loop)
  rootRef.value?.focus()
  window.addEventListener('resize', fit)
})
onUnmounted(() => { cancelAnimationFrame(raf); window.removeEventListener('resize', fit) })
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; outline: none; }
.cv { background: #0b0e1f; border-radius: 14px; box-shadow: 0 0 0 1px rgba(255,255,255,0.08), 0 12px 30px rgba(0,0,0,0.4); }
.powers { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; min-height: 26px; }
.power-pill { display: inline-flex; gap: 6px; align-items: center; padding: 4px 10px; border-radius: 999px; font-size: 0.78rem; color: #fff; backdrop-filter: blur(6px); }
.power-pill.slow { background: rgba(56, 189, 248, 0.3); border: 1px solid rgba(56, 189, 248, 0.6); }
.power-pill.double { background: rgba(250, 204, 21, 0.3); border: 1px solid rgba(250, 204, 21, 0.6); }
.power-pill.shrink { background: rgba(34, 197, 94, 0.3); border: 1px solid rgba(34, 197, 94, 0.6); }
.dpad { display: grid; gap: 6px; justify-items: center; }
.dpad .row { display: flex; gap: 36px; }
.dpad button { width: 56px; height: 56px; border-radius: 50%; border: none; background: rgba(255,255,255,0.1); color: #fff; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.dpad button:active { background: rgba(255,255,255,0.25); }
</style>
