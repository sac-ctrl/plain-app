<template>
  <div class="wrap">
    <div class="timer-bar">
      <div class="timer-fill" :style="{ width: pct + '%' }" />
    </div>
    <div class="card">
      <div class="streak">Streak {{ streak }}</div>
      <div class="q">{{ q }}</div>
      <div class="opts">
        <button v-for="(o, i) in opts" :key="i" class="opt" @click="pick(o)">{{ o }}</button>
      </div>
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
const q = ref('')
const opts = ref<number[]>([])
const answer = ref(0)
const streak = ref(0)
const total = ref(0)
const correct = ref(0)
const time = ref(15)
let timer = 0
const params: Record<string, { time: number; max: number }> = {
  easy: { time: 15, max: 15 },
  medium: { time: 12, max: 25 },
  hard: { time: 9, max: 45 },
  insane: { time: 6, max: 80 },
}
let cfg = params.medium
const pct = computed(() => Math.max(0, (time.value / cfg.time) * 100))

function newQ() {
  const a = 1 + Math.floor(Math.random() * cfg.max)
  const b = 1 + Math.floor(Math.random() * cfg.max)
  const ops = ['+', '-', '*']
  const op = ops[Math.floor(Math.random() * ops.length)]
  let res = 0
  if (op === '+') res = a + b
  if (op === '-') res = a - b
  if (op === '*') res = a * b
  q.value = `${a} ${op} ${b}`
  answer.value = res
  const set = new Set<number>([res])
  while (set.size < 4) set.add(res + (Math.floor(Math.random() * 10) - 5))
  opts.value = Array.from(set).sort(() => Math.random() - 0.5)
  time.value = cfg.time
}

function reset() {
  cfg = params[props.difficulty] || params.medium
  streak.value = 0
  total.value = 0
  correct.value = 0
  newQ()
  props.onScore(0)
  clearInterval(timer)
  timer = window.setInterval(() => {
    time.value -= 0.1
    if (time.value <= 0) end()
  }, 100)
}

function pick(o: number) {
  total.value++
  if (o === answer.value) {
    correct.value++
    streak.value++
    const sc = (streak.value * 10) + 5
    props.onScore((s) => s + sc)
    store.beep('tick')
    newQ()
  } else {
    streak.value = 0
    store.beep('lose')
    end()
  }
}

function end() {
  clearInterval(timer)
  props.onGameOver()
}

onMounted(reset)
onUnmounted(() => clearInterval(timer))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { width: min(420px, 100%); display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.timer-bar { height: 8px; border-radius: 999px; background: rgba(255,255,255,0.1); overflow: hidden; }
.timer-fill { height: 100%; background: linear-gradient(90deg, #22c55e, #facc15, #ef4444); transition: width 0.1s linear; }
.card { background: rgba(255,255,255,0.07); border: 1px solid rgba(255,255,255,0.12); border-radius: 18px; padding: 22px; text-align: center; backdrop-filter: blur(12px); }
.streak { font-size: 0.85rem; color: rgba(255,255,255,0.7); margin-bottom: 8px; }
.q { font-size: 2.4rem; font-weight: 800; color: #fff; margin: 14px 0 22px; }
.opts { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.opt { padding: 14px; border: none; border-radius: 12px; background: linear-gradient(135deg, #6366f1, #a855f7); color: #fff; font-size: 1.1rem; font-weight: 700; cursor: pointer; }
.opt:active { transform: scale(0.96); }
</style>
