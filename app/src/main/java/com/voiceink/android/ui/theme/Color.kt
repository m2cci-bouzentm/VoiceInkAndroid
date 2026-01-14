package com.voiceink.android.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Dark Theme Colors
object VoiceInkColors {
    // Primary gradient colors (vibrant blue)
    val Primary = Color(0xFF3B82F6)      // Blue-500
    val PrimaryLight = Color(0xFF60A5FA) // Blue-400
    val PrimaryDark = Color(0xFF2563EB)  // Blue-600
    
    // Secondary accent (cyan/teal for contrast)
    val Secondary = Color(0xFF06B6D4)
    val SecondaryLight = Color(0xFF22D3EE)
    
    // Background layers (rich dark)
    val Background = Color(0xFF0A0A0F)
    val BackgroundElevated = Color(0xFF0F1419)
    val Surface = Color(0xFF1A1F26)
    val SurfaceLight = Color(0xFF242C36)
    val SurfaceBright = Color(0xFF2E3844)
    
    // Text colors
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    
    // Status colors
    val Error = Color(0xFFEF4444)
    val ErrorDark = Color(0xFFDC2626)
    val Success = Color(0xFF10B981)
    val SuccessLight = Color(0xFF34D399)
    val Warning = Color(0xFFF59E0B)
    
    // Recording state
    val Recording = Color(0xFFEF4444)
    val RecordingGlow = Color(0x40EF4444)
    
    // Glass effect colors
    val GlassWhite = Color(0x15FFFFFF)
    val GlassBorder = Color(0x20FFFFFF)
    
    // Gradient colors for buttons/cards
    val GradientStart = Color(0xFF3B82F6)  // Blue-500
    val GradientMiddle = Color(0xFF2563EB) // Blue-600
    val GradientEnd = Color(0xFF1D4ED8)    // Blue-700
}

// Keep these for Material3 compatibility
val Blue80 = VoiceInkColors.PrimaryLight
val BlueGrey80 = VoiceInkColors.TextSecondary
val Cyan80 = VoiceInkColors.SecondaryLight

val Blue40 = VoiceInkColors.Primary
val BlueGrey40 = VoiceInkColors.TextMuted
val Cyan40 = VoiceInkColors.Secondary
