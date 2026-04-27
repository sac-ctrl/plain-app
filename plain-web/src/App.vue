<template>
  <div v-if="wsStatus" class="top-error">
    {{ $t('fix_disconnect_tips') }}
  </div>
  <router-view />
  <Teleport to="body">
    <MatrixRain v-if="theme.isMatrix" />
    <PanelThemeToggle />
    <modal-container />
    <div v-if="tapPhoneMessage" v-click-away="closeTapPhone" class="tap-phone-container" @click="closeTapPhone">
      <div>
        {{ tapPhoneMessage }}
      </div>
      <TouchPhone />
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppSocket } from '@/hooks/app-socket'
import { usePanelThemeStore } from '@/stores/theme'
import MatrixRain from '@/components/MatrixRain.vue'
import PanelThemeToggle from '@/components/PanelThemeToggle.vue'

const { wsStatus, tapPhoneMessage, closeTapPhone } = useAppSocket()
const theme = usePanelThemeStore()
onMounted(() => theme.init())
</script>

<style scoped>
.top-error {
  background-color: var(--md-sys-color-error);
  color: var(--md-sys-color-on-error);
  padding: 8px;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
