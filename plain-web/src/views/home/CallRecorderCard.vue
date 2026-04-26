<template>
  <div class="card recorder-card" :class="{ 'is-recording': state.recording }">
    <div class="header">
      <div class="title-row">
        <i-lucide:phone-call class="title-icon" />
        <h5 class="card-title">{{ $t('call_recorder') }}</h5>
        <span v-if="state.recording" class="rec-pill">
          <span class="rec-dot" /> {{ $t('recording_now_label') }}
          <span class="elapsed">{{ elapsed }}</span>
        </span>
      </div>
      <label class="switch">
        <input type="checkbox" :checked="state.enabled" @change="onToggle" />
        <span class="track" />
      </label>
    </div>

    <p class="hint">{{ $t('call_recorder_hint') }}</p>

    <div class="status-row">
      <div class="stat">
        <i-lucide:list class="stat-icon" />
        <span>{{ $t('call_recorder_count', { count: state.totalCount }, state.totalCount) }}</span>
      </div>
      <div class="stat">
        <i-lucide:hard-drive class="stat-icon" />
        <span>{{ $t('call_recorder_total_size', { size: formatFileSize(state.totalSize) }) }}</span>
      </div>
      <div v-if="state.recording" class="stat capture-stat" :class="state.speakerphoneForced ? 'good' : 'warn'">
        <i-lucide:volume-2 v-if="state.speakerphoneForced" class="stat-icon" />
        <i-lucide:volume-x v-else class="stat-icon" />
        <span>{{ state.speakerphoneForced ? $t('call_recorder_capture_both_sides') : $t('call_recorder_capture_one_side') }}</span>
      </div>
    </div>

    <div v-if="recent.length === 0" class="empty">{{ $t('call_recorder_no_recordings') }}</div>
    <ul v-else class="recent-list">
      <li v-for="r in recent" :key="r.filename" class="recent-item">
        <div class="recent-info">
          <div class="recent-name">{{ r.displayName || r.filename }}</div>
          <div class="recent-meta">
            <span class="badge" :class="r.source">{{ r.source }}</span>
            <span>{{ formatDateTime(new Date(r.startedAt).toISOString()) }}</span>
            <span>·</span>
            <span>{{ formatSeconds(Math.round(r.durationMs / 1000)) }}</span>
          </div>
        </div>
        <audio :src="getFileUrl(r.fileId)" controls preload="none" class="recent-audio" />
      </li>
    </ul>

    <div class="footer">
      <router-link to="/call-recordings" class="view-all">
        {{ $t('call_recorder_view_all') }}
        <i-lucide:arrow-right />
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { initMutation } from '@/lib/api/mutation'
import { callRecorderStateGQL, callRecordingsGQL } from '@/lib/api/query'
import { setCallRecorderEnabledGQL } from '@/lib/api/mutation'
import { getFileUrl } from '@/lib/api/file'
import { formatDateTime, formatFileSize, formatSeconds } from '@/lib/format'
import { useI18n } from 'vue-i18n'

interface ICallRecorderState {
  enabled: boolean
  recording: boolean
  currentDisplayName: string
  currentSource: string
  currentStartedAt: number
  totalCount: number
  totalSize: number
  lastError: string
  activeAudioSource: string
  speakerphoneForced: boolean
}

interface ICallRecording {
  id: string
  filename: string
  displayName: string
  source: string
  direction: string
  appId: string
  appName: string
  startedAt: number
  endedAt: number
  durationMs: number
  sizeBytes: number
  fileId: string
  audioSource: string
  speakerphoneForced: boolean
}

const { t } = useI18n()
const state = ref<ICallRecorderState>({
  enabled: true, recording: false, currentDisplayName: '', currentSource: '',
  currentStartedAt: 0, totalCount: 0, totalSize: 0, lastError: '',
})
const recent = ref<ICallRecording[]>([])

const tickNow = ref(Date.now())
let tickHandle: any
const elapsed = computed(() => {
  void tickNow.value
  if (!state.value.recording || !state.value.currentStartedAt) return ''
  const sec = Math.floor((Date.now() - state.value.currentStartedAt) / 1000)
  const m = Math.floor(sec / 60); const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
})

const { mutate: mToggle } = initMutation({ document: setCallRecorderEnabledGQL })

async function onToggle(e: Event) {
  const enabled = (e.target as HTMLInputElement).checked
  state.value.enabled = enabled
  await mToggle({ enabled })
  toast(enabled ? t('call_recorder_enabled') : t('call_recorder_disabled'))
}

async function loadAll() {
  const s = await gqlFetch<{ callRecorderState: ICallRecorderState }>(callRecorderStateGQL)
  if (!s.errors) state.value = s.data.callRecorderState
  const r = await gqlFetch<{ callRecordings: ICallRecording[] }>(callRecordingsGQL, { offset: 0, limit: 3 })
  if (!r.errors) recent.value = r.data.callRecordings
}

const onStateEvt = (d: any) => { if (d) state.value = d }
const onListChanged = () => { loadAll() }

onMounted(() => {
  loadAll()
  emitter.on('call_recorder_state', onStateEvt)
  emitter.on('call_recordings_changed', onListChanged)
  tickHandle = setInterval(() => { tickNow.value = Date.now() }, 1000)
})

onUnmounted(() => {
  emitter.off('call_recorder_state', onStateEvt)
  emitter.off('call_recordings_changed', onListChanged)
  if (tickHandle) clearInterval(tickHandle)
})
</script>

<style scoped lang="scss">
.recorder-card {
  grid-column: span 2;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  transition: box-shadow 0.2s ease;

  &.is-recording {
    box-shadow: 0 0 0 2px var(--md-sys-color-error);
  }
}

.header {
  display: flex; align-items: center; gap: 12px;
}
.title-row { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; flex-wrap: wrap; }
.title-icon { color: var(--md-sys-color-primary); }
.card-title {
  font-size: 1rem; font-weight: 500; margin: 0;
  text-transform: none; color: var(--md-sys-color-on-surface);
}
.rec-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 2px 10px; border-radius: 999px;
  background: var(--md-sys-color-error-container, #ffdad6);
  color: var(--md-sys-color-on-error-container, #410002);
  font-size: 0.75rem; font-weight: 500;
}
.rec-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--md-sys-color-error, #d32f2f);
  animation: blink 1s infinite;
}
.elapsed { font-variant-numeric: tabular-nums; }
@keyframes blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0.2; }
}

.switch {
  position: relative; display: inline-block; width: 44px; height: 24px;
  input { opacity: 0; width: 0; height: 0; }
  .track {
    position: absolute; cursor: pointer; inset: 0;
    background: var(--md-sys-color-surface-variant);
    border-radius: 24px; transition: 0.2s;
    &::before {
      position: absolute; content: ''; height: 18px; width: 18px;
      left: 3px; top: 3px;
      background: var(--md-sys-color-on-surface-variant);
      border-radius: 50%; transition: 0.2s;
    }
  }
  input:checked + .track {
    background: var(--md-sys-color-primary);
    &::before {
      transform: translateX(20px);
      background: var(--md-sys-color-on-primary);
    }
  }
}

.hint {
  font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant);
  margin: 0;
}

.status-row { display: flex; gap: 16px; flex-wrap: wrap; font-size: 0.85rem; }
.stat { display: inline-flex; align-items: center; gap: 6px; color: var(--md-sys-color-on-surface-variant); }
.stat-icon { width: 16px; height: 16px; }
.capture-stat {
  padding: 2px 8px; border-radius: 999px; font-weight: 500;
  &.good { background: var(--md-sys-color-secondary-container); color: var(--md-sys-color-on-secondary-container); }
  &.warn { background: var(--md-sys-color-error-container, #ffdad6); color: var(--md-sys-color-on-error-container, #410002); }
}

.empty {
  font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant);
  padding: 12px; text-align: center;
  background: var(--md-sys-color-surface);
  border-radius: 8px;
}

.recent-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.recent-item {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 10px; border-radius: 8px;
  background: var(--md-sys-color-surface);
}
.recent-info { flex: 1; min-width: 0; }
.recent-name {
  font-size: 0.9rem; font-weight: 500; color: var(--md-sys-color-on-surface);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.recent-meta {
  display: flex; gap: 6px; align-items: center; flex-wrap: wrap;
  font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant);
}
.badge {
  text-transform: capitalize; padding: 1px 6px;
  border-radius: 4px; font-size: 0.65rem;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}
.recent-audio { width: 220px; max-width: 50%; height: 32px; }

.footer { display: flex; justify-content: flex-end; }
.view-all {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 0.85rem; color: var(--md-sys-color-primary);
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

@media (max-width: 768px) {
  .recent-audio { width: 100%; max-width: none; }
  .recent-item { flex-direction: column; align-items: stretch; }
}
</style>
