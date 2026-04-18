package com.selfdox.rtspserver.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfdox.rtspserver.data.StreamRepository
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.model.VideoCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SettingsScreen.
 */
class SettingsViewModel : ViewModel() {
    
    // Observe current config from repository
    val currentConfig: StateFlow<StreamConfig> = StreamRepository.currentConfig
    val isStreaming: StateFlow<Boolean> = StreamRepository.isStreaming
    
    // Local edit state
    private val _editConfig = MutableStateFlow(StreamConfig())
    val editConfig: StateFlow<StreamConfig> = _editConfig.asStateFlow()
    
    // Dirty flag
    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()
    
    init {
        // Initialize edit state from current config
        viewModelScope.launch {
            currentConfig.collect { config ->
                if (!_hasChanges.value) {
                    _editConfig.value = config
                }
            }
        }
    }
    
    fun updateWidth(width: Int) {
        _editConfig.value = _editConfig.value.copy(width = width)
        _hasChanges.value = true
    }
    
    fun updateHeight(height: Int) {
        _editConfig.value = _editConfig.value.copy(height = height)
        _hasChanges.value = true
    }
    
    fun updateResolution(width: Int, height: Int) {
        _editConfig.value = _editConfig.value.copy(width = width, height = height)
        _hasChanges.value = true
    }
    
    fun updateBitrate(bitrate: Int) {
        _editConfig.value = _editConfig.value.copy(bitrate = bitrate)
        _hasChanges.value = true
    }
    
    fun updateFps(fps: Int) {
        _editConfig.value = _editConfig.value.copy(fps = fps)
        _hasChanges.value = true
    }
    
    fun updateCodec(codec: VideoCodec) {
        _editConfig.value = _editConfig.value.copy(codec = codec)
        _hasChanges.value = true
    }
    
    fun updateRtspPort(port: Int) {
        _editConfig.value = _editConfig.value.copy(rtspPort = port)
        _hasChanges.value = true
    }
    
    fun updateHttpPort(port: Int) {
        _editConfig.value = _editConfig.value.copy(httpPort = port)
        _hasChanges.value = true
    }
    
    fun updateShowPreview(show: Boolean) {
        _editConfig.value = _editConfig.value.copy(showPreview = show)
        _hasChanges.value = true
    }
    
    fun resetChanges() {
        _editConfig.value = currentConfig.value
        _hasChanges.value = false
    }
    
    fun getEditConfig(): StreamConfig = _editConfig.value
    
    fun clearChangesFlag() {
        _hasChanges.value = false
    }
}
