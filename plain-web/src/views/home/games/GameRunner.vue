<template>
  <GameShell
    v-if="def"
    :game-id="def.id"
    :title="def.name"
    :icon="def.icon"
    :desc="def.desc"
    @close="emit('close')"
  >
    <template #default="ctx">
      <component
        :is="def.loader()"
        :difficulty="ctx.difficulty"
        :running="ctx.running"
        :on-score="ctx.onScore"
        :on-game-over="ctx.onGameOver"
      />
    </template>
  </GameShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import GameShell from './GameShell.vue'
import { getGame } from './registry'

const props = defineProps<{ gameId: string }>()
const emit = defineEmits<{ close: [] }>()
const def = computed(() => getGame(props.gameId))
</script>
