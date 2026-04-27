<template>
  <div class="wrap">
    <div class="info">
      <div>Pairs {{ matched / 2 }}/{{ cards.length / 2 }}</div>
      <div>Time {{ time }}s</div>
    </div>
    <div class="board" :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)` }">
      <button
        v-for="(c, i) in cards"
        :key="i"
        class="card"
        :class="{ flipped: c.flipped || c.found, found: c.found }"
        :disabled="c.found || c.flipped || lock"
        @click="flip(i)"
      >
        <span class="back">?</span>
        <span class="front">{{ c.v }}</span>
      </button>
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
const symbols = ['🍎','🍌','🍇','🍒','🍑','🥝','🍉','🍍','🥑','🥕','🌶️','🍋','🍓','🥭','🍐','🥥','🍈','🥨']
const sizes: Record<string, [number, number]> = { easy: [3, 4], medium: [4, 4], hard: [4, 5], insane: [5, 6] }

interface Card { v: string; flipped: boolean; found: boolean; pid: number }
const cards = ref<Card[]>([])
const cols = ref(4)
const matched = ref(0)
const lock = ref(false)
const time = ref(0)
let timer = 0
const sel = ref<number[]>([])

function reset() {
  const [r, c] = sizes[props.difficulty] || sizes.medium
  cols.value = c
  const total = r * c
  const pairs = total / 2
  const sel2 = symbols.slice(0, pairs)
  const arr: Card[] = []
  sel2.forEach((s, i) => { arr.push({ v: s, flipped: false, found: false, pid: i }); arr.push({ v: s, flipped: false, found: false, pid: i }) })
  for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [arr[i], arr[j]] = [arr[j], arr[i]] }
  cards.value = arr
  matched.value = 0
  lock.value = false
  time.value = 0
  sel.value = []
  props.onScore(0)
  clearInterval(timer)
  timer = window.setInterval(() => { time.value++; updateScore() }, 1000)
}

function updateScore() {
  const base = matched.value * 50
  const bonus = Math.max(0, 200 - time.value * 2)
  props.onScore(base + bonus)
}

function flip(i: number) {
  const card = cards.value[i]
  if (card.found || card.flipped || lock.value) return
  card.flipped = true
  sel.value.push(i)
  store.beep('tap')
  if (sel.value.length === 2) {
    lock.value = true
    const [a, b] = sel.value
    if (cards.value[a].pid === cards.value[b].pid) {
      cards.value[a].found = true
      cards.value[b].found = true
      matched.value += 2
      sel.value = []
      lock.value = false
      store.beep('tick')
      updateScore()
      if (matched.value === cards.value.length) {
        clearInterval(timer)
        const final = cards.value.length * 50 + Math.max(0, 500 - time.value * 4)
        props.onScore(final)
        setTimeout(() => props.onGameOver(final), 400)
      }
    } else {
      setTimeout(() => {
        cards.value[a].flipped = false
        cards.value[b].flipped = false
        sel.value = []
        lock.value = false
      }, 700)
    }
  }
}

onMounted(reset)
onUnmounted(() => clearInterval(timer))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 8px; }
.info { display: flex; gap: 18px; color: #fff; font-weight: 600; }
.board { display: grid; gap: 8px; max-width: 460px; width: 100%; }
.card { aspect-ratio: 1; border-radius: 12px; border: none; background: linear-gradient(135deg, #6366f1, #a855f7); color: #fff; cursor: pointer; perspective: 600px; position: relative; font-size: 1.6rem; transition: transform 0.2s; }
.card:active { transform: scale(0.96); }
.card.found { background: linear-gradient(135deg, #10b981, #14b8a6); }
.front, .back { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; backface-visibility: hidden; transition: transform 0.4s; border-radius: 12px; }
.front { transform: rotateY(180deg); }
.card.flipped .back { transform: rotateY(180deg); }
.card.flipped .front { transform: rotateY(360deg); }
</style>
