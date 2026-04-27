<template>
  <canvas ref="canvas" class="matrix-rain-canvas" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvas = ref<HTMLCanvasElement | null>(null)
let raf = 0
let resizeObs: ResizeObserver | null = null
let drops: number[] = []
let columns = 0
let lastTs = 0

const FONT_SIZE = 14
const GLYPHS =
  'ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ012345789Z:・."=*+-<>¦|_'

function resize() {
  const c = canvas.value
  if (!c) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = window.innerWidth
  const h = window.innerHeight
  c.width = Math.floor(w * dpr)
  c.height = Math.floor(h * dpr)
  c.style.width = w + 'px'
  c.style.height = h + 'px'
  const ctx = c.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  columns = Math.ceil(w / FONT_SIZE)
  drops = new Array(columns).fill(0).map(() => Math.random() * (h / FONT_SIZE))
}

function draw(ts: number) {
  raf = requestAnimationFrame(draw)
  const c = canvas.value
  if (!c) return
  const ctx = c.getContext('2d')
  if (!ctx) return

  // Throttle to ~30fps
  if (ts - lastTs < 33) return
  lastTs = ts

  const w = window.innerWidth
  const h = window.innerHeight

  // Trail / fade effect
  ctx.fillStyle = 'rgba(0, 8, 4, 0.18)'
  ctx.fillRect(0, 0, w, h)

  ctx.font = `${FONT_SIZE}px ui-monospace, monospace`
  for (let i = 0; i < columns; i++) {
    const ch = GLYPHS.charAt(Math.floor(Math.random() * GLYPHS.length))
    const x = i * FONT_SIZE
    const y = drops[i] * FONT_SIZE

    // Bright head
    ctx.fillStyle = 'rgba(220, 255, 230, 0.95)'
    ctx.fillText(ch, x, y)
    // Trail glyph above (greener)
    ctx.fillStyle = 'rgba(0, 255, 124, 0.7)'
    ctx.fillText(ch, x, y - FONT_SIZE)

    if (y > h && Math.random() > 0.975) {
      drops[i] = 0
    }
    drops[i] += 1
  }
}

onMounted(() => {
  resize()
  raf = requestAnimationFrame(draw)
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', resize)
  if (resizeObs) resizeObs.disconnect()
})
</script>
