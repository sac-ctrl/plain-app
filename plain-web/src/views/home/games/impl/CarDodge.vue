<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <canvas ref="cv" class="cv"
      @touchstart.prevent="ts"
      @touchmove.prevent="tm"
      @touchend.prevent="tend"
    />
    <div class="bar">
      <div class="nitro">
        <div class="n-fill" :style="{ width: nitroPct + '%' }"></div>
        <span>NITRO {{ Math.round(nitroPct) }}%</span>
      </div>
      <button class="boost" :class="{ active: boosting }" @mousedown="boostStart" @mouseup="boostEnd" @touchstart.prevent="boostStart" @touchend.prevent="boostEnd">
        <i-lucide:flame /> Hold for nitro
      </button>
    </div>
    <div class="hint">Tap left/right side to switch lane. Hold the boost.</div>
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
let raf = 0, alive = true
const lanes = [60, 130, 200, 260]
let lane = 1
let player = { x: lanes[1], y: H - 80 }
type Car = { lane: number; y: number; color: string; counted?: boolean; missed?: boolean }
let cars: Car[] = []
let road = 0
let score = 0
let spawnT = 0
let elapsed = 0
const cfg: Record<string, { speed: number; spawn: number }> = {
  easy: { speed: 3, spawn: 1100 },
  medium: { speed: 4.5, spawn: 900 },
  hard: { speed: 6, spawn: 700 },
  insane: { speed: 8, spawn: 500 },
}
let c = cfg.medium
let baseSpeed = c.speed

const nitroPct = ref(100)
const boosting = ref(false)
const flashes = ref<{ x: number; y: number; life: number; text: string }[]>([])

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  baseSpeed = c.speed
  alive = true
  lane = 1
  player.x = lanes[1]
  cars = []
  road = 0
  score = 0
  spawnT = 0
  elapsed = 0
  nitroPct.value = 100
  boosting.value = false
  flashes.value = []
  props.onScore(0)
}

function setLane(l: number) {
  lane = Math.max(0, Math.min(lanes.length - 1, l))
  store.beep('tap')
  store.vibrate(20)
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') setLane(lane - 1)
  if (e.key === 'ArrowRight') setLane(lane + 1)
  if (e.key === ' ' || e.key === 'Shift') boostStart()
}
function onKeyUp(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'Shift') boostEnd()
}
function ts(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  const x = e.touches[0].clientX - r.left
  setLane(x < r.width / 2 ? lane - 1 : lane + 1)
}
function tm() { /* placeholder */ }
function tend() { /* placeholder */ }
function boostStart() { if (nitroPct.value > 5) { boosting.value = true; store.beep('power') } }
function boostEnd() { boosting.value = false }

function flash(text: string, x: number, y: number) {
  flashes.value.push({ x, y, life: 60, text })
}

function step(dt: number) {
  elapsed += dt
  // ramp difficulty over time
  const ramp = 1 + Math.min(2, elapsed / 30000)
  let spd = baseSpeed * ramp
  if (boosting.value && nitroPct.value > 0) {
    spd *= 1.8
    nitroPct.value = Math.max(0, nitroPct.value - dt * 0.05)
  } else {
    nitroPct.value = Math.min(100, nitroPct.value + dt * 0.012)
    if (nitroPct.value <= 0) boosting.value = false
  }
  road = (road + spd * 2) % 40
  player.x += (lanes[lane] - player.x) * 0.25
  cars.forEach((cr) => (cr.y += spd * 1.4))
  spawnT += dt
  const spawnRate = c.spawn / Math.min(2.5, ramp)
  if (spawnT >= spawnRate) {
    spawnT = 0
    const colors = ['#ef4444', '#facc15', '#3b82f6', '#22c55e']
    cars.push({ lane: Math.floor(Math.random() * lanes.length), y: -60, color: colors[Math.floor(Math.random() * colors.length)] })
  }
  cars = cars.filter((cr) => cr.y < H + 80)
  for (const cr of cars) {
    if (cr.lane === lane && Math.abs(cr.y - player.y) < 60) return die()
    // near miss bonus when adjacent lane car is right next to the player
    if (!cr.missed && Math.abs(cr.lane - lane) === 1 && Math.abs(cr.y - player.y) < 24) {
      cr.missed = true
      score += 3
      props.onScore(score)
      store.beep('power')
      flash('+3 NEAR MISS', lanes[cr.lane] + 18, player.y - 16)
    }
    if (cr.y > H && !cr.counted) {
      cr.counted = true
      score += 5
      if (boosting.value) score += 2
      props.onScore(score)
      store.beep('tick')
    }
  }
  flashes.value.forEach((f) => { f.life--; f.y -= 0.6 })
  flashes.value = flashes.value.filter((f) => f.life > 0)
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
    // headlights
    ctx.fillStyle = 'rgba(255,255,255,0.5)'
    ctx.fillRect(lanes[cr.lane] + 4, cr.y + 50, 6, 4)
    ctx.fillRect(lanes[cr.lane] + 26, cr.y + 50, 6, 4)
  }
  // player car
  ctx.fillStyle = boosting.value ? '#22d3ee' : '#a855f7'
  if (boosting.value) { ctx.shadowColor = '#22d3ee'; ctx.shadowBlur = 18 }
  ctx.fillRect(player.x, player.y, 36, 56)
  ctx.shadowBlur = 0
  // boost trail
  if (boosting.value) {
    for (let i = 1; i < 8; i++) {
      ctx.fillStyle = `rgba(34, 211, 238, ${0.3 - i * 0.03})`
      ctx.fillRect(player.x + 6, player.y + 56 + i * 6, 24, 6)
    }
  }
  // flashes
  for (const f of flashes.value) {
    ctx.fillStyle = `rgba(250, 204, 21, ${f.life / 60})`
    ctx.font = 'bold 12px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(f.text, f.x, f.y)
  }
}

let last = 0
let acc = 0
const FIXED_DT = 1000 / 60
function loop(now: number) {
  if (!alive) return
  const dt = Math.min(64, now - (last || now)); last = now
  if (props.paused) { raf = requestAnimationFrame(loop); return }
  acc += dt
  let safety = 0
  while (acc >= FIXED_DT && alive && safety < 5) {
    acc -= FIXED_DT
    step(FIXED_DT)
    safety++
  }
  draw()
  raf = requestAnimationFrame(loop)
}

onMounted(() => { cv.value!.width = W; cv.value!.height = H; reset(); raf = requestAnimationFrame(loop); root.value?.focus() })
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => { if (v) { reset(); raf = requestAnimationFrame(loop) } })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; outline: none; user-select: none; }
.cv { border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.4); touch-action: none; }
.bar { display: flex; gap: 10px; align-items: center; width: 320px; }
.nitro { flex: 1; position: relative; height: 18px; background: rgba(255,255,255,0.1); border-radius: 999px; overflow: hidden; }
.nitro span { position: absolute; left: 0; right: 0; top: 0; bottom: 0; display: flex; align-items: center; justify-content: center; font-size: 0.65rem; font-weight: 700; color: #fff; letter-spacing: 0.05em; }
.n-fill { height: 100%; background: linear-gradient(90deg, #06b6d4, #22d3ee); transition: width 0.1s; }
.boost { display: inline-flex; gap: 4px; align-items: center; background: linear-gradient(135deg, #ef4444, #f97316); border: none; color: #fff; border-radius: 999px; padding: 6px 12px; font-size: 0.78rem; font-weight: 700; cursor: pointer; }
.boost.active { box-shadow: 0 0 14px rgba(34, 211, 238, 0.7); }
.hint { color: rgba(255,255,255,0.6); font-size: 0.78rem; }
</style>
