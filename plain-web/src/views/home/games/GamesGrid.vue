<template>
  <div class="grid-wrap">
    <div class="hero-row">
      <div class="hero glass">
        <div>
          <div class="hero-eyebrow">Welcome back</div>
          <div class="hero-title">15 mini-games · {{ store.streak }}-day streak</div>
          <div class="hero-sub">Beat your best. Earn coins. Crush the daily challenges.</div>
        </div>
        <div class="hero-stats">
          <div class="coins-box"><i-lucide:coins /><span>{{ store.coins }}</span></div>
          <button class="theme-btn" @click="cycleTheme" title="Theme">
            <i-lucide:palette /> {{ themeLabel }}
          </button>
        </div>
      </div>

      <div v-if="lastGame" class="continue glass" @click="emit('play', lastGame.id)" :style="{ background: lastGame.gradient }">
        <div class="cont-eyebrow">Continue playing</div>
        <div class="cont-row">
          <div class="cont-icon">{{ lastGame.icon }}</div>
          <div>
            <div class="cont-name">{{ lastGame.name }}</div>
            <div class="cont-best">Best {{ store.bestOf(lastGame.id) }} · Plays {{ store.statsOf(lastGame.id).plays }}</div>
          </div>
          <div class="cont-play"><i-lucide:play /></div>
        </div>
      </div>
    </div>

    <div class="daily glass">
      <div class="daily-head">
        <div>
          <div class="daily-title"><i-lucide:flame /> Daily challenges</div>
          <div class="daily-sub">Resets every day. Earn bonus coins.</div>
        </div>
        <div class="streak-badge"><i-lucide:zap /> {{ store.streak }}d</div>
      </div>
      <div class="daily-list">
        <div v-for="c in dailies" :key="c.id" class="daily-item" :class="{ done: c.done }" @click="emit('play', c.gameId)">
          <div class="d-icon">{{ getGame(c.gameId)?.icon || '🎮' }}</div>
          <div class="d-meta">
            <div class="d-title">{{ c.title }}</div>
            <div class="d-sub" v-if="!c.done">+{{ c.reward }} coins</div>
            <div class="d-sub" v-else>Completed!</div>
          </div>
          <div class="d-state">
            <i-lucide:check v-if="c.done" />
            <i-lucide:chevron-right v-else />
          </div>
        </div>
      </div>
    </div>

    <div class="cards">
      <div
        v-for="g in games"
        :key="g.id"
        class="game-card"
        :style="{ background: g.gradient }"
        @click="emit('play', g.id)"
      >
        <div class="gc-top">
          <div class="gc-icon">{{ g.icon }}</div>
          <div v-if="g.badge" class="gc-badge">{{ g.badge }}</div>
        </div>
        <div class="gc-body">
          <div class="gc-name">{{ g.name }}</div>
          <div class="gc-desc">{{ g.desc }}</div>
        </div>
        <div class="gc-foot">
          <div class="gc-stat" title="Rating"><i-lucide:star /> {{ g.rating }}</div>
          <div class="gc-stat" title="Best"><i-lucide:trophy /> {{ store.bestOf(g.id) }}</div>
          <div class="gc-stat" title="Plays"><i-lucide:gamepad-2 /> {{ store.statsOf(g.id).plays }}</div>
          <div class="play-btn"><i-lucide:play /></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGamesStore, type ThemeName } from './gamesStore'
import { gameList, getGame } from './registry'

const emit = defineEmits<{ play: [string] }>()
const store = useGamesStore()
const games = gameList
const dailies = computed(() => store.todayChallenges)
const lastGame = computed(() => (store.lastPlayed ? getGame(store.lastPlayed) : null))
const themeLabel = computed(() => ({ neon: 'Neon', dark: 'Dark', glass: 'Glass', sunset: 'Sunset' } as Record<ThemeName, string>)[store.theme])
function cycleTheme() {
  const order: ThemeName[] = ['neon', 'dark', 'glass', 'sunset']
  const i = order.indexOf(store.theme)
  store.setTheme(order[(i + 1) % order.length])
}
</script>

<style lang="scss" scoped>
.grid-wrap { padding: 16px; overflow-y: auto; height: 100%; }
.hero-row { display: grid; grid-template-columns: 1fr; gap: 14px; margin-bottom: 14px; }
@media (min-width: 760px) { .hero-row { grid-template-columns: 1.4fr 1fr; } }
.glass {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.18), rgba(168, 85, 247, 0.18));
  border: 1px solid rgba(99, 102, 241, 0.18);
  border-radius: 20px;
  padding: 18px 20px;
  backdrop-filter: blur(8px);
}
.hero { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.hero-eyebrow { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--md-sys-color-on-surface-variant); margin-bottom: 4px; }
.hero-title { font-size: 1.35rem; font-weight: 800; color: var(--md-sys-color-on-surface); }
.hero-sub { margin-top: 4px; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.hero-stats { display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.coins-box { display: inline-flex; align-items: center; gap: 8px; background: rgba(250, 204, 21, 0.18); color: #b45309; font-weight: 700; border-radius: 999px; padding: 6px 12px; font-size: 0.95rem; }
.theme-btn { display: inline-flex; gap: 6px; align-items: center; background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.18); border-radius: 999px; padding: 6px 12px; cursor: pointer; font-size: 0.78rem; color: var(--md-sys-color-on-surface); }
.continue { color: #fff; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s; box-shadow: 0 8px 22px rgba(0, 0, 0, 0.25); }
.continue:hover { transform: translateY(-2px); box-shadow: 0 14px 30px rgba(0, 0, 0, 0.32); }
.cont-eyebrow { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.06em; opacity: 0.85; }
.cont-row { display: flex; align-items: center; gap: 12px; margin-top: 6px; }
.cont-icon { font-size: 2.2rem; filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.3)); }
.cont-name { font-weight: 800; font-size: 1.1rem; }
.cont-best { font-size: 0.8rem; opacity: 0.9; }
.cont-play { margin-left: auto; width: 40px; height: 40px; border-radius: 50%; background: rgba(255, 255, 255, 0.22); display: inline-flex; align-items: center; justify-content: center; }
.daily { margin-bottom: 16px; }
.daily-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.daily-title { font-weight: 800; display: inline-flex; gap: 6px; align-items: center; color: var(--md-sys-color-on-surface); }
.daily-sub { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.streak-badge { display: inline-flex; gap: 4px; align-items: center; background: linear-gradient(135deg, #f59e0b, #ef4444); color: #fff; padding: 5px 10px; border-radius: 999px; font-weight: 700; font-size: 0.78rem; }
.daily-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.daily-item { display: flex; align-items: center; gap: 10px; background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 14px; padding: 10px 12px; cursor: pointer; transition: background 0.15s; color: var(--md-sys-color-on-surface); }
.daily-item:hover { background: rgba(255, 255, 255, 0.16); }
.daily-item.done { opacity: 0.65; background: rgba(34, 197, 94, 0.15); border-color: rgba(34, 197, 94, 0.3); }
.d-icon { font-size: 1.5rem; }
.d-meta { flex: 1; min-width: 0; }
.d-title { font-weight: 600; font-size: 0.85rem; line-height: 1.2; }
.d-sub { font-size: 0.72rem; opacity: 0.7; margin-top: 2px; }
.d-state { opacity: 0.7; }
.cards { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); padding-bottom: 32px; }
.game-card { position: relative; border-radius: 18px; padding: 16px; color: #fff; cursor: pointer; min-height: 170px; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; transition: transform 0.18s ease, box-shadow 0.18s ease; box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22); }
.game-card::after { content: ''; position: absolute; inset: 0; background: radial-gradient(120% 60% at 100% 0%, rgba(255, 255, 255, 0.18), transparent 60%); pointer-events: none; }
.game-card:hover { transform: translateY(-3px) scale(1.01); box-shadow: 0 14px 28px rgba(0, 0, 0, 0.3); }
.gc-top { display: flex; justify-content: space-between; align-items: flex-start; }
.gc-icon { font-size: 2rem; filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.25)); }
.gc-badge { background: rgba(0, 0, 0, 0.22); border: 1px solid rgba(255, 255, 255, 0.25); border-radius: 999px; padding: 2px 10px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; }
.gc-name { font-weight: 800; font-size: 1.05rem; }
.gc-desc { font-size: 0.78rem; opacity: 0.92; margin-top: 2px; line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.gc-foot { display: flex; align-items: center; gap: 6px; margin-top: 10px; font-size: 0.74rem; flex-wrap: wrap; }
.gc-stat { display: inline-flex; align-items: center; gap: 4px; background: rgba(0, 0, 0, 0.18); border-radius: 999px; padding: 3px 7px; }
.play-btn { margin-left: auto; width: 32px; height: 32px; border-radius: 50%; background: rgba(255, 255, 255, 0.22); display: inline-flex; align-items: center; justify-content: center; }
</style>
