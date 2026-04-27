<template>
  <v-modal @close="cancel">
    <template #headline>
      <span class="vault-headline">
        <i-material-symbols:lock-outline />
        {{ mode === 'setup' ? $t('vault_setup_title') : $t('vault_unlock_title') }}
      </span>
    </template>
    <template #content>
      <p class="vault-desc">
        {{ mode === 'setup' ? $t('vault_setup_desc') : $t('vault_unlock_desc') }}
      </p>
      <div class="form-row">
        <v-text-field
          ref="passRef"
          v-model="pass1"
          type="password"
          :label="$t('vault_passphrase')"
          :error="!!err"
          :error-text="err ? $t(err) : ''"
          @keyup.enter="onEnter"
        />
      </div>
      <div v-if="mode === 'setup'" class="form-row">
        <v-text-field
          v-model="pass2"
          type="password"
          :label="$t('vault_passphrase_confirm')"
          @keyup.enter="onEnter"
        />
      </div>
      <div v-if="mode === 'setup'" class="vault-warn">
        <i-material-symbols:warning-outline-rounded />
        <span>{{ $t('vault_setup_warning') }}</span>
      </div>
    </template>
    <template #actions>
      <v-outlined-button @click="cancel">{{ $t('cancel') }}</v-outlined-button>
      <v-filled-button :loading="loading" @click="doAction">
        {{ mode === 'setup' ? $t('vault_create') : $t('vault_unlock') }}
      </v-filled-button>
    </template>
  </v-modal>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, type PropType } from 'vue'
import { popModal } from './modal'
import { useVaultStore } from '@/stores/vault'

const props = defineProps({
  mode: { type: String as PropType<'setup' | 'unlock'>, default: 'unlock' },
  blob: { type: String, default: '' },
  onUnlocked: { type: Function as PropType<() => void>, default: () => {} },
  onCancel: { type: Function as PropType<() => void>, default: () => {} },
})

const vault = useVaultStore()
const passRef = ref<HTMLInputElement>()
const pass1 = ref('')
const pass2 = ref('')
const err = ref('')
const loading = ref(false)

onMounted(async () => {
  await nextTick()
  setTimeout(() => passRef.value?.focus?.(), 80)
})

function cancel() {
  props.onCancel?.()
  popModal()
}

function onEnter() { doAction() }

async function doAction() {
  err.value = ''
  if (!pass1.value) { err.value = 'valid.required'; return }
  if (props.mode === 'setup') {
    if (pass1.value.length < 6) { err.value = 'vault_passphrase_too_short'; return }
    if (pass1.value !== pass2.value) { err.value = 'vault_passphrase_mismatch'; return }
    loading.value = true
    try {
      await vault.setupNew(pass1.value)
      props.onUnlocked?.()
      popModal()
    } finally { loading.value = false }
  } else {
    loading.value = true
    try {
      const ok = await vault.unlock(pass1.value, props.blob || null)
      if (!ok) { err.value = 'vault_passphrase_wrong'; return }
      props.onUnlocked?.()
      popModal()
    } finally { loading.value = false }
  }
}
</script>

<style scoped>
.vault-headline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.vault-desc {
  margin: 0 0 12px;
  font-size: 0.9rem;
  color: var(--md-sys-color-on-surface-variant);
  line-height: 1.5;
}
.vault-warn {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
  font-size: 0.82rem;
  line-height: 1.45;
}
</style>
