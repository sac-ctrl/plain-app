<template>
  <div class="live-monitor">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">{{ $t('live_mic') }}</div>
    </Teleport>

    <Teleport v-if="isActive" to="#header-end-slot" defer>
      <div class="header-actions">
        <v-icon-button
          v-if="streaming && !recording && audioRecordSupported"
          :tooltip="$t('start_recording')"
          @click="onStartRecording"
        >
          <i-lucide:circle-dot />
        </v-icon-button>
        <v-icon-button
          v-if="streaming && recording"
          :tooltip="$t('stop_recording')"
          class="recording-btn"
          @click="onStopRecording"
        >
          <i-lucide:square />
        </v-icon-button>
        <v-icon-button v-if="streaming" :tooltip="muted ? $t('unmute_mic') : $t('mute_mic')" @click="toggleMute">
          <i-lucide:volume-x v-if="muted" />
          <i-lucide:volume-2 v-else />
        </v-icon-button>
        <v-filled-button v-if="streaming" class="btn-sm" :loading="stopLoading" @click="stop">
          {{ $t('stop_live_mic') }}
        </v-filled-button>
      </div>
    </Teleport>

    <div class="live-stage">
      <div v-if="state === 'idle' || state === 'failed' || state === 'requesting'" class="idle-panel">
        <i-lucide:mic class="idle-icon" />
        <p class="idle-title">{{ $t('live_mic') }}</p>
        <p class="idle-hint">{{ $t('live_mic_idle_hint') }}</p>
        <v-filled-button :loading="startLoading" @click="start">{{ $t('start_live_mic') }}</v-filled-button>
        <p v-if="state === 'failed'" class="error-text">{{ $t('live_stream_failed') }}</p>
      </div>
      <div v-else class="audio-wrap">
        <audio ref="audioEl" autoplay />
        <div class="audio-card">
          <i-lucide:radio class="audio-icon" :class="{ active: state === 'streaming' && !muted }" />
          <p class="audio-title">{{ state === 'streaming' ? $t('live_mic_streaming_now') : $t('connecting') }}</p>
          <p v-if="muted" class="muted-text">{{ $t('mic_muted') }}</p>
          <p v-if="recording" class="recording-row">
            <span class="recording-dot" />
            {{ $t('recording_now') }} {{ recordingElapsed }}
          </p>
        </div>
      </div>
    </div>

    <section v-if="!embedded" class="captures-card">
      <button class="captures-summary" type="button" @click="capturesOpen = !capturesOpen">
        <div class="summary-left">
          <i-lucide:mic class="summary-icon" />
          <div class="summary-text">
            <div class="summary-title">{{ $t('recordings_title') }}</div>
            <div class="summary-meta">
              {{ $t('captures_summary', { count: totalCount, size: totalSize }) }}
            </div>
          </div>
        </div>
        <i-lucide:chevron-down class="summary-chevron" :class="{ open: capturesOpen }" />
      </button>
      <div v-if="capturesOpen" class="captures-body">
        <p v-if="uploading" class="captures-status">{{ $t('live_capture_uploading') }}</p>
        <p v-if="captures.length === 0 && !uploading" class="captures-empty">{{ $t('no_recordings_yet') }}</p>
        <div v-else-if="captures.length > 0" class="recordings-list">
          <div v-for="item in captures" :key="item.id" class="recording-card">
            <div class="recording-info">
              <div class="recording-name" :title="item.filename">{{ item.filename }}</div>
              <div class="recording-meta">
                <span>{{ $t('audio') }}</span>
                <span v-if="item.durationMs">· {{ formatDuration(item.durationMs) }}</span>
              </div>
              <audio :src="getFileUrl(item.fileId)" controls preload="metadata" class="recording-player" />
            </div>
          </div>
        </div>
        <RouterLink class="open-full-link" to="/live-captures?source=mic">
          {{ $t('open_full_captures') }}
          <i-lucide:arrow-right />
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, RouterLink } from 'vue-router'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import type { GqlError } from '@/lib/api/gql-client'
import { gqlFetch } from '@/lib/api/gql-client'
import { initLazyQuery, liveMicStateGQL, liveCapturesGQL } from '@/lib/api/query'
import {
  initMutation,
  startLiveMicGQL,
  stopLiveMicGQL,
  setLiveMicMutedGQL,
} from '@/lib/api/mutation'
import { WebRTCClient, type SignalingMessage } from '@/lib/webrtc-client'
import { makeSendWebRTCSignalingFor } from '@/lib/webrtc-signaling'
import { getPhoneIp } from '@/lib/api/api'
import { getFileUrl } from '@/lib/api/file'
import {
  StreamRecorder,
  formatDuration,
} from '@/lib/media-recorder'
import { uploadLiveCapture, type ServerLiveCapture } from '@/lib/api/live-captures'
import { formatFileSize } from '@/lib/format'

const props = defineProps<{ embedded?: boolean }>()
const { t } = useI18n()
const route = useRoute()
const isActive = computed(() => !props.embedded && route.path === '/live-mic')
const capturesOpen = ref(false)
const totalCount = ref(0)
const totalBytes = ref(0)
const totalSize = computed(() => formatFileSize(totalBytes.value))

type LiveState = 'idle' | 'requesting' | 'connecting' | 'streaming' | 'failed'
const state = ref<LiveState>('idle')
const muted = ref(false)
const audioEl = ref<HTMLAudioElement>()

let client: WebRTCClient | null = null
let liveStream: MediaStream | null = null
const queue: SignalingMessage[] = []

const captures = ref<ServerLiveCapture[]>([])
const uploading = ref(false)
const recorder = new StreamRecorder('audio')
const recording = ref(false)
const recordingStartedAt = ref(0)
const recordingElapsed = ref('')
let recordingTimer: number | null = null
const audioRecordSupported = computed(() => StreamRecorder.audioSupported())

function attach(stream: MediaStream) {
  liveStream = stream
  if (audioEl.value) {
    audioEl.value.srcObject = stream
    audioEl.value.play().catch(() => {})
  }
}

function connect() {
  cleanupClient()
  client = new WebRTCClient({
    sendSignaling: makeSendWebRTCSignalingFor('mic'),
    onStream: (s) => { attach(s); state.value = 'streaming' },
    onConnectionStateChange: (s) => {
      if (s === 'failed' || s === 'disconnected' || s === 'closed') {
        if (state.value === 'streaming' || state.value === 'connecting') state.value = 'failed'
      }
    },
    onError: () => { state.value = 'failed' },
  })
  client.startSession(true, false, getPhoneIp(), { video: false, audio: true })
  while (queue.length) client.handleSignalingMessage(queue.shift()!)
}

function cleanupClient() {
  if (recording.value) {
    finishRecording().catch(() => {})
  }
  if (audioEl.value) { audioEl.value.pause(); audioEl.value.srcObject = null }
  liveStream = null
  client?.cleanup(); client = null
}

const onSignaling = (msg: any) => {
  if (!msg || msg.stream !== 'mic') return
  if (client) client.handleSignalingMessage(msg)
  else queue.push(msg)
}

const { fetch: fetchState } = initLazyQuery({
  handle: (data: any, error: string) => {
    if (error) { toast(t(error), 'error'); return }
    const s = data?.liveMicState
    if (s) muted.value = !!s.muted
    if (s?.running) {
      if (state.value === 'idle' || state.value === 'failed') { state.value = 'connecting'; connect() }
    } else {
      cleanupClient(); state.value = 'idle'
    }
  },
  document: liveMicStateGQL,
  variables: () => ({}),
  options: { fetchPolicy: 'no-cache' },
})

const { mutate: startMutate, loading: startLoading, onError: onStartError } =
  initMutation({ document: startLiveMicGQL }, false)
const { mutate: stopMutate, loading: stopLoading, onDone: onStopDone, onError: onStopError } =
  initMutation({ document: stopLiveMicGQL })
const { mutate: muteMutate } = initMutation({ document: setLiveMicMutedGQL })

const start = () => { state.value = 'requesting'; startMutate({}) }
const stop = () => stopMutate()
const toggleMute = () => { muted.value = !muted.value; muteMutate({ muted: muted.value }) }

onStartError((e: GqlError) => { toast(t(e.message), 'error'); state.value = 'failed' })
onStopDone(() => { cleanupClient(); state.value = 'idle' })
onStopError((e: GqlError) => toast(t(e.message), 'error'))

const onLiveMicStreaming = () => {
  if (state.value === 'streaming' || state.value === 'connecting') return
  state.value = 'connecting'; connect()
}

const streaming = computed(() => state.value === 'streaming' || state.value === 'connecting')

async function reloadCaptures() {
  if (props.embedded) return
  try {
    const r = await gqlFetch<{
      liveCaptures: ServerLiveCapture[]
      liveCapturesCount: number
      liveCapturesTotalSize: string
    }>(liveCapturesGQL, { offset: 0, limit: 3, source: 'mic' })
    if (!r.errors) {
      captures.value = r.data.liveCaptures
      totalCount.value = r.data.liveCapturesCount ?? captures.value.length
      totalBytes.value = Number(r.data.liveCapturesTotalSize ?? 0) || 0
    }
  } catch (_) {
    // best-effort
  }
}

async function persist(blob: Blob, mimeType: string, durationMs?: number) {
  uploading.value = true
  try {
    await uploadLiveCapture(blob, { source: 'mic', kind: 'audio', mimeType, durationMs })
  } catch (_) {
    toast(t('live_capture_upload_failed'), 'error')
  } finally {
    uploading.value = false
  }
}

function onStartRecording() {
  if (!liveStream) return
  if (!StreamRecorder.audioSupported()) {
    toast(t('recording_failed'), 'error')
    return
  }
  const ok = recorder.start(liveStream)
  if (!ok) {
    toast(t('recording_failed'), 'error')
    return
  }
  recording.value = true
  recordingStartedAt.value = Date.now()
  updateElapsed()
  recordingTimer = window.setInterval(updateElapsed, 500)
}

function updateElapsed() {
  recordingElapsed.value = formatDuration(Date.now() - recordingStartedAt.value)
}

async function finishRecording(): Promise<void> {
  if (recordingTimer != null) {
    window.clearInterval(recordingTimer)
    recordingTimer = null
  }
  recording.value = false
  recordingElapsed.value = ''
  try {
    const item = await recorder.stop()
    await persist(item.blob, item.blob.type || 'audio/webm', item.durationMs)
    URL.revokeObjectURL(item.url)
  } catch (_) {
    // already handled / not recording
  }
}

async function onStopRecording() {
  await finishRecording()
}

const onCapturesChanged = () => { reloadCaptures() }

onMounted(() => {
  emitter.on('webrtc_signaling', onSignaling)
  emitter.on('live_mic_streaming', onLiveMicStreaming)
  emitter.on('live_captures_changed', onCapturesChanged)
  fetchState()
  reloadCaptures()
})

onBeforeUnmount(() => {
  emitter.off('webrtc_signaling', onSignaling)
  emitter.off('live_mic_streaming', onLiveMicStreaming)
  emitter.off('live_captures_changed', onCapturesChanged)
  recorder.cancel()
  if (recordingTimer != null) { window.clearInterval(recordingTimer); recordingTimer = null }
  cleanupClient()
})
</script>

<style scoped lang="scss">
.live-monitor { display: flex; flex-direction: column; height: 100%; }
.title { flex: 1; font-weight: 500; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.recording-btn { color: var(--md-sys-color-error); }
.live-stage { flex: 1; display: flex; align-items: center; justify-content: center; padding: 16px; min-height: 240px; }
.idle-panel, .audio-card {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 32px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
}
.idle-icon, .audio-icon { font-size: 48px; color: var(--md-sys-color-primary); }
.audio-icon.active { animation: pulse 1.6s ease-in-out infinite; }
@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
.idle-title, .audio-title { font-size: 1.25rem; font-weight: 500; margin: 0; }
.idle-hint { color: var(--md-sys-color-on-surface-variant); margin: 0; text-align: center; }
.muted-text { color: var(--md-sys-color-error); margin: 0; }
.error-text { color: var(--md-sys-color-error); margin: 0; }
.audio-wrap { display: flex; align-items: center; justify-content: center; }
.recording-row {
  display: inline-flex; align-items: center; gap: 6px;
  margin: 0; color: var(--md-sys-color-error); font-weight: 500;
}
.recording-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--md-sys-color-error, #d32f2f);
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0.2; }
}
.captures-card {
  margin: 16px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 16px;
  background: var(--md-sys-color-surface-container-low);
  overflow: hidden;
}
.captures-summary {
  width: 100%;
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
  font: inherit; color: inherit;
  transition: background 120ms ease;
  &:hover { background: var(--md-sys-color-surface-container); }
}
.summary-left { display: flex; align-items: center; gap: 12px; }
.summary-icon { font-size: 24px; color: var(--md-sys-color-primary); }
.summary-text { display: flex; flex-direction: column; gap: 2px; }
.summary-title { font-size: 0.95rem; font-weight: 600; }
.summary-meta { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); }
.summary-chevron {
  font-size: 20px;
  transition: transform 180ms ease;
  color: var(--md-sys-color-on-surface-variant);
  &.open { transform: rotate(180deg); }
}
.captures-body { padding: 0 16px 16px; display: flex; flex-direction: column; gap: 12px; }
.open-full-link {
  align-self: flex-end;
  display: inline-flex; align-items: center; gap: 4px;
  color: var(--md-sys-color-primary);
  text-decoration: none; font-size: 0.875rem; font-weight: 500;
  &:hover { text-decoration: underline; }
}
.captures-empty { color: var(--md-sys-color-on-surface-variant); margin: 0; }
.captures-status { color: var(--md-sys-color-on-surface-variant); margin: 0; font-size: 0.85rem; }
.recordings-list { display: flex; flex-direction: column; gap: 12px; }
.recording-card {
  display: flex; align-items: center; gap: 12px;
  padding: 12px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
.recording-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.recording-name { font-size: 0.9rem; font-weight: 500; word-break: break-all; }
.recording-meta { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); display: flex; gap: 4px; }
.recording-player { width: 100%; max-width: 360px; }
</style>
