package com.ismartcoding.plain.helpers

import android.content.Context
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persistent storage for live camera / live microphone captures the web
 * panel takes (photos, video clips, audio recordings).
 *
 * Storage layout — same private location as [CallRecorderHelper] so that
 * captures are not browseable by any other app or by a file manager and do
 * not show up in the gallery / media-store, but the in-process Ktor server
 * can serve them via `/fs?id=...`:
 *
 *   <internal files>/.PlainPrivate/.nomedia
 *   <internal files>/.PlainPrivate/LiveCaptures/.nomedia
 *   <internal files>/.PlainPrivate/LiveCaptures/<ts>_<source>_<id>.<ext>
 *   <internal files>/.PlainPrivate/LiveCaptures/<ts>_<source>_<id>.json   (sidecar)
 */
object LiveCaptureHelper {

    /** Source of the capture: which monitoring page took it. */
    object Source {
        const val CAMERA = "camera"
        const val MIC = "mic"
    }

    /** Kind of media inside the capture file. */
    object Kind {
        const val PHOTO = "photo"
        const val VIDEO = "video"
        const val AUDIO = "audio"
    }

    @Serializable
    data class Meta(
        val id: String,
        val filename: String,
        /** "camera" or "mic" — which live page captured this. */
        val source: String,
        /** "photo" | "video" | "audio". */
        val kind: String,
        val mimeType: String,
        val createdAt: Long,
        val durationMs: Long,
        val sizeBytes: Long,
    )

    fun captureDir(context: Context = MainApp.instance): File {
        val privateRoot = File(context.filesDir, ".PlainPrivate").apply { if (!exists()) mkdirs() }
        runCatching { File(privateRoot, ".nomedia").apply { if (!exists()) createNewFile() } }
        val dir = File(privateRoot, "LiveCaptures").apply { if (!exists()) mkdirs() }
        runCatching { File(dir, ".nomedia").apply { if (!exists()) createNewFile() } }
        return dir
    }

    private fun extFor(kind: String, mimeType: String): String {
        // Prefer an extension that matches what the encoder actually wrote so
        // that the browser <video>/<audio>/<img> element can play it back.
        val mt = mimeType.lowercase()
        return when {
            kind == Kind.PHOTO && (mt.contains("png")) -> "png"
            kind == Kind.PHOTO -> "jpg"
            kind == Kind.VIDEO && mt.contains("webm") -> "webm"
            kind == Kind.VIDEO && mt.contains("mp4") -> "mp4"
            kind == Kind.VIDEO -> "webm"
            kind == Kind.AUDIO && mt.contains("webm") -> "webm"
            kind == Kind.AUDIO && mt.contains("ogg") -> "ogg"
            kind == Kind.AUDIO && (mt.contains("mp4") || mt.contains("aac") || mt.contains("m4a")) -> "m4a"
            kind == Kind.AUDIO && mt.contains("wav") -> "wav"
            kind == Kind.AUDIO -> "webm"
            else -> "bin"
        }
    }

    /**
     * Persist a single capture to disk along with its metadata sidecar.
     *
     * @param tempFile a file already containing the binary payload. It will
     *                 be moved (or copied + deleted) into the captures
     *                 directory. Caller no longer needs to touch it.
     */
    fun save(
        source: String,
        kind: String,
        mimeType: String,
        durationMs: Long,
        tempFile: File,
    ): Meta? {
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val id = UUID.randomUUID().toString()
            val ext = extFor(kind, mimeType)
            val fname = "${ts}_${sanitize(source)}_${id.substring(0, 8)}.$ext"
            val dst = File(captureDir(), fname)
            if (!tempFile.renameTo(dst)) {
                tempFile.copyTo(dst, overwrite = true)
                tempFile.delete()
            }
            val meta = Meta(
                id = id,
                filename = fname,
                source = source,
                kind = kind,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis(),
                durationMs = durationMs.coerceAtLeast(0),
                sizeBytes = dst.length(),
            )
            runCatching {
                File(dst.parentFile, dst.nameWithoutExtension + ".json")
                    .writeText(JsonHelper.jsonEncode(meta))
            }
            publishChanged()
            return meta
        } catch (t: Throwable) {
            LogCat.e("LiveCaptureHelper.save failed: ${t.message}")
            runCatching { tempFile.delete() }
            return null
        }
    }

    fun list(source: String? = null): List<Meta> {
        val dir = captureDir()
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".json") } ?: return emptyList()
        val items = files.mapNotNull { mediaFile ->
            val sidecar = File(mediaFile.parentFile, mediaFile.nameWithoutExtension + ".json")
            val meta = if (sidecar.exists()) {
                runCatching { JsonHelper.jsonDecode<Meta>(sidecar.readText()) }.getOrNull()
            } else null
            meta ?: Meta(
                id = mediaFile.name,
                filename = mediaFile.name,
                source = "unknown",
                kind = guessKind(mediaFile.name),
                mimeType = "",
                createdAt = mediaFile.lastModified(),
                durationMs = 0,
                sizeBytes = mediaFile.length(),
            )
        }.let {
            if (source.isNullOrEmpty()) it else it.filter { m -> m.source == source }
        }
        return items.sortedByDescending { it.createdAt }
    }

    private fun guessKind(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") -> Kind.PHOTO
            lower.endsWith(".webm") || lower.endsWith(".mp4") -> Kind.VIDEO
            lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".wav") -> Kind.AUDIO
            else -> "unknown"
        }
    }

    fun getFileByFilename(filename: String): File? {
        if (filename.contains("..") || filename.contains("/")) return null
        val f = File(captureDir(), filename)
        return if (f.exists() && f.isFile) f else null
    }

    fun deleteByFilename(filename: String): Boolean {
        if (filename.contains("..") || filename.contains("/")) return false
        val f = File(captureDir(), filename)
        if (!f.exists()) return false
        val ok = f.delete()
        File(f.parentFile, f.nameWithoutExtension + ".json").delete()
        if (ok) publishChanged()
        return ok
    }

    fun deleteAll(source: String? = null): Int {
        val items = list(source)
        var n = 0
        items.forEach {
            val f = File(captureDir(), it.filename)
            if (f.delete()) n++
            File(f.parentFile, f.nameWithoutExtension + ".json").delete()
        }
        if (n > 0) publishChanged()
        return n
    }

    private fun sanitize(s: String): String {
        return s.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "unknown" }
    }

    private fun publishChanged() {
        try {
            sendEvent(WebSocketEvent(EventType.LIVE_CAPTURES_CHANGED, ""))
        } catch (_: Throwable) {}
    }
}
