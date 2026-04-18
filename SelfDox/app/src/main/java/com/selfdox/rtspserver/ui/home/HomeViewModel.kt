package com.selfdox.rtspserver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfdox.rtspserver.data.StreamRepository
import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.model.LogEntry
import com.selfdox.rtspserver.model.StreamConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for HomeScreen.
 * Observes state from StreamRepository.
 */
class HomeViewModel : ViewModel() {
    
    // Stream state
    val isStreaming: StateFlow<Boolean> = StreamRepository.isStreaming
    val clientCount: StateFlow<Int> = StreamRepository.clientCount
    val currentBitrate: StateFlow<Long> = StreamRepository.currentBitrate
    val streamStartTime: StateFlow<Long?> = StreamRepository.streamStartTime
    
    // Camera state
    val availableCameras: StateFlow<List<CameraInfo>> = StreamRepository.availableCameras
    val currentCameraId: StateFlow<String> = StreamRepository.currentCameraId
    
    // Config
    val currentConfig: StateFlow<StreamConfig> = StreamRepository.currentConfig
    
    // Device IP
    val deviceIp: StateFlow<String?> = StreamRepository.deviceIp
    
    // Logs
    val logEntries: StateFlow<List<LogEntry>> = StreamRepository.logEntries
    
    // Combined RTSP URL
    val rtspUrl: StateFlow<String> = combine(
        deviceIp,
        currentConfig
    ) { ip, config ->
        if (ip != null) "rtsp://$ip:${config.rtspPort}/" else ""
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
}
