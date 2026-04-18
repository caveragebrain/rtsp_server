package com.selfdox.rtspserver.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utility for getting device IP address.
 * DECISION: Per D8 spec — resolve IP for RTSP URL display.
 */
object IpAddressUtil {
    
    /**
     * Get the device's WiFi IP address.
     * Falls back to other network interfaces if WiFi is unavailable.
     */
    fun getDeviceIpAddress(context: Context): String? {
        // Try WiFi first
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
            if (ipInt != 0) {
                return intToIpAddress(ipInt)
            }
        }
        
        // Fallback: enumerate network interfaces
        return getIpFromNetworkInterfaces()
    }
    
    /**
     * Check if device is connected to WiFi.
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Convert integer IP (from WifiInfo) to dotted string format.
     */
    private fun intToIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
    
    /**
     * Get IP from network interfaces (fallback method).
     */
    private fun getIpFromNetworkInterfaces(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Skip loopback and down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // We want IPv4, non-loopback
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
}
