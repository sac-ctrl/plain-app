package com.ismartcoding.plain.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp

class LocateRingService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousVolume: Int = -1
    private var audioManager: AudioManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startRinging()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Locate Phone", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
        val stopIntent = Intent(this, LocateRingService::class.java).apply { action = ACTION_STOP }
        val stopPi = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PlainApp")
            .setContentText("Locating phone — tap Stop to silence")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .addAction(0, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            startForeground(NOTIF_ID, notif)
        } catch (e: Exception) {
            LogCat.e("LocateRingService startForeground failed: ${e.message}", e)
        }
    }

    private fun startRinging() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "PlainApp:LocateRing"
            ).apply { acquire(60_000L) }

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.let {
                previousVolume = it.getStreamVolume(AudioManager.STREAM_ALARM)
                val max = it.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                it.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }

            val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@LocateRingService, uri)
                isLooping = true
                prepare()
                start()
            }
            LogCat.d("LocateRingService: ringing started")
        } catch (e: Exception) {
            LogCat.e("LocateRingService startRinging failed: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            if (previousVolume >= 0) {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            }
        } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        running = false
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "locate_ring"
        const val NOTIF_ID = 7711
        const val ACTION_STOP = "com.ismartcoding.plain.LOCATE_STOP"

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context = MainApp.instance) {
            val intent = Intent(context, LocateRingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context = MainApp.instance) {
            val intent = Intent(context, LocateRingService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
