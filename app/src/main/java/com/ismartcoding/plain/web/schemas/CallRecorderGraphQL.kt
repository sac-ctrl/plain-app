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
}
