<template>
  <div class="wrap">
    <div class="info">Time {{ time.toFixed(1) }}s · Hits {{ hits }}/{{ shots }} · Acc {{ acc }}%</div>
    <div class="board" ref="board" @pointerdown="onMiss">
      <div
        v-for="t in targets"
        :key="t.id"
        class="target"
        :style="{ left: t.x + 'px', top: t.y + 'px', width: t.r * 2 + 'px', height: t.r * 2 + 'px' }"
        @pointerdown.stop="hit(t)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const board = ref<HTMLDivElement>()
interface T { id: number; x: number; y: number; r: number }
const targets = ref<T[]>([])
const hits = ref(0)
const shots = ref(0)
const time = ref(30)
let id = 0
let raf = 0
let last = 0
const cfg: Record<string, { count: number; r: number; time: number }> = {
  easy: { count: 4, r: 36, time: 30 },
  medium: { count: 5, r: 28, time: 30 },
  hard: { count: 6, r: 22, time: 30 },
  insane: { count: 7, r: 18, time: 30 },
}
let c = cfg.medium
const acc = computed(() => (shots.value ? Math.round((hits.value / shots.value) * 100) : 0))

function reset() {
  c = cfg[props.difficulty] || cfg.medium
  hits.value = 0; shots.value = 0; time.value = c.time
  targets.value = []
  for (let i = 0; i < c.count; i++) spawn()
  props.onScore(0)
  last = performance.now()
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(loop)
}

function spawn() {
  const b = board.value
  if (!b) return
  const w = b.clientWidth, h = b.clientHeight
  targets.value.push({ id: id++, x: Math.random() * (w - c.r * 2), y: Math.random() * (h - c.r * 2), r: c.r })
}

function loop(now: number) {
  const dt = (now - last) / 1000; last = now
  time.value -= dt
  if (time.value <= 0) {
    time.value = 0
    cancelAnimationFrame(raf)
    const sc = hits.value * 10 + acc.value
    props.onScore(sc)
    setTimeout(() => props.onGameOver(sc), 200)
    return
  }
  raf = requestAnimationFrame(loop)
}

function hit(t: T) {
  hits.value++; shots.value++
  store.beep('tick')
  targets.value = targets.value.filter((x) => x.id !== t.id)
  spawn()
  props.onScore(hits.value * 10 + acc.value)
}
function onMiss() {
  shots.value++
  store.beep('lose')
}

onMounted(() => setTimeout(reset, 30))
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: 10px; align-items: center; width: 100%; }
.info { color: #fff; font-weight: 600; }
.board { position: relative; width: min(420px, 95%); height: 460px; background: radial-gradient(70% 80% at 50% 40%, rgba(99,102,241,0.18), transparent 70%), #0b0e1f; border-radius: 18px; overflow: hidden; touch-action: none; cursor: crosshair; }
.target { position: absolute; border-radius: 50%; background: radial-gradient(circle at 30% 30%, #fff 0%, #ef4444 50%, #7f1d1d 100%); box-shadow: 0 0 20px rgba(239,68,68,0.6); cursor: crosshair; }
</style>
