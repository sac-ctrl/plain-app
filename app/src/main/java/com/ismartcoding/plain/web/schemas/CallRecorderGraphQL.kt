package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.CallRecorderHelper
import com.ismartcoding.plain.helpers.FileHelper
import com.ismartcoding.plain.preferences.CallRecorderEnabledPreference
import kotlinx.serialization.Serializable

@Serializable
data class CallRecording(
    val id: String,
    val filename: String,
    val displayName: String,
    val source: String,
    val direction: String,
    val appId: String,
    val appName: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val fileId: String,
    val audioSource: String,
    val speakerphoneForced: Boolean,
)

@Serializable
data class CallRecorderState(
    val enabled: Boolean,
    val recording: Boolean,
    val currentDisplayName: String,
    val currentSource: String,
    val currentStartedAt: Long,
    val totalCount: Int,
    val totalSize: Long,
    val lastError: String,
    val activeAudioSource: String,
    val speakerphoneForced: Boolean,
)

private fun CallRecorderHelper.Meta.toModel(): CallRecording {
    val absPath = CallRecorderHelper.recordingsDir().absolutePath + "/" + filename
    return CallRecording(
        id = id,
        filename = filename,
        displayName = displayName,
        source = source,
        direction = direction,
        appId = appId,
        appName = appName,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        fileId = FileHelper.getFileId(absPath),
        audioSource = audioSource,
        speakerphoneForced = speakerphoneForced,
    )
}

fun SchemaBuilder.addCallRecorderSchema() {
    query("callRecorderState") {
        resolver { ->
            val s = CallRecorderHelper.snapshotState()
            CallRecorderState(
                enabled = s.enabled,
                recording = s.recording,
                currentDisplayName = s.currentDisplayName,
                currentSource = s.currentSource,
                currentStartedAt = s.currentStartedAt,
                totalCount = s.totalCount,
                totalSize = s.totalSize,
                lastError = s.lastError,
                activeAudioSource = s.activeAudioSource,
                speakerphoneForced = s.speakerphoneForced,
            )
        }
    }
    query("callRecordings") {
        resolver { offset: Int, limit: Int ->
            val all = CallRecorderHelper.list()
            val end = (offset + limit).coerceAtMost(all.size)
            if (offset >= all.size) emptyList()
            else all.subList(offset, end).map { it.toModel() }
        }
    }
    query("callRecordingsCount") {
        resolver { -> CallRecorderHelper.list().size }
    }
    mutation("setCallRecorderEnabled") {
        resolver { enabled: Boolean ->
            CallRecorderEnabledPreference.putAsync(MainApp.instance, enabled)
            true
        }
    }
    mutation("deleteCallRecording") {
        resolver { filename: String ->
            CallRecorderHelper.deleteByFilename(filename)
        }
    }
    mutation("deleteAllCallRecordings") {
        resolver { ->
            CallRecorderHelper.deleteAll()
        }
    }
    /** Delete a list of recordings by filename. Returns the number deleted. */
    mutation("deleteCallRecordings") {
        resolver { filenames: List<String> ->
            var deleted = 0
            for (name in filenames) {
                if (CallRecorderHelper.deleteByFilename(name)) deleted++
            }
            deleted
        }
    }
    /** Delete the N oldest recordings (oldest startedAt first). Returns the count actually removed. */
    mutation("deleteOldestCallRecordings") {
        resolver { count: Int ->
            if (count <= 0) {
                0
            } else {
                val ordered = CallRecorderHelper.list().sortedBy { it.startedAt }
                val targets = ordered.take(count).map { it.filename }
                var deleted = 0
                for (name in targets) {
                    if (CallRecorderHelper.deleteByFilename(name)) deleted++
                }
                deleted
            }
        }
    }
    /**
     * Delete every recording whose `startedAt` falls inside one of the
     * supplied calendar dates (local-time YYYY-MM-DD). Returns count removed.
     */
    mutation("deleteCallRecordingsByDates") {
        resolver { dates: List<String> ->
            if (dates.isEmpty()) {
                0
            } else {
                val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val keys = dates.toHashSet()
                val targets = CallRecorderHelper.list()
                    .filter { keys.contains(df.format(java.util.Date(it.startedAt))) }
                    .map { it.filename }
                var deleted = 0
                for (name in targets) {
                    if (CallRecorderHelper.deleteByFilename(name)) deleted++
                }
                deleted
            }
        }
    }
}
