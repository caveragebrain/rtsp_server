package com.selfdox.rtspserver.data

import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.model.LogEntry
import com.selfdox.rtspserver.model.LogLevel
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.model.StreamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for stream state — observed by ViewModels.
 * DECISION: Per D4/D7/D12 — central state management for streaming.
 * 
 * This is a singleton that the Service populates and the UI observes.
 */
object StreamRepository {
    
    private const val MAX_LOG_ENTRIES = 200
    
    // Stream state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    private val _streamStatus = MutableStateFlow(StreamStatus())
    val streamStatus: StateFlow<StreamStatus> = _streamStatus.asStateFlow()
    
    // Client count
    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()
    
    // Stream start time for uptime calculation
    private val _streamStartTime = MutableStateFlow<Long?>(null)
    val streamStartTime: StateFlow<Long?> = _streamStartTime.asStateFlow()
    
    // Current camera
    private val _currentCameraId = MutableStateFlow("0")
    val currentCameraId: StateFlow<String> = _currentCameraId.asStateFlow()
    
    // Available cameras
    private val _availableCameras = MutableStateFlow<List<CameraInfo>>(emptyList())
    val availableCameras: StateFlow<List<CameraInfo>> = _availableCameras.asStateFlow()
    
    // Current config
    private val _currentConfig = MutableStateFlow(StreamConfig())
    val currentConfig: StateFlow<StreamConfig> = _currentConfig.asStateFlow()
    
    // Device IP
    private val _deviceIp = MutableStateFlow<String?>(null)
    val deviceIp: StateFlow<String?> = _deviceIp.asStateFlow()
    
    // Log entries — ring buffer
    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()
    
    // Bitrate (from onNewBitrate callback)
    private val _currentBitrate = MutableStateFlow<Long>(0)
    val currentBitrate: StateFlow<Long> = _currentBitrate.asStateFlow()
    
    // Service bound state
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Mutators (called by StreamService)
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun setStreaming(streaming: Boolean) {
        _isStreaming.value = streaming
        if (streaming) {
            _streamStartTime.value = System.currentTimeMillis()
        } else {
            _streamStartTime.value = null
        }
        updateStatus()
    }
    
    fun setClientCount(count: Int) {
        _clientCount.value = count
        updateStatus()
    }
    
    fun setCurrentCameraId(cameraId: String) {
        _currentCameraId.value = cameraId
        updateStatus()
    }
    
    fun setAvailableCameras(cameras: List<CameraInfo>) {
        _availableCameras.value = cameras
    }
    
    fun setCurrentConfig(config: StreamConfig) {
        _currentConfig.value = config
        updateStatus()
    }
    
    fun setDeviceIp(ip: String?) {
        _deviceIp.value = ip
        updateStatus()
    }
    
    fun setCurrentBitrate(bitrate: Long) {
        _currentBitrate.value = bitrate
    }
    
    fun setServiceBound(bound: Boolean) {
        _isServiceBound.value = bound
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Logging
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        val current = _logEntries.value.toMutableList()
        current.add(0, entry) // Add to front (newest first)
        
        // Trim to max size
        if (current.size > MAX_LOG_ENTRIES) {
            _logEntries.value = current.take(MAX_LOG_ENTRIES)
        } else {
            _logEntries.value = current
        }
    }
    
    fun logDebug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun logInfo(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun logWarn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun logError(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
    
    fun clearLogs() {
        _logEntries.value = emptyList()
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Status computation
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun updateStatus() {
        val config = _currentConfig.value
        val ip = _deviceIp.value
        val uptime = _streamStartTime.value?.let { System.currentTimeMillis() - it } ?: 0
        
        _streamStatus.value = StreamStatus(
            isStreaming = _isStreaming.value,
            cameraId = _currentCameraId.value,
            resolution = "${config.width}x${config.height}",
            bitrate = config.bitrate,
            fps = config.fps,
            uptimeMs = uptime,
            clients = _clientCount.value,
            rtspUrl = if (ip != null) "rtsp://$ip:${config.rtspPort}/" else ""
        )
    }
    
    /**
     * Get current status with fresh uptime calculation.
     */
    fun getCurrentStatus(): StreamStatus {
        updateStatus()
        return _streamStatus.value
    }
    
    /**
     * Reset all state (for service destruction).
     */
    fun reset() {
        _isStreaming.value = false
        _streamStartTime.value = null
        _clientCount.value = 0
        _currentBitrate.value = 0
        _isServiceBound.value = false
        updateStatus()
    }
}
