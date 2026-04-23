package com.ismartcoding.plain.web.websocket

import kotlinx.serialization.Serializable

@Serializable
data class WebRtcSignalingMessage(
    val type: String,
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null,
    val phoneIp: String? = null,
    // Stream discriminator: "screen" (default), "camera", or "mic".
    // Used to route signaling between multiple concurrent peer sessions.
    val stream: String? = null,
)
