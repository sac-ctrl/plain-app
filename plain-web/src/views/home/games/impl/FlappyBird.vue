<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @click="flap" @touchstart.prevent="flap">
    <canvas ref="cv" class="cv" />
    <div class="hint">
      <span v-if="hasGhost" class="ghost-tag">👻 Ghost mode active</span>
      <span v-else>Tap or press Space to flap</span>
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
let raf = 0
const W = 360
const H = 540
let bird = { x: 80, y: 270, vy: 0 }
let pipes: { x: number; gapY: number; passed: boolean }[] = []
let score = 0
let alive = true
let last = 0
let frameIdx = 0
let recordedFrames: { y: number }[] = []
let ghostFrames: { y: number }[] = []
const hasGhost = ref(false)
const params: Record<string, { gap: number; gravity: number; speed: number; spawn: number }> = {
  easy: { gap: 170, gravity: 0.45, speed: 2.2, spawn: 1700 },
  medium: { gap: 150, gravity: 0.55, speed: 2.6, spawn: 1500 },
  hard: { gap: 130, gravity: 0.65, speed: 3.0, spawn: 1300 },
  insane: { gap: 110, gravity: 0.8, speed: 3.6, spawn: 1100 },
}
let cfg = params.medium
let spawnT = 0

function reset() {
  cfg = params[props.difficulty] || params.medium
  bird = { x: 80, y: 270, vy: 0 }
  pipes = []
  score = 0
  alive = true
  last = performance.now()
  spawnT = 0
  frameIdx = 0
  recordedFrames = []
  const g = store.getGhost<{ frames: { y: number }[]; difficulty: string }>('flappy')
  if (g && g.frames && g.difficulty === props.difficulty) {
    ghostFrames = g.frames
    hasGhost.value = true
  } else {
    ghostFrames = []
    hasGhost.value = false
  }
  props.onScore(0)
}

function flap() {
  if (!alive) return
  bird.vy = -7
  store.beep('tap')
}

function onKey(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'ArrowUp') { e.preventDefault(); flap() }
}

function step(dt: number) {
  bird.vy += cfg.gravity
  bird.y += bird.vy
  recordedFrames.push({ y: bird.y })
  frameIdx++
  spawnT += dt
  if (spawnT >= cfg.spawn) {
    spawnT = 0
    pipes.push({ x: W, gapY: 80 + Math.random() * (H - 160 - cfg.gap), passed: false })
  }
  pipes.forEach((p) => (p.x -= cfg.speed))
  pipes = pipes.filter((p) => p.x > -60)
  if (bird.y > H - 20 || bird.y < 0) return die()
  for (const p of pipes) {
    if (bird.x + 14 > p.x && bird.x - 14 < p.x + 50) {
      if (bird.y - 14 < p.gapY || bird.y + 14 > p.gapY + cfg.gap) return die()
    }
    if (!p.passed && p.x + 50 < bird.x) {
      p.passed = true
      score += 1
      props.onScore(score)
      store.beep('tick')
    }
  }
}

function die() {
  alive = false
  cancelAnimationFrame(raf)
  // Save ghost if best
  const prevBest = store.bestOf('flappy')
  if (score >= prevBest && score > 0) {
    store.saveGhost('flappy', { frames: recordedFrames.slice(0, 1500), difficulty: props.difficulty })
  }
  props.onGameOver(score)
}

function draw() {
  const c = cv.value!
  const ctx = c.getContext('2d')!
  const g = ctx.createLinearGradient(0, 0, 0, H)
  g.addColorStop(0, '#0f172a'); g.addColorStop(1, '#1e293b')
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H)
  ctx.fillStyle = '#22c55e'
  ctx.shadowColor = '#22c55e'; ctx.shadowBlur = 8
  pipes.forEach((p) => {
    ctx.fillRect(p.x, 0, 50, p.gapY)
    ctx.fillRect(p.x, p.gapY + cfg.gap, 50, H - p.gapY - cfg.gap)
  })
  ctx.shadowBlur = 0
  // ghost
  if (ghostFrames.length > 0 && frameIdx < ghostFrames.length) {
    const gy = ghostFrames[frameIdx].y
    ctx.fillStyle = 'rgba(250, 204, 21, 0.35)'
    ctx.beginPath(); ctx.arc(bird.x, gy, 14, 0, Math.PI * 2); ctx.fill()
  }
  // bird
  ctx.fillStyle = '#facc15'
  ctx.shadowColor = '#facc15'; ctx.shadowBlur = 16
  ctx.beginPath(); ctx.arc(bird.x, bird.y, 14, 0, Math.PI * 2); ctx.fill()
  ctx.shadowBlur = 0
  ctx.fillStyle = '#000'; ctx.beginPath(); ctx.arc(bird.x + 5, bird.y - 4, 2, 0, Math.PI * 2); ctx.fill()
  // wing flap
  ctx.fillStyle = '#f97316'
  const flapY = Math.sin(performance.now() / 80) * 4
  ctx.beginPath(); ctx.ellipse(bird.x - 4, bird.y + flapY, 6, 4, 0, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 32px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(String(score), W / 2, 60)
}

function loop(now: number) {
  if (!alive) return
  const dt = now - last
  last = now
  if (props.paused) { raf = requestAnimationFrame(loop); return }
  step(dt)
  draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => {
  cv.value!.width = W; cv.value!.height = H
  reset(); raf = requestAnimationFrame(loop); root.value?.focus()
})
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; height: auto; touch-action: manipulation; }
.hint { color: rgba(255,255,255,0.7); font-size: 0.85rem; }
.ghost-tag { background: rgba(250,204,21,0.18); padding: 3px 10px; border-radius: 999px; }
</style>
