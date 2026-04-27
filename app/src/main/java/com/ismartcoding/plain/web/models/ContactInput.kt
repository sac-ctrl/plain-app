package com.ismartcoding.plain.web.models

data class ContentItemInput(
    var value: String = "",
    var type: Int = 1,
    var label: String = "",
)

data class OrganizationInput(
    var company: String = "",
    var title: String = "",
)

/**
 * All scalar fields default to safe values so a missing or null field from
 * the web client does not trigger a "Failed to coerce as ContactInput" error
 * (defence-in-depth against incomplete payloads).
 */
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
