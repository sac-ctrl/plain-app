<template>
  <div class="wrap">
    <div class="status">{{ status }}</div>
    <div class="board">
      <button
        v-for="(b, i) in 4"
        :key="i"
        class="pad"
        :class="['c' + i, { lit: lit === i, ok: state === 'show' }]"
        :disabled="state !== 'input'"
        @click="press(i)"
      />
    </div>
    <div class="hint">Repeat the lit-up pattern.</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useGamesStore } from '../gamesStore'

const props = defineProps<{
  difficulty: 'easy' | 'medium' | 'hard' | 'insane'
  running: boolean
  onScore: (s: number | ((p: number) => number)) => void
  onGameOver: (final?: number) => void
}>()

const store = useGamesStore()
const seq = ref<number[]>([])
const idx = ref(0)
const lit = ref<number | null>(null)
const state = ref<'show' | 'input' | 'done'>('show')
const status = ref('Watch the pattern')
const speedMap: Record<string, number> = { easy: 700, medium: 500, hard: 360, insane: 240 }
let speed = 500
let to: number[] = []

function clearTo() { to.forEach((t) => clearTimeout(t)); to = [] }

function reset() {
  speed = speedMap[props.difficulty] || 500
  seq.value = []
  idx.value = 0
  props.onScore(0)
  next()
}

function next() {
  seq.value.push(Math.floor(Math.random() * 4))
  idx.value = 0
  show()
}

function show() {
  state.value = 'show'
  status.value = 'Watch'
  let i = 0
  function play() {
    if (i >= seq.value.length) {
      state.value = 'input'
      status.value = 'Your turn — repeat the pattern'
      return
    }
    lit.value = seq.value[i]
    store.beep('tap')
    to.push(window.setTimeout(() => { lit.value = null; i++; to.push(window.setTimeout(play, 150)) }, speed))
  }
  to.push(window.setTimeout(play, 400))
}

function press(i: number) {
  if (state.value !== 'input') return
  lit.value = i
  store.beep('tap')
  setTimeout(() => (lit.value = null), 150)
  if (i !== seq.value[idx.value]) {
    state.value = 'done'
    store.beep('lose')
    props.onGameOver()
    return
  }
  idx.value++
  if (idx.value === seq.value.length) {
    props.onScore(seq.value.length * 10)
    setTimeout(next, 400)
  }
}

onMounted(reset)
onUnmounted(clearTo)
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.status { font-size: 0.95rem; color: #fff; opacity: 0.85; min-height: 1.5em; }
.board { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: min(320px, 90vw); }
.pad { aspect-ratio: 1; border: 2px solid rgba(255,255,255,0.15); border-radius: 14px; cursor: pointer; transition: filter 0.1s, transform 0.1s; }
.pad:disabled { cursor: not-allowed; }
.c0 { background: #ef4444; } .c1 { background: #22c55e; } .c2 { background: #3b82f6; } .c3 { background: #facc15; }
.pad.lit { filter: brightness(2); transform: scale(0.96); box-shadow: 0 0 32px currentColor; }
.hint { color: rgba(255,255,255,0.6); font-size: 0.82rem; }
</style>
