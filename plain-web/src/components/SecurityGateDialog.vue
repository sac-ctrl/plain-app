<template>
  <v-modal @close="cancel">
    <template #headline>
      <span class="sg-headline">
        <i-material-symbols:shield-lock-outline-rounded />
        {{ $t('security_gate_title') }}
      </span>
    </template>
    <template #content>
      <p class="sg-q">{{ disguise.currentQuestion }}</p>
      <p class="sg-hint">{{ $t('security_gate_hint') }}</p>
      <div class="form-row">
        <v-text-field
          ref="aRef"
          v-model="answer"
          type="password"
          :label="$t('security_answer')"
          :error="!!err"
          :error-text="err ? $t(err) : ''"
          @keyup.enter="doAction"
        />
      </div>
      <div class="sg-tries" :class="{ warn: tries >= 2 }">
        {{ $t('security_tries_left', { n: Math.max(0, 5 - tries) }) }}
      </div>
    </template>
    <template #actions>
      <v-outlined-button @click="cancel">{{ $t('cancel') }}</v-outlined-button>
      <v-filled-button :loading="loading" @click="doAction">
        {{ $t('security_unlock') }}
      </v-filled-button>
    </template>
  </v-modal>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, type PropType } from 'vue'
import { popModal } from './modal'
import { useDisguiseStore } from '@/stores/disguise'
import toast from './toaster'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  onUnlocked: { type: Function as PropType<() => void>, default: () => {} },
  onCancel: { type: Function as PropType<() => void>, default: () => {} },
})

const { t } = useI18n()
const disguise = useDisguiseStore()
const aRef = ref<HTMLInputElement>()
const answer = ref('')
const err = ref('')
const loading = ref(false)
const tries = ref(0)

onMounted(async () => {
  await nextTick()
  setTimeout(() => aRef.value?.focus?.(), 80)
})

function cancel() {
  props.onCancel?.()
  popModal()
}

async function doAction() {
  err.value = ''
  if (!answer.value.trim()) { err.value = 'valid.required'; return }
  loading.value = true
  try {
    const ok = await disguise.tryUnlock(answer.value)
    if (!ok) {
      tries.value += 1
      err.value = 'security_wrong_answer'
      if (tries.value >= 5) {
        toast(t('security_too_many_tries'), 'error')
        cancel()
      }
      return
    }
    props.onUnlocked?.()
    popModal()
  } finally { loading.value = false }
}
</script>

<style scoped>
.sg-headline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.sg-q {
  margin: 0 0 8px;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--md-sys-color-on-surface);
  line-height: 1.4;
}
.sg-hint {
  margin: 0 0 12px;
  font-size: 0.82rem;
  color: var(--md-sys-color-on-surface-variant);
  line-height: 1.4;
}
.sg-tries {
  margin-top: 10px;
  font-size: 0.78rem;
  color: var(--md-sys-color-on-surface-variant);
}
.sg-tries.warn {
  color: var(--md-sys-color-error);
  font-weight: 600;
}
</style>
