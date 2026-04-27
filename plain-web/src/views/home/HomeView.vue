<template>
  <div class="home-root">
    <div class="tabs-card">
      <div class="tabs-inner">
        <button
          class="tab"
          :class="{ active: disguise.activeTab === 'games' }"
          @click="goGames"
        >
          <i-lucide:gamepad-2 />
          <span>Games</span>
        </button>
        <button
          class="tab"
          :class="{ active: disguise.activeTab === 'feedback' }"
          @click="goFeedback"
        >
          <i-material-symbols:rate-review-outline-rounded />
          <span>Feedback</span>
        </button>
      </div>
    </div>

    <div class="tab-body">
      <transition name="swap" mode="out-in">
        <div v-if="disguise.activeTab === 'games'" key="games" class="games-tab">
          <GamesGrid @play="onPlay" />
        </div>
        <div v-else-if="disguise.activeTab === 'feedback' && disguise.unlocked" key="feedback" class="feedback-tab">
          <MainDashboard />
        </div>
        <div v-else key="locked" class="locked-tab">
          <div class="locked-card">
            <i-material-symbols:rate-review-outline-rounded class="lock-icon" />
            <div class="lock-title">Quick feedback</div>
            <div class="lock-sub">Help us improve. Answer one quick question to share your feedback.</div>
            <button class="open-btn" @click="openGate">Open feedback</button>
          </div>
        </div>
      </transition>
    </div>

    <GameRunner v-if="activeGame" :game-id="activeGame" @close="activeGame = null" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useDisguiseStore } from '@/stores/disguise'
import { pushModal } from '@/components/modal'
import SecurityGateDialog from '@/components/SecurityGateDialog.vue'
import GamesGrid from './games/GamesGrid.vue'
import MainDashboard from './MainDashboard.vue'
import GameRunner from './games/GameRunner.vue'

const disguise = useDisguiseStore()
const activeGame = ref<string | null>(null)

onMounted(() => {
  disguise.refreshFromServer()
})

function goGames() {
  disguise.setTab('games')
}

function goFeedback() {
  disguise.setTab('feedback')
  if (!disguise.unlocked) {
    openGate()
  }
}

function openGate() {
  pushModal(SecurityGateDialog, {
    onUnlocked: () => {
      disguise.setTab('feedback')
    },
    onCancel: () => {
      if (!disguise.unlocked) disguise.setTab('games')
    },
  })
}

function onPlay(id: string) {
  activeGame.value = id
}
</script>

<style lang="scss" scoped>
.home-root {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  height: 100%;
}

.tabs-card {
  display: flex;
  justify-content: center;
  padding: 14px 16px 4px;
}

.tabs-inner {
  display: inline-flex;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.18);
  border-radius: 16px;
  padding: 4px;
  box-shadow: 0 4px 18px rgba(99, 102, 241, 0.12);
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  border: none;
  background: transparent;
  color: var(--md-sys-color-on-surface-variant);
  font-weight: 600;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
  font-size: 0.95rem;
}
.tab svg {
  width: 18px;
  height: 18px;
}
.tab.active {
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.32);
}

.tab-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.games-tab,
.feedback-tab,
.locked-tab {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.locked-tab {
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
}

.locked-card {
  width: min(380px, 100%);
  text-align: center;
  background: var(--md-sys-color-surface-container);
  border-radius: 24px;
  padding: 32px 22px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}

.lock-icon {
  width: 48px;
  height: 48px;
  color: var(--md-sys-color-primary);
  margin-bottom: 8px;
}

.lock-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--md-sys-color-on-surface);
}

.lock-sub {
  margin: 6px 0 18px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.92rem;
  line-height: 1.4;
}

.open-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff;
  border: none;
  padding: 12px 24px;
  border-radius: 999px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.3);
}

.swap-enter-active,
.swap-leave-active {
  transition: opacity 0.18s, transform 0.18s;
}
.swap-enter-from,
.swap-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
