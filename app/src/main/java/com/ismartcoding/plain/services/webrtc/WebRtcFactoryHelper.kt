package com.ismartcoding.plain.services.webrtc

import android.content.Context
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

private var webrtcInitialized = false

internal fun ensureWebRtcInitialized(context: Context) {
    if (!webrtcInitialized) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        webrtcInitialized = true
    }
}

/**
 * Simpler factory used by camera/mic live monitor (no MediaProjection,
 * no audio playback-capture swap, no audio-record callbacks needed).
 */
internal fun createSimpleWebRtcFactory(
    context: Context,
    eglBase: EglBase,
    audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
): Pair<PeerConnectionFactory, JavaAudioDeviceModule> {
    ensureWebRtcInitialized(context)

    val adm = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(false)
        .setUseHardwareNoiseSuppressor(false)
        // For listening to a phone/VoIP call from the web, MIC is the only
        // source that consistently picks up the audio coming out of the
        // loudspeaker (when speakerphone is on) without being silenced by the
        // OS the way VOICE_COMMUNICATION is during another app's call.
        .setAudioSource(audioSource)
        .createAudioDeviceModule()

    val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    val options = PeerConnectionFactory.Options().apply {
        networkIgnoreMask = 1 shl 3
        disableNetworkMonitor = true
    }
    val factory = PeerConnectionFactory.builder()
        .setOptions(options)
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .setAudioDeviceModule(adm)
        .createPeerConnectionFactory()

    return Pair(factory, adm)
}

internal fun createWebRtcFactory(
    context: Context,
    eglBase: EglBase,
    projection: MediaProjection,
    onAudioRecordStart: () -> Unit,
    onAudioRecordStop: () -> Unit,
): Pair<PeerConnectionFactory, JavaAudioDeviceModule> {
    ensureWebRtcInitialized(context)

    val adm = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(false)
        .setUseHardwareNoiseSuppressor(false)
        .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
            override fun onWebRtcAudioRecordStart() = onAudioRecordStart()
            override fun onWebRtcAudioRecordStop() = onAudioRecordStop()
        })
        .createAudioDeviceModule()

    val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    // Skip VPN adapter (ADAPTER_TYPE_VPN = 1 << 3) so that WebRTC binds UDP
    // sockets directly to Wi-Fi. VPN apps typically tunnel all UDP through the
    // VPN, making LAN WebRTC unreachable even with the correct ICE candidate IP.
    val options = PeerConnectionFactory.Options().apply {
        networkIgnoreMask = 1 shl 3
        // Use basic getifaddrs() enumeration instead of Android's ConnectivityManager.
        // ConnectivityManager doesn't list the hotspot AP interface, so WebRTC can't
        // create UDP candidates for it. disableNetworkMonitor forces BasicNetworkManager
        // which discovers ALL interfaces including the Wi-Fi AP (hotspot).
        disableNetworkMonitor = true
    }
    val factory = PeerConnectionFactory.builder()
        .setOptions(options)
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .setAudioDeviceModule(adm)
        .createPeerConnectionFactory()

    return Pair(factory, adm)
}
