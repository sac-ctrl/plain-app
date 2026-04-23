# Always-On Services — Cloudflare Tunnel + Local Web Server

This document explains, end-to-end, how the Cloudflare tunnel **and** the local
web server are kept running on your phone even when:

- the app is swiped away from the recents list,
- the screen is off and the phone enters Doze,
- the OEM (Xiaomi / Realme / Oppo / Vivo / OnePlus / Samsung) tries to "clean"
  background apps,
- the phone is rebooted.

It also lists the (rare) situations where Android can still kill them and
exactly how the system brings them back automatically — without you opening
the app.

---

## 1. When can the services stop?

There are only four realistic cases where either service stops, and three of
the four are handled automatically:

| # | Situation | What happens | Auto-recovery? |
|---|-----------|--------------|----------------|
| 1 | You tap the **Stop** button (notification or in-app) | Service stops cleanly. | No — you asked for it. |
| 2 | You **swipe the app from recents** | Service receives `onTaskRemoved` and immediately re-launches itself as a foreground service. | **Yes — instant.** |
| 3 | The OS kills the process (low memory, OEM cleaner, Doze) | The 5-minute **AlarmManager watchdog** wakes up, notices the service is dead, and starts it again. | **Yes — within ≤5 min.** |
| 4 | You **reboot the phone** | `BootCompletedReceiver` fires the moment the system finishes booting and re-launches every service that was previously enabled. | **Yes — automatic, no need to open the app.** |

So the only way the tunnel or the local server stays off is if **you**
explicitly stopped it.

---

## 2. Will it start without me opening the app?

**Yes**, in every case below the service comes back **without** you launching
the app:

- **Phone reboot** → `BootCompletedReceiver` starts both services.
- **App swiped from recents** → `onTaskRemoved` re-arms the foreground service.
- **OS kills the service in the background** → `KeepAliveWatchdogReceiver`
  (an exact AlarmManager alarm) fires every 5 minutes, checks what should be
  running, and re-launches anything that has died.
- **App update / reinstall** → `BootCompletedReceiver` also listens for
  `MY_PACKAGE_REPLACED`, so the services come back as soon as the new APK is
  installed.

The only time you have to open the app is the very first time, to enable Web
Console / Cloudflare Tunnel and grant the one-time permissions.

---

## 3. What was changed in the codebase

Two services needed the same persistence treatment:

- `CloudflareTunnelService` — already done in the previous patch.
- `HttpServerService` — done in this patch, mirroring the tunnel pattern.

Below is a complete inventory of every file involved, grouped by concern.

### 3.1 Foreground service declarations (manifest)

`app/src/main/AndroidManifest.xml`

- `HttpServerService`
  - `android:foregroundServiceType="specialUse|dataSync"`
  - `android:stopWithTask="false"` ← **changed from `true`**
    Android will no longer terminate the service when the task is removed.
- `CloudflareTunnelService`
  - `android:foregroundServiceType="specialUse"`
  - `android:stopWithTask="false"`

Both services run as foreground services with a sticky notification, which is
what tells Android "this is user-visible work, don't kill it".

### 3.2 Self-restart on swipe-away

Both services override `onTaskRemoved` to fire a `startForegroundService`
intent at themselves instead of stopping:

- `app/src/main/java/com/ismartcoding/plain/services/CloudflareTunnelService.kt`
  → `onTaskRemoved` re-arms itself + schedules the watchdog.
- `app/src/main/java/com/ismartcoding/plain/services/HttpServerService.kt`
  → `onTaskRemoved` re-arms itself + schedules the watchdog (was previously
  calling `stopSelf()` — the root cause of the "connection lost" issue).

This is what makes the local web server keep serving even after you swipe the
app off the recents screen.

### 3.3 Sticky start mode

Both services return `START_STICKY` from `onStartCommand`, so if Android does
manage to kill the process for memory reasons, the system itself queues the
service for restart as soon as resources are available.

### 3.4 Wake / Wi-Fi locks (resist Doze)

While each service is running it holds:

- `PowerManager.PARTIAL_WAKE_LOCK` — keeps the CPU awake.
- `WifiManager.WIFI_MODE_FULL_HIGH_PERF` Wi-Fi lock — prevents the radio from
  going into low-power mode when the screen is off.

Files:
- `CloudflareTunnelService.kt` — acquires both locks in `onCreate`, releases
  them in `onDestroy`.
- `services/HttpServerLockManager.kt` — manages the same two locks for the
  HTTP server with an inactivity policy (released after 30 min of inactivity
  unless KeepAwake / USB / window focus dictates otherwise).

### 3.5 Watchdog (resurrects dead services)

`app/src/main/java/com/ismartcoding/plain/receivers/KeepAliveWatchdogReceiver.kt`

- Uses `AlarmManager.setExactAndAllowWhileIdle` so it fires even in Doze
  (falls back to `setAndAllowWhileIdle` when exact-alarm permission is not
  granted).
- Wakes up every **5 minutes**.
- On every fire it checks all three things and restarts anything that should
  be on but isn't:
  1. **Cloudflare tunnel** — if `CloudflareTunnelEnabledPreference` is on and
     `CloudflareTunnelService.isRunning()` is false, calls
     `CloudflareTunnelManager.start()`.
  2. **Local web server** — if `WebPreference` is on and
     `HttpServerService.isRunning()` is false, calls
     `startForegroundService(HttpServerService)`. *(Added in this patch.)*
  3. **Sink VPN** — if `KeepAliveVpnEnabledPreference` is on, restarts
     `KeepAliveVpnService`.
- Self-reschedules at the end of every fire so the chain never breaks.
- The alarm is also (re-)scheduled every time either service starts and every
  time `onTaskRemoved` runs — so even if you cleared task history, the alarm
  is back in the queue immediately.

### 3.6 Boot / package-replaced auto-start

`app/src/main/java/com/ismartcoding/plain/receivers/BootCompletedReceiver.kt`

- Triggers on `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, the Quickboot
  variants used by HTC/MIUI, and `MY_PACKAGE_REPLACED`.
- Re-arms automatically (no need to open the app):
  1. Cloudflare tunnel — if it was enabled and auto-start is on.
  2. **Local web server — if `WebPreference` was on.** *(Added in this patch.)*
  3. Sink VPN — if it was enabled.
  4. Watchdog alarm — if it was enabled.

The receiver runs even before the user unlocks the phone for the first time
(thanks to `LOCKED_BOOT_COMPLETED` + Direct Boot-aware datastore reads),
which is what gives you "boot the phone, the tunnel is already up" behaviour.

### 3.7 Ancillary helpers that keep the OS from killing the process

These are not new but they are part of why the whole thing works:

- `PlainAccessibilityService` — when enabled by the user, it raises the
  process priority class on aggressive OEMs (MIUI, ColorOS). This is the
  single biggest "background killer" bypass on Xiaomi/Realme/Oppo phones.
- `KeepAliveVpnService` — empty sink VPN. When the user turns on Android's
  system "Always-on VPN" for PlainApp, the OS treats the app as a critical
  service that **must** run, which protects every other foreground service in
  the same process.
- `PlainDeviceAdminReceiver` — Device Admin grant raises priority and blocks
  accidental uninstalls.
- Battery-optimization exemption — requested via
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; without this, Doze can suspend the
  alarms and locks.

### 3.8 Required permissions

Already declared in the manifest:

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `FOREGROUND_SERVICE_DATA_SYNC` — to run as foreground services.
- `WAKE_LOCK` — for the partial wake lock.
- `CHANGE_WIFI_STATE` — for the high-performance Wi-Fi lock.
- `RECEIVE_BOOT_COMPLETED` — to start automatically after reboot.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — for the 5-min watchdog.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — to ask Android to leave the app
  alone in Doze.
- `BIND_VPN_SERVICE` — for the sink VPN.

---

## 4. Worst-case timeline

| Event | Time until service is back |
|-------|----------------------------|
| You swipe the app away | < 1 second (instant restart from `onTaskRemoved`). |
| OS kills the process (low memory) | < 1 second if `START_STICKY` is honoured, otherwise ≤ 5 minutes via the watchdog. |
| Aggressive OEM cleaner kills it | ≤ 5 minutes via the watchdog. |
| Device reboot | A few seconds after Android finishes booting (via `BootCompletedReceiver`). |
| App update / reinstall | A few seconds after install completes (via `MY_PACKAGE_REPLACED`). |

So in the worst case the public site (`phone.shakti.buzz`) sees a < 5 minute
gap; in the common cases there is no gap at all.

---

## 5. What you (the user) only need to do once

These are the manual one-time steps Android does not let any app automate:

1. Disable battery optimization for PlainApp.
2. Enable the PlainApp Accessibility Service.
3. Enable the Keep-Alive VPN, then in Android **VPN settings** turn on
   "Always-on VPN" + "Block connections without VPN" for PlainApp.
4. Grant Device Admin.
5. On Xiaomi / Realme / Oppo / Vivo / OnePlus / Samsung: the brand-specific
   steps shown on the in-app **Always-On Mode** screen (autostart,
   lock-card-in-recents, "Unrestricted" battery, etc.).
6. Make sure the Cloudflare Tunnel page shows **Connected** and the Web
   Console toggle is **On**.

After that, the system runs forever on its own — including across reboots —
unless you press **Stop** yourself.
