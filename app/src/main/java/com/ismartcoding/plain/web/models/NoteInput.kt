package com.ismartcoding.plain.web.models

import kotlinx.serialization.Serializable

/**
 * @Serializable is REQUIRED — KGraphQL deserializes input variables via
 * kotlinx.serialization (`Json.decodeFromJsonElement(serializer(kType), ...)`)
 * which throws "Failed to coerce as NoteInput" if the class lacks a serializer.
 *
 * Defaults make the input tolerant: an empty title or content from the web
 * client will never trigger a coercion error.
 *
 * When isPrivate=true the server stores ONLY the opaque [encryptedBlob]
 * (AES-GCM ciphertext produced in the browser) and writes empty placeholders
 * to title/content. The Android side never holds the encryption key and
 * cannot read the contents.
 */
@Serializable
data class NoteInput(
    var title: String = "",
    var content: String = "",
    var isPrivate: Boolean = false,
    var encryptedBlob: String? = null,
)
