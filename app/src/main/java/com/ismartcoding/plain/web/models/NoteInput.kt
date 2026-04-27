package com.ismartcoding.plain.web.models

/**
 * Defaults make the input tolerant: an empty title or content from the web
 * client will never trigger a "Failed to coerce as NoteInput" error.
 */
data class NoteInput(
    var title: String = "",
    var content: String = "",
)
