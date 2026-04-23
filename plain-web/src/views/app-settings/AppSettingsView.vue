<template>
  <div class="scroll-content">
    <div class="top-app-bar">
      <div class="title">{{ $t('app_settings') }}</div>
    </div>

    <div class="grids">
      <!-- Open app on device -->
      <section class="card">
        <h5 class="card-title">{{ $t('open_app_on_device') }}</h5>
        <div class="card-body">
          <p class="desc">{{ $t('open_app_on_device_desc') }}</p>
          <v-filled-button :loading="openLoading" @click.prevent="openOnDevice">
            {{ $t('open') }}
          </v-filled-button>
        </div>
      </section>

      <!-- Hide launcher icon -->
      <section class="card">
        <h5 class="card-title">{{ $t('hide_launcher_icon') }}</h5>
        <div class="card-body">
          <p class="desc">{{ $t('hide_launcher_icon_desc') }}</p>
          <label class="row">
            <v-checkbox touch-target="wrapper" :checked="launcherIconHidden" @change="toggleLauncherIcon" />
            <span>{{ $t('hide_launcher_icon') }}</span>
          </label>
        </div>
      </section>

      <!-- App lock -->
      <section class="card lock-card">
        <h5 class="card-title">{{ $t('app_lock') }}</h5>
        <div class="card-body">
          <p class="desc">{{ $t('app_lock_enable_desc') }}</p>

          <label class="row">
            <v-checkbox touch-target="wrapper" :checked="lockEnabled" @change="toggleLockEnabled" />
            <span>{{ $t('app_lock_enable') }}</span>
          </label>

          <label class="row">
            <v-checkbox touch-target="wrapper" :checked="biometricEnabled" @change="toggleBiometric" />
            <span>{{ $t('app_lock_biometric') }}</span>
          </label>
          <p class="desc small">{{ $t('app_lock_biometric_desc') }}</p>

          <h6 class="sub-title">
            {{ hasPin ? $t('app_lock_change_pin') : $t('app_lock_set_pin') }}
          </h6>

          <v-text-field
            v-if="hasPin"
            v-model="currentPin"
            type="password"
            inputmode="numeric"
            :label="$t('app_lock_current_pin')"
            class="pin-input"
          />
          <v-text-field
            v-model="newPin"
            type="password"
            inputmode="numeric"
            :label="$t('app_lock_new_pin')"
            class="pin-input"
            :error="pinError !== ''"
            :error-text="pinError"
          />
          <v-text-field
            v-model="confirmPin"
            type="password"
            inputmode="numeric"
            :label="$t('app_lock_confirm_pin')"
            class="pin-input"
          />

          <div class="actions">
            <v-filled-button :loading="pinLoading" @click.prevent="savePin">
              {{ $t('save') }}
            </v-filled-button>
            <v-filled-button v-if="hasPin" class="danger" :loading="pinLoading" @click.prevent="removePin">
              {{ $t('app_lock_remove_pin') }}
            </v-filled-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  initMutation,
  openAppOnDeviceGQL,
  setLauncherIconHiddenGQL,
  setAppLockEnabledGQL,
  setAppLockBiometricEnabledGQL,
  setAppPinGQL,
} from '@/lib/api/mutation'
import emitter from '@/plugins/eventbus'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const launcherIconHidden = ref(false)
const lockEnabled = ref(false)
const biometricEnabled = ref(false)
const hasPin = ref(false)

const currentPin = ref('')
const newPin = ref('')
const confirmPin = ref('')
const pinError = ref('')

const { mutate: openMutate, loading: openLoading } = initMutation({ document: openAppOnDeviceGQL })
const { mutate: launcherMutate } = initMutation({ document: setLauncherIconHiddenGQL })
const { mutate: lockEnabledMutate } = initMutation({ document: setAppLockEnabledGQL })
const { mutate: biometricMutate } = initMutation({ document: setAppLockBiometricEnabledGQL })
const { mutate: pinMutate, loading: pinLoading } = initMutation({ document: setAppPinGQL })

const APP_LOCK_QUERY = `
  query appLockSettings {
    appLockSettings {
      enabled
      biometricEnabled
      hasPin
      launcherIconHidden
    }
  }
`

async function refreshState() {
  try {
    const r = await gqlFetch<{ appLockSettings: any }>(APP_LOCK_QUERY)
    if (r.errors?.length) {
      emitter.emit('toast', r.errors[0].message)
      return
    }
    const s = r.data.appLockSettings
    lockEnabled.value = s.enabled
    biometricEnabled.value = s.biometricEnabled
    hasPin.value = s.hasPin
    launcherIconHidden.value = s.launcherIconHidden
  } catch (e: any) {
    emitter.emit('toast', e?.message ?? 'network_error')
  }
}

onMounted(refreshState)

function digitsOnly(v: string): string {
  return (v ?? '').replace(/\D+/g, '').slice(0, 12)
}

async function openOnDevice() {
  const r = await openMutate()
  if (r != null) emitter.emit('toast', t('saved'))
}

async function toggleLauncherIcon(e: Event) {
  const target = !launcherIconHidden.value
  const r = await launcherMutate({ hidden: target })
  if (r != null) launcherIconHidden.value = target
  else (e.target as HTMLInputElement).checked = launcherIconHidden.value
}

async function toggleLockEnabled(e: Event) {
  const target = !lockEnabled.value
  if (target && !hasPin.value) {
    emitter.emit('toast', t('app_lock_set_pin_first'))
    ;(e.target as HTMLInputElement).checked = false
    return
  }
  const r = await lockEnabledMutate({ enabled: target })
  if (r != null) lockEnabled.value = target
  else (e.target as HTMLInputElement).checked = lockEnabled.value
}

async function toggleBiometric(e: Event) {
  const target = !biometricEnabled.value
  const r = await biometricMutate({ enabled: target })
  if (r != null) biometricEnabled.value = target
  else (e.target as HTMLInputElement).checked = biometricEnabled.value
}

async function savePin() {
  pinError.value = ''
  newPin.value = digitsOnly(newPin.value)
  confirmPin.value = digitsOnly(confirmPin.value)
  currentPin.value = digitsOnly(currentPin.value)

  if (newPin.value.length < 4) {
    pinError.value = t('app_lock_pin_too_short')
    return
  }
  if (newPin.value !== confirmPin.value) {
    pinError.value = t('app_lock_pin_mismatch')
    return
  }
  const r = await pinMutate({ currentPin: currentPin.value, newPin: newPin.value })
  if (r != null) {
    hasPin.value = true
    currentPin.value = ''
    newPin.value = ''
    confirmPin.value = ''
    emitter.emit('toast', t('saved'))
  }
}

async function removePin() {
  const r = await pinMutate({ currentPin: digitsOnly(currentPin.value), newPin: '' })
  if (r != null) {
    hasPin.value = false
    lockEnabled.value = false
    biometricEnabled.value = false
    currentPin.value = ''
    newPin.value = ''
    confirmPin.value = ''
    emitter.emit('toast', t('saved'))
  }
}
</script>

<style lang="scss" scoped>
.scroll-content {
  padding: 0 0 16px 0;
}
.grids {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  padding: 16px;
}
.card {
  padding: 16px;
}
.card-title {
  font-size: 1rem;
  font-weight: 500;
  margin: 0 0 8px 0;
}
.sub-title {
  font-size: 0.95rem;
  font-weight: 500;
  margin: 16px 0 8px 0;
}
.desc {
  font-size: 0.85rem;
  color: var(--md-sys-color-on-surface-variant);
  margin: 0 0 12px 0;
  &.small { font-size: 0.8rem; margin-top: 4px; }
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  cursor: pointer;
}
.pin-input { display: block; margin: 8px 0; max-width: 320px; }
.actions { display: flex; gap: 12px; margin-top: 16px; flex-wrap: wrap; }
.lock-card { grid-column: span 2; }
@media (max-width: 768px) {
  .lock-card { grid-column: span 1; }
}
</style>
