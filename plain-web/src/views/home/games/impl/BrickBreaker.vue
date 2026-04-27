<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <canvas ref="cv" class="cv"
      @mousemove="onMouse"
      @touchmove.prevent="onTouch"
      @touchstart.prevent="onTouch"
    />
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
const W = 360
const H = 540
let raf = 0
let alive = true
let paddle = { x: W / 2 - 40, w: 80, h: 12, y: H - 30 }
let ball = { x: W / 2, y: H - 50, vx: 3, vy: -3, r: 7 }
let bricks: { x: number; y: number; w: number; h: number; alive: boolean; color: string }[] = []
let score = 0
let lives = 3
const cfg: Record<string, { speed: number; rows: number; lives: number }> = {
  easy: { speed: 3, rows: 3, lives: 5 },
  medium: { speed: 4, rows: 4, lives: 3 },
  hard: { speed: 5, rows: 5, lives: 3 },
  insane: { speed: 6.5, rows: 6, lives: 2 },
}
let c = cfg.medium

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  score = 0
  lives = c.lives
  paddle = { x: W / 2 - 40, w: 80, h: 12, y: H - 30 }
  const ang = (Math.random() * 0.6 - 0.3) - Math.PI / 2
  ball = { x: W / 2, y: H - 50, vx: Math.cos(ang) * c.speed, vy: Math.sin(ang) * c.speed, r: 7 }
  bricks = []
  const cols = 8
  const bw = (W - 40) / cols, bh = 18
  const colors = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#a855f7']
  for (let r = 0; r < c.rows; r++) for (let i = 0; i < cols; i++)
    bricks.push({ x: 20 + i * bw, y: 60 + r * (bh + 4), w: bw - 4, h: bh, alive: true, color: colors[r % colors.length] })
  alive = true
  props.onScore(0)
}

function onMouse(e: MouseEvent) { setPad(e.offsetX) }
function onTouch(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  setPad(e.touches[0].clientX - r.left)
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') paddle.x -= 24
  if (e.key === 'ArrowRight') paddle.x += 24
  paddle.x = Math.max(0, Math.min(W - paddle.w, paddle.x))
}
function setPad(x: number) {
  paddle.x = Math.max(0, Math.min(W - paddle.w, x - paddle.w / 2))
}

function step() {
  ball.x += ball.vx; ball.y += ball.vy
  if (ball.x < ball.r) { ball.x = ball.r; ball.vx *= -1 }
  if (ball.x > W - ball.r) { ball.x = W - ball.r; ball.vx *= -1 }
  if (ball.y < ball.r) { ball.y = ball.r; ball.vy *= -1 }
  if (ball.y > paddle.y - ball.r && ball.y < paddle.y + paddle.h && ball.x > paddle.x && ball.x < paddle.x + paddle.w && ball.vy > 0) {
    ball.vy *= -1
    const off = (ball.x - (paddle.x + paddle.w / 2)) / (paddle.w / 2)
    ball.vx = off * c.speed
    store.beep('tap')
  }
  if (ball.y > H + 20) {
    lives -= 1
    store.beep('lose')
    if (lives <= 0) return die()
    ball = { x: W / 2, y: H - 50, vx: c.speed * (Math.random() < 0.5 ? -1 : 1), vy: -c.speed, r: 7 }
    return
  }
  for (const b of bricks) {
    if (!b.alive) continue
    if (ball.x > b.x && ball.x < b.x + b.w && ball.y > b.y && ball.y < b.y + b.h) {
      b.alive = false
      ball.vy *= -1
      score += 10
      props.onScore(score)
      store.beep('tick')
      break
    }
  }
  if (bricks.every((b) => !b.alive)) {
    score += 100
    props.onScore(score)
    return die()
  }
}

function die() {
  alive = false
  cancelAnimationFrame(raf)
  props.onGameOver(score)
}

function draw() {
  const ctx = cv.value!.getContext('2d')!
  ctx.fillStyle = '#0b0e1f'; ctx.fillRect(0, 0, W, H)
  for (const b of bricks) {
    if (!b.alive) continue
    ctx.fillStyle = b.color
    ctx.fillRect(b.x, b.y, b.w, b.h)
  }
  ctx.fillStyle = '#fff'
  ctx.fillRect(paddle.x, paddle.y, paddle.w, paddle.h)
  ctx.fillStyle = '#facc15'
  ctx.beginPath(); ctx.arc(ball.x, ball.y, ball.r, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 14px sans-serif'
  ctx.fillText('Lives ' + lives, 8, 18)
}

function loop() {
  if (!alive) return
  step(); draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => { cv.value!.width = W; cv.value!.height = H; reset(); raf = requestAnimationFrame(loop); root.value?.focus() })
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; align-items: center; justify-content: center; outline: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; touch-action: none; }
</style>
