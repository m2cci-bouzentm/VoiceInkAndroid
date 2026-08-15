package com.voiceink.android.ui.screens.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceink.android.data.audio.RecordingState
import com.voiceink.android.ui.components.AudioLevelIndicator
import com.voiceink.android.ui.components.EnhancementActionBar
import com.voiceink.android.ui.theme.VoiceInkColors
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val audioAmplitude by viewModel.audioAmplitude.collectAsState()

    var showOverlayHint by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(false) }

    if (showGuide) {
        OverlayGuideDialog(onDismiss = { showGuide = false })
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
    }

    LaunchedEffect(showOverlayHint) {
        if (showOverlayHint) {
            kotlinx.coroutines.delay(8000)
            showOverlayHint = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiceInkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Premium Top Bar
            PremiumTopBar(
                onGuideClick = { showGuide = true },
                onHistoryClick = onNavigateToHistory,
                onSettingsClick = onNavigateToSettings
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Floating button hint
                AnimatedVisibility(
                    visible = showOverlayHint,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    HintCard(
                        onClick = onNavigateToSettings,
                        onDismiss = { showOverlayHint = false }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Model selector
                selectedModel?.let { model ->
                    ModelChip(
                        modelName = model.name,
                        onClick = onNavigateToSettings
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transcription result area
                TranscriptionCard(
                    transcription = uiState.transcription,
                    enhancedText = uiState.enhancedText,
                    error = uiState.error,
                    recordingState = recordingState,
                    audioAmplitude = audioAmplitude,
                    onClearEnhanced = { viewModel.clearEnhancedText() },
                    modifier = Modifier.weight(1f)
                )

                // Enhancement actions - show when transcription exists
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    EnhancementActionBar(
                        visible = uiState.transcription.isNotBlank() && recordingState == RecordingState.IDLE,
                        isEnhancing = uiState.isEnhancing,
                        activeEnhancement = uiState.activeEnhancement,
                        onEnhancementClick = { viewModel.enhanceText(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Record button
                val isProcessing = recordingState == RecordingState.PROCESSING || uiState.isLoading
                PremiumRecordButton(
                    isRecording = recordingState == RecordingState.RECORDING,
                    isProcessing = isProcessing,
                    onClick = {
                        if (uiState.isLoading) {
                            Toast.makeText(
                                context,
                                "Transcription in progress. Please wait.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (viewModel.hasPermission()) {
                            viewModel.toggleRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCancel = {
                        viewModel.cancelRecording()
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun PremiumTopBar(
    onGuideClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "VoiceInk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoiceInkColors.TextPrimary
        )

        Row {
            IconButton(
                onClick = onGuideClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = VoiceInkColors.SurfaceLight,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = "How to use",
                    tint = VoiceInkColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = VoiceInkColors.SurfaceLight,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "History",
                    tint = VoiceInkColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = VoiceInkColors.SurfaceLight,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = VoiceInkColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun HintCard(
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = VoiceInkColors.SurfaceLight
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                VoiceInkColors.Primary,
                                VoiceInkColors.Secondary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enable Floating Button",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoiceInkColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Record from any app with one tap",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoiceInkColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun ModelChip(
    modelName: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = VoiceInkColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = VoiceInkColors.GlassBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(VoiceInkColors.Success, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelLarge,
                color = VoiceInkColors.TextSecondary
            )
        }
    }
}

@Composable
private fun TranscriptionCard(
    transcription: String,
    enhancedText: String?,
    error: String?,
    recordingState: RecordingState,
    audioAmplitude: Float,
    onClearEnhanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine which text to show
    val displayText = enhancedText ?: transcription
    val isShowingEnhanced = enhancedText != null
    // An empty idle card is just a large hollow rectangle, so drop its chrome.
    val isEmptyIdle = displayText.isEmpty() && error == null && recordingState == RecordingState.IDLE

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when {
                    isEmptyIdle -> Color.Transparent
                    isShowingEnhanced -> VoiceInkColors.Primary.copy(alpha = 0.5f)
                    else -> VoiceInkColors.GlassBorder
                },
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmptyIdle) Color.Transparent else VoiceInkColors.Surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Enhanced text indicator
            AnimatedVisibility(visible = isShowingEnhanced) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoiceInkColors.Primary.copy(alpha = 0.1f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enhanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = VoiceInkColors.Primary
                    )
                    TextButton(onClick = onClearEnhanced) {
                        Text(
                            text = "Show Original",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoiceInkColors.Primary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                if (displayText.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when {
                            error != null -> {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VoiceInkColors.Error,
                                    textAlign = TextAlign.Center
                                )
                            }
                            recordingState == RecordingState.RECORDING -> {
                                AudioLevelIndicator(
                                    amplitude = audioAmplitude,
                                    size = 120.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Listening...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VoiceInkColors.TextPrimary
                                )
                            }
                            recordingState == RecordingState.PROCESSING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = VoiceInkColors.Primary,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Transcribing...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VoiceInkColors.TextPrimary
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = VoiceInkColors.TextMuted
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Tap to start recording",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VoiceInkColors.TextMuted
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        SelectionContainer {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = VoiceInkColors.TextPrimary,
                                lineHeight = 28.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale2)
                .background(
                    color = VoiceInkColors.Recording.copy(alpha = alpha * 0.5f),
                    shape = CircleShape
                )
        )
        // Inner pulse
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(scale1)
                .background(
                    color = VoiceInkColors.Recording.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
        // Center dot
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(VoiceInkColors.Recording, CircleShape)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumRecordButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "button")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRecording) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "buttonScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(buttonScale)
        ) {
            // Glow effect
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    VoiceInkColors.Recording.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Main button
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(
                        elevation = if (isRecording) 16.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = if (isRecording) VoiceInkColors.Recording else VoiceInkColors.Primary,
                        spotColor = if (isRecording) VoiceInkColors.Recording else VoiceInkColors.Primary
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isRecording) {
                                listOf(
                                    Color(0xFFF87171),
                                    VoiceInkColors.Recording,
                                    Color(0xFFDC2626)
                                )
                            } else if (isProcessing) {
                                listOf(
                                    VoiceInkColors.SurfaceLight,
                                    VoiceInkColors.Surface
                                )
                            } else {
                                listOf(
                                    VoiceInkColors.PrimaryLight,
                                    VoiceInkColors.Primary,
                                    VoiceInkColors.PrimaryDark
                                )
                            }
                        )
                    )
                    .combinedClickable(
                        onClick = { if (!isProcessing) onClick() },
                        onLongClick = { if (isRecording) onCancel() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = VoiceInkColors.Primary
                    )
                } else {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Start recording",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // Cancel text - shown when recording or processing
        Box(
            modifier = Modifier
                .height(36.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isRecording || isProcessing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextButton(
                    onClick = onCancel
                ) {
                    Text(
                        text = "Cancel",
                        color = VoiceInkColors.Error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Hint text (reserve space to prevent layout jumps)
        Box(
            modifier = Modifier
                .height(20.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isRecording || isProcessing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = if (isProcessing) "Transcribing..." else "Long-press to cancel",
                    color = VoiceInkColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * How the floating overlay button behaves.
 *
 * The gestures are not discoverable — long-press does two different things
 * depending on whether a recording is in progress — so they are spelled out
 * here rather than left to be found by accident.
 */
@Composable
private fun OverlayGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = VoiceInkColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Using the floating button",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoiceInkColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Works from any app once enabled in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                GuideRow("Tap", "Start recording. The button turns red.")
                GuideRow("Tap again", "Stop and transcribe.")
                GuideRow(
                    "Long-press while recording",
                    "Abort. The audio is discarded and nothing is transcribed. " +
                        "You get a short vibration."
                )
                GuideRow(
                    "Long-press when idle",
                    "On the Termux destination: cut the agent's speech and " +
                        "interrupt what it is doing. Otherwise: clear the focused " +
                        "text field, which needs Text Injection enabled."
                )
                GuideRow("Drag", "Move the button anywhere on screen.")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Where the text goes is set by Transcript Destination " +
                        "in Settings: typed into the focused app, handed to a " +
                        "Termux script, or POSTed to a URL. If delivery fails it " +
                        "falls back to the clipboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Got it", color = VoiceInkColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun GuideRow(gesture: String, meaning: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = gesture,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = VoiceInkColors.TextPrimary,
            modifier = Modifier.width(120.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall,
            color = VoiceInkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}
