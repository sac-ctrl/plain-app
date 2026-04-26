package com.ismartcoding.plain.web.routes

import com.ismartcoding.lib.helpers.CryptoHelper
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.LiveCaptureHelper
import com.ismartcoding.plain.web.HttpServerManager
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import kotlin.text.decodeToString

@Serializable
private data class LiveCaptureUploadInfo(
    val source: String,
    val kind: String,
    val mimeType: String = "",
    val durationMs: Long = 0,
)

/**
 * Persistent uploader for the web panel's live-camera / live-mic captures.
 *
 * Mirrors the auth + multipart layout of [Route.addUploads] (`/upload`):
 *   - `c-id` header identifies the client / token.
 *   - Multipart body has two parts:
 *       * `info`: a chacha20-encrypted JSON describing the capture.
 *       * `file`: the binary payload (photo / video / audio blob).
 *
 * The payload is streamed to a temp file, then handed to [LiveCaptureHelper]
 * which writes the metadata sidecar and rotates / publishes the change event.
 */
fun Route.addLiveCaptureUpload() {
    post("/live_capture_upload") {
        val clientId = call.request.header("c-id") ?: ""
        if (clientId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "c-id header is missing")
            return@post
        }
        val token = HttpServerManager.tokenCache[clientId]
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        try {
            lateinit var info: LiveCaptureUploadInfo
            var savedFilename = ""
            var infoSeen = false
            call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> when (part.name) {
                        "info" -> {
                            val decrypted = CryptoHelper.chaCha20Decrypt(token, part.provider().toByteArray())
                                ?: throw IllegalStateException("Unauthorized")
                            val s = decrypted.decodeToString()
                            info = JsonHelper.jsonDecode(s)
                            infoSeen = true
                        }

                        "file" -> {
                            if (!infoSeen) throw IllegalStateException("missing info part")
                            // Stream to a temp file in the cache dir first so that
                            // partial / failed uploads never appear in the listing.
                            val tempFile = File(
                                MainApp.instance.cacheDir,
                                "live_capture_${System.currentTimeMillis()}_${Thread.currentThread().id}",
                            )
                            tempFile.parentFile?.mkdirs()
                            FileOutputStream(tempFile).use { fos ->
                                part.provider().copyTo(fos)
                                fos.fd.sync()
                            }
                            val meta = LiveCaptureHelper.save(
                                source = info.source,
                                kind = info.kind,
                                mimeType = info.mimeType,
                                durationMs = info.durationMs,
                                tempFile = tempFile,
                            ) ?: throw IllegalStateException("save failed")
                            savedFilename = meta.filename
                        }

                        else -> {}
                    }

                    else -> {}
                }
                part.dispose()
            }
            if (savedFilename.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "no file part")
                return@post
            }
            call.respond(HttpStatusCode.Created, savedFilename)
        } catch (ex: IllegalStateException) {
            call.respond(HttpStatusCode.Unauthorized, ex.message ?: "")
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, ex.message ?: "")
        }
    }
}
