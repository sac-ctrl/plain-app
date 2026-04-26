package com.ismartcoding.plain.helpers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import com.ismartcoding.plain.preferences.CallRecorderEnabledPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Tracks every phone / VoIP call and records the microphone for the entire
 * duration into the app's external-files dir (`<app>/files/CallRecordings/`).
 *
 * - Source: [MediaRecorder.AudioSource.MIC]. Capturing the actual VOICE_CALL
 *   stream requires a system signature permission on modern Android, so we
 *   capture the mic instead. To pick up the remote party as well, the user
 *   should turn on speakerphone for the call (the same hint the live-listen
 *   page already shows).
 * - Format: AAC inside MP4 container (`.m4a`), 96 kbps mono 44.1 kHz.
 * - Each recording has a sidecar `.json` with metadata.
 */
object CallRecorderHelper {

    @Serializable
    data class Meta(
        val id: String,
        val filename: String,
        val displayName: String,
        val source: String,        // phone | whatsapp | telegram | …
        val direction: String,     // incoming | outgoing
        val appId: String,
        val appName: String,
        val startedAt: Long,
        val endedAt: Long,
        val durationMs: Long,
        val sizeBytes: Long,
    )

    @Serializable
    data class State(
        val enabled: Boolean,
        val recording: Boolean,
        val currentDisplayName: String,
        val currentSource: String,
        val currentStartedAt: Long,
        val totalCount: Int,
        val totalSize: Long,
        val lastError: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var currentFile: File? = null
    @Volatile private var currentDisplayName: String = ""
    @Volatile private var currentSource: String = ""
    @Volatile private var currentDirection: String = ""
    @Volatile private var currentAppId: String = ""
    @Volatile private var currentAppName: String = ""
    @Volatile private var startedAt: Long = 0
    @Volatile private var lastError: String = ""

    fun isRecording(): Boolean = recorder != null

    /**
     * Storage is the app's *internal* `filesDir` (i.e. `/data/data/<pkg>/files/...`),
     * which is **not browsable by any file manager, gallery, or other app**
     * without root — only the running PlainApp process can read it. The
     * additional `.PlainPrivate` parent + `.nomedia` marker make sure media
     * scanners and shell `ls` ignore it as well.
     *
     * The web panel still has full access because the Ktor server runs inside
     * this same app process and reads the file by absolute path.
     */
    fun recordingsDir(context: Context = MainApp.instance): File {
        val privateRoot = File(context.filesDir, ".PlainPrivate").apply { if (!exists()) mkdirs() }
        runCatching { File(privateRoot, ".nomedia").apply { if (!exists()) createNewFile() } }
        val dir = File(privateRoot, "CallRecordings").apply { if (!exists()) mkdirs() }
        runCatching { File(dir, ".nomedia").apply { if (!exists()) createNewFile() } }
        migrateLegacyRecordingsIfNeeded(context, dir)
        return dir
    }

    /**
     * Older builds stored recordings under `getExternalFilesDir(null)/CallRecordings`,
     * which was visible in Android's per-app folder. Move any leftovers into
     * the new private location on first access.
     */
    @Volatile private var legacyMigrated = false
    private fun migrateLegacyRecordingsIfNeeded(context: Context, target: File) {
        if (legacyMigrated) return
        legacyMigrated = true
        runCatching {
            val legacyBase = context.getExternalFilesDir(null) ?: return@runCatching
            val legacyDir = File(legacyBase, "CallRecordings")
            if (!legacyDir.exists() || !legacyDir.isDirectory) return@runCatching
            legacyDir.listFiles()?.forEach { src ->
                val dst = File(target, src.name)
                if (!dst.exists()) {
                    runCatching { src.copyTo(dst, overwrite = false) }
                }
                runCatching { src.delete() }
            }
            runCatching { legacyDir.delete() }
        }
    }

    /** Called by [com.ismartcoding.plain.services.LiveCallTracker] when a call becomes active. */
    fun onCallActive(
        displayName: String,
        source: String,
        direction: String,
        appId: String,
        appName: String,
    ) {
        scope.launch {
            try {
                val enabled = CallRecorderEnabledPreference.getAsync(MainApp.instance)
                if (!enabled) return@launch
                startRecording(displayName, source, direction, appId, appName)
            } catch (t: Throwable) {
                LogCat.e("CallRecorder onCallActive failed: ${t.message}")
                lastError = t.message ?: t.javaClass.simpleName
                publishState()
            }
        }
    }

    /** Called by LiveCallTracker when a call ends. */
    fun onCallEnded() {
        scope.launch { stopRecording() }
    }

    private fun sanitize(s: String): String {
        return s.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "Unknown" }
    }

    @Synchronized
    private fun startRecording(
        displayName: String,
        source: String,
        direction: String,
        appId: String,
        appName: String,
    ) {
        synchronized(lock) {
            if (recorder != null) return
            val ctx = MainApp.instance
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = sanitize(displayName.ifEmpty { source })
            val file = File(recordingsDir(ctx), "${ts}_${sanitize(source)}_${safeName}.m4a")

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(ctx)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            try {
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioChannels(1)
                rec.setAudioSamplingRate(44100)
                rec.setAudioEncodingBitRate(96_000)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
            } catch (t: Throwable) {
                LogCat.e("CallRecorder start failed: ${t.message}")
                runCatching { rec.release() }
                lastError = t.message ?: t.javaClass.simpleName
                publishState()
                return
            }
            recorder = rec
            currentFile = file
            currentDisplayName = displayName
            currentSource = source
            currentDirection = direction
            currentAppId = appId
            currentAppName = appName
            startedAt = System.currentTimeMillis()
            lastError = ""
            LogCat.d("CallRecorder started → ${file.absolutePath}")
        }
        publishState()
    }

    @Synchronized
    private fun stopRecording() {
        var savedFile: File? = null
        var meta: Meta? = null
        synchronized(lock) {
            val rec = recorder ?: return
            val file = currentFile
            val started = startedAt
            recorder = null
            currentFile = null
            startedAt = 0
            try {
                rec.stop()
            } catch (t: Throwable) {
                LogCat.e("CallRecorder stop failed: ${t.message}")
            } finally {
                runCatching { rec.release() }
            }
            if (file != null && file.exists() && file.length() > 1024) {
                val ended = System.currentTimeMillis()
                val m = Meta(
                    id = UUID.randomUUID().toString(),
                    filename = file.name,
                    displayName = currentDisplayName,
                    source = currentSource,
                    direction = currentDirection,
                    appId = currentAppId,
                    appName = currentAppName,
                    startedAt = started,
                    endedAt = ended,
                    durationMs = (ended - started).coerceAtLeast(0),
                    sizeBytes = file.length(),
                )
                runCatching {
                    File(file.parentFile, file.nameWithoutExtension + ".json")
                        .writeText(JsonHelper.jsonEncode(m))
                }
                savedFile = file
                meta = m
            } else {
                // Recording was too short / never wrote — drop the file.
                file?.delete()
            }
            currentDisplayName = ""
            currentSource = ""
            currentDirection = ""
            currentAppId = ""
            currentAppName = ""
        }
        if (savedFile != null) {
            LogCat.d("CallRecorder finished → ${savedFile?.absolutePath} (${meta?.durationMs}ms)")
            publishRecordingsChanged()
        }
        publishState()
    }

    /** Returns all stored recordings, newest first. */
    fun list(): List<Meta> {
        val dir = recordingsDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".m4a") } ?: return emptyList()
        return files.mapNotNull { audioFile ->
            val sidecar = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".json")
            val meta = if (sidecar.exists()) {
                runCatching { JsonHelper.jsonDecode<Meta>(sidecar.readText()) }.getOrNull()
            } else null
            meta ?: Meta(
                id = audioFile.name,
                filename = audioFile.name,
                displayName = audioFile.nameWithoutExtension,
                source = "unknown",
                direction = "unknown",
                appId = "",
                appName = "",
                startedAt = audioFile.lastModified(),
                endedAt = audioFile.lastModified(),
                durationMs = 0,
                sizeBytes = audioFile.length(),
            )
        }.sortedByDescending { it.startedAt }
    }

    fun getFileByFilename(filename: String): File? {
        if (filename.contains("..") || filename.contains("/")) return null
        val f = File(recordingsDir(), filename)
        return if (f.exists() && f.isFile) f else null
    }

    fun deleteByFilename(filename: String): Boolean {
        if (filename.contains("..") || filename.contains("/")) return false
        val f = File(recordingsDir(), filename)
        if (!f.exists()) return false
        val ok = f.delete()
        File(f.parentFile, f.nameWithoutExtension + ".json").delete()
        if (ok) publishRecordingsChanged()
        return ok
    }

    fun deleteAll(): Int {
        val dir = recordingsDir()
        val files = dir.listFiles() ?: return 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        if (n > 0) publishRecordingsChanged()
        return n
    }

    fun snapshotState(): State {
        val items = list()
        val enabled = runCatching {
            kotlinx.coroutines.runBlocking { CallRecorderEnabledPreference.getAsync(MainApp.instance) }
        }.getOrDefault(true)
        return State(
            enabled = enabled,
            recording = recorder != null,
            currentDisplayName = currentDisplayName,
            currentSource = currentSource,
            currentStartedAt = startedAt,
            totalCount = items.size,
            totalSize = items.sumOf { it.sizeBytes },
            lastError = lastError,
        )
    }

    private fun publishState() {
        try {
            sendEvent(WebSocketEvent(EventType.CALL_RECORDER_STATE, JsonHelper.jsonEncode(snapshotState())))
        } catch (_: Throwable) {}
    }

    private fun publishRecordingsChanged() {
        try {
            sendEvent(WebSocketEvent(EventType.CALL_RECORDINGS_CHANGED, ""))
        } catch (_: Throwable) {}
    }
}
