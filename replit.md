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
