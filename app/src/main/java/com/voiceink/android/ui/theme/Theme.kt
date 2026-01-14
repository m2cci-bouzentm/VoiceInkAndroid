package com.voiceink.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    // Primary colors
    primary = VoiceInkColors.Primary,
    onPrimary = Color.White,
    primaryContainer = VoiceInkColors.PrimaryDark,
    onPrimaryContainer = VoiceInkColors.PrimaryLight,
    
    // Secondary colors
    secondary = VoiceInkColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = VoiceInkColors.SurfaceLight,
    onSecondaryContainer = VoiceInkColors.SecondaryLight,
    
    // Tertiary colors
    tertiary = VoiceInkColors.SecondaryLight,
    onTertiary = Color.Black,
    
    // Background colors
    background = VoiceInkColors.Background,
    onBackground = VoiceInkColors.TextPrimary,
    
    // Surface colors
    surface = VoiceInkColors.Surface,
    onSurface = VoiceInkColors.TextPrimary,
    surfaceVariant = VoiceInkColors.SurfaceLight,
    onSurfaceVariant = VoiceInkColors.TextSecondary,
    
    // Other colors
    error = VoiceInkColors.Error,
    onError = Color.White,
    errorContainer = VoiceInkColors.ErrorDark,
    onErrorContainer = Color.White,
    
    outline = VoiceInkColors.GlassBorder,
    outlineVariant = VoiceInkColors.SurfaceBright,
    
    inverseSurface = VoiceInkColors.TextPrimary,
    inverseOnSurface = VoiceInkColors.Background,
    inversePrimary = VoiceInkColors.PrimaryDark,
    
    surfaceTint = VoiceInkColors.Primary,
)

private val LightColorScheme = lightColorScheme(
    // For now, use same as dark - premium apps often are dark-first
    primary = VoiceInkColors.Primary,
    onPrimary = Color.White,
    primaryContainer = VoiceInkColors.PrimaryLight,
    onPrimaryContainer = VoiceInkColors.PrimaryDark,
    
    secondary = VoiceInkColors.Secondary,
    onSecondary = Color.White,
    
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A1A24),
    
    surface = Color.White,
    onSurface = Color(0xFF1A1A24),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    
    error = VoiceInkColors.Error,
    onError = Color.White,
)

@Composable
fun VoiceInkTheme(
    darkTheme: Boolean = true, // Default to dark theme for premium look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
