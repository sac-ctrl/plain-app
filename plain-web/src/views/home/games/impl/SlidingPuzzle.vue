<template>
  <div class="wrap">
    <div class="info">Moves {{ moves }} · Time {{ time }}s</div>
    <div class="board" :style="{ gridTemplateColumns: `repeat(${size}, 1fr)` }">
      <button
        v-for="(t, i) in tiles"
        :key="i"
        class="tile"
        :class="{ empty: t === 0 }"
        :disabled="t === 0"
        @click="tap(i)"
      >{{ t || '' }}</button>
    </div>
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
const sizes: Record<string, number> = { easy: 3, medium: 4, hard: 5, insane: 6 }
const size = ref(4)
const tiles = ref<number[]>([])
const moves = ref(0)
const time = ref(0)
let timer = 0

function shuffle(): number[] {
  const n = size.value * size.value
  const arr = Array.from({ length: n }, (_, i) => i)
  // Make solvable shuffle by random valid moves
  let zero = n - 1
  for (let s = 0; s < 200 + size.value * 60; s++) {
    const r = Math.floor(zero / size.value), c = zero % size.value
    const cand: number[] = []
    if (r > 0) cand.push(zero - size.value)
    if (r < size.value - 1) cand.push(zero + size.value)
    if (c > 0) cand.push(zero - 1)
    if (c < size.value - 1) cand.push(zero + 1)
    const pick = cand[Math.floor(Math.random() * cand.length)]
    ;[arr[zero], arr[pick]] = [arr[pick], arr[zero]]
    zero = pick
  }
  return arr
}

function reset() {
  size.value = sizes[props.difficulty] || 4
  tiles.value = shuffle()
  moves.value = 0
  time.value = 0
  props.onScore(0)
  clearInterval(timer)
  timer = window.setInterval(() => time.value++, 1000)
}

function tap(i: number) {
  const z = tiles.value.indexOf(0)
  const r1 = Math.floor(i / size.value), c1 = i % size.value
  const r2 = Math.floor(z / size.value), c2 = z % size.value
  if (Math.abs(r1 - r2) + Math.abs(c1 - c2) !== 1) return
  ;[tiles.value[i], tiles.value[z]] = [tiles.value[z], tiles.value[i]]
  moves.value++
  store.beep('tap')
  if (won()) {
    clearInterval(timer)
    const sc = Math.max(0, 1000 - moves.value * 4 - time.value * 3)
    props.onScore(sc)
    setTimeout(() => props.onGameOver(sc), 300)
  }
}

function won(): boolean {
  for (let i = 0; i < tiles.value.length - 1; i++) if (tiles.value[i] !== i + 1) return false
  return true
}

onMounted(reset)
onUnmounted(() => clearInterval(timer))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.info { color: #fff; font-weight: 600; }
.board { display: grid; gap: 6px; max-width: 380px; width: 100%; }
.tile { aspect-ratio: 1; border: none; border-radius: 10px; background: linear-gradient(135deg, #6366f1, #a855f7); color: #fff; font-weight: 800; font-size: 1.4rem; cursor: pointer; transition: transform 0.1s; }
.tile:active { transform: scale(0.95); }
.tile.empty { background: rgba(255,255,255,0.05); cursor: default; }
</style>
