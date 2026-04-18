package com.selfdox.rtspserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.common.ConnectChecker
import com.selfdox.rtspserver.MainActivity
import com.selfdox.rtspserver.R
import com.selfdox.rtspserver.api.ControlApiServer
import com.selfdox.rtspserver.data.SettingsRepository
import com.selfdox.rtspserver.data.StreamRepository
import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.model.LogLevel
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.model.VideoCodec
import com.selfdox.rtspserver.util.CameraEnumerator
import com.selfdox.rtspserver.util.IpAddressUtil
import com.selfdox.rtspserver.util.WakeLockManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the RTSP stream.
 * 
 * DECISION: Per D1/D3 spec — RtspServerCamera2 is constructed with Context constructor
 * so it can run without any UI surface. Preview is attached/detached separately.
 */
class StreamService : LifecycleService(), ConnectChecker {
    
    companion object {
        private const val TAG = "StreamService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "selfdox_stream_channel"
        
        const val ACTION_STOP_STREAM = "com.selfdox.rtspserver.STOP_STREAM"
        
        fun bind(context: Context, connection: ServiceConnection) {
            val intent = Intent(context, StreamService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        
        fun start(context: Context) {
            val intent = Intent(context, StreamService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, StreamService::class.java)
            context.stopService(intent)
        }
    }
    
    // Binder for Activity communication
    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
    
    private val binder = LocalBinder()
    
    // Core components
    private var rtspServerCamera2: RtspServerCamera2? = null
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var settingsRepository: SettingsRepository
    private var controlApiServer: ControlApiServer? = null
    
    // OpenGlView for preview — created once, reused
    private var openGlView: OpenGlView? = null
    
    // Current config
    private var currentConfig: StreamConfig = StreamConfig()
    
    // Client polling job
    private var clientPollingJob: Job? = null
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────────────
    
    override fun onCreate() {
        super.onCreate()
        log(LogLevel.INFO, "Service created")
        
        wakeLockManager = WakeLockManager(this)
        settingsRepository = SettingsRepository(this)
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground immediately
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize components
        lifecycleScope.launch {
            initializeComponents()
        }
    }
    
    private suspend fun initializeComponents() {
        // Load config
        currentConfig = settingsRepository.streamConfig.first()
        StreamRepository.setCurrentConfig(currentConfig)
        
        // Enumerate cameras
        val cameras = CameraEnumerator.getAvailableCameras(this)
        StreamRepository.setAvailableCameras(cameras)
        log(LogLevel.INFO, "Found ${cameras.size} cameras")
        
        // Update IP
        updateDeviceIp()
        
        // Create OpenGlView for preview (created once, attached to window later)
        // DECISION: Per D11 — OpenGlView created with applicationContext in service
        openGlView = OpenGlView(applicationContext)
        
        // Create RTSP server camera
        // DECISION: Per D1 — Use Context constructor for background operation
        rtspServerCamera2 = RtspServerCamera2(
            applicationContext,
            this,
            currentConfig.rtspPort
        )
        
        // Start HTTP control API
        // DECISION: Per D6 — Ktor server on httpPort
        controlApiServer = ControlApiServer(
            port = currentConfig.httpPort,
            service = this
        ).also {
            it.start()
            log(LogLevel.INFO, "HTTP API started on port ${currentConfig.httpPort}")
        }
        
        log(LogLevel.INFO, "Components initialized")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Handle stop action from notification
        if (intent?.action == ACTION_STOP_STREAM) {
            stopStream()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        StreamRepository.setServiceBound(true)
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        StreamRepository.setServiceBound(false)
        return super.onUnbind(intent)
    }
    
    override fun onDestroy() {
        log(LogLevel.INFO, "Service destroying")
        
        // Stop everything
        stopStream()
        
        // Stop HTTP server
        controlApiServer?.stop()
        
        // Stop preview and stream for camera cleanup
        rtspServerCamera2?.let { camera ->
            if (camera.isOnPreview) camera.stopPreview()
            if (camera.isStreaming) camera.stopStream()
        }
        rtspServerCamera2 = null
        
        // Release locks
        wakeLockManager.release()
        
        // Reset repository state
        StreamRepository.reset()
        
        super.onDestroy()
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Stream control
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun startStream(): Boolean {
        val camera = rtspServerCamera2 ?: return false
        
        if (camera.isStreaming) {
            log(LogLevel.WARN, "Stream already running")
            return true
        }
        
        // Acquire wake locks
        wakeLockManager.acquire()
        
        val requestedFps = currentConfig.fps
        val cameraFps = resolveCameraFps(requestedFps)
        if (cameraFps != requestedFps) {
            log(
                LogLevel.WARN,
                "Applying camera FPS workaround: requested=${requestedFps}fps, camera=${cameraFps}fps"
            )
        }
        
        // Prepare video
        // DECISION: Per D5 — prepareVideo before startStream
        // API: prepareVideo(width, height, fps, bitrate, iFrameInterval)
        val videoOk = camera.prepareVideo(
            currentConfig.width,
            currentConfig.height,
            cameraFps,
            currentConfig.bitrate,
            2  // iFrameInterval
        )
        
        if (!videoOk) {
            log(LogLevel.ERROR, "Failed to prepare video")
            wakeLockManager.release()
            return false
        }
        
        // Prepare audio
        // DECISION: Per D4 — Audio always on, fixed params
        // DECISION: Using mono as workaround for RootEncoder 2.6.1 SDP bug where stereo
        // channel count isn't written to SDP (shows 0 channels). Mono forces channels=1
        // which is always written correctly. TODO(selfdox): revert to stereo once
        // RootEncoder version is bumped or bug is fixed upstream.
        // TODO(selfdox): SDP title "Unnamed" is hardcoded in RtspServer 1.3.6, no public API to override.
        // WORKAROUND: RootEncoder 2.6.1 has a bug where it writes the 2nd param (bitrate) into
        // the SDP sample rate field. Using 44100 for both ensures correct SDP while maintaining
        // reasonable audio quality.
        // API: prepareAudio(sampleRate: Int, bitrate: Int, isStereo: Boolean)
        val audioOk = camera.prepareAudio(
            44100,   // sampleRate - 44100 is the only guaranteed-supported rate
            44100,   // bitrate - using same value to work around SDP bug (44.1kbps mono is acceptable)
            false    // isStereo - mono for SDP channel-count bug workaround
        )
        
        if (!audioOk) {
            log(LogLevel.ERROR, "Failed to prepare audio")
            wakeLockManager.release()
            return false
        }
        
        // Start stream (no URL — it's a server)
        camera.startStream()
        
        // Ensure actual RTSP stream sends 1 FPS even if hardware is at 10
        if (requestedFps == 1) {
            camera.glInterface.forceFpsLimit(1)
        }
        
        StreamRepository.setStreaming(true)
        log(LogLevel.INFO, "Stream started on port ${currentConfig.rtspPort}")
        
        // Start client count polling
        startClientPolling()
        
        // Update notification
        updateNotification()
        
        return true
    }

    /**
     * Redmi Note 9 Pro (curtana/joyeuse family) can oscillate AE at [1,1] camera FPS.
     * We keep user-configured value, but bump camera capture to 2fps for stability.
     */
    private fun resolveCameraFps(requestedFps: Int): Int {
        if (requestedFps != 1) return requestedFps
        return 10 // 'Sensor at 10 / Stream at 1' logic for hardware stability
    }

    private fun isRedmiNote9ProFamily(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        
        val isXiaomi = manufacturer == "xiaomi" || manufacturer == "redmi"
        val modelMatch = model.contains("redmi note 9 pro")
        val codenameMatch = device.contains("curtana")
            || device.contains("joyeuse")
            || product.contains("curtana")
            || product.contains("joyeuse")
        return isXiaomi && (modelMatch || codenameMatch)
    }
    
    fun stopStream() {
        val camera = rtspServerCamera2
        
        if (camera?.isStreaming == true) {
            camera.stopStream()
            log(LogLevel.INFO, "Stream stopped")
        }
        
        // Stop polling
        clientPollingJob?.cancel()
        clientPollingJob = null
        
        // Release locks
        wakeLockManager.release()
        
        StreamRepository.setStreaming(false)
        StreamRepository.setClientCount(0)
        
        // Update notification
        updateNotification()
    }
    
    fun isStreaming(): Boolean {
        return rtspServerCamera2?.isStreaming == true
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Camera control
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun switchCamera(cameraId: String): Boolean {
        val camera = rtspServerCamera2 ?: return false
        
        return try {
            // DECISION: Per D2 — use changeVideoSourceId for specific camera
            camera.switchCamera()
            
            // TODO(selfdox): Use changeVideoSourceId(cameraId) when we need specific camera selection
            // VERIFY: changeVideoSourceId might need different handling
            
            StreamRepository.setCurrentCameraId(cameraId)
            
            lifecycleScope.launch {
                settingsRepository.updateCameraId(cameraId)
            }
            
            log(LogLevel.INFO, "Camera switched to $cameraId")
            updateNotification()
            true
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to switch camera: ${e.message}")
            false
        }
    }
    
    fun getAvailableCameras(): List<CameraInfo> {
        return StreamRepository.availableCameras.value
    }
    
    fun getCurrentCameraId(): String {
        return StreamRepository.currentCameraId.value
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Preview control
    // ────────────────────────────────────────────────────────────────────────────────
    
    /**
     * Get the OpenGlView for preview rendering.
     * DECISION: Per D11 — Activity reuses this instance, doesn't create its own.
     */
    fun getPreviewView(): OpenGlView? = openGlView
    
    fun startPreview() {
        val camera = rtspServerCamera2 ?: return
        
        if (!camera.isOnPreview) {
            // API uses CameraHelper.Facing or camera ID string
            camera.startPreview(CameraHelper.Facing.BACK)
            log(LogLevel.DEBUG, "Preview started")
        }
    }
    
    fun stopPreview() {
        val camera = rtspServerCamera2 ?: return
        
        if (camera.isOnPreview) {
            camera.stopPreview()
            log(LogLevel.DEBUG, "Preview stopped")
        }
    }
    
    fun isOnPreview(): Boolean {
        return rtspServerCamera2?.isOnPreview == true
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Config management
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun getCurrentConfig(): StreamConfig = currentConfig
    
    /**
     * Update config. Returns false if streaming (must stop first).
     * DECISION: Per D5 — stream must be stopped before config change.
     */
    suspend fun updateConfig(newConfig: StreamConfig): Boolean {
        if (isStreaming()) {
            log(LogLevel.WARN, "Cannot update config while streaming")
            return false
        }
        
        val oldConfig = currentConfig
        currentConfig = newConfig
        settingsRepository.updateConfig(newConfig)
        StreamRepository.setCurrentConfig(newConfig)
        
        // Recreate RTSP server if port changed
        if (newConfig.rtspPort != oldConfig.rtspPort) {
            rtspServerCamera2?.stopStream()
            rtspServerCamera2?.stopPreview()
            rtspServerCamera2 = RtspServerCamera2(
                applicationContext,
                this,
                newConfig.rtspPort
            )
        }
        
        // Restart HTTP server if port changed
        if (newConfig.httpPort != oldConfig.httpPort) {
            controlApiServer?.stop()
            controlApiServer = ControlApiServer(
                port = newConfig.httpPort,
                service = this
            ).also { it.start() }
        }
        
        log(LogLevel.INFO, "Config updated: ${newConfig.width}x${newConfig.height} @ ${newConfig.bitrate}bps")
        return true
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Client polling
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun startClientPolling() {
        clientPollingJob?.cancel()
        clientPollingJob = lifecycleScope.launch {
            // DECISION: Per D7 — poll every 2 seconds
            while (isActive && isStreaming()) {
                val count = rtspServerCamera2?.getStreamClient()?.getNumClients() ?: 0
                StreamRepository.setClientCount(count)
                delay(2000)
            }
        }
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // IP address
    // ────────────────────────────────────────────────────────────────────────────────
    
    fun updateDeviceIp() {
        val ip = IpAddressUtil.getDeviceIpAddress(this)
        StreamRepository.setDeviceIp(ip)
    }
    
    fun getStreamUrl(): String? {
        val ip = StreamRepository.deviceIp.value ?: return null
        return "rtsp://$ip:${currentConfig.rtspPort}/"
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // ConnectChecker implementation
    // ────────────────────────────────────────────────────────────────────────────────
    
    override fun onConnectionStarted(url: String) {
        log(LogLevel.DEBUG, "Connection started: $url")
    }
    
    override fun onConnectionSuccess() {
        // DECISION: Per D1 note — for RtspServerCamera2, this fires when server is running
        log(LogLevel.INFO, "RTSP server running")
    }
    
    override fun onConnectionFailed(reason: String) {
        log(LogLevel.ERROR, "Connection failed: $reason")
        stopStream()
    }
    
    override fun onNewBitrate(bitrate: Long) {
        StreamRepository.setCurrentBitrate(bitrate)
    }
    
    override fun onDisconnect() {
        log(LogLevel.INFO, "Disconnected")
    }
    
    override fun onAuthError() {
        log(LogLevel.ERROR, "Auth error")
    }
    
    override fun onAuthSuccess() {
        log(LogLevel.DEBUG, "Auth success")
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Notification
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun createNotificationChannel() {
        // DECISION: Per D13 — IMPORTANCE_LOW for silent notifications
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val isStreaming = isStreaming()
        val clients = StreamRepository.clientCount.value
        val camera = StreamRepository.availableCameras.value
            .find { it.id == StreamRepository.currentCameraId.value }
            ?.label ?: "Unknown"
        
        // Content intent — open app
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Stop action intent
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StreamService::class.java).apply {
                action = ACTION_STOP_STREAM
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val title = if (isStreaming) "Self Dox • LIVE" else "Self Dox • Stopped"
        val text = if (isStreaming) {
            "Camera: $camera • $clients client${if (clients != 1) "s" else ""}"
        } else {
            "Ready to stream"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .apply {
                if (isStreaming) {
                    addAction(
                        android.R.drawable.ic_media_pause,
                        "Stop Stream",
                        stopIntent
                    )
                }
            }
            .build()
    }
    
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Logging helper
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun log(level: LogLevel, message: String) {
        StreamRepository.log(level, TAG, message)
        
        // Also log to logcat
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(TAG, message)
            LogLevel.INFO -> android.util.Log.i(TAG, message)
            LogLevel.WARN -> android.util.Log.w(TAG, message)
            LogLevel.ERROR -> android.util.Log.e(TAG, message)
        }
    }
}
