<template>
  <div class="wrap" tabindex="0" ref="rootRef" @keydown="onKey">
    <canvas ref="canvasRef" class="cv" />
    <div class="dpad" v-if="running">
      <button @click="set(0,-1)"><i-lucide:chevron-up /></button>
      <div class="row">
        <button @click="set(-1,0)"><i-lucide:chevron-left /></button>
        <button @click="set(1,0)"><i-lucide:chevron-right /></button>
      </div>
      <button @click="set(0,1)"><i-lucide:chevron-down /></button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  running: boolean
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
let timer = 0
let raf = 0
let last = 0
const baseSpeed: Record<string, number> = { easy: 140, medium: 100, hard: 75, insane: 55 }
let speed = 100
let alive = true

function reset() {
  alive = true
  snake = [
    { x: 10, y: 11 },
    { x: 9, y: 11 },
    { x: 8, y: 11 },
  ]
  dir = { x: 1, y: 0 }
  pendingDir = { x: 1, y: 0 }
  speed = baseSpeed[props.difficulty] || 100
  placeFood()
  props.onScore(0)
  last = performance.now()
  timer = 0
}

function placeFood() {
  while (true) {
    const f = { x: Math.floor(Math.random() * cells), y: Math.floor(Math.random() * cells) }
    if (!snake.find((s) => s.x === f.x && s.y === f.y)) {
      food = f
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
    ArrowUp: [0, -1], w: [0, -1],
    ArrowDown: [0, 1], s: [0, 1],
    ArrowLeft: [-1, 0], a: [-1, 0],
    ArrowRight: [1, 0], d: [1, 0],
  }
  const v = m[e.key]
  if (v) { e.preventDefault(); set(v[0], v[1]) }
}

function step() {
  dir = pendingDir
  const head = { x: snake[0].x + dir.x, y: snake[0].y + dir.y }
  if (head.x < 0 || head.x >= cells || head.y < 0 || head.y >= cells) return die()
  if (snake.find((s) => s.x === head.x && s.y === head.y)) return die()
  snake.unshift(head)
  if (head.x === food.x && head.y === food.y) {
    props.onScore((s) => s + 10)
    store.beep('tick')
    placeFood()
    if (speed > 35) speed = Math.max(35, speed - 2)
  } else {
    snake.pop()
  }
}

function die() {
  alive = false
  cancelAnimationFrame(raf)
  props.onGameOver()
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
  ctx.fillStyle = '#ec4899'
  ctx.shadowColor = '#ec4899'; ctx.shadowBlur = 12
  ctx.fillRect(food.x * SIZE + 3, food.y * SIZE + 3, SIZE - 6, SIZE - 6)
  ctx.shadowBlur = 0
  snake.forEach((s, i) => {
    const t = i / Math.max(1, snake.length)
    ctx.fillStyle = i === 0 ? '#a855f7' : `rgba(99, 102, 241, ${1 - t * 0.5})`
    ctx.shadowColor = '#a855f7'; ctx.shadowBlur = i === 0 ? 14 : 6
    ctx.fillRect(s.x * SIZE + 1, s.y * SIZE + 1, SIZE - 2, SIZE - 2)
  })
  ctx.shadowBlur = 0
}

function loop(now: number) {
  if (!alive) return
  const dt = now - last
  last = now
  timer += dt
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

onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', fit)
})

watch(() => props.running, (v) => {
  if (v) { reset(); raf = requestAnimationFrame(loop) }
})
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 14px; outline: none; }
.cv { background: #0b0e1f; border-radius: 14px; box-shadow: 0 0 0 1px rgba(255,255,255,0.08), 0 12px 30px rgba(0,0,0,0.4); }
.dpad { display: grid; gap: 6px; justify-items: center; }
.dpad .row { display: flex; gap: 36px; }
.dpad button { width: 56px; height: 56px; border-radius: 50%; border: none; background: rgba(255,255,255,0.1); color: #fff; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.dpad button:active { background: rgba(255,255,255,0.25); }
</style>
