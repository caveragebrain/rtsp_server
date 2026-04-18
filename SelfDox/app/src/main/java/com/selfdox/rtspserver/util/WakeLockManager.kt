package com.selfdox.rtspserver.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Manages wake locks to keep CPU and WiFi alive during streaming.
 * DECISION: Per D3 spec — PARTIAL_WAKE_LOCK + WIFI_MODE_FULL_HIGH_PERF
 */
class WakeLockManager(private val context: Context) {
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    
    /**
     * Acquire both CPU and WiFi locks for streaming.
     */
    @Suppress("DEPRECATION")
    fun acquire() {
        // CPU wake lock
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SelfDox::StreamWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        
        // WiFi lock
        if (wifiLock == null) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "SelfDox::StreamWifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }
    
    /**
     * Release all locks.
     */
    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
        
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wifiLock = null
        }
    }
    
    /**
     * Check if locks are currently held.
     */
    fun isHeld(): Boolean {
        return (wakeLock?.isHeld == true) && (wifiLock?.isHeld == true)
    }
}
