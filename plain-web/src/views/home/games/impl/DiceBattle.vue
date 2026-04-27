<template>
  <div class="wrap">
    <div class="row">
      <div class="side">
        <div class="who">You</div>
        <div class="dice">{{ youDice || '·' }}</div>
        <div class="pts">{{ you }}</div>
        <div class="turn-pts">Turn: {{ turnPts }}</div>
      </div>
      <div class="vs">VS</div>
      <div class="side">
        <div class="who">AI</div>
        <div class="dice">{{ aiDice || '·' }}</div>
        <div class="pts">{{ ai }}</div>
        <div class="turn-pts">&nbsp;</div>
      </div>
    </div>
    <div class="actions">
      <button :disabled="busy || turn !== 'you'" @click="roll" class="primary">Roll</button>
      <button :disabled="busy || turn !== 'you' || !turnPts" @click="hold" class="ghost">Hold ({{ turnPts }})</button>
    </div>
    <div class="log">{{ log }}</div>
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
const target = 50
const you = ref(0); const ai = ref(0)
const youDice = ref(0); const aiDice = ref(0)
const turnPts = ref(0)
const turn = ref<'you' | 'ai'>('you')
const busy = ref(false)
const log = ref('Roll the dice. Hold to bank. First to 50 wins.')
const aiHoldAt: Record<string, number> = { easy: 12, medium: 18, hard: 22, insane: 26 }
let aiTimer = 0

function reset() {
  you.value = 0; ai.value = 0
  youDice.value = 0; aiDice.value = 0
  turnPts.value = 0
  turn.value = 'you'
  busy.value = false
  log.value = 'Roll the dice. Hold to bank.'
  props.onScore(0)
}

function roll() {
  if (busy.value) return
  busy.value = true
  store.beep('tap')
  let i = 0
  const animate = () => {
    youDice.value = 1 + Math.floor(Math.random() * 6)
    if (++i < 6) setTimeout(animate, 50)
    else done()
  }
  animate()
  function done() {
    if (youDice.value === 1) {
      log.value = 'Rolled 1. Lost turn points.'
      turnPts.value = 0
      busy.value = false
      switchTurn()
    } else {
      turnPts.value += youDice.value
      log.value = `Rolled ${youDice.value}.`
      busy.value = false
    }
  }
}

function hold() {
  you.value += turnPts.value
  props.onScore(you.value)
  log.value = `Banked ${turnPts.value}. Total ${you.value}.`
  turnPts.value = 0
  if (you.value >= target) {
    log.value = `You win!`
    props.onScore(you.value + 50)
    setTimeout(() => props.onGameOver(you.value + 50), 500)
    return
  }
  switchTurn()
}

function switchTurn() {
  turn.value = turn.value === 'you' ? 'ai' : 'you'
  if (turn.value === 'ai') aiTurn()
}

function aiTurn() {
  const limit = aiHoldAt[props.difficulty] || 18
  let aiTurnPts = 0
  busy.value = true
  function step() {
    aiDice.value = 1 + Math.floor(Math.random() * 6)
    store.beep('tick')
    if (aiDice.value === 1) {
      log.value = 'AI rolled 1. Lost turn points.'
      busy.value = false
      switchTurn()
      return
    }
    aiTurnPts += aiDice.value
    if (aiTurnPts >= limit || aiTurnPts + ai.value >= target) {
      ai.value += aiTurnPts
      log.value = `AI banked ${aiTurnPts}. Total ${ai.value}.`
      if (ai.value >= target) {
        log.value = 'AI wins!'
        setTimeout(() => props.onGameOver(you.value), 500)
        return
      }
      busy.value = false
      switchTurn()
      return
    }
    aiTimer = window.setTimeout(step, 600)
  }
  aiTimer = window.setTimeout(step, 600)
}

onMounted(reset)
onUnmounted(() => clearTimeout(aiTimer))
watch(() => props.running, (v) => v && reset())
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; align-items: center; gap: 18px; width: min(420px, 100%); padding: 8px; }
.row { display: flex; align-items: center; justify-content: space-around; width: 100%; gap: 12px; }
.side { background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12); border-radius: 18px; padding: 18px; text-align: center; flex: 1; }
.who { font-size: 0.85rem; opacity: 0.7; color: #fff; }
.dice { font-size: 3.4rem; font-weight: 800; color: #fff; min-height: 3.6rem; }
.pts { font-size: 1.6rem; font-weight: 800; color: #facc15; }
.turn-pts { font-size: 0.78rem; color: rgba(255,255,255,0.7); margin-top: 4px; }
.vs { font-weight: 800; color: #fff; opacity: 0.5; }
.actions { display: flex; gap: 10px; }
.primary { background: linear-gradient(135deg, #6366f1, #a855f7); color: #fff; border: none; padding: 10px 22px; border-radius: 999px; font-weight: 700; cursor: pointer; }
.ghost { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.18); color: #fff; padding: 10px 22px; border-radius: 999px; cursor: pointer; }
.primary:disabled, .ghost:disabled { opacity: 0.4; cursor: not-allowed; }
.log { color: rgba(255,255,255,0.75); font-size: 0.9rem; min-height: 1.4em; text-align: center; }
</style>
