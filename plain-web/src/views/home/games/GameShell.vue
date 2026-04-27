<template>
  <div class="shell" :data-theme="store.theme">
    <header class="bar">
      <button class="back" @click="emit('close')" title="Close">
        <i-lucide:arrow-left />
      </button>
      <div class="meta">
        <div class="title">{{ title }}</div>
        <div class="sub">Best {{ store.bestOf(gameId) }} · Coins {{ store.coins }} · Streak {{ store.streak }}d</div>
      </div>
      <div class="bar-actions">
        <button v-if="phase === 'play'" class="icon-btn pause" @click="togglePause" title="Pause">
          <i-lucide:pause v-if="!paused" />
          <i-lucide:play v-else />
        </button>
        <button class="icon-btn" :class="{ off: !store.sound }" @click="store.toggleSound()" title="Sound">
          <i-lucide:volume-2 v-if="store.sound" />
          <i-lucide:volume-x v-else />
        </button>
        <button class="icon-btn" :class="{ off: !store.haptics }" @click="store.toggleHaptics()" title="Haptics">
          <i-lucide:vibrate />
        </button>
        <div class="theme-pick">
          <button class="icon-btn" @click="cycleTheme" title="Theme">
            <i-lucide:palette />
          </button>
        </div>
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
              <div class="stat"><span>Avg</span><b>{{ stats.avg }}</b></div>
              <div class="stat"><span>Time</span><b>{{ formatTime(stats.totalTime) }}</b></div>
            </div>
            <div v-if="modes.length > 1" class="row-label">Mode</div>
            <div v-if="modes.length > 1" class="diff-row">
              <button v-for="m in modes" :key="m" class="chip" :class="{ active: mode === m }" @click="mode = m">
                {{ modeLabel(m) }}
              </button>
            </div>
            <div class="row-label">Difficulty</div>
            <div class="diff-row">
              <button v-for="d in difficulties" :key="d" class="chip" :class="{ active: difficulty === d }" @click="difficulty = d">
                {{ d }}
              </button>
            </div>
            <button class="play" @click="start"><i-lucide:play /> Play</button>
            <button class="lb-link" @click="showLb = !showLb">
              <i-lucide:trophy /> {{ showLb ? 'Hide' : 'View' }} leaderboard
            </button>
            <transition name="fade">
              <div v-if="showLb" class="lb">
                <div class="lb-row head"><span>#</span><span>Player</span><span>Mode</span><span>Score</span></div>
                <div v-for="(e, i) in mergedLb" :key="i" class="lb-row" :class="{ me: e.me }">
                  <span>{{ i + 1 }}</span><span>{{ e.name }}</span><span>{{ modeLabel(e.mode) }}</span><span>{{ e.score }}</span>
                </div>
              </div>
            </transition>
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
              :mode="mode"
              :on-score="onScore"
              :on-game-over="onGameOver"
              :running="phase === 'play' && !paused"
              :paused="paused"
            />
          </div>
          <transition name="fade">
            <div v-if="paused" class="pause-overlay" @click.self="togglePause">
              <div class="pause-card">
                <div class="p-title">Paused</div>
                <div class="p-actions">
                  <button class="play" @click="togglePause"><i-lucide:play /> Resume</button>
                  <button class="ghost" @click="quit"><i-lucide:home /> Home</button>
                </div>
              </div>
            </div>
          </transition>
        </div>

        <div v-else key="result" class="screen result">
          <div class="r-card">
            <div class="r-icon">{{ newBest ? '🏆' : (lastScore > 0 ? '🎯' : '💥') }}</div>
            <div class="r-title">{{ newBest ? 'New high score!' : 'Run finished' }}</div>
            <div class="r-score">{{ lastScore }}</div>
            <div class="r-stats">
              <div><span>Best</span><b>{{ store.bestOf(gameId) }}</b></div>
              <div><span>Mode</span><b>{{ modeLabel(mode) }}</b></div>
              <div><span>Earned</span><b>+{{ Math.max(1, Math.floor(lastScore / 5)) }}🪙</b></div>
              <div v-if="lastRank > 0"><span>Rank</span><b>#{{ lastRank }}</b></div>
            </div>
            <div v-if="lastReward > 0" class="r-bonus">+{{ lastReward }} coins from daily challenge!</div>
            <div class="r-actions">
              <button class="play big" @click="start"><i-lucide:rotate-ccw /> Replay</button>
              <button class="ghost" @click="phase = 'start'"><i-lucide:home /> Home</button>
              <button class="ghost" @click="share"><i-lucide:share-2 /> Share</button>
            </div>
            <div class="lb-mini">
              <div class="lb-mini-title">Top scores</div>
              <div v-for="(e, i) in topScores.slice(0, 5)" :key="i" class="lb-mini-row">
                <span>#{{ i + 1 }}</span><span>{{ e.score }}</span><span>{{ modeLabel(e.mode) }}</span>
              </div>
              <div v-if="!topScores.length" class="lb-empty">Be the first to set a score!</div>
            </div>
          </div>
        </div>
      </transition>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGamesStore, fakeLeaderboardNames, type Difficulty, type GameMode, type ThemeName } from './gamesStore'
import toast from '@/components/toaster'

const props = defineProps<{
  gameId: string
  title: string
  icon: string
  desc?: string
  difficulties?: Difficulty[]
  modes?: GameMode[]
}>()
const emit = defineEmits<{ close: [] }>()

const store = useGamesStore()
const phase = ref<'start' | 'play' | 'result'>('start')
const difficulty = ref<Difficulty>('medium')
const mode = ref<GameMode>('classic')
const score = ref(0)
const lastScore = ref(0)
const lastRank = ref(0)
const lastReward = ref(0)
const newBest = ref(false)
const paused = ref(false)
const showLb = ref(false)
const startTs = ref(0)

const stats = computed(() => store.statsOf(props.gameId))
const difficulties = computed<Difficulty[]>(() => props.difficulties || ['easy', 'medium', 'hard', 'insane'])
const modes = computed<GameMode[]>(() => props.modes || ['classic'])
const desc = computed(() => props.desc || 'Tap play to begin. Beat your best score!')
const topScores = computed(() => store.leaderboardOf(props.gameId))

const mergedLb = computed(() => {
  const real = store.leaderboardOf(props.gameId).map((e) => ({ name: 'You', score: e.score, mode: e.mode, me: true }))
  const fake = fakeLeaderboardNames.slice(0, 7).map((n, i) => ({
    name: n,
    score: Math.max(20, Math.round((stats.value.best || 50) * (1.4 - i * 0.12) + Math.random() * 20)),
    mode: 'classic' as GameMode,
    me: false,
  }))
  return [...real, ...fake].sort((a, b) => b.score - a.score).slice(0, 10)
})

function modeLabel(m: GameMode): string {
  return ({ classic: 'Classic', time: 'Time Attack', survival: 'Survival', challenge: 'Challenge' } as Record<GameMode, string>)[m]
}

function formatTime(sec: number): string {
  if (sec < 60) return sec + 's'
  const m = Math.floor(sec / 60)
  if (m < 60) return m + 'm'
  return Math.floor(m / 60) + 'h ' + (m % 60) + 'm'
}

function start() {
  score.value = 0
  lastScore.value = 0
  newBest.value = false
  lastReward.value = 0
  paused.value = false
  startTs.value = performance.now()
  phase.value = 'play'
}

function onScore(v: number | ((s: number) => number)) {
  score.value = typeof v === 'function' ? v(score.value) : v
}

function onGameOver(finalScore?: number) {
  if (typeof finalScore === 'number') score.value = finalScore
  lastScore.value = score.value
  const elapsedMs = performance.now() - startTs.value
  const r = store.finishRun(props.gameId, score.value, mode.value, difficulty.value, elapsedMs)
  newBest.value = r.newBest
  lastRank.value = r.rank
  lastReward.value = r.rewardEarned
  store.beep(newBest.value ? 'win' : 'lose')
  store.vibrate(newBest.value ? [40, 50, 80] : 80)
  phase.value = 'result'
}

function togglePause() {
  paused.value = !paused.value
  store.beep('tap')
}

function quit() {
  paused.value = false
  phase.value = 'start'
}

async function share() {
  const text = `I scored ${lastScore.value} in ${props.title} (${modeLabel(mode.value)}, ${difficulty.value}) on PlainApp Mini Games!`
  try {
    type NavWithShare = Navigator & { share?: (data: { text?: string }) => Promise<void> }
    const n = navigator as NavWithShare
    if (n.share) {
      await n.share({ text })
      return
    }
    await navigator.clipboard.writeText(text)
    toast('Score copied to clipboard.', 'success')
  } catch {
    toast('Share not supported.', 'error')
  }
}

function cycleTheme() {
  const order: ThemeName[] = ['neon', 'dark', 'glass', 'sunset']
  const i = order.indexOf(store.theme)
  store.setTheme(order[(i + 1) % order.length])
  store.beep('tap')
}
</script>

<style lang="scss" scoped>
.shell {
  position: fixed;
  inset: 0;
  z-index: 4000;
  color: #fff;
  display: flex;
  flex-direction: column;
}
.shell[data-theme='neon'] {
  background:
    radial-gradient(80% 60% at 30% 0%, rgba(99, 102, 241, 0.4), transparent 60%),
    radial-gradient(60% 60% at 90% 30%, rgba(168, 85, 247, 0.35), transparent 60%),
    radial-gradient(70% 60% at 50% 100%, rgba(236, 72, 153, 0.35), transparent 60%),
    #0b0e1f;
}
.shell[data-theme='dark'] {
  background: linear-gradient(160deg, #0a0a0a, #1a1a1a 40%, #050505);
}
.shell[data-theme='glass'] {
  background: linear-gradient(160deg, #0f172a, #1e293b 50%, #0f172a);
}
.shell[data-theme='sunset'] {
  background:
    radial-gradient(60% 60% at 20% 100%, rgba(236, 72, 153, 0.5), transparent 70%),
    radial-gradient(60% 60% at 80% 20%, rgba(251, 146, 60, 0.5), transparent 70%),
    #1c1129;
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
.icon-btn:hover { background: rgba(255, 255, 255, 0.16); }
.icon-btn.off { opacity: 0.45; }
.icon-btn.pause { background: linear-gradient(135deg, #f59e0b, #ef4444); border-color: transparent; }
.meta { flex: 1; min-width: 0; }
.title { font-weight: 700; font-size: 1.05rem; }
.sub { font-size: 0.78rem; opacity: 0.7; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bar-actions { display: flex; gap: 8px; }
.theme-pick { display: flex; gap: 4px; }
.stage { flex: 1; min-height: 0; display: flex; align-items: stretch; justify-content: center; overflow: hidden; }
.screen { flex: 1; display: flex; align-items: center; justify-content: center; padding: 16px; overflow-y: auto; }
.hero {
  width: min(440px, 100%);
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(14px);
  border-radius: 24px;
  padding: 24px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.hero-icon { font-size: 3rem; line-height: 1; }
.hero-title { font-size: 1.5rem; font-weight: 700; margin: 8px 0 4px; }
.hero-desc { font-size: 0.9rem; opacity: 0.78; margin-bottom: 14px; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 12px 0 14px; }
.stat { background: rgba(255, 255, 255, 0.06); border-radius: 12px; padding: 8px 4px; }
.stat span { display: block; font-size: 0.68rem; opacity: 0.7; }
.stat b { font-size: 0.95rem; }
.row-label { font-size: 0.72rem; opacity: 0.6; text-transform: uppercase; letter-spacing: 0.05em; margin: 8px 0 6px; text-align: left; }
.diff-row { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-bottom: 12px; }
.chip { background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.1); color: #fff; border-radius: 999px; padding: 6px 14px; font-size: 0.82rem; text-transform: capitalize; cursor: pointer; }
.chip.active { background: linear-gradient(135deg, #6366f1, #a855f7); border-color: transparent; }
.play { display: inline-flex; align-items: center; gap: 8px; background: linear-gradient(135deg, #6366f1, #a855f7); border: none; color: #fff; border-radius: 999px; padding: 12px 28px; font-size: 1rem; font-weight: 700; cursor: pointer; box-shadow: 0 8px 22px rgba(99, 102, 241, 0.45); margin-top: 6px; }
.play.big { padding: 14px 32px; }
.play:hover { filter: brightness(1.1); }
.play:active { transform: translateY(1px); }
.ghost { background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.18); color: #fff; border-radius: 999px; padding: 12px 18px; cursor: pointer; font-size: 0.95rem; display: inline-flex; align-items: center; gap: 6px; }
.lb-link { background: transparent; border: none; color: rgba(255, 255, 255, 0.7); margin-top: 12px; cursor: pointer; display: inline-flex; gap: 6px; align-items: center; font-size: 0.85rem; }
.lb { background: rgba(0, 0, 0, 0.25); border-radius: 12px; padding: 10px; margin-top: 10px; text-align: left; }
.lb-row { display: grid; grid-template-columns: 28px 1fr 80px 60px; gap: 6px; padding: 5px 4px; font-size: 0.82rem; opacity: 0.92; border-bottom: 1px solid rgba(255, 255, 255, 0.04); }
.lb-row.head { font-weight: 600; opacity: 0.55; font-size: 0.72rem; text-transform: uppercase; }
.lb-row.me { color: #facc15; font-weight: 700; }
.hud { position: absolute; top: 70px; left: 16px; right: 16px; display: flex; justify-content: space-between; font-size: 0.85rem; pointer-events: none; z-index: 10; }
.hud b { font-size: 1.15rem; margin-left: 4px; }
.game-host { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.pause-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}
.pause-card { background: rgba(255, 255, 255, 0.1); border: 1px solid rgba(255, 255, 255, 0.16); border-radius: 22px; padding: 26px 30px; text-align: center; }
.p-title { font-size: 1.4rem; font-weight: 800; margin-bottom: 14px; }
.p-actions { display: flex; gap: 10px; }
.r-card { width: min(440px, 100%); background: rgba(255, 255, 255, 0.07); border: 1px solid rgba(255, 255, 255, 0.12); backdrop-filter: blur(14px); border-radius: 24px; padding: 26px; text-align: center; }
.r-icon { font-size: 3rem; }
.r-title { font-size: 1.2rem; font-weight: 700; margin-top: 6px; animation: pop 0.5s ease; }
@keyframes pop { 0% { transform: scale(0.5); opacity: 0; } 60% { transform: scale(1.15); } 100% { transform: scale(1); opacity: 1; } }
.r-score { font-size: 3rem; font-weight: 800; background: linear-gradient(135deg, #38bdf8, #a855f7); -webkit-background-clip: text; background-clip: text; color: transparent; margin: 6px 0 12px; }
.r-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
.r-stats > div { background: rgba(255, 255, 255, 0.06); border-radius: 10px; padding: 8px 4px; }
.r-stats span { display: block; font-size: 0.68rem; opacity: 0.7; }
.r-stats b { font-size: 0.92rem; }
.r-bonus { background: linear-gradient(135deg, rgba(250,204,21,0.18), rgba(251,146,60,0.18)); border: 1px solid rgba(250,204,21,0.3); color: #fde68a; border-radius: 12px; padding: 8px; font-size: 0.85rem; margin-bottom: 12px; }
.r-actions { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.lb-mini { margin-top: 14px; background: rgba(0, 0, 0, 0.2); border-radius: 12px; padding: 10px; text-align: left; }
.lb-mini-title { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.6; margin-bottom: 4px; }
.lb-mini-row { display: grid; grid-template-columns: 32px 1fr 100px; gap: 6px; padding: 3px 2px; font-size: 0.85rem; }
.lb-empty { opacity: 0.55; font-size: 0.82rem; padding: 4px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.18s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
