package com.selfdox.rtspserver.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.model.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings persistence via DataStore.
 * DECISION: Per D9 spec — all settings stored in DataStore<Preferences>.
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "selfdox_settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val KEY_RTSP_PORT = intPreferencesKey("rtsp_port")
        val KEY_HTTP_PORT = intPreferencesKey("http_port")
        val KEY_WIDTH = intPreferencesKey("width")
        val KEY_HEIGHT = intPreferencesKey("height")
        val KEY_BITRATE = intPreferencesKey("bitrate")
        val KEY_FPS = intPreferencesKey("fps")
        val KEY_CODEC = stringPreferencesKey("codec")
        val KEY_CAMERA_ID = stringPreferencesKey("camera_id")
        val KEY_SHOW_PREVIEW = booleanPreferencesKey("show_preview")
    }
    
    val streamConfig: Flow<StreamConfig> = context.dataStore.data.map { prefs ->
        StreamConfig(
            width = prefs[KEY_WIDTH] ?: 1280,
            height = prefs[KEY_HEIGHT] ?: 720,
            fps = prefs[KEY_FPS] ?: 30,
            bitrate = prefs[KEY_BITRATE] ?: 2_000_000,
            codec = prefs[KEY_CODEC]?.let { VideoCodec.valueOf(it) } ?: VideoCodec.H264,
            rtspPort = prefs[KEY_RTSP_PORT] ?: 1935,
            httpPort = prefs[KEY_HTTP_PORT] ?: 8080,
            cameraId = prefs[KEY_CAMERA_ID] ?: "0",
            showPreview = prefs[KEY_SHOW_PREVIEW] ?: true
        )
    }
    
    suspend fun updateConfig(config: StreamConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIDTH] = config.width
            prefs[KEY_HEIGHT] = config.height
            prefs[KEY_FPS] = config.fps
            prefs[KEY_BITRATE] = config.bitrate
            prefs[KEY_CODEC] = config.codec.name
            prefs[KEY_RTSP_PORT] = config.rtspPort
            prefs[KEY_HTTP_PORT] = config.httpPort
            prefs[KEY_CAMERA_ID] = config.cameraId
            prefs[KEY_SHOW_PREVIEW] = config.showPreview
        }
    }
    
    suspend fun updateCameraId(cameraId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAMERA_ID] = cameraId
        }
    }
    
    suspend fun updateShowPreview(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_PREVIEW] = show
        }
    }
    
    suspend fun updateResolution(width: Int, height: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIDTH] = width
            prefs[KEY_HEIGHT] = height
        }
    }
    
    suspend fun updateBitrate(bitrate: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BITRATE] = bitrate
        }
    }
    
    suspend fun updateFps(fps: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FPS] = fps
        }
    }
    
    suspend fun updateCodec(codec: VideoCodec) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CODEC] = codec.name
        }
    }
    
    suspend fun updatePorts(rtspPort: Int, httpPort: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RTSP_PORT] = rtspPort
            prefs[KEY_HTTP_PORT] = httpPort
        }
    }
}
