package com.selfdox.rtspserver.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.library.view.OpenGlView

/**
 * Preview view wrapper for Compose.
 * DECISION: Per D11 — reuses OpenGlView from service, 16:9 aspect ratio.
 */
@Composable
fun StreamPreviewView(
    openGlView: OpenGlView?,
    modifier: Modifier = Modifier,
    onDispose: () -> Unit = {}
) {
    if (openGlView != null) {
        AndroidView(
            factory = { openGlView },
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        )
        
        DisposableEffect(Unit) {
            onDispose { onDispose() }
        }
    }
}
