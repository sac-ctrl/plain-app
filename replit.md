# PlainApp

Open-source Android (Kotlin) app that turns your phone into a self-hosted web console. Built with Gradle; the project's output is an APK installed on a phone — there is no Replit-hosted web server. The "Status Page" workflow on this Repl just shows a static info page about the project.

## Build

```
./build-apk.sh
```

A GitHub Actions workflow at `.github/workflows/build-apk.yml` auto-builds a debug APK on every push.

## Cloudflare Tunnel (built into the app)

Recently added so the web console can be reached from anywhere on the internet through your own domain (e.g. `shakti.buzz`), with **no extra apps installed** on the phone.

Files involved:
- `app/src/main/java/com/ismartcoding/plain/services/CloudflareTunnelService.kt` — foreground service that runs the bundled `cloudflared` binary.
- `app/src/main/java/com/ismartcoding/plain/services/CloudflareTunnelManager.kt` — start/stop helper.
- `app/src/main/java/com/ismartcoding/plain/ui/page/web/CloudflareTunnelPage.kt` — settings UI (Web Console → Cloudflare Tunnel).
- `app/src/main/res/values/strings_cloudflare.xml` — UI strings.
- Preferences in `Preferences.kt`: `CloudflareTunnelTokenPreference`, `CloudflareTunnelEnabledPreference`, `CloudflareTunnelHostnamePreference`, `CloudflareTunnelAutoStartPreference`.
- `app/build.gradle.kts` — `downloadCloudflared` Gradle task downloads the official `cloudflared` Linux ARM/ARM64 binaries into `build/generated/cloudflared/jniLibs/<abi>/libcloudflared.so` so they ship inside the APK as native libraries (the only Android-allowed way to ship an executable).
- `AndroidManifest.xml` — `extractNativeLibs="true"` (required to exec the binary at runtime), `CloudflareTunnelService` declared, broadcast actions wired.
- `HttpModule.kt` — CORS opened up (`anyHost()`) so external origins can talk to the server through the public domain.

### How a user uses it
1. Cloudflare account (free) → Zero Trust → Networks → Tunnels → Create tunnel → copy token.
2. Public Hostname → subdomain `phone.shakti.buzz` → service `http://localhost:8080`.
3. In PlainApp: Web Console settings → **Cloudflare Tunnel** → paste token → enable.
4. Disable battery optimization for PlainApp.

### Caveats
Phone must stay on; aggressive OEM battery killers (Xiaomi/Realme/Vivo/Oppo) may suspend the tunnel — autostart whitelist + battery optimization off is required.

## Live camera & microphone streaming (one-way phone → web)

Modeled on the existing screen-mirror WebRTC stack. Two new optional streams that can run independently and concurrently with screen mirror.

### Phone side
- `services/LiveCameraService.kt` — foreground service (`foregroundServiceType="camera"`).
- `services/LiveMicService.kt` — foreground service (`foregroundServiceType="microphone"`).
- `services/webrtc/LiveCameraWebRtcManager.kt` — `Camera2Enumerator`, 1280×720@30, supports `switchCamera()`.
- `services/webrtc/LiveMicWebRtcManager.kt` — audio-only, supports mute toggle.
- `services/webrtc/LivePeerSession.kt` — generic peer session that tags every signaling message with a `stream` discriminator.
- `services/webrtc/WebRtcFactoryHelper.kt` — added `createSimpleWebRtcFactory()` (no MediaProjection / audio swap) and shared `ensureWebRtcInitialized()`.
- `helpers/NotificationHelper.kt` — added `createLiveServiceNotification()` on a new `IMPORTANCE_MIN` / `VISIBILITY_SECRET` channel for discreet ongoing notifications.
- `web/schemas/LiveMonitorGraphQL.kt` — new GraphQL schema (`liveCameraState`, `liveMicState`, `startLiveCamera`, `stopLiveCamera`, `switchLiveCameraFacing`, `startLiveMic`, `stopLiveMic`, `setLiveMicMuted`).
- `web/schemas/ScreenMirrorGraphQL.kt` — `sendWebRtcSignaling` resolver now routes by `payload.stream` to the matching service (`screen` / `camera` / `mic`); existing screen-mirror flow is unchanged when `stream` is null.
- `web/websocket/WebRtcSignalingMessage.kt` — added optional `stream: String?`.
- `events/AppEvents.kt` — new `StartLiveCameraEvent`, `StartLiveMicEvent`.
- `events/WebSocketEvents.kt` — new event types `LIVE_CAMERA_STREAMING(20)`, `LIVE_MIC_STREAMING(21)`.
- `Constants.kt` — `ACTION_STOP_LIVE_CAMERA`, `ACTION_STOP_LIVE_MIC`, `LIVE_MONITOR_NOTIFICATION_CHANNEL_ID`.
- `MainActivity.kt` / `MainActivityEvents.kt` — runtime permission launchers for `CAMERA` and `RECORD_AUDIO`, then start the matching foreground service.
- `AndroidManifest.xml` — declares both services, adds `FOREGROUND_SERVICE_CAMERA` / `FOREGROUND_SERVICE_MICROPHONE` permissions, adds the two STOP broadcast actions.
- `receivers/ServiceStopBroadcastReceiver.kt` — handles the stop actions from the notification.

### Web side
- `lib/webrtc-client.ts` — `SignalingMessage` gained an optional `stream` field; `startSession()` accepts `mediaKinds` so the answerer can negotiate audio-only or video-only.
- `lib/webrtc-signaling.ts` — `makeSendWebRTCSignalingFor(stream)` returns a sender that auto-tags messages.
- `views/screen-mirror/screen-mirror-webrtc.ts` — filters out incoming signaling tagged for a different stream (so screen mirror is never confused by camera/mic frames).
- `views/live-monitor/LiveCameraView.vue` and `LiveMicView.vue` — new pages that mirror the screen-mirror UX but with start/stop, camera-flip, and mute controls.
- `plugins/router.ts` — `/live-camera` and `/live-mic` routes.
- `views/home/HomeView.vue` — feature cards for the two new pages.
- `hooks/app-socket.ts` — maps event types `20` and `21` to `live_camera_streaming` / `live_mic_streaming`.
- `lib/api/query.ts` / `lib/api/mutation.ts` — new GraphQL operations.
- `locales/en-US/monitor.ts` + `locales/en-US/common.ts` — English strings (other locales fall back).

After editing the web app run from the project root:
```
cd plain-web && corepack enable && corepack yarn install && corepack yarn build
rm -rf ../app/src/main/resources/web/* && cp -r dist/* ../app/src/main/resources/web/
```

## Hide launcher icon — full disappearance + ghost-icon refresh

Hiding the launcher icon is done by disabling the `LauncherAlias` activity-alias declared in `AndroidManifest.xml`. The actual `MainActivity` only carries `MAIN` (no `LAUNCHER`) and a separate `LEANBACK_LAUNCHER` filter for Android TV, so disabling the alias is enough to take PlainApp out of phone launchers.

Many OEM home apps (Samsung One UI, MIUI, ColorOS, FuntouchOS, EMUI, MagicUI, etc.) cache every icon in their app drawer. Even after the alias is disabled the cached "ghost" icon still appears, and tapping it opens system "App info" because Android no longer has a real launcher target. To force the cache to clear immediately, `LauncherIconHelper.setHidden()` now also calls `ActivityManager.killBackgroundProcesses()` on the resolved home-launcher package and on a list of well-known OEM launcher package names. This requires `KILL_BACKGROUND_PROCESSES` (a normal permission) in the manifest.

Files involved:
- `helpers/LauncherIconHelper.kt` — disables/enables the alias and kicks the launcher process to refresh.
- `AndroidManifest.xml` — `KILL_BACKGROUND_PROCESSES` permission.
- `res/values/strings_settings.xml` — updated `hide_launcher_icon_desc` warns about ghost icons on OEM launchers.

## Live camera & mic: client-side capture/recording

The Live camera and Live microphone screens (`plain-web/src/views/live-monitor/`) record entirely in the browser using the standard `MediaRecorder` API on top of the incoming WebRTC `MediaStream`. Nothing is uploaded to the phone — captures live in-memory as `Blob` URLs and the user downloads them locally.

- `plain-web/src/lib/media-recorder.ts` — shared helper. Exposes `StreamRecorder` (start / stop with format auto-detection: `video/webm vp9/vp8/opus`, `audio/webm opus`, MP4 fallback), `takePhoto(video)` (canvas → JPEG `Blob`), `downloadBlob`, `revokeCapture`, `formatDuration`, `timestampedFilename`.
- `LiveCameraView.vue` — header buttons: "Take photo", "Start recording" / "Stop recording" (red), plus existing camera-flip and stop-stream. Recording badge overlays the live video. Captures grid below the video shows photo thumbnails and inline `<video controls>` previews with download/delete per item.
- `LiveMicView.vue` — header buttons: "Start recording" / "Stop recording" plus existing mute and stop-stream. Recording line is shown inside the audio card. Recordings list below the card uses inline `<audio controls>` players with download/delete per item.
- `plain-web/src/locales/en-US/monitor.ts` — added strings `take_photo`, `start_recording`, `stop_recording`, `recording_now`, `recording_failed`, `capture_failed`, `captures_title`, `recordings_title`, `no_captures_yet`, `no_recordings_yet`, `download`, `delete`, `photo`, `video`, `audio`.

Captures are kept in `ref<CaptureItem[]>` on the page only — they are wiped on navigation away. If the live stream is torn down while a recording is in progress, the recorder is finalized first so the user keeps the file.

## Screen mirror: avoiding repeated consent popups

Android's `MediaProjectionManager.createScreenCaptureIntent()` consent dialog is enforced by the OS — it cannot be suppressed or "remembered" once granted. However, our app used to re-fire that intent every time the web UI sent `startScreenMirror`, which produced an unnecessary popup whenever the browser reconnected to an already-running mirror session.

Both layers now check for an existing running session before triggering the intent:

- `web/schemas/ScreenMirrorGraphQL.kt` — `startScreenMirror` mutation: if `ScreenMirrorService.instance?.isRunning() == true`, it just rebroadcasts `WebSocketEvent(SCREEN_MIRRORING)` so the (re)connecting browser begins WebRTC signalling against the existing projection. Otherwise it falls back to firing `StartScreenMirrorEvent` as before.
- `ui/MainActivityEvents.kt` — `StartScreenMirrorEvent` handler does the same defensive check before calling `screenCapture.launch(...)`.

Net effect: the OS popup now only appears the first time per active session — closing/reopening the browser tab, navigating away and back, or a second viewer joining will reuse the existing projection silently. The popup will appear again only after the user (or the system) explicitly stops the screen mirror service.

## Device Admin: PIN-protected deactivation

`PlainDeviceAdminReceiver.onDisableRequested()` is invoked by the system *before* the user can confirm the "Deactivate" dialog in Settings > Security > Device admin apps. We hook into it to launch a full-screen lock activity that requires the in-app PIN (and biometric if enabled) before allowing the deactivation to proceed.

- If the user enters the correct PIN: the unlock activity finishes; the user is back in Settings and may then confirm the system's "Deactivate" dialog.
- If the user cancels or presses Back: we send them to the home screen with `Intent.ACTION_MAIN` / `CATEGORY_HOME` so they leave the Security page entirely.
- If no PIN has ever been set in PlainApp's "App lock" page, we do not block (otherwise users could lock themselves out).

Files involved:
- `receivers/PlainDeviceAdminReceiver.kt` — overrides `onDisableRequested()`, launches `DeviceAdminUnlockActivity`, returns a warning string the system shows in its dialog.
- `ui/DeviceAdminUnlockActivity.kt` — Compose-based full-screen PIN/biometric lock. Reuses `AppLockPinPreference` and `AppLockHelper`.
- `AndroidManifest.xml` — registers `DeviceAdminUnlockActivity` with `singleTask`, `noHistory`, `excludeFromRecents`, `Theme.PlainActivity`.
- `res/values/strings_settings.xml` — `device_admin_disable_warning`, `device_admin_unlock_title`, `device_admin_unlock_subtitle`, `device_admin_unlock_biometric_subtitle`.

## Auto call recorder

PlainApp now records every active phone or VoIP call automatically and exposes the recordings to the web panel.

Android side:
- `helpers/CallRecorderHelper.kt` — singleton `MediaRecorder` wrapper. Source `MIC` (capturing the actual `VOICE_CALL` stream needs a system signature permission that user-installed apps cannot get; speakerphone is the documented workaround and the live-call page already prompts for it). Output: AAC inside MP4, mono 44.1 kHz, 96 kbps, written to `<app>/files/CallRecordings/<ts>_<source>_<name>.m4a` with a sidecar `.json` carrying display name, source, direction, app id/name, timestamps, duration and size.
- `services/LiveCallTracker.kt` — calls `CallRecorderHelper.onCallActive(...)` from the app-notification active branch, the phone OFFHOOK branch and `acceptFromPanel()`. `end()` calls `onCallEnded()` first so the recording is always finalised before the call state is cleared.
- `preferences/Preferences.kt` — `CallRecorderEnabledPreference` (default `true`) acts as the user kill-switch.
- `events/WebSocketEvents.kt` — new event ids `CALL_RECORDER_STATE(25)` and `CALL_RECORDINGS_CHANGED(26)`.
- `web/schemas/CallRecorderGraphQL.kt` — exposes `callRecorderState`, `callRecordings(offset, limit)`, `callRecordingsCount`, plus mutations `setCallRecorderEnabled`, `deleteCallRecording`, `deleteAllCallRecordings`. File downloads piggy-back on the existing `/fs?id=` route via `FileHelper.getFileId(absPath)`.
- Registered in `web/MainGraphQL.kt` via `addCallRecorderSchema()`.

Web side:
- `lib/api/query.ts` — `callRecorderStateGQL`, `callRecordingsGQL`.
- `lib/api/mutation.ts` — `setCallRecorderEnabledGQL`, `deleteCallRecordingGQL`, `deleteAllCallRecordingsGQL`.
- `hooks/app-socket.ts` — maps the new event ids to the `call_recorder_state` / `call_recordings_changed` bus events for live UI updates.
- `views/home/CallRecorderCard.vue` — home tile with on/off toggle, live "recording now" pill with elapsed timer, total count + size, three most recent recordings with inline `<audio>` players and a link to the full page.
- `views/call-recordings/CallRecordingsView.vue` — full page with status header, toggle, list of all recordings (player + download + delete) and a delete-all action.
- `plugins/router.ts` — `/call-recordings` route.
- `views/home/HomeView.vue` — new `FeatureCard` tile and the `CallRecorderCard` component.
- Locale strings added in `locales/en-US/common.ts` (`call_recorder*`, `call_recordings`, `recording_now_label`).

Build: `cd plain-web && corepack yarn build`, then `rm -rf app/src/main/resources/web/* && cp -r plain-web/dist/* app/src/main/resources/web/`. APK production happens via the GitHub Actions workflow added earlier.
