package com.selfdox.rtspserver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.selfdox.rtspserver.model.CameraInfo
import com.selfdox.rtspserver.ui.theme.CardBackground
import com.selfdox.rtspserver.ui.theme.Primary
import com.selfdox.rtspserver.ui.theme.TextSecondary

/**
 * Camera selector with chips for each available camera.
 * DECISION: Per D2 — displays all enumerated cameras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraSelector(
    cameras: List<CameraInfo>,
    selectedId: String,
    onCameraSelected: (CameraInfo) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Camera",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cameras.forEach { camera ->
                CameraChip(
                    camera = camera,
                    isSelected = camera.id == selectedId,
                    onClick = { onCameraSelected(camera) },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun CameraChip(
    camera: CameraInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = if (isSelected) {
        Primary.copy(alpha = 0.2f)
    } else {
        CardBackground
    }
    
    val textColor = if (isSelected) {
        Primary
    } else {
        TextSecondary
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = camera.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) textColor else textColor.copy(alpha = 0.5f)
        )
    }
}
