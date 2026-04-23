package com.ismartcoding.plain.services.webrtc

import android.content.Context
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.web.websocket.WebRtcSignalingMessage
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class LiveCameraWebRtcManager(private val context: Context) {
    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var eglBase: EglBase? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private val sessions = mutableMapOf<String, LivePeerSession>()

    @Volatile var facing: String = "back"
        private set

    fun start(initialFacing: String): Boolean {
        facing = if (initialFacing == "front") "front" else "back"
        eglBase = EglBase.create()
        val (f, a) = createSimpleWebRtcFactory(context, eglBase!!)
        factory = f; adm = a
        val enumerator = Camera2Enumerator(context)
        val deviceName = pickDevice(enumerator, facing) ?: run {
            LogCat.e("live camera: no camera devices found")
            return false
        }
        val cap = enumerator.createCapturer(deviceName, null) ?: run {
            LogCat.e("live camera: createCapturer failed for $deviceName")
            return false
        }
        capturer = cap
        surfaceHelper = SurfaceTextureHelper.create("LiveCameraThread", eglBase!!.eglBaseContext)
        videoSource = factory!!.createVideoSource(false)
        cap.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        cap.startCapture(1280, 720, 30)
        videoTrack = factory!!.createVideoTrack("live_camera_video", videoSource)
        return true
    }

    fun switchFacing() {
        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                facing = if (isFrontCamera) "front" else "back"
            }
            override fun onCameraSwitchError(errorDescription: String) {
                LogCat.e("live camera switch failed: $errorDescription")
            }
        })
    }

    fun handleSignaling(clientId: String, message: WebRtcSignalingMessage) {
        when (message.type) {
            "ready" -> {
                val factory = factory ?: return
                val track = videoTrack ?: return
                sessions.remove(clientId)?.release()
                val s = LivePeerSession(clientId, "camera", factory, track, null)
                sessions[clientId] = s
                s.createPeerConnectionAndOffer()
            }
            "answer" -> if (!message.sdp.isNullOrBlank()) sessions[clientId]?.handleAnswer(message.sdp)
            "ice_candidate" -> if (!message.candidate.isNullOrBlank()) sessions[clientId]?.handleIceCandidate(message)
            else -> Unit
        }
    }

    fun release() {
        sessions.values.forEach { it.release() }; sessions.clear()
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose(); capturer = null
        videoTrack = null
        videoSource?.dispose(); videoSource = null
        surfaceHelper?.dispose(); surfaceHelper = null
        adm?.release(); adm = null
        factory?.dispose(); factory = null
        eglBase?.release(); eglBase = null
    }

    private fun pickDevice(e: Camera2Enumerator, facing: String): String? {
        val names = e.deviceNames
        return names.firstOrNull { if (facing == "front") e.isFrontFacing(it) else e.isBackFacing(it) }
            ?: names.firstOrNull()
    }
}
