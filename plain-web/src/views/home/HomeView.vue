<template>
  <div class="home-shell">
    <div class="tab-card-wrap">
      <div class="tab-card" role="tablist">
        <button
          type="button"
          class="tab"
          role="tab"
          :class="{ active: activeTab === 'games' }"
          :aria-selected="activeTab === 'games'"
          @click="goGames"
        >
          <i-material-symbols:sports-esports-outline-rounded />
          <span>{{ $t('tab_games') }}</span>
        </button>
        <button
          type="button"
          class="tab"
          role="tab"
          :class="{ active: activeTab === 'security' }"
          :aria-selected="activeTab === 'security'"
          @click="goSecurity"
        >
          <i-material-symbols:shield-lock-outline-rounded />
          <span>{{ $t('tab_security') }}</span>
          <i-material-symbols:lock v-if="!disguise.unlocked" class="lock-icon" />
        </button>
        <span class="indicator" :class="{ right: activeTab === 'security' }"></span>
      </div>
    </div>

    <div class="tab-body">
      <transition name="fade-tab" mode="out-in">
        <GamesArcade v-if="activeTab === 'games'" key="g" />
        <MainDashboard v-else-if="disguise.unlocked" key="s" />
        <div v-else class="locked-screen" key="l">
          <div class="locked-card">
            <div class="lock-emblem">
              <i-material-symbols:shield-lock-outline-rounded />
            </div>
            <h2>{{ $t('security_locked_title') }}</h2>
            <p>{{ $t('security_locked_desc') }}</p>
            <v-filled-button @click="askGate">{{ $t('security_unlock') }}</v-filled-button>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useDisguiseStore } from '@/stores/disguise'
import { openModal } from '@/components/modal'
import SecurityGateDialog from '@/components/SecurityGateDialog.vue'
import GamesArcade from '@/views/games/GamesArcade.vue'
import MainDashboard from './MainDashboard.vue'

const disguise = useDisguiseStore()
const { activeTab } = storeToRefs(disguise)

onMounted(() => {
  disguise.ensureFirstTimeAnswerStored()
})

function goGames() { disguise.setTab('games') }

function goSecurity() {
  if (disguise.unlocked) {
    disguise.setTab('security')
    return
  }
  askGate()
}

function askGate() {
  openModal(SecurityGateDialog, {
    onUnlocked: () => disguise.setTab('security'),
  })
}
</script>

<style lang="scss" scoped>
.home-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.tab-card-wrap {
  display: flex;
  justify-content: center;
  padding: 18px 16px 8px;
}

.tab-card {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 320px;
  height: 320px;
  max-width: 90vw;
  max-height: 60vw;
  background: var(--md-sys-color-surface-container);
  border-radius: 28px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 8px 28px rgba(0, 0, 0, 0.10);
  overflow: hidden;
  padding: 8px;
  gap: 8px;
}

.tab {
  position: relative;
  z-index: 1;
  border: none;
  background: transparent;
  border-radius: 22px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  color: var(--md-sys-color-on-surface-variant);
  font-family: inherit;
  font-weight: 600;
  font-size: 0.95rem;
  letter-spacing: 0.2px;
  transition: color 0.2s ease, transform 0.15s ease;

  svg {
    width: 44px;
    height: 44px;
  }

  .lock-icon {
    position: absolute;
    top: 14px;
    right: 14px;
    width: 18px;
    height: 18px;
    opacity: 0.7;
  }

  &:hover {
    color: var(--md-sys-color-on-surface);
    transform: translateY(-1px);
  }

  &.active {
    color: var(--md-sys-color-on-primary-container);
  }
}

.indicator {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 8px;
  width: calc(50% - 12px);
  background: linear-gradient(135deg,
    var(--md-sys-color-primary-container),
    color-mix(in srgb, var(--md-sys-color-primary-container) 70%, var(--md-sys-color-secondary-container)));
  border-radius: 22px;
  transition: transform 0.32s cubic-bezier(0.2, 0.8, 0.2, 1);
  z-index: 0;

  &.right {
    transform: translateX(calc(100% + 8px));
  }
}

.tab-body {
  flex: 1;
  min-height: 0;
  display: flex;
  position: relative;
}

.tab-body > * {
  width: 100%;
}

.fade-tab-enter-active,
.fade-tab-leave-active {
  transition: opacity 0.18s ease, transform 0.22s ease;
}
.fade-tab-enter-from,
.fade-tab-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.locked-screen {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 24px;
}

.locked-card {
  text-align: center;
  background: var(--md-sys-color-surface-container);
  padding: 36px 28px;
  border-radius: 24px;
  max-width: 420px;
  width: 100%;
  box-shadow: 0 4px 22px rgba(0, 0, 0, 0.08);

  h2 {
    margin: 0 0 8px;
    font-size: 1.25rem;
    color: var(--md-sys-color-on-surface);
  }
  p {
    margin: 0 0 20px;
    color: var(--md-sys-color-on-surface-variant);
    line-height: 1.5;
  }
}

.lock-emblem {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--md-sys-color-primary-container);
  color: var(--md-sys-color-on-primary-container);
  margin-bottom: 16px;

  svg {
    width: 36px;
    height: 36px;
  }
}

@media (max-width: 600px) {
  .tab-card {
    width: 280px;
    height: 280px;
  }
}
</style>
