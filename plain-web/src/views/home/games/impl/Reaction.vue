<template>
  <div class="wrap">
    <div class="board" :class="state" @click="onTap">
      <div v-if="state === 'wait'" class="msg">Wait for green…</div>
      <div v-else-if="state === 'go'" class="msg big">TAP NOW</div>
      <div v-else-if="state === 'too-soon'" class="msg">Too soon! Tap to retry.</div>
      <div v-else-if="state === 'done'" class="msg">
        <div class="ms-big">{{ ms }}<span>ms</span></div>
        <small>Round {{ round }}/{{ rounds }}. Tap to continue.</small>
      </div>
      <div v-else class="msg">Tap to start round {{ round + 1 }}/{{ rounds }}</div>
    </div>
    <div class="info">Best: {{ Math.round(bestMs) || '—' }} ms · Avg: {{ Math.round(avgMs) || '—' }} ms</div>
    <div class="lb">
      <div class="lb-title">Live ranking</div>
      <div v-for="(e, i) in liveLb" :key="i" class="lb-row" :class="{ me: e.me }">
        <span>#{{ i + 1 }}</span>
        <span>{{ e.name }}</span>
        <span>{{ e.ms }} ms</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useGamesStore, fakeLeaderboardNames } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  mode?: 'classic' | 'time'
  running: boolean
  paused?: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const state = ref<'idle' | 'wait' | 'go' | 'done' | 'too-soon'>('idle')
const round = ref(0)
const ms = ref(0)
const times = ref<number[]>([])
let startTs = 0
let to = 0
const rounds = 5
const dCfg: Record<string, [number, number]> = {
  easy: [1500, 4000],
  medium: [1200, 3500],
  hard: [800, 2800],
  insane: [500, 2000],
}

function reset() {
  state.value = 'idle'
  round.value = 0
  times.value = []
  ms.value = 0
  props.onScore(0)
}

function startRound() {
  state.value = 'wait'
  const [a, b] = dCfg[props.difficulty] || dCfg.medium
  const wait = a + Math.random() * (b - a)
  to = window.setTimeout(() => {
    state.value = 'go'
    startTs = performance.now()
  }, wait)
}

function onTap() {
  if (state.value === 'idle') return startRound()
  if (state.value === 'wait') {
    clearTimeout(to)
    state.value = 'too-soon'
    store.beep('lose')
    store.vibrate(80)
    return
  }
  if (state.value === 'too-soon') return reset()
  if (state.value === 'go') {
    ms.value = Math.round(performance.now() - startTs)
    times.value.push(ms.value)
    state.value = 'done'
    const sc = Math.max(0, 1500 - ms.value)
    props.onScore((s) => s + sc)
    store.beep('tick')
    store.vibrate(20)
    return
  }
  if (state.value === 'done') {
    round.value++
    if (round.value >= rounds) { props.onGameOver(); return }
    startRound()
  }
}

const bestMs = computed(() => (times.value.length ? Math.min(...times.value) : 0))
const avgMs = computed(() => (times.value.length ? times.value.reduce((a, b) => a + b, 0) / times.value.length : 0))
const liveLb = computed(() => {
  const myMs = bestMs.value || 9999
  const fake = fakeLeaderboardNames.slice(0, 8).map((n, i) => ({ name: n, ms: 180 + i * 22 + Math.floor(Math.random() * 10), me: false }))
  if (myMs < 9999) fake.push({ name: 'You', ms: Math.round(myMs), me: true })
  return fake.sort((a, b) => a.ms - b.ms).slice(0, 8)
})

onMounted(reset)
onUnmounted(() => clearTimeout(to))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%; max-width: 420px; padding: 0 12px; }
.board { width: 100%; height: 320px; border-radius: 22px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.15s, box-shadow 0.15s; user-select: none; }
.board.idle { background: linear-gradient(135deg, #334155, #475569); }
.board.wait { background: linear-gradient(135deg, #ef4444, #dc2626); box-shadow: 0 0 40px rgba(239, 68, 68, 0.4); }
.board.go { background: linear-gradient(135deg, #22c55e, #16a34a); box-shadow: 0 0 60px rgba(34, 197, 94, 0.6); animation: pulse 0.4s ease infinite alternate; }
.board.done { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.board.too-soon { background: linear-gradient(135deg, #f59e0b, #ea580c); }
@keyframes pulse { from { transform: scale(1); } to { transform: scale(1.02); } }
.msg { color: #fff; font-size: 1.1rem; text-align: center; }
.msg.big { font-size: 2rem; font-weight: 800; letter-spacing: 0.05em; }
.ms-big { font-size: 3.5rem; font-weight: 800; line-height: 1; }
.ms-big span { font-size: 1.2rem; opacity: 0.7; margin-left: 4px; }
.info { color: rgba(255,255,255,0.7); font-size: 0.88rem; }
.lb { width: 100%; background: rgba(0, 0, 0, 0.25); border-radius: 14px; padding: 10px 12px; }
.lb-title { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.06em; opacity: 0.6; margin-bottom: 6px; }
.lb-row { display: grid; grid-template-columns: 30px 1fr 70px; gap: 6px; padding: 3px 0; font-size: 0.85rem; opacity: 0.92; border-bottom: 1px solid rgba(255, 255, 255, 0.04); }
.lb-row.me { color: #facc15; font-weight: 700; }
</style>
