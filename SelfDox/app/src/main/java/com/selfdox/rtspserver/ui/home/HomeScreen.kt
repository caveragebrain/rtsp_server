package com.selfdox.rtspserver.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedro.library.view.OpenGlView
import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.ui.components.CameraSelector
import com.selfdox.rtspserver.ui.components.LogRow
import com.selfdox.rtspserver.ui.components.StatCard
import com.selfdox.rtspserver.ui.components.StatusBadge
import com.selfdox.rtspserver.ui.components.StreamPreviewView
import com.selfdox.rtspserver.ui.theme.CardBackground
import com.selfdox.rtspserver.ui.theme.LiveGreen
import com.selfdox.rtspserver.ui.theme.Primary
import com.selfdox.rtspserver.ui.theme.StopRed
import com.selfdox.rtspserver.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Home screen with stream URL, status, controls, preview, and logs.
 * DECISION: Per D14 — Duolingo-inspired dark UI.
 */
@Composable
fun HomeScreen(
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onSwitchCamera: (CameraInfo) -> Unit,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    previewView: OpenGlView?,
    showPreview: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    
    val isStreaming by viewModel.isStreaming.collectAsState()
    val clientCount by viewModel.clientCount.collectAsState()
    val currentBitrate by viewModel.currentBitrate.collectAsState()
    val streamStartTime by viewModel.streamStartTime.collectAsState()
    val availableCameras by viewModel.availableCameras.collectAsState()
    val currentCameraId by viewModel.currentCameraId.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()
    val rtspUrl by viewModel.rtspUrl.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    
    // Uptime counter
    var uptimeMs by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(isStreaming, streamStartTime) {
        while (isStreaming && streamStartTime != null) {
            uptimeMs = System.currentTimeMillis() - streamStartTime!!
            delay(1000)
        }
        if (!isStreaming) {
            uptimeMs = 0
        }
    }
    
    // Preview management
    LaunchedEffect(showPreview, previewView) {
        if (showPreview && previewView != null) {
            onStartPreview()
        }
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Status badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Self Dox",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                StatusBadge(isStreaming = isStreaming)
            }
        }
        
        // RTSP URL card
        item {
            UrlCard(
                url = rtspUrl,
                isStreaming = isStreaming,
                onCopy = {
                    copyToClipboard(context, rtspUrl)
                }
            )
        }
        
        // Start/Stop button
        item {
            StreamButton(
                isStreaming = isStreaming,
                onStart = onStartStream,
                onStop = onStopStream
            )
        }
        
        // Stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Uptime",
                    value = formatUptime(uptimeMs),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Clients",
                    value = clientCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Bitrate",
                    value = formatBitrate(currentBitrate),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "FPS",
                    value = currentConfig.fps.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Camera selector
        item {
            CameraSelector(
                cameras = availableCameras,
                selectedId = currentCameraId,
                onCameraSelected = onSwitchCamera,
                enabled = true // Can switch while streaming
            )
        }
        
        // Preview
        if (showPreview && previewView != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    StreamPreviewView(
                        openGlView = previewView,
                        onDispose = onStopPreview
                    )
                }
            }
        }
        
        // Logs section
        item {
            Text(
                text = "Logs",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        
        items(
            items = logEntries.take(50),
            key = { it.id }
        ) { entry ->
            LogRow(entry = entry)
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun UrlCard(
    url: String,
    isStreaming: Boolean,
    onCopy: () -> Unit
) {
    val displayUrl = url.ifEmpty { "Not connected to WiFi" }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .clickable(enabled = url.isNotEmpty()) { onCopy() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Stream URL",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isStreaming) LiveGreen else MaterialTheme.colorScheme.onSurface
            )
        }
        
        if (url.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy URL",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StreamButton(
    isStreaming: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Button(
        onClick = if (isStreaming) onStop else onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isStreaming) StopRed else Primary
        )
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = if (isStreaming) "Stop Stream" else "Start Stream",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("RTSP URL", text))
    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
}

private fun formatUptime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    val hours = ms / 3600000
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatBitrate(bps: Long): String {
    return when {
        bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.0f Kbps".format(bps / 1_000.0)
        else -> "$bps bps"
    }
}
