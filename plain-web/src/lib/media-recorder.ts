/**
 * Tiny wrapper around the browser MediaRecorder API used by the
 * Live camera and Live microphone screens to save the incoming WebRTC
 * MediaStream to a downloadable Blob entirely client-side. Nothing is
 * uploaded back to the phone.
 */

export type CaptureKind = 'photo' | 'video' | 'audio'

export interface CaptureItem {
  id: string
  kind: CaptureKind
  url: string
  blob: Blob
  filename: string
  createdAt: number
  /** Duration in seconds for video/audio recordings. */
  durationMs?: number
}

const VIDEO_MIME_CANDIDATES = [
  'video/webm;codecs=vp9,opus',
  'video/webm;codecs=vp8,opus',
  'video/webm;codecs=vp9',
  'video/webm;codecs=vp8',
  'video/webm',
  'video/mp4',
]

const AUDIO_MIME_CANDIDATES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
  'audio/mp4',
]

function pickSupportedMime(candidates: string[]): string | null {
  if (typeof MediaRecorder === 'undefined') return null
  for (const m of candidates) {
    try {
      if (MediaRecorder.isTypeSupported(m)) return m
    } catch (_) {}
  }
  return null
}

export function pickVideoMime(): string | null {
  return pickSupportedMime(VIDEO_MIME_CANDIDATES)
}

export function pickAudioMime(): string | null {
  return pickSupportedMime(AUDIO_MIME_CANDIDATES)
}

export function extensionFor(mime: string | null | undefined): string {
  if (!mime) return 'bin'
  if (mime.includes('webm')) return 'webm'
  if (mime.includes('mp4')) return 'mp4'
  if (mime.includes('ogg')) return 'ogg'
  return 'bin'
}

export function timestampedFilename(prefix: string, ext: string): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp =
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}_` +
    `${pad(d.getHours())}-${pad(d.getMinutes())}-${pad(d.getSeconds())}`
  return `${prefix}_${stamp}.${ext}`
}

export function downloadBlob(item: CaptureItem) {
  const a = document.createElement('a')
  a.href = item.url
  a.download = item.filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

export function revokeCapture(item: CaptureItem) {
  try {
    URL.revokeObjectURL(item.url)
  } catch (_) {}
}

export function takePhoto(video: HTMLVideoElement): Promise<CaptureItem> {
  return new Promise((resolve, reject) => {
    const w = video.videoWidth
    const h = video.videoHeight
    if (!w || !h) {
      reject(new Error('video_not_ready'))
      return
    }
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      reject(new Error('canvas_unsupported'))
      return
    }
    try {
      ctx.drawImage(video, 0, 0, w, h)
    } catch (e) {
      reject(e)
      return
    }
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('toBlob_failed'))
          return
        }
        const filename = timestampedFilename('photo', 'jpg')
        const url = URL.createObjectURL(blob)
        resolve({
          id: cryptoRandomId(),
          kind: 'photo',
          url,
          blob,
          filename,
          createdAt: Date.now(),
        })
      },
      'image/jpeg',
      0.92,
    )
  })
}

export class StreamRecorder {
  private recorder: MediaRecorder | null = null
  private chunks: Blob[] = []
  private startedAt = 0
  private mime: string | null = null
  private resolveStop: ((item: CaptureItem) => void) | null = null
  private rejectStop: ((err: any) => void) | null = null

  constructor(private kind: 'video' | 'audio') {}

  static videoSupported(): boolean {
    return pickVideoMime() != null
  }

  static audioSupported(): boolean {
    return pickAudioMime() != null
  }

  isActive(): boolean {
    return this.recorder?.state === 'recording'
  }

  start(stream: MediaStream): boolean {
    if (this.recorder) return false
    const mime =
      this.kind === 'video' ? pickVideoMime() : pickAudioMime()
    if (!mime) return false
    try {
      this.recorder = new MediaRecorder(stream, { mimeType: mime })
    } catch (_) {
      this.recorder = null
      return false
    }
    this.mime = mime
    this.chunks = []
    this.startedAt = Date.now()
    this.recorder.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) this.chunks.push(e.data)
    }
    this.recorder.onstop = () => {
      const blob = new Blob(this.chunks, { type: this.mime || 'application/octet-stream' })
      const ext = extensionFor(this.mime)
      const prefix = this.kind === 'video' ? 'video' : 'audio'
      const filename = timestampedFilename(prefix, ext)
      const url = URL.createObjectURL(blob)
      const item: CaptureItem = {
        id: cryptoRandomId(),
        kind: this.kind === 'video' ? 'video' : 'audio',
        url,
        blob,
        filename,
        createdAt: Date.now(),
        durationMs: Date.now() - this.startedAt,
      }
      this.recorder = null
      this.chunks = []
      this.resolveStop?.(item)
      this.resolveStop = null
      this.rejectStop = null
    }
    try {
      this.recorder.start(1000)
    } catch (e) {
      this.recorder = null
      return false
    }
    return true
  }

  stop(): Promise<CaptureItem> {
    return new Promise((resolve, reject) => {
      if (!this.recorder || this.recorder.state !== 'recording') {
        reject(new Error('not_recording'))
        return
      }
      this.resolveStop = resolve
      this.rejectStop = reject
      try {
        this.recorder.stop()
      } catch (e) {
        this.recorder = null
        reject(e)
      }
    })
  }

  cancel() {
    try {
      if (this.recorder?.state === 'recording') this.recorder.stop()
    } catch (_) {}
    this.recorder = null
    this.chunks = []
    this.resolveStop = null
    this.rejectStop = null
  }
}

function cryptoRandomId(): string {
  try {
    const arr = new Uint8Array(8)
    crypto.getRandomValues(arr)
    return Array.from(arr).map((b) => b.toString(16).padStart(2, '0')).join('')
  } catch (_) {
    return Math.random().toString(36).slice(2) + Date.now().toString(36)
  }
}

export function formatDuration(ms?: number): string {
  if (!ms || ms < 0) return ''
  const total = Math.round(ms / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${m}:${String(s).padStart(2, '0')}`
}
