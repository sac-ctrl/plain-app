<template>
  <div class="wrap" tabindex="0" ref="root" @keydown.space.prevent="jump" @click="jump" @touchstart.prevent="jump">
    <canvas ref="cv" class="cv" />
    <div class="hud">
      <div v-if="combo > 1" class="combo">x{{ combo }} COMBO!</div>
      <div class="hint">Tap to jump. Pass through matching colors.</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

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
const W = 320, H = 540
const colors = ['#ef4444', '#3b82f6', '#22c55e', '#facc15']
let raf = 0, alive = true
let ball = { x: W / 2, y: H - 80, vy: 0, color: 0, r: 12 }
let rings: { y: number; rot: number; rotSpeed: number; passed: boolean }[] = []
let switches: { y: number; passed: boolean }[] = []
let scrollY = 0
let score = 0
const combo = ref(0)
let lastPassTs = 0
let trail: { x: number; y: number; life: number; color: string }[] = []
const cfg: Record<string, { gravity: number; jump: number; speed: number }> = {
  easy: { gravity: 0.4, jump: -7, speed: 1.4 },
  medium: { gravity: 0.5, jump: -7.5, speed: 1.7 },
  hard: { gravity: 0.6, jump: -8, speed: 2.1 },
  insane: { gravity: 0.7, jump: -8.5, speed: 2.6 },
}
let c = cfg.medium

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  alive = true
  ball = { x: W / 2, y: H - 80, vy: 0, color: 0, r: 12 }
  rings = []
  switches = []
  scrollY = 0
  combo.value = 0
  trail = []
  for (let i = 0; i < 6; i++) {
    rings.push({ y: H - 200 - i * 220, rot: Math.random() * Math.PI * 2, rotSpeed: 0.02 + Math.random() * 0.02, passed: false })
    switches.push({ y: H - 320 - i * 220, passed: false })
  }
  score = 0
  props.onScore(0)
}

function jump() {
  if (!alive) return
  ball.vy = c.jump
  store.beep('tap')
}

function step() {
  ball.vy += c.gravity
  ball.y += ball.vy
  trail.push({ x: ball.x, y: ball.y, life: 16, color: colors[ball.color] })
  trail.forEach((t) => t.life--)
  trail = trail.filter((t) => t.life > 0)
  if (ball.y < H * 0.4) {
    const dy = H * 0.4 - ball.y
    ball.y = H * 0.4
    scrollY += dy
    rings.forEach((r) => (r.y += dy))
    switches.forEach((s) => (s.y += dy))
  }
  rings.forEach((r) => (r.rot += r.rotSpeed))
  if (ball.y > H + 40) return die()

  for (const r of rings) {
    const cx = W / 2, cy = r.y
    const dx = ball.x - cx, dy = ball.y - cy
    const dist = Math.sqrt(dx * dx + dy * dy)
    if (dist < 90 && dist > 60) {
      const ang = Math.atan2(dy, dx) - r.rot
      const seg = Math.floor(((ang + Math.PI * 2) % (Math.PI * 2)) / (Math.PI / 2))
      if (seg !== ball.color) return die()
    }
    if (!r.passed && r.y > ball.y + 100) {
      r.passed = true
      const now = performance.now()
      if (now - lastPassTs < 1500) combo.value++
      else combo.value = 1
      lastPassTs = now
      const points = 10 * Math.max(1, combo.value)
      score += points
      props.onScore(score)
      store.beep(combo.value > 2 ? 'power' : 'tick')
    }
  }
  for (const s of switches) {
    if (!s.passed && Math.abs(s.y - ball.y) < 14) {
      s.passed = true
      ball.color = (ball.color + 1 + Math.floor(Math.random() * 3)) % 4
      store.beep('tick')
    }
  }
  if (rings[rings.length - 1].y > -200) {
    const ny = rings[rings.length - 1].y - 220
    rings.push({ y: ny, rot: Math.random() * Math.PI * 2, rotSpeed: 0.02 + Math.random() * 0.04, passed: false })
    switches.push({ y: ny - 100, passed: false })
  }
  rings = rings.filter((r) => r.y < H + 40)
  switches = switches.filter((s) => s.y < H + 40)
}

function die() { alive = false; cancelAnimationFrame(raf); props.onGameOver(score) }

function draw() {
  const ctx = cv.value!.getContext('2d')!
  ctx.fillStyle = '#0b0e1f'; ctx.fillRect(0, 0, W, H)
  for (const r of rings) {
    if (r.y < -100 || r.y > H + 100) continue
    const cx = W / 2
    for (let i = 0; i < 4; i++) {
      ctx.strokeStyle = colors[i]
      ctx.shadowColor = colors[i]; ctx.shadowBlur = 12
      ctx.lineWidth = 26
      ctx.beginPath()
      ctx.arc(cx, r.y, 78, r.rot + (i * Math.PI) / 2, r.rot + ((i + 1) * Math.PI) / 2)
      ctx.stroke()
    }
  }
  ctx.shadowBlur = 0
  for (const s of switches) {
    if (s.y < -20 || s.y > H + 20) continue
    ctx.fillStyle = '#fff'
    ctx.shadowColor = '#fff'; ctx.shadowBlur = 8
    ctx.beginPath(); ctx.arc(W / 2, s.y, 6, 0, Math.PI * 2); ctx.fill()
  }
  ctx.shadowBlur = 0
  // trail
  for (const t of trail) {
    ctx.fillStyle = t.color
    ctx.globalAlpha = t.life / 16 * 0.4
    ctx.beginPath(); ctx.arc(t.x, t.y, ball.r, 0, Math.PI * 2); ctx.fill()
  }
  ctx.globalAlpha = 1
  ctx.fillStyle = colors[ball.color]
  ctx.shadowColor = colors[ball.color]; ctx.shadowBlur = 24
  ctx.beginPath(); ctx.arc(ball.x, ball.y, ball.r, 0, Math.PI * 2); ctx.fill()
  ctx.shadowBlur = 0
}

function loop() {
  if (!alive) return
  if (props.paused) { raf = requestAnimationFrame(loop); return }
  step(); draw(); raf = requestAnimationFrame(loop)
}
onMounted(() => { cv.value!.width = W; cv.value!.height = H; reset(); raf = requestAnimationFrame(loop); root.value?.focus() })
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); touch-action: manipulation; }
.hud { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.combo { background: linear-gradient(135deg, #f59e0b, #ef4444); padding: 4px 12px; border-radius: 999px; color: #fff; font-weight: 800; font-size: 0.9rem; animation: bump 0.3s ease; }
@keyframes bump { from { transform: scale(0.5); } to { transform: scale(1); } }
.hint { color: rgba(255,255,255,0.7); font-size: 0.85rem; }
</style>
