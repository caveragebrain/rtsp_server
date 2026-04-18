package com.selfdox.rtspserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DECISION: Dark-only theme per D14 — no isSystemInDarkTheme() branching
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryVariant,
    onSecondaryContainer = Color.White,
    tertiary = Secondary,
    onTertiary = OnSecondary,
    error = Error,
    onError = OnError,
    errorContainer = Error.copy(alpha = 0.3f),
    onErrorContainer = Error,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = CardBorder,
    scrim = Color.Black
)

@Composable
fun SelfDoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SelfDoxTypography,
        content = content
    )
}
