package com.ismartcoding.plain.web.models

import kotlinx.serialization.Serializable

@Serializable
data class ContentItemInput(
    var value: String = "",
    var type: Int = 1,
    var label: String = "",
)

@Serializable
data class OrganizationInput(
    var company: String = "",
    var title: String = "",
)

/**
 * @Serializable is REQUIRED — KGraphQL deserializes input variables via
 * kotlinx.serialization, which throws "Failed to coerce as ContactInput"
 * for any data class without a serializer.
 *
 * All scalar fields default to safe values so a missing or null field from
 * the web client does not trigger a coercion error.
 */
@Serializable
data class ContactInput(
    var prefix: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var lastName: String = "",
    var suffix: String = "",
    var nickname: String = "",
    var phoneNumbers: List<ContentItemInput> = emptyList(),
    var emails: List<ContentItemInput> = emptyList(),
    var addresses: List<ContentItemInput> = emptyList(),
    var events: List<ContentItemInput> = emptyList(),
    var source: String = "",
    var starred: Boolean = false,
    var notes: String = "",
    var groupIds: List<ID> = emptyList(),
    var organization: OrganizationInput? = null,
    var websites: List<ContentItemInput> = emptyList(),
    var ims: List<ContentItemInput> = emptyList(),
)
