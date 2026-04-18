package com.selfdox.rtspserver.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfdox.rtspserver.model.StreamConfig
import com.selfdox.rtspserver.model.VideoCodec
import com.selfdox.rtspserver.ui.theme.CardBackground
import com.selfdox.rtspserver.ui.theme.Primary
import com.selfdox.rtspserver.ui.theme.StopRed
import com.selfdox.rtspserver.ui.theme.TextSecondary

/**
 * Settings screen for configuring stream parameters.
 * DECISION: Per D5 — all configurable params displayed here.
 */
@Composable
fun SettingsScreen(
    onApplyConfig: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    
    val isStreaming by viewModel.isStreaming.collectAsState()
    val editConfig by viewModel.editConfig.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        // Warning if streaming
        if (isStreaming) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StopRed.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Stop stream to change settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StopRed
                    )
                }
            }
        }
        
        // Resolution section
        item {
            SectionTitle("Video")
        }
        
        item {
            ResolutionSelector(
                width = editConfig.width,
                height = editConfig.height,
                onSelect = { w, h -> viewModel.updateResolution(w, h) },
                enabled = !isStreaming
            )
        }
        
        // Bitrate
        item {
            NumberField(
                label = "Bitrate (bps)",
                value = editConfig.bitrate,
                onValueChange = { viewModel.updateBitrate(it) },
                enabled = !isStreaming
            )
        }
        
        // FPS
        item {
            FpsSelector(
                fps = editConfig.fps,
                onSelect = { viewModel.updateFps(it) },
                enabled = !isStreaming
            )
        }
        
        // Codec
        item {
            CodecSelector(
                codec = editConfig.codec,
                onSelect = { viewModel.updateCodec(it) },
                enabled = !isStreaming
            )
        }
        
        // Network section
        item {
            SectionTitle("Network")
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NumberField(
                    label = "RTSP Port",
                    value = editConfig.rtspPort,
                    onValueChange = { viewModel.updateRtspPort(it) },
                    enabled = !isStreaming,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "HTTP Port",
                    value = editConfig.httpPort,
                    onValueChange = { viewModel.updateHttpPort(it) },
                    enabled = !isStreaming,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Preview section
        item {
            SectionTitle("Display")
        }
        
        item {
            ToggleRow(
                label = "Show Preview",
                description = "Display camera preview on home screen",
                checked = editConfig.showPreview,
                onCheckedChange = { viewModel.updateShowPreview(it) }
            )
        }
        
        // Apply button
        if (hasChanges && !isStreaming) {
            item {
                Button(
                    onClick = {
                        onApplyConfig(viewModel.getEditConfig())
                        viewModel.clearChangesFlag()
                        Toast.makeText(context, "Settings applied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(
                        text = "Apply Changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ResolutionSelector(
    width: Int,
    height: Int,
    onSelect: (Int, Int) -> Unit,
    enabled: Boolean
) {
    val resolutions = listOf(
        "1920x1080" to (1920 to 1080),
        "1280x720" to (1280 to 720),
        "854x480" to (854 to 480),
        "640x360" to (640 to 360)
    )
    
    val currentRes = "${width}x${height}"
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Resolution",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            resolutions.forEach { (label, size) ->
                val isSelected = label == currentRes
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Primary.copy(alpha = 0.2f)
                            else CardBackground
                        )
                        .clickable(enabled = enabled) {
                            onSelect(size.first, size.second)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) {
                            if (isSelected) Primary else TextSecondary
                        } else {
                            TextSecondary.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FpsSelector(
    fps: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean
) {
    val options = listOf(1, 15, 24, 30, 60)
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Frame Rate",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == fps
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Primary.copy(alpha = 0.2f)
                            else CardBackground
                        )
                        .clickable(enabled = enabled) { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${option}fps",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) {
                            if (isSelected) Primary else TextSecondary
                        } else {
                            TextSecondary.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CodecSelector(
    codec: VideoCodec,
    onSelect: (VideoCodec) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Codec",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodec.entries.forEach { option ->
                val isSelected = option == codec
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Primary.copy(alpha = 0.2f)
                            else CardBackground
                        )
                        .clickable(enabled = enabled) { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) {
                            if (isSelected) Primary else TextSecondary
                        } else {
                            TextSecondary.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { str ->
            str.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = CardBackground,
            disabledBorderColor = CardBackground.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.3f)
            )
        )
    }
}
