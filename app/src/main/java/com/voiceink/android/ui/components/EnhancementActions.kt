package com.voiceink.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.voiceink.android.domain.postprocessing.EnhancementType
import com.voiceink.android.ui.theme.VoiceInkColors

/**
 * Horizontal scrollable action bar for AI enhancement options
 */
@Composable
fun EnhancementActionBar(
    visible: Boolean,
    isEnhancing: Boolean,
    activeEnhancement: EnhancementType?,
    onEnhancementClick: (EnhancementType) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnhancementChip(
                label = "Clean Up",
                icon = Icons.Outlined.AutoFixHigh,
                isLoading = isEnhancing && activeEnhancement == EnhancementType.CLEAN_UP,
                isEnabled = !isEnhancing,
                onClick = { onEnhancementClick(EnhancementType.CLEAN_UP) }
            )

            EnhancementChip(
                label = "Summarize",
                icon = Icons.Outlined.Summarize,
                isLoading = isEnhancing && activeEnhancement == EnhancementType.SUMMARIZE,
                isEnabled = !isEnhancing,
                onClick = { onEnhancementClick(EnhancementType.SUMMARIZE) }
            )
        }
    }
}

@Composable
private fun EnhancementChip(
    label: String,
    icon: ImageVector,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = { if (isEnabled) onClick() },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = VoiceInkColors.Primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isEnabled) VoiceInkColors.TextSecondary else VoiceInkColors.TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) VoiceInkColors.TextSecondary else VoiceInkColors.TextMuted
                )
            }
        },
        enabled = isEnabled,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = VoiceInkColors.Surface,
            labelColor = VoiceInkColors.TextSecondary,
            disabledContainerColor = VoiceInkColors.Surface.copy(alpha = 0.5f),
            disabledLabelColor = VoiceInkColors.TextMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = VoiceInkColors.GlassBorder,
            disabledBorderColor = VoiceInkColors.GlassBorder.copy(alpha = 0.5f),
            enabled = isEnabled,
            selected = false
        )
    )
}
