package com.selfdox.rtspserver.model

import kotlinx.serialization.Serializable

/**
 * Stream configuration settings.
 * DECISION: Per D5 spec — defaults and configurable params.
 */
@Serializable
data class StreamConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000,
    val codec: VideoCodec = VideoCodec.H264,
    val rtspPort: Int = 1935,
    val httpPort: Int = 8080,
    val cameraId: String = "0",
    val showPreview: Boolean = true
)

@Serializable
enum class VideoCodec {
    H264,
    H265
}
