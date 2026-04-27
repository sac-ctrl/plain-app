package com.ismartcoding.plain.web.models

/**
 * Defaults make the input tolerant: an empty title or content from the web
 * client will never trigger a "Failed to coerce as NoteInput" error.
 *
 * When isPrivate=true the server stores ONLY the opaque [encryptedBlob]
 * (AES-GCM ciphertext produced in the browser) and writes empty placeholders
 * to title/content. The Android side never holds the encryption key and
 * cannot read the contents.
 */
data class NoteInput(
    var title: String = "",
    var content: String = "",
    var isPrivate: Boolean = false,
    var encryptedBlob: String? = null,
)
