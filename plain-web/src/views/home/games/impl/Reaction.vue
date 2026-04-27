<template>
  <div class="wrap">
    <div class="board" :class="state" @click="onTap">
      <div v-if="state === 'wait'" class="msg">Wait for green…</div>
      <div v-else-if="state === 'go'" class="msg big">TAP NOW</div>
      <div v-else-if="state === 'too-soon'" class="msg">Too soon! Tap to retry.</div>
      <div v-else-if="state === 'done'" class="msg">
        {{ ms }} ms<br />
        <small>Round {{ round }}/{{ rounds }}. Tap to continue.</small>
      </div>
      <div v-else class="msg">Tap to start round {{ round + 1 }}/{{ rounds }}</div>
    </div>
    <div class="info">Best: {{ Math.round(bestMs) }} ms · Avg: {{ Math.round(avgMs) }} ms</div>
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
    return
  }
  if (state.value === 'done') {
    round.value++
    if (round.value >= rounds) {
      props.onGameOver()
      return
    }
    startRound()
  }
}

const bestMs = computed(() => (times.value.length ? Math.min(...times.value) : 0))
const avgMs = computed(() => (times.value.length ? times.value.reduce((a, b) => a + b, 0) / times.value.length : 0))

onMounted(reset)
onUnmounted(() => clearTimeout(to))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%; }
.board { width: min(380px, 95%); height: 360px; border-radius: 22px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.15s; user-select: none; }
.board.idle { background: #334155; }
.board.wait { background: #ef4444; }
.board.go { background: #22c55e; }
.board.done { background: #3b82f6; }
.board.too-soon { background: #f59e0b; }
.msg { color: #fff; font-size: 1.1rem; text-align: center; }
.msg.big { font-size: 2rem; font-weight: 800; }
.info { color: rgba(255,255,255,0.7); font-size: 0.88rem; }
</style>
