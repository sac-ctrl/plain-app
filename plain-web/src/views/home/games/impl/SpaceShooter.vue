<template>
  <div class="wrap" tabindex="0" ref="root" @keydown="onKey" @keyup="onKeyUp">
    <canvas ref="cv" class="cv"
      @touchstart.prevent="ts"
      @touchmove.prevent="tm"
      @touchend.prevent="tend"
    />
    <div class="hud">
      <div class="lives">
        <i-lucide:heart v-for="i in livesArr" :key="i" />
      </div>
      <div class="weapon">Lvl <b>{{ weaponLevel }}</b> <span v-if="weaponLevel > 1">{{ weaponName }}</span></div>
      <div class="wave">Wave {{ wave }}</div>
    </div>
    <div class="hint">Move ← → · Space to shoot · Catch power drops</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'classic' | 'survival'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

type Drop = { x: number; y: number; kind: 'wpn' | 'shield' | 'life' }
type Enemy = { x: number; y: number; vx: number; hp: number; isBoss?: boolean; maxHp: number; phase?: number }
type Bullet = { x: number; y: number; vx?: number; from: 'p' | 'e' }

const store = useGamesStore()
const root = ref<HTMLDivElement>()
const cv = ref<HTMLCanvasElement>()
const W = 360, H = 540
let raf = 0, alive = true
let player = { x: W / 2, y: H - 50, vx: 0 }
let bullets: Bullet[] = []
let enemies: Enemy[] = []
let drops: Drop[] = []
let particles: { x: number; y: number; vx: number; vy: number; life: number; color: string }[] = []
let lastShot = 0
let lastSpawn = 0
const score = ref(0)
const lives = ref(3)
const livesArr = computed(() => Array.from({ length: lives.value }))
const weaponLevel = ref(1)
const wave = ref(1)
const weaponName = computed(() => (['', '', 'Twin', 'Spread', 'Heavy', 'Beam'][weaponLevel.value] || 'Beam'))
let killsThisWave = 0
let bossActive = false
let shieldUntil = 0
let keys: Record<string, boolean> = {}
const cfg: Record<string, { es: number; spawn: number; bossEvery: number; killsPerWave: number }> = {
  easy: { es: 1.2, spawn: 1300, bossEvery: 3, killsPerWave: 8 },
  medium: { es: 1.7, spawn: 1000, bossEvery: 3, killsPerWave: 10 },
  hard: { es: 2.3, spawn: 700, bossEvery: 2, killsPerWave: 12 },
  insane: { es: 3, spawn: 500, bossEvery: 2, killsPerWave: 14 },
}
let c = cfg.medium
let touchPullX: number | null = null

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  alive = true
  player = { x: W / 2, y: H - 50, vx: 0 }
  bullets = []; enemies = []; drops = []; particles = []
  lastShot = 0; lastSpawn = 0
  score.value = 0; lives.value = 3
  weaponLevel.value = 1
  wave.value = 1
  killsThisWave = 0
  bossActive = false
  shieldUntil = 0
  props.onScore(0)
}

function shoot() {
  store.beep('tap')
  const lvl = weaponLevel.value
  if (lvl === 1) bullets.push({ x: player.x, y: player.y - 18, from: 'p' })
  else if (lvl === 2) {
    bullets.push({ x: player.x - 8, y: player.y - 18, from: 'p' })
    bullets.push({ x: player.x + 8, y: player.y - 18, from: 'p' })
  } else if (lvl === 3) {
    bullets.push({ x: player.x, y: player.y - 18, from: 'p' })
    bullets.push({ x: player.x - 8, y: player.y - 18, from: 'p', vx: -2 })
    bullets.push({ x: player.x + 8, y: player.y - 18, from: 'p', vx: 2 })
  } else if (lvl === 4) {
    for (let i = -2; i <= 2; i++) bullets.push({ x: player.x + i * 6, y: player.y - 18, from: 'p', vx: i * 1.4 })
  } else {
    for (let i = -3; i <= 3; i++) bullets.push({ x: player.x + i * 5, y: player.y - 18, from: 'p', vx: i * 0.8 })
  }
}
function onKey(e: KeyboardEvent) {
  keys[e.key] = true
  if (e.key === ' ' && performance.now() - lastShot > shotCooldown()) { shoot(); lastShot = performance.now(); e.preventDefault() }
}
function shotCooldown() { return Math.max(120, 240 - weaponLevel.value * 25) }
function onKeyUp(e: KeyboardEvent) { keys[e.key] = false }

function ts(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  touchPullX = e.touches[0].clientX - r.left
  if (performance.now() - lastShot > shotCooldown()) { shoot(); lastShot = performance.now() }
}
function tm(e: TouchEvent) {
  const r = cv.value!.getBoundingClientRect()
  touchPullX = e.touches[0].clientX - r.left
}
function tend() { touchPullX = null }

function spawnBoss() {
  bossActive = true
  enemies.push({ x: W / 2, y: -40, vx: 1.6, hp: 18 + wave.value * 4, maxHp: 18 + wave.value * 4, isBoss: true, phase: 0 })
  store.beep('lose')
}

function spawnDrop(x: number, y: number) {
  const r = Math.random()
  const kind: Drop['kind'] = r < 0.6 ? 'wpn' : r < 0.85 ? 'shield' : 'life'
  drops.push({ x, y, kind })
}

function explode(x: number, y: number, color = '#facc15') {
  for (let i = 0; i < 14; i++)
    particles.push({ x, y, vx: (Math.random() - 0.5) * 6, vy: (Math.random() - 0.5) * 6, life: 36, color })
}

function step(dt: number) {
  if (keys['ArrowLeft']) player.vx = -5
  else if (keys['ArrowRight']) player.vx = 5
  else player.vx *= 0.8
  if (touchPullX != null) {
    player.x += (touchPullX - player.x) * 0.25
    if (performance.now() - lastShot > shotCooldown() + 20) { shoot(); lastShot = performance.now() }
  } else { player.x += player.vx }
  player.x = Math.max(20, Math.min(W - 20, player.x))

  bullets.forEach((b) => { b.y += b.from === 'p' ? -8 : 4; if (b.vx) b.x += b.vx })
  bullets = bullets.filter((b) => b.y > -10 && b.y < H + 20)
  drops.forEach((d) => (d.y += 1.6))
  for (const d of drops) {
    if (Math.abs(d.x - player.x) < 18 && Math.abs(d.y - player.y) < 18) {
      if (d.kind === 'wpn') { weaponLevel.value = Math.min(5, weaponLevel.value + 1); store.beep('power') }
      else if (d.kind === 'shield') { shieldUntil = performance.now() + 6000; store.beep('power') }
      else { lives.value = Math.min(5, lives.value + 1); store.beep('win') }
      d.y = H + 100
    }
  }
  drops = drops.filter((d) => d.y < H + 20)

  lastSpawn += dt
  if (!bossActive && lastSpawn >= c.spawn) {
    lastSpawn = 0
    enemies.push({ x: 30 + Math.random() * (W - 60), y: -20, vx: (Math.random() - 0.5) * 1.4, hp: 1, maxHp: 1 })
    if (wave.value >= 3 && Math.random() < 0.25) {
      enemies.push({ x: 30 + Math.random() * (W - 60), y: -40, vx: (Math.random() - 0.5) * 2, hp: 2, maxHp: 2 })
    }
  }
  enemies.forEach((en) => {
    en.y += en.isBoss ? c.es * 0.4 : c.es
    en.x += en.vx
    if (en.x < 30 || en.x > W - 30) en.vx *= -1
    if (en.isBoss && en.y > 80) { en.y = 80; en.phase = (en.phase || 0) + 1; if (en.phase! % 60 === 0) bullets.push({ x: en.x, y: en.y + 16, from: 'e' }) }
  })
  for (const en of enemies) {
    if (en.y > H + 20 && !en.isBoss) { takeHit(); en.y = H + 100 }
    if (Math.abs(en.x - player.x) < 18 && Math.abs(en.y - player.y) < 18) { takeHit(); en.y = H + 100 }
  }
  for (const b of bullets) {
    if (b.from === 'e' && Math.abs(b.x - player.x) < 14 && Math.abs(b.y - player.y) < 14) { takeHit(); b.y = H + 100 }
  }
  enemies = enemies.filter((en) => en.y < H + 20 && en.hp > 0)
  for (const b of bullets) {
    if (b.from !== 'p') continue
    for (const en of enemies) {
      if (Math.abs(b.x - en.x) < (en.isBoss ? 32 : 16) && Math.abs(b.y - en.y) < (en.isBoss ? 24 : 16)) {
        en.hp -= 1; b.y = -100
        if (en.hp <= 0) {
          score.value += en.isBoss ? 100 : 10
          props.onScore(score.value)
          explode(en.x, en.y, en.isBoss ? '#ec4899' : '#facc15')
          en.y = H + 100
          store.beep(en.isBoss ? 'win' : 'tick')
          if (en.isBoss) {
            bossActive = false
            wave.value++
            killsThisWave = 0
            spawnDrop(en.x, en.y)
          } else {
            killsThisWave++
            if (Math.random() < 0.12) spawnDrop(en.x, en.y)
            if (killsThisWave >= c.killsPerWave && wave.value % c.bossEvery === 0) {
              spawnBoss()
            } else if (killsThisWave >= c.killsPerWave) {
              wave.value++
              killsThisWave = 0
            }
          }
        }
      }
    }
  }
  particles.forEach((p) => { p.x += p.vx; p.y += p.vy; p.life-- })
  particles = particles.filter((p) => p.life > 0)
}

function takeHit() {
  if (performance.now() < shieldUntil) return
  lives.value--
  store.beep('lose')
  store.vibrate(80)
  if (lives.value <= 0) die()
}

function die() { alive = false; cancelAnimationFrame(raf); props.onGameOver(score.value) }

function draw() {
  const ctx = cv.value!.getContext('2d')!
  ctx.fillStyle = '#070815'; ctx.fillRect(0, 0, W, H)
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = 'rgba(255,255,255,0.3)'
    ctx.fillRect((i * 53) % W, (i * 87 + (performance.now() / 8) % H) % H, 2, 2)
  }
  // shield
  if (performance.now() < shieldUntil) {
    ctx.strokeStyle = `rgba(56, 189, 248, ${0.5 + Math.sin(performance.now() / 100) * 0.3})`
    ctx.lineWidth = 2
    ctx.beginPath(); ctx.arc(player.x, player.y, 22, 0, Math.PI * 2); ctx.stroke()
  }
  // player
  ctx.fillStyle = '#a855f7'
  ctx.beginPath(); ctx.moveTo(player.x, player.y - 14); ctx.lineTo(player.x - 14, player.y + 14); ctx.lineTo(player.x + 14, player.y + 14); ctx.closePath(); ctx.fill()
  // engine
  ctx.fillStyle = '#22d3ee'
  ctx.fillRect(player.x - 4, player.y + 14, 8, 6 + Math.sin(performance.now() / 80) * 3)
  // bullets
  bullets.forEach((b) => {
    ctx.fillStyle = b.from === 'p' ? '#facc15' : '#ef4444'
    ctx.fillRect(b.x - 2, b.y - 8, 4, 12)
  })
  // enemies
  enemies.forEach((en) => {
    if (en.isBoss) {
      ctx.fillStyle = '#ec4899'
      ctx.fillRect(en.x - 32, en.y - 24, 64, 48)
      // boss hp bar
      ctx.fillStyle = '#475569'
      ctx.fillRect(40, 8, W - 80, 8)
      ctx.fillStyle = '#ef4444'
      ctx.fillRect(40, 8, (W - 80) * (en.hp / en.maxHp), 8)
    } else {
      ctx.fillStyle = en.maxHp > 1 ? '#f97316' : '#ef4444'
      ctx.fillRect(en.x - 14, en.y - 12, 28, 24)
    }
  })
  // drops
  drops.forEach((d) => {
    const col = d.kind === 'wpn' ? '#facc15' : d.kind === 'shield' ? '#38bdf8' : '#ef4444'
    ctx.fillStyle = col; ctx.shadowColor = col; ctx.shadowBlur = 12
    ctx.beginPath(); ctx.arc(d.x, d.y, 8, 0, Math.PI * 2); ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#000'; ctx.font = 'bold 10px sans-serif'; ctx.textAlign = 'center'
    ctx.fillText(d.kind === 'wpn' ? '+' : d.kind === 'shield' ? 'S' : '♥', d.x, d.y + 3)
  })
  particles.forEach((p) => {
    ctx.fillStyle = p.color.replace(')', `,${p.life / 36})`).replace('rgb', 'rgba')
    if (p.color.startsWith('#')) ctx.fillStyle = p.color
    ctx.globalAlpha = p.life / 36
    ctx.fillRect(p.x, p.y, 3, 3)
    ctx.globalAlpha = 1
  })
}

let last = 0
function loop(now: number) {
  if (!alive) return
  const dt = now - (last || now); last = now
  if (props.paused) { raf = requestAnimationFrame(loop); return }
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
.hud { display: flex; gap: 14px; align-items: center; width: 360px; justify-content: space-between; padding: 0 6px; }
.lives { color: #ef4444; display: flex; gap: 2px; }
.weapon { color: #facc15; font-size: 0.82rem; }
.weapon b { color: #fff; }
.wave { color: rgba(255,255,255,0.7); font-size: 0.82rem; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.78rem; }
</style>
