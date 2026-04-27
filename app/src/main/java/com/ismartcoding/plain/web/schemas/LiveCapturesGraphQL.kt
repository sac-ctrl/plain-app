package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.helpers.FileHelper
import com.ismartcoding.plain.helpers.LiveCaptureHelper
import kotlinx.serialization.Serializable

@Serializable
data class LiveCapture(
    val id: String,
    val filename: String,
    val source: String,
    val kind: String,
    val mimeType: String,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val fileId: String,
)

private fun LiveCaptureHelper.Meta.toModel(): LiveCapture {
    val absPath = LiveCaptureHelper.captureDir().absolutePath + "/" + filename
    return LiveCapture(
        id = id,
        filename = filename,
        source = source,
        kind = kind,
        mimeType = mimeType,
        createdAt = createdAt,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        fileId = FileHelper.getFileId(absPath),
    )
}

fun SchemaBuilder.addLiveCapturesSchema() {
    query("liveCaptures") {
        resolver { offset: Int, limit: Int, source: String? ->
            val all = LiveCaptureHelper.list(source)
            if (offset >= all.size) emptyList()
            else {
                val end = (offset + limit).coerceAtMost(all.size)
                all.subList(offset, end).map { it.toModel() }
            }
        }
    }
    query("liveCapturesCount") {
        resolver { source: String? -> LiveCaptureHelper.list(source).size }
    }
    query("liveCapturesTotalSize") {
        // Returned as a string so JS does not lose precision on > 2^53 bytes.
        resolver { source: String? ->
            LiveCaptureHelper.list(source).sumOf { it.sizeBytes }.toString()
        }
    }
    mutation("deleteLiveCapture") {
        resolver { filename: String -> LiveCaptureHelper.deleteByFilename(filename) }
    }
    mutation("deleteAllLiveCaptures") {
        resolver { source: String? -> LiveCaptureHelper.deleteAll(source) }
    }
}
