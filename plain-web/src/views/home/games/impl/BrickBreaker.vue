<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <canvas ref="cv" class="cv"
      @mousemove="onMouse"
      @touchmove.prevent="onTouch"
      @touchstart.prevent="onTouch"
    />
    <div class="hud">
      <div>Level <b>{{ level }}</b></div>
      <div>Balls <b>{{ balls.length }}</b></div>
      <div v-if="laserUntil > now"><b>LASER</b> {{ Math.ceil((laserUntil - now) / 1000) }}s</div>
      <div v-if="bigUntil > now"><b>WIDE</b> {{ Math.ceil((bigUntil - now) / 1000) }}s</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

type Brick = { x: number; y: number; w: number; h: number; alive: boolean; color: string; hp: number }
type Ball = { x: number; y: number; vx: number; vy: number; r: number }
type Drop = { x: number; y: number; kind: 'multi' | 'wide' | 'laser' | 'life' }
type Laser = { x: number; y: number }

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
const W = 360
const H = 540
let raf = 0
let alive = true
let paddle = { x: W / 2 - 40, w: 80, h: 12, y: H - 30 }
let balls: Ball[] = []
let bricks: Brick[] = []
let drops: Drop[] = []
let lasers: Laser[] = []
let lastLaserShot = 0
const score = ref(0)
const lives = ref(3)
const level = ref(1)
const now = ref(0)
const laserUntil = ref(0)
const bigUntil = ref(0)
const cfg: Record<string, { speed: number; rows: number; lives: number }> = {
  easy: { speed: 3, rows: 3, lives: 5 },
  medium: { speed: 4, rows: 4, lives: 3 },
  hard: { speed: 5, rows: 5, lives: 3 },
  insane: { speed: 6.5, rows: 6, lives: 2 },
}
let c = cfg.medium

function buildLevel(lvl: number) {
  bricks = []
  const cols = 8
  const bw = (W - 40) / cols, bh = 18
  const colors = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#a855f7']
  const rows = Math.min(8, c.rows + Math.floor((lvl - 1) / 2))
  for (let r = 0; r < rows; r++) {
    for (let i = 0; i < cols; i++) {
      // Skip some randomly to make patterns past level 1
      if (lvl > 1 && Math.random() < 0.06 + lvl * 0.01) continue
      const hp = lvl > 2 && r === 0 ? 2 : 1
      bricks.push({ x: 20 + i * bw, y: 60 + r * (bh + 4), w: bw - 4, h: bh, alive: true, color: colors[r % colors.length], hp })
    }
  }
}

function spawnBall(x: number, y: number, dir = -1) {
  const ang = (Math.random() * 0.6 - 0.3) - Math.PI / 2
  balls.push({ x, y, vx: Math.cos(ang) * c.speed * (Math.random() < 0.5 ? -1 : 1), vy: Math.sin(ang) * c.speed * (dir < 0 ? 1 : -1), r: 7 })
}

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  score.value = 0
  lives.value = c.lives
  level.value = 1
  paddle = { x: W / 2 - 40, w: 80, h: 12, y: H - 30 }
  balls = []
  drops = []
  lasers = []
  laserUntil.value = 0
  bigUntil.value = 0
  buildLevel(1)
  spawnBall(W / 2, H - 50)
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
function setPad(x: number) { paddle.x = Math.max(0, Math.min(W - paddle.w, x - paddle.w / 2)) }

function spawnDrop(x: number, y: number) {
  const r = Math.random()
  const kind: Drop['kind'] = r < 0.4 ? 'multi' : r < 0.65 ? 'wide' : r < 0.9 ? 'laser' : 'life'
  drops.push({ x, y, kind })
}

function step() {
  now.value = performance.now()
  paddle.w = bigUntil.value > now.value ? 130 : 80

  for (const ball of balls) {
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
    for (const b of bricks) {
      if (!b.alive) continue
      if (ball.x > b.x && ball.x < b.x + b.w && ball.y > b.y && ball.y < b.y + b.h) {
        b.hp -= 1
        if (b.hp <= 0) {
          b.alive = false
          score.value += 10
          props.onScore(score.value)
          if (Math.random() < 0.18) spawnDrop(b.x + b.w / 2, b.y + b.h)
        }
        ball.vy *= -1
        store.beep('tick')
        break
      }
    }
  }
  balls = balls.filter((b) => b.y < H + 20)
  if (balls.length === 0) {
    lives.value -= 1
    store.beep('lose')
    if (lives.value <= 0) return die()
    spawnBall(W / 2, H - 50)
    return
  }
  drops.forEach((d) => (d.y += 2))
  for (const d of drops) {
    if (d.y > paddle.y && d.y < paddle.y + paddle.h && d.x > paddle.x && d.x < paddle.x + paddle.w) {
      if (d.kind === 'multi') {
        const b0 = balls[0]
        spawnBall(b0.x, b0.y, 1); spawnBall(b0.x, b0.y, -1)
      } else if (d.kind === 'wide') bigUntil.value = now.value + 8000
      else if (d.kind === 'laser') laserUntil.value = now.value + 6000
      else if (d.kind === 'life') lives.value++
      store.beep('power')
      d.y = H + 100
    }
  }
  drops = drops.filter((d) => d.y < H + 20)
  // laser
  if (laserUntil.value > now.value && now.value - lastLaserShot > 220) {
    lastLaserShot = now.value
    lasers.push({ x: paddle.x + 8, y: paddle.y })
    lasers.push({ x: paddle.x + paddle.w - 8, y: paddle.y })
  }
  lasers.forEach((l) => (l.y -= 8))
  for (const l of lasers) {
    for (const b of bricks) {
      if (!b.alive) continue
      if (l.x > b.x && l.x < b.x + b.w && l.y < b.y + b.h && l.y > b.y - 6) {
        b.hp -= 1
        if (b.hp <= 0) { b.alive = false; score.value += 10; props.onScore(score.value) }
        l.y = -100
        store.beep('tick')
      }
    }
  }
  lasers = lasers.filter((l) => l.y > -10)
  if (bricks.every((b) => !b.alive)) {
    score.value += 100
    props.onScore(score.value)
    level.value += 1
    buildLevel(level.value)
    balls = []
    spawnBall(W / 2, H - 50)
    store.beep('win')
  }
}

function die() {
  alive = false
  cancelAnimationFrame(raf)
  props.onGameOver(score.value)
}

function draw() {
  const ctx = cv.value!.getContext('2d')!
  const grad = ctx.createLinearGradient(0, 0, 0, H)
  grad.addColorStop(0, '#0b0e1f'); grad.addColorStop(1, '#1e1b4b')
  ctx.fillStyle = grad; ctx.fillRect(0, 0, W, H)
  for (const b of bricks) {
    if (!b.alive) continue
    ctx.fillStyle = b.color
    ctx.fillRect(b.x, b.y, b.w, b.h)
    if (b.hp > 1) {
      ctx.fillStyle = 'rgba(0,0,0,0.3)'
      ctx.fillRect(b.x, b.y, b.w, b.h)
    }
  }
  ctx.fillStyle = laserUntil.value > now.value ? '#ef4444' : '#fff'
  ctx.fillRect(paddle.x, paddle.y, paddle.w, paddle.h)
  ctx.fillStyle = '#facc15'
  for (const ball of balls) {
    ctx.shadowColor = '#facc15'; ctx.shadowBlur = 10
    ctx.beginPath(); ctx.arc(ball.x, ball.y, ball.r, 0, Math.PI * 2); ctx.fill()
  }
  ctx.shadowBlur = 0
  for (const d of drops) {
    const col = d.kind === 'multi' ? '#a855f7' : d.kind === 'wide' ? '#22d3ee' : d.kind === 'laser' ? '#ef4444' : '#22c55e'
    ctx.fillStyle = col
    ctx.beginPath(); ctx.arc(d.x, d.y, 7, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = '#fff'; ctx.font = 'bold 9px sans-serif'; ctx.textAlign = 'center'
    ctx.fillText(d.kind === 'multi' ? '+' : d.kind === 'wide' ? 'W' : d.kind === 'laser' ? 'L' : '♥', d.x, d.y + 3)
  }
  ctx.fillStyle = '#ef4444'
  for (const l of lasers) ctx.fillRect(l.x - 1, l.y - 8, 2, 8)
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 14px sans-serif'; ctx.textAlign = 'left'
  ctx.fillText('Lives ' + lives.value, 8, 18)
}

function loop() {
  if (!alive) return
  if (props.paused) { raf = requestAnimationFrame(loop); return }
  step(); draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => { cv.value!.width = W; cv.value!.height = H; reset(); raf = requestAnimationFrame(loop); root.value?.focus() })
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; touch-action: none; }
.hud { display: flex; gap: 16px; color: rgba(255,255,255,0.85); font-size: 0.82rem; }
.hud b { color: #facc15; margin-left: 4px; }
</style>
