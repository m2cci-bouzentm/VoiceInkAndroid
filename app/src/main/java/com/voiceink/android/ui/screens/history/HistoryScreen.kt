package com.voiceink.android.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceink.android.data.database.TranscriptionEntity
import com.voiceink.android.ui.theme.VoiceInkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VoiceInkColors.Background,
                        VoiceInkColors.BackgroundElevated
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            HistoryHeader(
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onNavigateBack = onNavigateBack,
                onToggleSearch = viewModel::toggleSearch,
                onSearchQueryChange = viewModel::setSearchQuery,
                onDeleteAll = viewModel::showDeleteAllConfirmation,
                hasItems = uiState.transcriptions.isNotEmpty()
            )

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VoiceInkColors.Primary)
                    }
                }
                uiState.transcriptions.isEmpty() -> {
                    EmptyHistoryContent(
                        isSearching = uiState.searchQuery.isNotBlank()
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.transcriptions,
                            key = { it.id }
                        ) { entry ->
                            TranscriptionCard(
                                entry = entry,
                                isCopied = uiState.copiedId == entry.id,
                                onCopy = { viewModel.copyToClipboard(entry) },
                                onDelete = { viewModel.showDeleteConfirmation(entry) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (uiState.showDeleteConfirmation) {
            DeleteConfirmationDialog(
                title = "Delete Transcription",
                message = "Are you sure you want to delete this transcription?",
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::hideDeleteConfirmation
            )
        }

        // Delete all confirmation dialog
        if (uiState.showDeleteAllConfirmation) {
            DeleteConfirmationDialog(
                title = "Delete All History",
                message = "Are you sure you want to delete all transcription history? This cannot be undone.",
                onConfirm = viewModel::confirmDeleteAll,
                onDismiss = viewModel::hideDeleteAllConfirmation
            )
        }
    }
}

@Composable
private fun HistoryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onNavigateBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDeleteAll: () -> Unit,
    hasItems: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(40.dp)
                .background(VoiceInkColors.Surface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = VoiceInkColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title or Search field
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search transcriptions...",
                        color = VoiceInkColors.TextMuted
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VoiceInkColors.Surface,
                    unfocusedContainerColor = VoiceInkColors.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = VoiceInkColors.Primary,
                    focusedTextColor = VoiceInkColors.TextPrimary,
                    unfocusedTextColor = VoiceInkColors.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedVisibility(
            visible = !isSearchActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoiceInkColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Search button
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier
                .size(40.dp)
                .background(VoiceInkColors.Surface, CircleShape)
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (isSearchActive) "Close search" else "Search",
                tint = VoiceInkColors.TextPrimary
            )
        }

        // Delete all button (only show if there are items)
        if (hasItems) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDeleteAll,
                modifier = Modifier
                    .size(40.dp)
                    .background(VoiceInkColors.Surface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Delete all",
                    tint = VoiceInkColors.Error
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryContent(isSearching: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = VoiceInkColors.TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearching) "No results found" else "No transcriptions yet",
                style = MaterialTheme.typography.titleMedium,
                color = VoiceInkColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSearching) "Try a different search term" else "Start recording to create your first transcription",
                style = MaterialTheme.typography.bodyMedium,
                color = VoiceInkColors.TextMuted
            )
        }
    }
}

@Composable
private fun TranscriptionCard(
    entry: TranscriptionEntity,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceInkColors.Surface)
            .border(1.dp, VoiceInkColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Text content
        SelectionContainer {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoiceInkColors.TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metadata row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: timestamp, model, duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )
                Text(
                    text = " \u2022 ",
                    color = VoiceInkColors.TextMuted
                )
                Text(
                    text = entry.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " \u2022 ",
                    color = VoiceInkColors.TextMuted
                )
                Text(
                    text = formatDuration(entry.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: action buttons
            Row {
                // Copy button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCopied) VoiceInkColors.Success.copy(alpha = 0.2f) else VoiceInkColors.SurfaceLight)
                        .clickable { onCopy() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (isCopied) VoiceInkColors.Success else VoiceInkColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VoiceInkColors.SurfaceLight)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = VoiceInkColors.Error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = VoiceInkColors.TextPrimary
            )
        },
        text = {
            Text(
                text = message,
                color = VoiceInkColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = VoiceInkColors.Error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = VoiceInkColors.TextSecondary
                )
            }
        },
        containerColor = VoiceInkColors.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDuration(seconds: Float): String {
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}:${secs.toString().padStart(2, '0')}"
    } else {
        "0:${secs.toString().padStart(2, '0')}"
    }
}
