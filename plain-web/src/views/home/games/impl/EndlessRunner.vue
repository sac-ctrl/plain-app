<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey">
    <canvas ref="cv" class="cv"
      @touchstart.prevent="ts"
      @touchmove.prevent="tm"
      @touchend.prevent="tend"
    />
    <div class="hint">Swipe up to jump · Swipe down to slide</div>
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
const W = 480, H = 260
let raf = 0, alive = true
const ground = H - 40
let player = { y: ground, vy: 0, sliding: false, slideT: 0 }
let obstacles: { x: number; type: 'low' | 'high'; w: number; h: number; passed: boolean }[] = []
let coins: { x: number; y: number; got: boolean }[] = []
let score = 0
let speed = 5
let spawnT = 0
const cfg: Record<string, { speed: number; gap: [number, number] }> = {
  easy: { speed: 4, gap: [800, 1500] },
  medium: { speed: 5, gap: [700, 1300] },
  hard: { speed: 6.5, gap: [600, 1100] },
  insane: { speed: 8.5, gap: [500, 900] },
}
let c = cfg.medium

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  alive = true
  player = { y: ground, vy: 0, sliding: false, slideT: 0 }
  obstacles = []
  coins = []
  score = 0
  speed = c.speed
  spawnT = 0
  props.onScore(0)
}

function jump() {
  if (player.y === ground) { player.vy = -11; store.beep('tap') }
}
function slide() {
  if (player.y === ground) { player.sliding = true; player.slideT = 30; store.beep('tap') }
}
function onKey(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'ArrowUp') { e.preventDefault(); jump() }
  if (e.key === 'ArrowDown') { e.preventDefault(); slide() }
}

let tsy = 0, tsx = 0
function ts(e: TouchEvent) { tsy = e.touches[0].clientY; tsx = e.touches[0].clientX }
function tm() { /* ignore */ }
function tend(e: TouchEvent) {
  const dy = e.changedTouches[0].clientY - tsy
  const dx = e.changedTouches[0].clientX - tsx
  if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 20) {
    if (dy < 0) jump(); else slide()
  } else {
    jump()
  }
}

function step(dt: number) {
  player.vy += 0.6
  player.y += player.vy
  if (player.y > ground) { player.y = ground; player.vy = 0 }
  if (player.sliding) {
    player.slideT--
    if (player.slideT <= 0) player.sliding = false
  }
  spawnT += dt
  const [a, b] = c.gap
  if (spawnT >= a + Math.random() * (b - a)) {
    spawnT = 0
    const high = Math.random() < 0.4
    if (high) obstacles.push({ x: W, type: 'high', w: 24, h: 24, passed: false })
    else obstacles.push({ x: W, type: 'low', w: 24, h: 30, passed: false })
    if (Math.random() < 0.5) coins.push({ x: W + 80, y: ground - 80, got: false })
  }
  obstacles.forEach((o) => (o.x -= speed))
  coins.forEach((co) => (co.x -= speed))
  for (const o of obstacles) {
    const px = 80, pw = 26
    let pYTop = player.y - 30, pYBot = player.y
    if (player.sliding) { pYTop = player.y - 14 }
    let oYTop = ground - o.h, oYBot = ground
    if (o.type === 'high') { oYTop = ground - 70; oYBot = ground - 50 }
    if (o.x < px + pw && o.x + o.w > px) {
      if (oYBot > pYTop && oYTop < pYBot) return die()
    }
    if (!o.passed && o.x + o.w < px) {
      o.passed = true
      score += 5
      props.onScore(score)
      store.beep('tick')
    }
  }
  for (const co of coins) {
    if (co.got) continue
    if (Math.abs(co.x - 80) < 20 && Math.abs(co.y - (player.y - 16)) < 30) {
      co.got = true
      score += 15
      props.onScore(score)
      store.beep('tick')
    }
  }
  obstacles = obstacles.filter((o) => o.x > -40)
  coins = coins.filter((co) => co.x > -40 && !co.got)
  speed += 0.001
}

function die() { alive = false; cancelAnimationFrame(raf); props.onGameOver(score) }

function draw() {
  const ctx = cv.value!.getContext('2d')!
  const grad = ctx.createLinearGradient(0, 0, 0, H)
  grad.addColorStop(0, '#0f172a'); grad.addColorStop(1, '#1e293b')
  ctx.fillStyle = grad; ctx.fillRect(0, 0, W, H)
  ctx.fillStyle = '#22c55e'; ctx.fillRect(0, ground, W, H - ground)
  // player
  ctx.fillStyle = '#facc15'
  if (player.sliding) ctx.fillRect(80 - 14, player.y - 14, 28, 14)
  else ctx.fillRect(80 - 12, player.y - 30, 24, 30)
  // obstacles
  for (const o of obstacles) {
    if (o.type === 'high') { ctx.fillStyle = '#a855f7'; ctx.fillRect(o.x, ground - 70, o.w, 22) }
    else { ctx.fillStyle = '#ef4444'; ctx.fillRect(o.x, ground - o.h, o.w, o.h) }
  }
  // coins
  ctx.fillStyle = '#fbbf24'
  coins.forEach((co) => { ctx.beginPath(); ctx.arc(co.x, co.y, 8, 0, Math.PI * 2); ctx.fill() })
  ctx.fillStyle = '#fff'; ctx.font = 'bold 16px sans-serif'
  ctx.fillText(String(score), 10, 22)
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
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; touch-action: none; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.82rem; }
</style>
