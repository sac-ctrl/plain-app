package com.ismartcoding.plain.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp

/**
 * Shows a system overlay message on top of any app.
 * Requires SYSTEM_ALERT_WINDOW (Settings.canDrawOverlays).
 */
class MessageOverlayService : Service() {
    private var view: View? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "PlainApp"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: ""
        val durationMs = intent?.getLongExtra(EXTRA_DURATION, 5000L) ?: 5000L
        val blocking = intent?.getBooleanExtra(EXTRA_BLOCKING, false) ?: false

        showOverlay(title, message, blocking)
        if (!blocking) {
            handler.postDelayed({ removeOverlay(); stopSelf() }, durationMs)
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(title: String, message: String, blocking: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            LogCat.e("MessageOverlay: SYSTEM_ALERT_WINDOW not granted")
            stopSelf()
            return
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        removeOverlay()

        val ctx = this
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F2202124"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }
        val titleView = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val msgView = TextView(ctx).apply {
            text = message
            setTextColor(Color.parseColor("#EEFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, 0)
        }
        container.addView(titleView)
        container.addView(msgView)

        if (blocking) {
            val dismiss = TextView(ctx).apply {
                text = "Dismiss"
                setTextColor(Color.parseColor("#FF8AB4F8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(12), 0, 0)
                gravity = Gravity.END
                setOnClickListener { removeOverlay(); stopSelf() }
            }
            container.addView(dismiss)
        }

        val frame = FrameLayout(ctx).apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    if (blocking) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(60)
        }
        try {
            wm?.addView(frame, params)
            view = frame
        } catch (e: Exception) {
            LogCat.e("MessageOverlay addView failed: ${e.message}", e)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        view?.let { v ->
            try { wm?.removeView(v) } catch (_: Exception) {}
        }
        view = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_BLOCKING = "blocking"

        fun show(title: String, message: String, durationMs: Long = 5000L, blocking: Boolean = false, context: Context = MainApp.instance) {
            val i = Intent(context, MessageOverlayService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_DURATION, durationMs)
                putExtra(EXTRA_BLOCKING, blocking)
            }
            context.startService(i)
        }
    }
}
