<template>
  <div class="live-monitor">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">{{ $t('live_camera') }}</div>
    </Teleport>

    <Teleport v-if="isActive" to="#header-end-slot" defer>
      <div class="header-actions">
        <v-icon-button
          v-if="streaming && !recording"
          :tooltip="$t('take_photo')"
          @click="onTakePhoto"
        >
          <i-lucide:camera />
        </v-icon-button>
        <v-icon-button
          v-if="streaming && !recording && videoRecordSupported"
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
        <v-icon-button v-if="streaming" :tooltip="$t('switch_camera')" @click="switchFacing">
          <i-lucide:repeat-2 />
        </v-icon-button>
        <v-filled-button v-if="streaming" class="btn-sm" :loading="stopLoading" @click="stop">
          {{ $t('stop_live_camera') }}
        </v-filled-button>
      </div>
    </Teleport>

    <div class="live-stage">
      <div v-if="state === 'idle' || state === 'failed' || state === 'requesting'" class="idle-panel">
        <i-lucide:camera class="idle-icon" />
        <p class="idle-title">{{ $t('live_camera') }}</p>
        <p class="idle-hint">{{ $t('live_camera_idle_hint') }}</p>
        <div class="facing-row">
          <v-segmented-button v-model="facing" :options="facingOptions" />
        </div>
        <v-filled-button :loading="startLoading" @click="start">{{ $t('start_live_camera') }}</v-filled-button>
        <p v-if="state === 'failed'" class="error-text">{{ $t('live_stream_failed') }}</p>
      </div>
      <div v-else class="video-wrap">
        <video ref="videoEl" class="live-video" autoplay playsinline muted />
        <div v-if="state !== 'streaming'" class="overlay">
          <i-lucide:loader-circle class="spinner" />
          <span>{{ $t('connecting') }}</span>
        </div>
        <div v-if="recording" class="recording-badge">
          <span class="recording-dot" />
          {{ $t('recording_now') }} {{ recordingElapsed }}
        </div>
      </div>
    </div>

    <section class="captures-section">
      <h3 class="captures-heading">{{ $t('captures_title') }}</h3>
      <p v-if="captures.length === 0" class="captures-empty">{{ $t('no_captures_yet') }}</p>
      <div v-else class="captures-grid">
        <div v-for="item in captures" :key="item.id" class="capture-card">
          <div class="capture-media">
            <img v-if="item.kind === 'photo'" :src="item.url" :alt="item.filename" />
            <video v-else :src="item.url" controls preload="metadata" />
          </div>
          <div class="capture-info">
            <div class="capture-name" :title="item.filename">{{ item.filename }}</div>
            <div class="capture-meta">
              <span class="capture-kind">{{ item.kind === 'photo' ? $t('photo') : $t('video') }}</span>
              <span v-if="item.durationMs">· {{ formatDuration(item.durationMs) }}</span>
            </div>
          </div>
          <div class="capture-actions">
            <v-icon-button :tooltip="$t('download')" @click="downloadBlob(item)">
              <i-lucide:download />
            </v-icon-button>
            <v-icon-button :tooltip="$t('delete')" @click="removeCapture(item.id)">
              <i-lucide:trash-2 />
            </v-icon-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import type { GqlError } from '@/lib/api/gql-client'
import { initLazyQuery, liveCameraStateGQL } from '@/lib/api/query'
import {
  initMutation,
  startLiveCameraGQL,
  stopLiveCameraGQL,
  switchLiveCameraFacingGQL,
} from '@/lib/api/mutation'
import { WebRTCClient, type SignalingMessage } from '@/lib/webrtc-client'
import { makeSendWebRTCSignalingFor } from '@/lib/webrtc-signaling'
import { getPhoneIp } from '@/lib/api/api'
import {
  StreamRecorder,
  takePhoto,
  downloadBlob,
  revokeCapture,
  formatDuration,
  type CaptureItem,
} from '@/lib/media-recorder'

const { t } = useI18n()
const route = useRoute()
const isActive = computed(() => route.path === '/live-camera')

type LiveState = 'idle' | 'requesting' | 'connecting' | 'streaming' | 'failed'
const state = ref<LiveState>('idle')
const facing = ref<'back' | 'front'>('back')
const videoEl = ref<HTMLVideoElement>()

const facingOptions = computed(() => [
  { value: 'back', label: t('camera_back') },
  { value: 'front', label: t('camera_front') },
])

let client: WebRTCClient | null = null
let liveStream: MediaStream | null = null
const queue: SignalingMessage[] = []

const captures = ref<CaptureItem[]>([])
const recorder = new StreamRecorder('video')
const recording = ref(false)
const recordingStartedAt = ref(0)
const recordingElapsed = ref('')
let recordingTimer: number | null = null
const videoRecordSupported = computed(() => StreamRecorder.videoSupported())

function attach(stream: MediaStream) {
  liveStream = stream
  if (videoEl.value) {
    videoEl.value.srcObject = stream
    videoEl.value.play().catch(() => {})
  }
}

function connect() {
  cleanupClient()
  client = new WebRTCClient({
    sendSignaling: makeSendWebRTCSignalingFor('camera'),
    onStream: (s) => { attach(s); state.value = 'streaming' },
    onConnectionStateChange: (s) => {
      if (s === 'failed' || s === 'disconnected' || s === 'closed') {
        if (state.value === 'streaming' || state.value === 'connecting') state.value = 'failed'
      }
    },
    onError: () => { state.value = 'failed' },
  })
  client.startSession(false, false, getPhoneIp(), { video: true, audio: false })
  while (queue.length) client.handleSignalingMessage(queue.shift()!)
}

function cleanupClient() {
  // If we are currently recording, stop and keep the file before tearing down the stream.
  if (recording.value) {
    finishRecording().catch(() => {})
  }
  if (videoEl.value) { videoEl.value.pause(); videoEl.value.srcObject = null }
  liveStream = null
  client?.cleanup(); client = null
}

const onSignaling = (msg: any) => {
  if (!msg || msg.stream !== 'camera') return
  if (client) client.handleSignalingMessage(msg)
  else queue.push(msg)
}

const { fetch: fetchState } = initLazyQuery({
  handle: (data: any, error: string) => {
    if (error) { toast(t(error), 'error'); return }
    const s = data?.liveCameraState
    if (s?.facing) facing.value = s.facing === 'front' ? 'front' : 'back'
    if (s?.running) {
      if (state.value === 'idle' || state.value === 'failed') { state.value = 'connecting'; connect() }
    } else {
      cleanupClient(); state.value = 'idle'
    }
  },
  document: liveCameraStateGQL,
  variables: () => ({}),
  options: { fetchPolicy: 'no-cache' },
})

const { mutate: startMutate, loading: startLoading, onError: onStartError } =
  initMutation({ document: startLiveCameraGQL }, false)
const { mutate: stopMutate, loading: stopLoading, onDone: onStopDone, onError: onStopError } =
  initMutation({ document: stopLiveCameraGQL })
const { mutate: switchMutate } = initMutation({ document: switchLiveCameraFacingGQL })

const start = () => { state.value = 'requesting'; startMutate({ facing: facing.value }) }
const stop = () => stopMutate()
const switchFacing = () => switchMutate()

onStartError((e: GqlError) => { toast(t(e.message), 'error'); state.value = 'failed' })
onStopDone(() => { cleanupClient(); state.value = 'idle' })
onStopError((e: GqlError) => toast(t(e.message), 'error'))

const onLiveCameraStreaming = () => {
  if (state.value === 'streaming' || state.value === 'connecting') return
  state.value = 'connecting'; connect()
}

const streaming = computed(() => state.value === 'streaming' || state.value === 'connecting')

watch(videoEl, (el) => {
  if (el && client && state.value !== 'idle') {
    // re-attach if we already have a stream
    el.play().catch(() => {})
  }
})

async function onTakePhoto() {
  if (!videoEl.value) return
  try {
    const item = await takePhoto(videoEl.value)
    captures.value = [item, ...captures.value]
  } catch (_) {
    toast(t('capture_failed'), 'error')
  }
}

function onStartRecording() {
  if (!liveStream) return
  if (!StreamRecorder.videoSupported()) {
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
    captures.value = [item, ...captures.value]
  } catch (_) {
    // already handled / not recording
  }
}

async function onStopRecording() {
  await finishRecording()
}

function removeCapture(id: string) {
  const idx = captures.value.findIndex((c) => c.id === id)
  if (idx < 0) return
  revokeCapture(captures.value[idx])
  captures.value.splice(idx, 1)
}

onMounted(() => {
  emitter.on('webrtc_signaling', onSignaling)
  emitter.on('live_camera_streaming', onLiveCameraStreaming)
  fetchState()
})

onBeforeUnmount(() => {
  emitter.off('webrtc_signaling', onSignaling)
  emitter.off('live_camera_streaming', onLiveCameraStreaming)
  recorder.cancel()
  if (recordingTimer != null) { window.clearInterval(recordingTimer); recordingTimer = null }
  cleanupClient()
})
</script>

<style scoped lang="scss">
.live-monitor { display: flex; flex-direction: column; height: 100%; }
.title { flex: 1; font-weight: 500; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.recording-btn {
  color: var(--md-sys-color-error);
}
.live-stage { flex: 1; display: flex; align-items: center; justify-content: center; padding: 16px; min-height: 240px; }
.idle-panel {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 32px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
}
.idle-icon { font-size: 48px; color: var(--md-sys-color-primary); }
.idle-title { font-size: 1.25rem; font-weight: 500; margin: 0; }
.idle-hint { color: var(--md-sys-color-on-surface-variant); margin: 0; text-align: center; }
.facing-row { margin: 8px 0; }
.video-wrap { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #000; border-radius: 12px; overflow: hidden; }
.live-video { width: 100%; height: 100%; object-fit: contain; background: #000; }
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px; color: #fff;
  background: rgba(0,0,0,0.4);
}
.spinner { font-size: 32px; }
.error-text { color: var(--md-sys-color-error); margin: 0; }
.recording-badge {
  position: absolute; top: 12px; left: 12px;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 999px;
  background: rgba(0,0,0,0.55); color: #fff;
  font-size: 0.875rem; font-weight: 500;
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
.captures-section { padding: 16px; border-top: 1px solid var(--md-sys-color-outline-variant); }
.captures-heading { font-size: 1rem; font-weight: 500; margin: 0 0 8px; }
.captures-empty { color: var(--md-sys-color-on-surface-variant); margin: 0; }
.captures-grid {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}
.capture-card {
  display: flex; flex-direction: column; gap: 8px;
  padding: 8px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
.capture-media {
  width: 100%; aspect-ratio: 16 / 9; background: #000;
  border-radius: 8px; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  img, video { width: 100%; height: 100%; object-fit: contain; }
}
.capture-info { display: flex; flex-direction: column; gap: 2px; }
.capture-name { font-size: 0.875rem; font-weight: 500; word-break: break-all; }
.capture-meta { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }
.capture-actions { display: flex; gap: 4px; justify-content: flex-end; }
</style>
