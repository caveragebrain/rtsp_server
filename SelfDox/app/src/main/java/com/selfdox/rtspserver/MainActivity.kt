package com.selfdox.rtspserver

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pedro.library.view.OpenGlView
import com.selfdox.rtspserver.data.StreamRepository
import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.service.StreamService
import com.selfdox.rtspserver.ui.home.HomeScreen
import com.selfdox.rtspserver.ui.settings.SettingsScreen
import com.selfdox.rtspserver.ui.theme.Background
import com.selfdox.rtspserver.ui.theme.Primary
import com.selfdox.rtspserver.ui.theme.SelfDoxTheme
import com.selfdox.rtspserver.ui.theme.Surface
import com.selfdox.rtspserver.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Single-activity app with BottomNavigation.
 * DECISION: Per D14 — Home + Settings tabs.
 */
class MainActivity : ComponentActivity() {
    
    private var streamService: StreamService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as StreamService.LocalBinder
            streamService = localBinder.getService()
            serviceBound = true
            StreamRepository.setServiceBound(true)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            streamService = null
            serviceBound = false
            StreamRepository.setServiceBound(false)
        }
    }
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startAndBindService()
        } else {
            Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check permissions and start service
        checkPermissionsAndStart()
        
        setContent {
            SelfDoxTheme {
                MainContent(
                    onStartStream = { startStream() },
                    onStopStream = { stopStream() },
                    onSwitchCamera = { camera -> switchCamera(camera) },
                    onApplyConfig = { config -> applyConfig(config) },
                    getPreviewView = { streamService?.getPreviewView() },
                    onStartPreview = { streamService?.startPreview() },
                    onStopPreview = { streamService?.stopPreview() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Permissions
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun checkPermissionsAndStart() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isEmpty()) {
            startAndBindService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
    
    private fun startAndBindService() {
        // Start foreground service
        StreamService.start(this)
        
        // Bind to get reference
        val intent = Intent(this, StreamService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }
    
    // ────────────────────────────────────────────────────────────────────────────────
    // Stream controls
    // ────────────────────────────────────────────────────────────────────────────────
    
    private fun startStream() {
        streamService?.startStream() ?: run {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopStream() {
        streamService?.stopStream()
    }
    
    private fun switchCamera(camera: CameraInfo) {
        streamService?.switchCamera(camera.id)
    }
    
    private fun applyConfig(config: StreamConfig) {
        lifecycleScope.launch {
            val success = streamService?.updateConfig(config) ?: false
            if (!success) {
                Toast.makeText(this@MainActivity, "Stop stream to change settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Composable content
// ────────────────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "Home")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun MainContent(
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onSwitchCamera: (CameraInfo) -> Unit,
    onApplyConfig: (StreamConfig) -> Unit,
    getPreviewView: () -> OpenGlView?,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Settings)
    
    // Track preview view
    var previewView by remember { mutableStateOf<OpenGlView?>(null) }
    
    // Get showPreview setting
    val config by StreamRepository.currentConfig.collectAsState()
    
    DisposableEffect(Unit) {
        previewView = getPreviewView()
        onDispose { }
    }
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = Surface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    Screen.Home -> Icons.Default.Home
                                    Screen.Settings -> Icons.Default.Settings
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartStream = onStartStream,
                    onStopStream = onStopStream,
                    onSwitchCamera = onSwitchCamera,
                    onStartPreview = onStartPreview,
                    onStopPreview = onStopPreview,
                    previewView = previewView,
                    showPreview = config.showPreview
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onApplyConfig = onApplyConfig
                )
            }
        }
    }
}
