package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.Context
import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.DScreenMirrorQuality
import com.ismartcoding.plain.data.ScreenMirrorControlInput
import com.ismartcoding.plain.enums.ScreenMirrorMode
import com.ismartcoding.plain.events.RequestScreenMirrorAudioEvent
import com.ismartcoding.plain.events.StartScreenMirrorEvent
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.preferences.ScreenMirrorQualityPreference
import com.ismartcoding.plain.services.LiveCallTracker
import com.ismartcoding.plain.services.PlainAccessibilityService
import com.ismartcoding.plain.services.ScreenMirrorService
import com.ismartcoding.plain.web.models.toModel
import com.ismartcoding.plain.web.websocket.WebRtcSignalingMessage
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

fun SchemaBuilder.addScreenMirrorSchema() {
    query("screenMirrorState") {
        resolver { ->
            ScreenMirrorService.instance?.isRunning() == true
        }
    }
    query("screenMirrorControlEnabled") {
        resolver { ->
            PlainAccessibilityService.isEnabled()
        }
    }
    query("screenMirrorQuality") {
        resolver { ->
            ScreenMirrorQualityPreference.getValueAsync(MainApp.instance).toModel()
        }
    }
    mutation("startScreenMirror") {
        resolver { audio: Boolean ->
            ScreenMirrorService.qualityData = ScreenMirrorQualityPreference.getValueAsync(MainApp.instance)
            sendEvent(StartScreenMirrorEvent(audio))
            true
        }
    }
    mutation("requestScreenMirrorAudio") {
        resolver { ->
            if (Permission.RECORD_AUDIO.can(MainApp.instance)) {
                true
            } else {
                sendEvent(RequestScreenMirrorAudioEvent())
                false
            }
        }
    }
    mutation("stopScreenMirror") {
        resolver { ->
            ScreenMirrorService.instance?.stop()
            ScreenMirrorService.instance = null
            true
        }
    }
    mutation("updateScreenMirrorQuality") {
        resolver { mode: ScreenMirrorMode ->
            val resolution = when (mode) {
                ScreenMirrorMode.AUTO -> 1080
                ScreenMirrorMode.HD -> 1080
                ScreenMirrorMode.SMOOTH -> 720
            }
            val qualityData = DScreenMirrorQuality(mode, resolution)
            ScreenMirrorQualityPreference.putAsync(MainApp.instance, qualityData)
            ScreenMirrorService.qualityData = qualityData
            ScreenMirrorService.instance?.onQualityChanged()
            true
        }
    }
    mutation("sendWebRtcSignaling") {
        resolver { payload: WebRtcSignalingMessage, context: Context ->
            val call = context.get<ApplicationCall>()
            val clientId = call?.request?.header("c-id") ?: ""
            when (payload.stream) {
                "camera" -> com.ismartcoding.plain.services.LiveCameraService.instance?.handleWebRtcSignaling(clientId, payload)
                "mic" -> {
                    // Defensive: if a `ready`/offer/etc arrives for the mic
                    // stream before the foreground service has finished
                    // initialising, kick it off and wait briefly. This
                    // closes the race that previously left the browser
                    // stuck on "Connecting…" forever.
                    var svc = com.ismartcoding.plain.services.LiveMicService.instance
                    if (svc?.isRunning() != true) {
                        LiveCallTracker.ensureListening()
                        LiveCallTracker.awaitListenerReady(4000L)
                        svc = com.ismartcoding.plain.services.LiveMicService.instance
                    }
                    svc?.handleWebRtcSignaling(clientId, payload)
                }
                else -> ScreenMirrorService.instance?.handleWebRtcSignaling(clientId, payload)
            }
            true
        }
    }
    mutation("sendScreenMirrorControl") {
        resolver { input: ScreenMirrorControlInput ->
            val service = PlainAccessibilityService.instance
                ?: throw GraphQLError("Accessibility service is not enabled")
            val screenSize = PlainAccessibilityService.getScreenSize(MainApp.instance)
            service.dispatchControl(input, screenSize.x, screenSize.y)
            true
        }
    }
}
