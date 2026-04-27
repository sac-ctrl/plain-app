<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <canvas ref="cv" class="cv"
      @touchstart.prevent="ts"
      @touchmove.prevent="tm"
      @touchend.prevent="tend"
    />
    <div class="hint">Move ← → · Space to shoot</div>
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
const W = 360, H = 540
let raf = 0, alive = true
let player = { x: W / 2, y: H - 50, vx: 0 }
let bullets: { x: number; y: number }[] = []
let enemies: { x: number; y: number; vx: number; hp: number }[] = []
let particles: { x: number; y: number; vx: number; vy: number; life: number }[] = []
let lastShot = 0
let lastSpawn = 0
let score = 0
let lives = 3
let keys: Record<string, boolean> = {}
const cfg: Record<string, { es: number; spawn: number; bossEvery: number }> = {
  easy: { es: 1.2, spawn: 1300, bossEvery: 30 },
  medium: { es: 1.7, spawn: 1000, bossEvery: 25 },
  hard: { es: 2.3, spawn: 700, bossEvery: 20 },
  insane: { es: 3, spawn: 500, bossEvery: 15 },
}
let c = cfg.medium
let touchPullX: number | null = null

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  alive = true
  player = { x: W / 2, y: H - 50, vx: 0 }
  bullets = []; enemies = []; particles = []
  lastShot = 0; lastSpawn = 0
  score = 0; lives = 3
  props.onScore(0)
}

function shoot() {
  bullets.push({ x: player.x, y: player.y - 18 })
  store.beep('tap')
}
function onKey(e: KeyboardEvent) {
  keys[e.key] = true
  if (e.key === ' ' && performance.now() - lastShot > 220) { shoot(); lastShot = performance.now(); e.preventDefault() }
}
function onKeyUp(e: KeyboardEvent) { keys[e.key] = false }

function ts(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  touchPullX = e.touches[0].clientX - r.left
  if (performance.now() - lastShot > 200) { shoot(); lastShot = performance.now() }
}
function tm(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  touchPullX = e.touches[0].clientX - r.left
}
function tend() { touchPullX = null }

function step(dt: number) {
  if (keys['ArrowLeft']) player.vx = -5
  else if (keys['ArrowRight']) player.vx = 5
  else player.vx *= 0.8
  if (touchPullX != null) {
    player.x += (touchPullX - player.x) * 0.25
    if (performance.now() - lastShot > 280) { shoot(); lastShot = performance.now() }
  } else {
    player.x += player.vx
  }
  player.x = Math.max(20, Math.min(W - 20, player.x))

  bullets.forEach((b) => (b.y -= 8))
  bullets = bullets.filter((b) => b.y > -10)
  lastSpawn += dt
  if (lastSpawn >= c.spawn) {
    lastSpawn = 0
    enemies.push({ x: 30 + Math.random() * (W - 60), y: -20, vx: (Math.random() - 0.5) * 1.4, hp: 1 })
  }
  enemies.forEach((en) => { en.y += c.es; en.x += en.vx; if (en.x < 20 || en.x > W - 20) en.vx *= -1 })
  for (const en of enemies) {
    if (en.y > H + 20) { lives--; store.beep('lose'); if (lives <= 0) return die() }
    if (Math.abs(en.x - player.x) < 18 && Math.abs(en.y - player.y) < 18) { lives--; store.beep('lose'); en.y = H + 50; if (lives <= 0) return die() }
  }
  enemies = enemies.filter((en) => en.y < H + 20)
  for (const b of bullets) {
    for (const en of enemies) {
      if (Math.abs(b.x - en.x) < 16 && Math.abs(b.y - en.y) < 16) {
        en.hp -= 1; b.y = -100
        if (en.hp <= 0) {
          score += 10
          props.onScore(score)
          for (let i = 0; i < 8; i++) particles.push({ x: en.x, y: en.y, vx: (Math.random() - 0.5) * 4, vy: (Math.random() - 0.5) * 4, life: 30 })
          en.y = H + 100
          store.beep('tick')
        }
      }
    }
  }
  particles.forEach((p) => { p.x += p.vx; p.y += p.vy; p.life-- })
  particles = particles.filter((p) => p.life > 0)
}

function die() { alive = false; cancelAnimationFrame(raf); props.onGameOver(score) }

function draw() {
  const ctx = cv.value!.getContext('2d')!
  ctx.fillStyle = '#070815'; ctx.fillRect(0, 0, W, H)
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = 'rgba(255,255,255,0.3)'
    ctx.fillRect((i * 53) % W, (i * 87 + (performance.now() / 8) % H) % H, 2, 2)
  }
  ctx.fillStyle = '#a855f7'
  ctx.beginPath(); ctx.moveTo(player.x, player.y - 14); ctx.lineTo(player.x - 14, player.y + 14); ctx.lineTo(player.x + 14, player.y + 14); ctx.closePath(); ctx.fill()
  ctx.fillStyle = '#facc15'
  bullets.forEach((b) => ctx.fillRect(b.x - 2, b.y - 8, 4, 12))
  ctx.fillStyle = '#ef4444'
  enemies.forEach((en) => { ctx.fillRect(en.x - 14, en.y - 12, 28, 24) })
  particles.forEach((p) => { ctx.fillStyle = `rgba(250, 204, 21, ${p.life / 30})`; ctx.fillRect(p.x, p.y, 3, 3) })
  ctx.fillStyle = '#fff'; ctx.font = 'bold 14px sans-serif'
  ctx.fillText('Lives ' + lives, 10, 20)
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
