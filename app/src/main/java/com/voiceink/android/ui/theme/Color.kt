package com.voiceink.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.voiceink.android.BuildConfig

// The Agent build gets its own blue identity; the original build keeps its green palette.
object VoiceInkColors {
    private val isAgent = BuildConfig.BUILD_TYPE == "agent"

    val Primary = Color(if (isAgent) 0xFF3B82F6 else 0xFF22C55E)
    val PrimaryLight = Color(if (isAgent) 0xFF60A5FA else 0xFF4ADE80)
    val PrimaryDark = Color(if (isAgent) 0xFF2563EB else 0xFF16A34A)

    val Secondary = Color(if (isAgent) 0xFF38BDF8 else 0xFF14B8A6)
    val SecondaryLight = Color(if (isAgent) 0xFF7DD3FC else 0xFF2DD4BF)

    val Background = Color(if (isAgent) 0xFF081426 else 0xFF0A0A0F)
    val BackgroundElevated = Color(if (isAgent) 0xFF0B1B32 else 0xFF0F1419)
    val Surface = Color(if (isAgent) 0xFF10243D else 0xFF1A1F26)
    val SurfaceLight = Color(if (isAgent) 0xFF17314F else 0xFF242C36)
    val SurfaceBright = Color(if (isAgent) 0xFF214363 else 0xFF2E3844)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Keep error and warning semantic; recolor green status accents for Agent.
    val Error = Color(0xFFEF4444)
    val ErrorDark = Color(0xFFDC2626)
    val Success = Color(if (isAgent) 0xFF60A5FA else 0xFF10B981)
    val SuccessLight = Color(if (isAgent) 0xFF93C5FD else 0xFF34D399)
    val Warning = Color(0xFFF59E0B)

    val Recording = Color(if (isAgent) 0xFF1D4ED8 else 0xFFEF4444)
    val RecordingGlow = Color(if (isAgent) 0x401D4ED8 else 0x40EF4444)

    val GlassWhite = Color(0x15FFFFFF)
    val GlassBorder = Color(0x20FFFFFF)

    val GradientStart = PrimaryLight
    val GradientMiddle = Primary
    val GradientEnd = Color(if (isAgent) 0xFF1E40AF else 0xFF15803D)
    val OpenAIProvider = Color(if (isAgent) 0xFF3B82F6 else 0xFF10A37F)
}

// Keep these for Material3 compatibility
val Blue80 = VoiceInkColors.PrimaryLight
val BlueGrey80 = VoiceInkColors.TextSecondary
val Cyan80 = VoiceInkColors.SecondaryLight

val Blue40 = VoiceInkColors.Primary
val BlueGrey40 = VoiceInkColors.TextMuted
val Cyan40 = VoiceInkColors.Secondary
