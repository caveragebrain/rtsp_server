package com.selfdox.rtspserver.api

import android.hardware.camera2.CameraCharacteristics
import com.selfdox.rtspserver.data.StreamRepository
import com.selfdox.rtspserver.model.ApiResponse
import com.selfdox.rtspserver.model.CameraResponse
import com.selfdox.rtspserver.model.CameraSwitchRequest
import com.selfdox.rtspserver.model.ConfigUpdateRequest
import com.selfdox.rtspserver.model.FpsUpdateRequest
import com.selfdox.rtspserver.model.LogLevel
import com.selfdox.rtspserver.service.StreamService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Ktor HTTP server for REST control API.
 * DECISION: Per D6 spec — all endpoints return application/json.
 */
class ControlApiServer(
    private val port: Int,
    private val service: StreamService
) {
    companion object {
        private const val TAG = "ControlAPI"
    }
    
    private var server: NettyApplicationEngine? = null
    
    fun start() {
        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
            
            routing {
                // GET /status — current stream status
                get("/status") {
                    logRequest("GET", "/status")
                    val status = StreamRepository.getCurrentStatus()
                    call.respond(status)
                }
                
                // POST /start — start stream (idempotent)
                post("/start") {
                    logRequest("POST", "/start")
                    val success = withContext(Dispatchers.Main) {
                        service.startStream()
                    }
                    if (success) {
                        call.respond(ApiResponse(success = true, message = "Stream started"))
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse(success = false, message = "Failed to start stream")
                        )
                    }
                }
                
                // POST /stop — stop stream
                post("/stop") {
                    logRequest("POST", "/stop")
                    withContext(Dispatchers.Main) {
                        service.stopStream()
                    }
                    call.respond(ApiResponse(success = true, message = "Stream stopped"))
                }
                
                // POST /camera — switch camera
                post("/camera") {
                    logRequest("POST", "/camera")
                    try {
                        val request = call.receive<CameraSwitchRequest>()
                        val success = withContext(Dispatchers.Main) {
                            service.switchCamera(request.id)
                        }
                        if (success) {
                            call.respond(ApiResponse(success = true, message = "Camera switched to ${request.id}"))
                        } else {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse(success = false, message = "Failed to switch camera")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = "Invalid request: ${e.message}")
                        )
                    }
                }
                
                // GET /cameras — list available cameras
                get("/cameras") {
                    logRequest("GET", "/cameras")
                    val cameras = service.getAvailableCameras()
                    val currentId = service.getCurrentCameraId()
                    
                    val response = cameras.map { camera ->
                        CameraResponse(
                            id = camera.id,
                            label = camera.label,
                            facing = when (camera.facing) {
                                CameraCharacteristics.LENS_FACING_BACK -> "back"
                                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                                else -> "external"
                            },
                            active = camera.id == currentId
                        )
                    }
                    call.respond(response)
                }
                
                // POST /config — update configuration
                post("/config") {
                    logRequest("POST", "/config")
                    
                    // DECISION: Per D6 — return 409 if streaming
                    if (service.isStreaming()) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse(success = false, message = "Cannot update config while streaming. Stop stream first.")
                        )
                        return@post
                    }
                    
                    try {
                        val request = call.receive<ConfigUpdateRequest>()
                        val currentConfig = service.getCurrentConfig()
                        
                        val newConfig = currentConfig.copy(
                            width = request.width ?: currentConfig.width,
                            height = request.height ?: currentConfig.height,
                            bitrate = request.bitrate ?: currentConfig.bitrate,
                            fps = request.fps ?: currentConfig.fps
                        )
                        
                        val success = service.updateConfig(newConfig)
                        if (success) {
                            call.respond(ApiResponse(success = true, message = "Config updated"))
                        } else {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiResponse(success = false, message = "Failed to update config")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = "Invalid request: ${e.message}")
                        )
                    }
                }
                
                // POST /fps — change FPS on the fly (auto-restarts stream if running)
                post("/fps") {
                    logRequest("POST", "/fps")
                    try {
                        val request = call.receive<FpsUpdateRequest>()
                        
                        // DECISION: Allow 1-60 fps range
                        if (request.fps < 1 || request.fps > 60) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse(success = false, message = "FPS must be between 1 and 60")
                            )
                            return@post
                        }
                        
                        val wasStreaming = service.isStreaming()
                        
                        // Stop stream if running
                        if (wasStreaming) {
                            withContext(Dispatchers.Main) {
                                service.stopStream()
                            }
                        }
                        
                        // Update config
                        val currentConfig = service.getCurrentConfig()
                        val newConfig = currentConfig.copy(fps = request.fps)
                        service.updateConfig(newConfig)
                        
                        // Restart stream if it was running
                        if (wasStreaming) {
                            withContext(Dispatchers.Main) {
                                service.startStream()
                            }
                        }
                        
                        call.respond(ApiResponse(
                            success = true,
                            message = "FPS changed to ${request.fps}" + if (wasStreaming) " (stream restarted)" else ""
                        ))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = "Invalid request: ${e.message}")
                        )
                    }
                }
                
                // Health check
                get("/health") {
                    call.respondText("OK", ContentType.Text.Plain)
                }
            }
        }.start(wait = false)
    }
    
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
    
    private fun logRequest(method: String, path: String) {
        StreamRepository.log(LogLevel.DEBUG, TAG, "$method $path")
    }
}
