package com.selfdox.rtspserver.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Current stream status — exposed to UI and REST API.
 * DECISION: Per D6/D7 spec — includes client count and uptime.
 */
@Serializable
data class StreamStatus(
    @SerialName("streaming")
    val isStreaming: Boolean = false,
    
    @SerialName("camera_id")
    val cameraId: String = "0",
    
    val resolution: String = "1280x720",
    val bitrate: Int = 2_000_000,
    val fps: Int = 30,
    
    @SerialName("uptime_ms")
    val uptimeMs: Long = 0,
    
    val clients: Int = 0,
    
    @SerialName("rtsp_url")
    val rtspUrl: String = ""
)

/**
 * Response for /cameras endpoint.
 */
@Serializable
data class CameraResponse(
    val id: String,
    val label: String,
    val facing: String,
    val active: Boolean
)

/**
 * Request body for /camera endpoint.
 */
@Serializable
data class CameraSwitchRequest(
    val id: String
)

/**
 * Request body for /config endpoint.
 */
@Serializable
data class ConfigUpdateRequest(
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val fps: Int? = null
)

/**
 * Request body for /fps endpoint (on-the-fly FPS change).
 */
@Serializable
data class FpsUpdateRequest(
    val fps: Int
)

/**
 * Generic API response.
 */
@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String? = null
)
