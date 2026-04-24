package com.ismartcoding.plain.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.HandlerThread
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ismartcoding.lib.isSPlus
import com.ismartcoding.lib.logcat.LogCat
import android.os.Handler
import android.os.Looper
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.ScreenMirrorControlInput
import com.ismartcoding.plain.enums.ScreenMirrorControlAction
import com.ismartcoding.plain.features.PackageHelper

/**
 * Accessibility Service for injecting touch/gesture events during screen mirror remote control.
 *
 * This service uses AccessibilityService.dispatchGesture() to inject:
 * - Tap, long press, swipe (via gesture paths)
 * - Back, Home, Recents (via performGlobalAction)
 *
 * The user must manually enable this service in Android Settings > Accessibility.
 */
class PlainAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogCat.d("PlainAccessibilityService connected")
        ensureWorker()
        startEnforcementLoop()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Dedicated worker thread for ALL disk-touching work (SharedPreferences
    // read/write, PackageManager queries, JSON parsing). The accessibility
    // event callback runs on the main thread and Android marks the service
    // as "malfunctioning" if it doesn't return quickly. We must never do
    // blocking I/O from the callback itself.
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private fun ensureWorker() {
        if (workerHandler != null) return
        val t = HandlerThread("PlainAccessWorker", android.os.Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        workerThread = t
        workerHandler = Handler(t.looper)
    }

    private fun postWork(r: Runnable) {
        ensureWorker()
        workerHandler?.post {
            try { r.run() } catch (t: Throwable) {
                // Swallow everything — an uncaught exception here would
                // propagate up and crash the accessibility worker, which
                // is exactly what causes the "malfunctioning" status.
                LogCat.e("PlainAccessibility worker error: ${t.message}")
            }
        }
    }

    private val enforcementRunnable = object : Runnable {
        override fun run() {
            // The runnable itself runs on the main handler so we can call
            // performGlobalAction. Push all I/O onto the worker.
            postWork(Runnable {
                try {
                    val pkg = currentForegroundPackage ?: return@Runnable
                    val enteredAt = currentForegroundEnteredAt
                    if (enteredAt <= 0) return@Runnable
                    val now = System.currentTimeMillis()
                    val delta = now - enteredAt
                    if (delta in 100..6 * 60 * 60 * 1000L) {
                        AppBlockHelper.addUsage(pkg, delta)
                        currentForegroundEnteredAt = now
                    }
                    val reason = AppBlockHelper.blockReason(pkg)
                    if (reason != null) {
                        mainHandler.post {
                            try {
                                MessageOverlayService.show(
                                    title = if (reason == "time_limit") "Daily limit reached" else "App blocked",
                                    message = "$pkg has been blocked.",
                                    durationMs = 3500L,
                                )
                            } catch (_: Exception) {}
                            try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            })
            mainHandler.postDelayed(this, 5000L)
        }
    }
    private fun startEnforcementLoop() {
        mainHandler.removeCallbacks(enforcementRunnable)
        mainHandler.postDelayed(enforcementRunnable, 5000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // CRITICAL: this runs on the main thread. Do nothing here that
        // touches disk, SharedPreferences, PackageManager, or anything
        // that could ever block. Snapshot the few primitives we need and
        // hand off to the worker thread immediately.
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return
        if (pkg == "com.android.systemui" || pkg.startsWith("android")) return

        val now = System.currentTimeMillis()
        val prev = currentForegroundPackage
        val prevEnteredAt = currentForegroundEnteredAt
        currentForegroundPackage = pkg
        currentForegroundEnteredAt = now

        postWork(Runnable {
            // Track usage time for the previously-foreground app so daily
            // time limits work.
            if (prev != null && prev != pkg && prevEnteredAt > 0L) {
                val delta = now - prevEnteredAt
                if (delta in 100..6 * 60 * 60 * 1000L) {
                    try { AppBlockHelper.addUsage(prev, delta) } catch (_: Throwable) {}
                }
            }

            try { AppBlockHelper.recordLaunch(pkg) } catch (_: Throwable) {}
            try {
                val label = PackageHelper.getLabel(pkg).ifEmpty { pkg }
                TimelineHelper.add("launch", "Opened $label", "", pkg, label, now)
            } catch (_: Throwable) {}

            val reason = try { AppBlockHelper.blockReason(pkg) } catch (_: Throwable) { null }
            if (reason != null) {
                LogCat.d("PlainAccessibilityService: blocking $pkg ($reason)")
                mainHandler.post {
                    try {
                        val title = when (reason) {
                            "time_limit" -> "Daily limit reached"
                            "bedtime" -> "Bedtime mode"
                            else -> "App is blocked"
                        }
                        val message = when (reason) {
                            "time_limit" -> "You have used $pkg longer than the allowed daily time."
                            "bedtime" -> "$pkg is unavailable during bedtime hours."
                            else -> "$pkg has been blocked from this device."
                        }
                        MessageOverlayService.show(title, message, durationMs = 4000L)
                    } catch (_: Exception) {}
                    // Must run on main thread — accessibility actions are not
                    // allowed from background threads.
                    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
                }
            }
        })
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mainHandler.removeCallbacks(enforcementRunnable)
        try { workerThread?.quitSafely() } catch (_: Throwable) {}
        workerThread = null
        workerHandler = null
        LogCat.d("PlainAccessibilityService destroyed")
    }

    /**
     * Dispatch a screen mirror control event.
     * @param control The control event with normalized coordinates [0, 1]
     * @param screenWidth The actual screen width in pixels
     * @param screenHeight The actual screen height in pixels
     */
    fun dispatchControl(control: ScreenMirrorControlInput, screenWidth: Int, screenHeight: Int) {
        when (control.action) {
            ScreenMirrorControlAction.TAP -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                dispatchTap(x, y)
            }

            ScreenMirrorControlAction.LONG_PRESS -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                val duration = control.duration ?: 500L
                dispatchLongPress(x, y, duration)
            }

            ScreenMirrorControlAction.SWIPE -> {
                val startX = (control.x ?: return) * screenWidth
                val startY = (control.y ?: return) * screenHeight
                val endX = (control.endX ?: return) * screenWidth
                val endY = (control.endY ?: return) * screenHeight
                val duration = control.duration ?: 300L
                dispatchSwipe(startX, startY, endX, endY, duration)
            }

            ScreenMirrorControlAction.SCROLL -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                val deltaY = control.deltaY ?: 0f
                // Convert scroll delta to a swipe: positive deltaY = scroll down = swipe up
                val scrollDistance = deltaY.coerceIn(-500f, 500f)
                dispatchSwipe(x, y, x, y + scrollDistance, 200L)
            }

            ScreenMirrorControlAction.BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            ScreenMirrorControlAction.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            ScreenMirrorControlAction.RECENTS -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }

            ScreenMirrorControlAction.LOCK_SCREEN -> {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }

            ScreenMirrorControlAction.KEY -> {
                // Key injection requires InputManager or root; skip for now
                LogCat.d("Key action not yet supported: ${control.key}")
            }
        }
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchLongPress(x: Float, y: Float, duration: Long) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(500))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(50))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: PlainAccessibilityService? = null

        @Volatile var currentForegroundPackage: String? = null
        @Volatile var currentForegroundEnteredAt: Long = 0L

        /**
         * Check if the accessibility service is currently enabled.
         */
        fun isEnabled(context: Context = MainApp.instance): Boolean {
            return instance != null
        }

        /**
         * Cached screen size to avoid repeated DisplayMetrics lookups on every control event.
         * Invalidated on configuration changes (rotation, display changes).
         */
        @Volatile
        private var cachedScreenSize: Point? = null

        /**
         * Get the real physical screen size for coordinate mapping (cached).
         * Uses real display size to include navigation bar and status bar areas,
         * which are visible in the screen mirror video captured by MediaProjection.
         */
        fun getScreenSize(context: Context): Point {
            return cachedScreenSize ?: run {
                val size = getRealScreenSize(context)
                cachedScreenSize = size
                size
            }
        }

        /**
         * Get the real physical screen dimensions including system bars (nav bar, status bar).
         * On Android <= 11, displayMetrics.widthPixels/heightPixels may exclude the navigation
         * bar, causing coordinate mapping errors for screen mirror touch control.
         */
        private fun getRealScreenSize(context: Context): Point {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (isSPlus()) {
                val bounds = wm.currentWindowMetrics.bounds
                Point(bounds.width(), bounds.height())
            } else {
                val size = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(size)
                size
            }
        }

        /**
         * Invalidate the cached screen size. Call on configuration changes.
         */
        fun invalidateScreenSizeCache() {
            cachedScreenSize = null
        }

        /**
         * Open the system Accessibility Settings page so the user can enable this service.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
