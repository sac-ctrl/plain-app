<template>
  <div class="shell">
    <header class="bar">
      <button class="back" @click="emit('close')">
        <i-lucide:arrow-left />
      </button>
      <div class="meta">
        <div class="title">{{ title }}</div>
        <div class="sub">Best {{ store.bestOf(gameId) }} · Coins {{ store.coins }}</div>
      </div>
      <div class="bar-actions">
        <button class="icon-btn" :class="{ off: !store.sound }" @click="store.toggleSound()">
          <i-lucide:volume-2 v-if="store.sound" />
          <i-lucide:volume-x v-else />
        </button>
        <button class="icon-btn" :class="{ off: !store.haptics }" @click="store.toggleHaptics()">
          <i-lucide:vibrate />
        </button>
      </div>
    </header>

    <main class="stage">
      <transition name="fade" mode="out-in">
        <div v-if="phase === 'start'" key="start" class="screen start">
          <div class="hero">
            <div class="hero-icon">{{ icon }}</div>
            <div class="hero-title">{{ title }}</div>
            <div class="hero-desc">{{ desc }}</div>
            <div class="stats-row">
              <div class="stat"><span>Best</span><b>{{ stats.best }}</b></div>
              <div class="stat"><span>Plays</span><b>{{ stats.plays }}</b></div>
              <div class="stat"><span>Avg</span><b>{{ stats.plays ? Math.round(stats.totalScore / stats.plays) : 0 }}</b></div>
            </div>
            <div class="diff-row">
              <button
                v-for="d in difficulties"
                :key="d"
                class="chip"
                :class="{ active: difficulty === d }"
                @click="difficulty = d"
              >{{ d }}</button>
            </div>
            <button class="play" @click="start"><i-lucide:play /> Play</button>
          </div>
        </div>

        <div v-else-if="phase === 'play'" key="play" class="screen play">
          <div class="hud">
            <div class="hud-score">Score <b>{{ score }}</b></div>
            <div class="hud-best">Best <b>{{ store.bestOf(gameId) }}</b></div>
          </div>
          <div class="game-host">
            <slot
              :difficulty="difficulty"
              :on-score="onScore"
              :on-game-over="onGameOver"
              :running="phase === 'play'"
            />
          </div>
        </div>

        <div v-else key="result" class="screen result">
          <div class="r-card">
            <div class="r-icon">{{ newBest ? '🏆' : (lastScore > 0 ? '🎯' : '💥') }}</div>
            <div class="r-title">{{ newBest ? 'New high score!' : 'Run finished' }}</div>
            <div class="r-score">{{ lastScore }}</div>
            <div class="r-stats">
              <div><span>Best</span><b>{{ store.bestOf(gameId) }}</b></div>
              <div><span>Earned</span><b>+{{ Math.max(1, Math.floor(lastScore / 5)) }} coins</b></div>
            </div>
            <div class="r-actions">
              <button class="play big" @click="start"><i-lucide:rotate-ccw /> Replay</button>
              <button class="ghost" @click="phase = 'start'">Home</button>
            </div>
          </div>
        </div>
      </transition>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGamesStore } from './gamesStore'
import type { Difficulty } from './gamesStore'

const props = defineProps<{
  gameId: string
  title: string
  icon: string
  desc?: string
  difficulties?: Difficulty[]
}>()
const emit = defineEmits<{ close: [] }>()

const store = useGamesStore()
const phase = ref<'start' | 'play' | 'result'>('start')
const difficulty = ref<Difficulty>('medium')
const score = ref(0)
const lastScore = ref(0)
const newBest = ref(false)

const stats = computed(() => store.statsOf(props.gameId))
const difficulties = computed<Difficulty[]>(() => props.difficulties || ['easy', 'medium', 'hard', 'insane'])
const desc = computed(() => props.desc || 'Tap play to begin. Beat your best score!')

function start() {
  score.value = 0
  lastScore.value = 0
  newBest.value = false
  phase.value = 'play'
}

function onScore(v: number | ((s: number) => number)) {
  const next = typeof v === 'function' ? v(score.value) : v
  score.value = next
}

function onGameOver(finalScore?: number) {
  if (typeof finalScore === 'number') score.value = finalScore
  lastScore.value = score.value
  const r = store.finishRun(props.gameId, score.value)
  newBest.value = r.newBest
  store.beep(newBest.value ? 'win' : 'lose')
  store.vibrate(80)
  phase.value = 'result'
}
</script>

<style lang="scss" scoped>
.shell {
  position: fixed;
  inset: 0;
  z-index: 4000;
  background:
    radial-gradient(80% 60% at 30% 0%, rgba(99, 102, 241, 0.4), transparent 60%),
    radial-gradient(60% 60% at 90% 30%, rgba(168, 85, 247, 0.35), transparent 60%),
    radial-gradient(70% 60% at 50% 100%, rgba(236, 72, 153, 0.35), transparent 60%),
    #0b0e1f;
  color: #fff;
  display: flex;
  flex-direction: column;
}
.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  backdrop-filter: blur(10px);
  background: rgba(0, 0, 0, 0.25);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.back,
.icon-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;
  border-radius: 12px;
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}
.back:hover,
.icon-btn:hover {
  background: rgba(255, 255, 255, 0.16);
}
.icon-btn.off {
  opacity: 0.45;
}
.meta {
  flex: 1;
  min-width: 0;
}
.title {
  font-weight: 700;
  font-size: 1.05rem;
}
.sub {
  font-size: 0.78rem;
  opacity: 0.7;
  margin-top: 2px;
}
.bar-actions {
  display: flex;
  gap: 8px;
}
.stage {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: stretch;
  justify-content: center;
  overflow: hidden;
}
.screen {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.hero {
  width: min(420px, 100%);
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(14px);
  border-radius: 24px;
  padding: 28px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.hero-icon {
  font-size: 3rem;
  line-height: 1;
}
.hero-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 8px 0 4px;
}
.hero-desc {
  font-size: 0.9rem;
  opacity: 0.78;
  margin-bottom: 16px;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 14px 0 18px;
}
.stat {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 8px 6px;
}
.stat span {
  display: block;
  font-size: 0.72rem;
  opacity: 0.7;
}
.stat b {
  font-size: 1.1rem;
}
.diff-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-bottom: 16px;
}
.chip {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 0.82rem;
  text-transform: capitalize;
  cursor: pointer;
}
.chip.active {
  background: linear-gradient(135deg, #6366f1, #a855f7);
  border-color: transparent;
}
.play {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  border: none;
  color: #fff;
  border-radius: 999px;
  padding: 12px 28px;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(99, 102, 241, 0.45);
}
.play.big {
  padding: 14px 32px;
}
.ghost {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
  border-radius: 999px;
  padding: 12px 22px;
  cursor: pointer;
  font-size: 1rem;
}
.play:hover {
  filter: brightness(1.1);
}
.play:active {
  transform: translateY(1px);
}
.play.big:hover {
  filter: brightness(1.1);
}
.hud {
  position: absolute;
  top: 70px;
  left: 16px;
  right: 16px;
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  pointer-events: none;
  z-index: 10;
}
.hud b {
  font-size: 1.15rem;
  margin-left: 4px;
}
.play.screen,
.screen.play {
  position: relative;
}
.game-host {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.r-card {
  width: min(420px, 100%);
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(14px);
  border-radius: 24px;
  padding: 28px;
  text-align: center;
}
.r-icon {
  font-size: 3rem;
}
.r-title {
  font-size: 1.2rem;
  font-weight: 700;
  margin-top: 6px;
}
.r-score {
  font-size: 3rem;
  font-weight: 800;
  background: linear-gradient(135deg, #38bdf8, #a855f7);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 6px 0 12px;
}
.r-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
}
.r-stats > div {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 8px;
}
.r-stats span {
  display: block;
  font-size: 0.72rem;
  opacity: 0.7;
}
.r-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
