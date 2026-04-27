package com.ismartcoding.plain.web.models

import com.ismartcoding.plain.db.DNote
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class Note(
    val id: ID,
    val title: String,
    val content: String,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isPrivate: Boolean,
    val encryptedBlob: String?,
)

fun DNote.toModel(): Note {
    return Note(ID(id), title, content, deletedAt, createdAt, updatedAt, isPrivate, encryptedBlob)
}


@Serializable
data class ExportNote(
    val id: ID,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tags: List<Tag>,
    val isPrivate: Boolean = false,
    val encryptedBlob: String? = null,
)

fun DNote.toExportModel(tags: List<Tag>): ExportNote {
    return ExportNote(ID(id), title, content, createdAt, updatedAt, tags, isPrivate, encryptedBlob)
}
