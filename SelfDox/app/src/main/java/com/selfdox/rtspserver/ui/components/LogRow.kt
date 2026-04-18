package com.selfdox.rtspserver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfdox.rtspserver.model.LogEntry
import com.selfdox.rtspserver.model.LogLevel
import com.selfdox.rtspserver.ui.theme.CardBackground
import com.selfdox.rtspserver.ui.theme.Error
import com.selfdox.rtspserver.ui.theme.LiveGreen
import com.selfdox.rtspserver.ui.theme.Primary
import com.selfdox.rtspserver.ui.theme.TextSecondary
import com.selfdox.rtspserver.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single log entry row.
 * DECISION: Per D12 — colored by log level.
 */
@Composable
fun LogRow(
    entry: LogEntry,
    modifier: Modifier = Modifier
) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> TextTertiary
        LogLevel.INFO -> LiveGreen
        LogLevel.WARN -> Primary
        LogLevel.ERROR -> Error
    }
    
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = timeFormat.format(Date(entry.timestamp))
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timestamp
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        
        // Level indicator
        Text(
            text = entry.level.name.first().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = levelColor
        )
        
        // Message
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
