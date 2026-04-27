<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @mousedown.prevent="flap" @touchstart.prevent="flap">
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
const PIPE_W = 60
const BIRD_R = 14
const BIRD_X = 90
const GROUND_H = 40
let bird = { y: 220, vy: 0 }
let pipes: { x: number; gapY: number; passed: boolean }[] = []
let score = 0
let alive = true
let started = false // wait for first flap
let last = 0
let acc = 0
const FIXED_DT = 1000 / 60 // 16.67ms physics step
let frameIdx = 0
let recordedFrames: { y: number }[] = []
let ghostFrames: { y: number }[] = []
const hasGhost = ref(false)

// Tuned to be fair and consistent at 60Hz physics regardless of display refresh.
const params: Record<string, { gap: number; gravity: number; jump: number; speed: number; spawn: number }> = {
  easy:   { gap: 200, gravity: 0.42, jump: -7.0, speed: 1.9, spawn: 1900 },
  medium: { gap: 170, gravity: 0.50, jump: -7.4, speed: 2.3, spawn: 1700 },
  hard:   { gap: 145, gravity: 0.58, jump: -7.8, speed: 2.7, spawn: 1500 },
  insane: { gap: 125, gravity: 0.68, jump: -8.2, speed: 3.2, spawn: 1300 },
}
let cfg = params.medium
let spawnT = 0
let bgOffset = 0

function reset() {
  cfg = params[props.difficulty] || params.medium
  bird = { y: 220, vy: 0 }
  pipes = []
  score = 0
  alive = true
  started = false
  last = performance.now()
  acc = 0
  spawnT = -800 // delay first pipe ~800ms after starting
  frameIdx = 0
  recordedFrames = []
  bgOffset = 0
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
  started = true
  bird.vy = cfg.jump
  store.beep('tap')
}

function onKey(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W') {
    e.preventDefault()
    flap()
  }
}

function physicsStep() {
  // Until first input, hold bird steady so player can orient.
  if (!started) {
    // gentle hover bob
    bird.y = 220 + Math.sin(frameIdx / 12) * 6
    frameIdx++
    return
  }
  bird.vy += cfg.gravity
  if (bird.vy > 9) bird.vy = 9 // clamp terminal velocity
  bird.y += bird.vy
  recordedFrames.push({ y: bird.y })
  frameIdx++
  spawnT += FIXED_DT
  if (spawnT >= cfg.spawn) {
    spawnT = 0
    const minY = 70
    const maxY = H - GROUND_H - cfg.gap - 70
    const gapY = minY + Math.random() * Math.max(40, maxY - minY)
    pipes.push({ x: W + 10, gapY, passed: false })
  }
  pipes.forEach((p) => (p.x -= cfg.speed))
  pipes = pipes.filter((p) => p.x > -PIPE_W - 10)
  bgOffset = (bgOffset + cfg.speed * 0.4) % 40

  // ground & ceiling
  if (bird.y + BIRD_R > H - GROUND_H || bird.y - BIRD_R < 0) return die()
  for (const p of pipes) {
    const inX = BIRD_X + BIRD_R > p.x && BIRD_X - BIRD_R < p.x + PIPE_W
    if (inX && (bird.y - BIRD_R < p.gapY || bird.y + BIRD_R > p.gapY + cfg.gap)) {
      return die()
    }
    if (!p.passed && p.x + PIPE_W < BIRD_X - BIRD_R) {
      p.passed = true
      score += 1
      props.onScore(score)
      store.beep('tick')
    }
  }
}

function die() {
  if (!alive) return
  alive = false
  cancelAnimationFrame(raf)
  const prevBest = store.bestOf('flappy')
  if (score >= prevBest && score > 0) {
    store.saveGhost('flappy', { frames: recordedFrames.slice(0, 1500), difficulty: props.difficulty })
  }
  props.onGameOver(score)
}

function draw() {
  const c = cv.value!
  const ctx = c.getContext('2d')!
  // sky gradient
  const sky = ctx.createLinearGradient(0, 0, 0, H)
  sky.addColorStop(0, '#0b1d3a')
  sky.addColorStop(0.6, '#1e3a8a')
  sky.addColorStop(1, '#3b82f6')
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, W, H)
  // stars / sparkle
  ctx.fillStyle = 'rgba(255,255,255,0.35)'
  for (let i = 0; i < 25; i++) {
    const sx = (i * 53 + bgOffset * 0.2) % W
    const sy = ((i * 37) % (H - GROUND_H - 80)) + 10
    ctx.fillRect(sx, sy, 1.5, 1.5)
  }
  // distant hills
  ctx.fillStyle = 'rgba(15, 30, 60, 0.55)'
  ctx.beginPath()
  ctx.moveTo(0, H - GROUND_H - 30)
  for (let x = 0; x <= W; x += 30) {
    const y = H - GROUND_H - 30 - Math.sin((x + bgOffset) / 30) * 8
    ctx.lineTo(x, y)
  }
  ctx.lineTo(W, H - GROUND_H)
  ctx.lineTo(0, H - GROUND_H)
  ctx.closePath()
  ctx.fill()
  // pipes (with caps)
  for (const p of pipes) {
    const grd = ctx.createLinearGradient(p.x, 0, p.x + PIPE_W, 0)
    grd.addColorStop(0, '#16a34a')
    grd.addColorStop(0.5, '#22c55e')
    grd.addColorStop(1, '#15803d')
    ctx.fillStyle = grd
    ctx.fillRect(p.x, 0, PIPE_W, p.gapY)
    ctx.fillRect(p.x, p.gapY + cfg.gap, PIPE_W, H - GROUND_H - p.gapY - cfg.gap)
    // caps
    ctx.fillStyle = '#15803d'
    ctx.fillRect(p.x - 4, p.gapY - 14, PIPE_W + 8, 14)
    ctx.fillRect(p.x - 4, p.gapY + cfg.gap, PIPE_W + 8, 14)
    ctx.fillStyle = 'rgba(255,255,255,0.18)'
    ctx.fillRect(p.x + 6, 0, 4, p.gapY)
    ctx.fillRect(p.x + 6, p.gapY + cfg.gap, 4, H - GROUND_H - p.gapY - cfg.gap)
  }
  // ground
  const grdG = ctx.createLinearGradient(0, H - GROUND_H, 0, H)
  grdG.addColorStop(0, '#854d0e')
  grdG.addColorStop(1, '#422006')
  ctx.fillStyle = grdG
  ctx.fillRect(0, H - GROUND_H, W, GROUND_H)
  ctx.fillStyle = '#65a30d'
  ctx.fillRect(0, H - GROUND_H, W, 6)
  ctx.fillStyle = 'rgba(0,0,0,0.18)'
  for (let x = -bgOffset; x < W; x += 22) {
    ctx.fillRect(x, H - GROUND_H + 6, 12, 4)
  }
  // ghost
  if (ghostFrames.length > 0 && frameIdx < ghostFrames.length) {
    const gy = ghostFrames[frameIdx].y
    ctx.fillStyle = 'rgba(250, 204, 21, 0.30)'
    ctx.beginPath()
    ctx.arc(BIRD_X, gy, BIRD_R, 0, Math.PI * 2)
    ctx.fill()
  }
  // bird
  const tilt = Math.max(-0.5, Math.min(1.1, bird.vy / 10))
  ctx.save()
  ctx.translate(BIRD_X, bird.y)
  ctx.rotate(tilt)
  // body
  const bg = ctx.createRadialGradient(-4, -4, 4, 0, 0, BIRD_R + 4)
  bg.addColorStop(0, '#fde68a')
  bg.addColorStop(1, '#f59e0b')
  ctx.fillStyle = bg
  ctx.shadowColor = 'rgba(250, 204, 21, 0.6)'
  ctx.shadowBlur = 14
  ctx.beginPath()
  ctx.arc(0, 0, BIRD_R, 0, Math.PI * 2)
  ctx.fill()
  ctx.shadowBlur = 0
  // wing
  const flapY = Math.sin(performance.now() / 60) * 4
  ctx.fillStyle = '#ea580c'
  ctx.beginPath()
  ctx.ellipse(-3, flapY + 2, 8, 5, -0.3, 0, Math.PI * 2)
  ctx.fill()
  // eye
  ctx.fillStyle = '#fff'
  ctx.beginPath()
  ctx.arc(5, -3, 4, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#000'
  ctx.beginPath()
  ctx.arc(6, -3, 2, 0, Math.PI * 2)
  ctx.fill()
  // beak
  ctx.fillStyle = '#dc2626'
  ctx.beginPath()
  ctx.moveTo(BIRD_R - 2, -2)
  ctx.lineTo(BIRD_R + 8, 0)
  ctx.lineTo(BIRD_R - 2, 3)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
  // score
  ctx.save()
  ctx.fillStyle = '#fff'
  ctx.strokeStyle = 'rgba(0,0,0,0.7)'
  ctx.lineWidth = 4
  ctx.font = 'bold 36px sans-serif'
  ctx.textAlign = 'center'
  ctx.strokeText(String(score), W / 2, 60)
  ctx.fillText(String(score), W / 2, 60)
  ctx.restore()
  // tap to start hint
  if (!started && alive) {
    ctx.save()
    ctx.fillStyle = 'rgba(0,0,0,0.45)'
    ctx.fillRect(W / 2 - 110, H / 2 - 30, 220, 60)
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 16px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('Tap / Space to start', W / 2, H / 2 + 6)
    ctx.restore()
  }
}

function loop(now: number) {
  if (!alive) return
  const dt = Math.min(64, now - last)
  last = now
  if (props.paused) {
    raf = requestAnimationFrame(loop)
    return
  }
  acc += dt
  // run physics at fixed 60Hz steps regardless of display refresh
  let safety = 0
  while (acc >= FIXED_DT && alive && safety < 5) {
    acc -= FIXED_DT
    physicsStep()
    safety++
  }
  draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => {
  cv.value!.width = W
  cv.value!.height = H
  reset()
  raf = requestAnimationFrame(loop)
  root.value?.focus()
})
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => {
  if (v) {
    reset()
    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(loop)
  }
})
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; touch-action: manipulation; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); max-width: 100%; height: auto; touch-action: manipulation; cursor: pointer; }
.hint { color: rgba(255,255,255,0.7); font-size: 0.85rem; }
.ghost-tag { background: rgba(250,204,21,0.18); padding: 3px 10px; border-radius: 999px; }
</style>
