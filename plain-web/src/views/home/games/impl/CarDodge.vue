<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <canvas ref="cv" class="cv"
      @touchstart.prevent="ts"
      @touchmove.prevent="tm"
    />
    <div class="hint">Tap left/right side or use arrow keys</div>
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
const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()
const W = 320, H = 540
let raf = 0, alive = true
const lanes = [60, 130, 200, 260]
let lane = 1
let player = { x: lanes[1], y: H - 80 }
let cars: { lane: number; y: number; color: string }[] = []
let road = 0
let score = 0
let spawnT = 0
const cfg: Record<string, { speed: number; spawn: number }> = {
  easy: { speed: 3, spawn: 1100 },
  medium: { speed: 4.5, spawn: 900 },
  hard: { speed: 6, spawn: 700 },
  insane: { speed: 8, spawn: 500 },
}
let c = cfg.medium

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  alive = true
  lane = 1
  player.x = lanes[1]
  cars = []
  road = 0
  score = 0
  spawnT = 0
  props.onScore(0)
}

function setLane(l: number) { lane = Math.max(0, Math.min(lanes.length - 1, l)); store.beep('tap') }
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') setLane(lane - 1)
  if (e.key === 'ArrowRight') setLane(lane + 1)
}
function ts(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  const x = e.touches[0].clientX - r.left
  setLane(x < r.width / 2 ? lane - 1 : lane + 1)
}
function tm() { /* placeholder */ }

function step(dt: number) {
  road = (road + c.speed * 2) % 40
  player.x += (lanes[lane] - player.x) * 0.25
  cars.forEach((cr) => (cr.y += c.speed * 1.4))
  spawnT += dt
  if (spawnT >= c.spawn) {
    spawnT = 0
    const colors = ['#ef4444', '#facc15', '#3b82f6', '#22c55e']
    cars.push({ lane: Math.floor(Math.random() * lanes.length), y: -60, color: colors[Math.floor(Math.random() * colors.length)] })
  }
  cars = cars.filter((cr) => cr.y < H + 80)
  for (const cr of cars) {
    if (cr.lane === lane && Math.abs(cr.y - player.y) < 60) return die()
    if (cr.y > H && !(cr as any).counted) {
      ;(cr as any).counted = true
      score += 5
      props.onScore(score)
      store.beep('tick')
    }
  }
}

function die() { alive = false; cancelAnimationFrame(raf); props.onGameOver(score) }

function draw() {
  const ctx = cv.value!.getContext('2d')!
  ctx.fillStyle = '#0b0e1f'; ctx.fillRect(0, 0, W, H)
  ctx.fillStyle = '#1f2937'; ctx.fillRect(30, 0, W - 60, H)
  ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.setLineDash([18, 18])
  for (let i = 1; i < lanes.length; i++) {
    ctx.beginPath()
    const x = (lanes[i] + lanes[i - 1]) / 2 + 18
    ctx.moveTo(x, -road); ctx.lineTo(x, H); ctx.stroke()
  }
  ctx.setLineDash([])
  for (const cr of cars) {
    ctx.fillStyle = cr.color
    ctx.fillRect(lanes[cr.lane], cr.y, 36, 56)
  }
  ctx.fillStyle = '#a855f7'
  ctx.fillRect(player.x, player.y, 36, 56)
}

let last = 0
function loop(now: number) {
  if (!alive) return
  const dt = now - (last || now); last = now
  step(dt); draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => { cv.value!.width = W; cv.value!.height = H; reset(); raf = requestAnimationFrame(loop); root.value?.focus() })
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); touch-action: none; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.82rem; }
</style>
