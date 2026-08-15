package com.voiceink.android.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.voiceink.android.data.model.DownloadState
import com.voiceink.android.domain.output.TranscriptDestination
import com.voiceink.android.domain.output.TranscriptOutputRouter
import com.voiceink.android.domain.model.CloudModel
import com.voiceink.android.domain.model.Language
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.ModelBadge
import com.voiceink.android.domain.model.ModelProvider
import com.voiceink.android.domain.model.ModelScoring
import com.voiceink.android.domain.model.ModelBenchmark
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.domain.model.WhisperLanguages
import androidx.compose.material.icons.outlined.Language
import com.voiceink.android.services.OverlayService
import com.voiceink.android.services.TextInjectionService
import com.voiceink.android.ui.theme.VoiceInkColors
import com.voiceink.android.data.subscription.SubscriptionTier
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showProModal by remember { mutableStateOf(false) }
    var pendingLargeModelDownload by remember { mutableStateOf<LocalModel?>(null) }

    var hasRunCommandPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                TERMUX_RUN_COMMAND_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val runCommandPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRunCommandPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Termux permission denied — transcripts will fall back to the clipboard",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.setOverlayEnabled(true)
            OverlayService.start(context)
        } else {
            Toast.makeText(
                context,
                "Microphone permission required for floating button",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Pro Features Modal
    if (showProModal) {
        ProFeaturesModal(
            onDismiss = { showProModal = false },
            onSubscribe = {
                showProModal = false
                // TODO: Trigger RevenueCat purchase flow
            }
        )
    }

    // Large Model Download Warning Dialog
    pendingLargeModelDownload?.let { model ->
        LargeModelDownloadDialog(
            model = model,
            modelSize = viewModel.getModelSize(model),
            onConfirm = {
                viewModel.downloadModel(model)
                pendingLargeModelDownload = null
            },
            onDismiss = {
                pendingLargeModelDownload = null
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                
                if (uiState.isOverlayEnabled && hasOverlayPermission) {
                    OverlayService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoiceInkColors.Background,
                    titleContentColor = VoiceInkColors.TextPrimary,
                    navigationIconContentColor = VoiceInkColors.TextPrimary
                )
            )
        },
        containerColor = VoiceInkColors.Background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Usage Card (compact)
            item {
                CompactUsageCard(
                    subscriptionTier = uiState.subscriptionTier,
                    localMinutesUsed = uiState.usageStats.localMinutesUsed,
                    cloudMinutesUsed = uiState.usageStats.cloudMinutesUsed,
                    onUpgradeClick = { showProModal = true }
                )
            }

            // Transcription Model Section
            item {
                SectionHeader(title = "Transcription Model")
            }

            items(PredefinedModels.featuredModels.filter { 
                // Filter out broken models
                it !is LocalModel || !it.isBroken
            }) { model ->
                val isLocalModel = model is LocalModel
                val modelSize = if (model is LocalModel) viewModel.getModelSize(model) else 0L
                
                val hasRequiredApiKey = when {
                    model is CloudModel && model.provider == ModelProvider.GEMINI -> uiState.geminiApiKey.isNotBlank()
                    model is CloudModel && model.provider == ModelProvider.OPENAI -> uiState.openaiApiKey.isNotBlank()
                    model is CloudModel && model.provider == ModelProvider.OPENROUTER ->
                        uiState.openRouterApiKey.isNotBlank() && uiState.openRouterModelId.isNotBlank()
                    else -> true
                }
                val canUseCloudModel = hasRequiredApiKey
                
                ModelItem(
                    model = model,
                    isSelected = model.id == uiState.selectedModelId,
                    isDownloaded = model.id in uiState.downloadedModels,
                    downloadState = uiState.downloadStates[model.id] ?: DownloadState.Idle,
                    isEnabled = if (model is CloudModel) canUseCloudModel else true,
                    onClick = {
                        val canSelect = when {
                            isLocalModel -> model.id in uiState.downloadedModels
                            model is CloudModel -> canUseCloudModel
                            else -> true
                        }
                        if (canSelect) {
                            viewModel.selectModel(model.id)
                        }
                    },
                    onDownloadClick = {
                        if (model is LocalModel) {
                            if (modelSize > 500_000_000L) {
                                pendingLargeModelDownload = model
                            } else {
                                viewModel.downloadModel(model)
                            }
                        }
                    },
                    onDeleteClick = {
                        if (model is LocalModel) viewModel.deleteModel(model)
                    }
                )
            }

            // Language Selection
            val selectedModel = PredefinedModels.allModels.find { it.id == uiState.selectedModelId }
            val showLanguageSelector = when (selectedModel) {
                is LocalModel -> selectedModel.supportsLanguageSelection
                is CloudModel -> true
                else -> false
            }
            if (showLanguageSelector) {
                item {
                    SectionHeader(title = "Language")
                }

                item {
                    LanguageSelector(
                        selectedLanguageCode = uiState.selectedLanguage,
                        onLanguageSelected = { viewModel.setSelectedLanguage(it.code) }
                    )
                }
            }

            // Features Section
            item {
                SectionHeader(title = "Features")
            }

            item {
                SettingsGroup {
                    SettingsToggleRow(
                        title = "Floating Button",
                        subtitle = when {
                            uiState.isOverlayEnabled && hasOverlayPermission -> "Enabled"
                            !hasOverlayPermission -> "Tap to grant permission"
                            else -> "Record from any app"
                        },
                        infoText = "Tap to record/stop. Long-press while recording cancels. Long-press while idle clears the focused input (requires Text Injection).",
                        isChecked = uiState.isOverlayEnabled && hasOverlayPermission,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (hasOverlayPermission) {
                                    if (hasMicPermission) {
                                        viewModel.setOverlayEnabled(true)
                                        OverlayService.start(context)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            } else {
                                viewModel.setOverlayEnabled(false)
                                OverlayService.stop(context)
                            }
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsNavigationRow(
                        title = "Text Injection",
                        subtitle = if (uiState.isAccessibilityEnabled) "Enabled" else "Setup required",
                        showChevron = !uiState.isAccessibilityEnabled,
                        trailing = if (uiState.isAccessibilityEnabled) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = VoiceInkColors.Success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else null,
                        onClick = { TextInjectionService.openAccessibilitySettings(context) }
                    )
                    
                    SettingsDivider()
                    
                    val isLocalModelSelected = selectedModel is LocalModel
                    val autoPunctuationSubtitle = when {
                        !isLocalModelSelected -> "Only for local models"
                        uiState.geminiApiKey.isBlank() -> "Requires Gemini API key"
                        else -> "AI-powered formatting"
                    }
                    SettingsToggleRow(
                        title = "Auto-Punctuation",
                        subtitle = autoPunctuationSubtitle,
                        infoText = "Runs after local model transcriptions. Uses Gemini 2.0 Flash to add punctuation and capitalization. Requires a Gemini API key; if missing or it fails, text stays unchanged. Cloud models skip this step.",
                        isChecked = uiState.isAutoPunctuationEnabled && isLocalModelSelected && uiState.geminiApiKey.isNotBlank(),
                        enabled = isLocalModelSelected && uiState.geminiApiKey.isNotBlank(),
                        onCheckedChange = { viewModel.setAutoPunctuationEnabled(it) }
                    )
                }
            }

            // Transcript Destination Section
            item {
                SectionHeader(title = "Transcript Destination")
            }

            item {
                SettingsGroup {
                    TranscriptDestinationSelector(
                        selected = uiState.transcriptDestination,
                        onSelect = { destination ->
                            viewModel.setTranscriptDestination(destination)
                            if (destination == TranscriptDestination.TERMUX_SCRIPT &&
                                !hasRunCommandPermission
                            ) {
                                runCommandPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
                            }
                        }
                    )

                    when (uiState.transcriptDestination) {
                        TranscriptDestination.TERMUX_SCRIPT -> {
                            SettingsDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            PlainTextField(
                                label = "Script path",
                                value = uiState.termuxScriptPath,
                                onValueChange = { viewModel.setTermuxScriptPath(it) },
                                placeholder = TranscriptOutputRouter.DEFAULT_SCRIPT_PATH
                            )
                            if (!hasRunCommandPermission) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Termux permission not granted — transcripts will " +
                                        "fall back to the clipboard. Tap \"Run Termux script\" " +
                                        "again to be asked.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoiceInkColors.Warning,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The transcript is passed as the first argument. " +
                                    "Requires the F-Droid build of Termux — the Play Store " +
                                    "build has no RUN_COMMAND service. In Termux, run " +
                                    "'termux-setup-storage' once and allow external apps in " +
                                    "~/.termux/termux.properties.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoiceInkColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        TranscriptDestination.HTTP_POST -> {
                            SettingsDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            PlainTextField(
                                label = "URL",
                                value = uiState.transcriptPostUrl,
                                onValueChange = { viewModel.setTranscriptPostUrl(it) },
                                placeholder = "http://127.0.0.1:8765/transcript"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sent as a plain-text POST body.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoiceInkColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        TranscriptDestination.TEXT_INJECTION -> Unit
                    }
                }
            }

            // API Keys Section
            item {
                SectionHeader(title = "API Keys")
            }

            item {
                SettingsGroup {
                    Spacer(modifier = Modifier.height(8.dp))
                    ApiKeyField(
                        label = "Gemini",
                        value = uiState.geminiApiKey,
                        onValueChange = viewModel::setGeminiApiKey,
                        placeholder = "Enter API key"
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ApiKeyField(
                        label = "OpenAI",
                        value = uiState.openaiApiKey,
                        onValueChange = viewModel::setOpenaiApiKey,
                        placeholder = "Enter API key"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ApiKeyField(
                        label = "OpenRouter",
                        value = uiState.openRouterApiKey,
                        onValueChange = viewModel::setOpenRouterApiKey,
                        placeholder = "Enter API key"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Typed, not picked: OpenRouter fronts hundreds of models and
                    // the list changes constantly, so any bundled dropdown would
                    // be stale and mostly irrelevant to a given user.
                    PlainTextField(
                        label = "OpenRouter model",
                        value = uiState.openRouterModelId,
                        onValueChange = viewModel::setOpenRouterModelId,
                        placeholder = "google/gemini-2.5-flash"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Must accept audio input. Browse at openrouter.ai/models",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceInkColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Get API Keys hint
            item {
                Text(
                    text = "Get keys: aistudio.google.com • platform.openai.com • openrouter.ai/keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ============================================
// SECTION HEADER
// ============================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = VoiceInkColors.TextMuted,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 6.dp
        )
    )
}

// ============================================
// SETTINGS GROUP (Card wrapper)
// ============================================

@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = VoiceInkColors.Surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = VoiceInkColors.GlassBorder.copy(alpha = 0.5f)
    )
}

// ============================================
// SETTINGS ROWS
// ============================================

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    infoText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) VoiceInkColors.TextPrimary else VoiceInkColors.TextMuted
                )
                if (infoText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    InfoTooltip(text = infoText)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextMuted
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VoiceInkColors.Primary,
                uncheckedThumbColor = VoiceInkColors.TextMuted,
                uncheckedTrackColor = VoiceInkColors.SurfaceBright
            )
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = VoiceInkColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextMuted
            )
        }
        
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = VoiceInkColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================
// MODEL ITEM (Simplified)
// ============================================

@Composable
private fun ModelItem(
    model: TranscriptionModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    downloadState: DownloadState,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isLocalModel = model is LocalModel
    val isDownloading = downloadState is DownloadState.Downloading || downloadState is DownloadState.Extracting
    val canSelect = isEnabled && (!isLocalModel || isDownloaded)
    var showBenchmarks by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> VoiceInkColors.Primary.copy(alpha = 0.1f)
            else -> VoiceInkColors.Surface
        },
        tonalElevation = if (isSelected) 0.dp else 1.dp,
        onClick = { if (canSelect) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = when (model.provider) {
                            ModelProvider.LOCAL -> VoiceInkColors.Secondary.copy(alpha = 0.15f)
                            ModelProvider.GEMINI -> Color(0xFF4285F4).copy(alpha = 0.15f)
                            ModelProvider.OPENAI -> Color(0xFF10A37F).copy(alpha = 0.15f)
                            ModelProvider.OPENROUTER -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocalModel) Icons.Outlined.Memory else Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = when (model.provider) {
                        ModelProvider.LOCAL -> VoiceInkColors.Secondary
                        ModelProvider.GEMINI -> Color(0xFF4285F4)
                        ModelProvider.OPENAI -> Color(0xFF10A37F)
                        ModelProvider.OPENROUTER -> Color(0xFF8B5CF6)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Model info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isEnabled) VoiceInkColors.TextPrimary else VoiceInkColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (model.badge != ModelBadge.NONE) {
                        SmallBadge(badge = model.badge)
                    }
                }
                
                Text(
                    text = listOfNotNull(
                        model.description,
                        compactScoreSummary(model.benchmark)
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (model is CloudModel && !isEnabled) {
                    Text(
                        text = "API key required",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceInkColors.Warning
                    )
                }

                if (showBenchmarks) {
                    Spacer(modifier = Modifier.height(8.dp))

                    ModelMetricRow(
                        label = "Accuracy",
                        score = ModelScoring.accuracyScore(model.benchmark),
                        detail = formatAccuracyDetail(model.benchmark),
                        barColor = Color(0xFFFFB800)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ModelMetricRow(
                        label = "Speed",
                        score = ModelScoring.speedScore(model.benchmark),
                        detail = formatSpeedDetail(model.benchmark),
                        barColor = VoiceInkColors.Success
                    )
                }

                // Download progress
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (downloadState is DownloadState.Downloading)
                                downloadState.progress / 100f
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = VoiceInkColors.Primary,
                        trackColor = VoiceInkColors.SurfaceBright
                    )
                }

                if (downloadState is DownloadState.Error) {
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceInkColors.Error
                    )
                }
            }

            if (model.benchmark != null) {
                IconButton(
                    onClick = { showBenchmarks = !showBenchmarks },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (showBenchmarks) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (showBenchmarks) "Hide benchmarks" else "Show benchmarks",
                        tint = VoiceInkColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Action button
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = VoiceInkColors.Primary
                    )
                }
                isLocalModel && !isDownloaded -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download",
                            tint = VoiceInkColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                isLocalModel && isDownloaded && !isSelected -> {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = VoiceInkColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                isSelected -> {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(VoiceInkColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallBadge(badge: ModelBadge) {
    val (text, color) = when (badge) {
        ModelBadge.FASTEST -> "Fast" to VoiceInkColors.Success
        ModelBadge.RECOMMENDED -> "Best" to VoiceInkColors.Primary
        ModelBadge.MOST_ACCURATE -> "Accurate" to Color(0xFFFFB800)
        ModelBadge.ENGLISH_BEST -> "EN" to VoiceInkColors.Secondary
        ModelBadge.NONE -> return
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ModelMetricRow(
    label: String,
    score: Int?,
    detail: String,
    barColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VoiceInkColors.TextMuted,
                modifier = Modifier.width(64.dp)
            )

            MetricBar(
                score = score,
                barColor = barColor,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = score?.let { "$it/5" } ?: "N/A",
                style = MaterialTheme.typography.labelSmall,
                color = VoiceInkColors.TextMuted
            )
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = VoiceInkColors.TextMuted,
            modifier = Modifier.padding(start = 64.dp, top = 2.dp)
        )
    }
}

@Composable
private fun MetricBar(
    score: Int?,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val clampedScore = score?.coerceIn(0, 5) ?: 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i <= clampedScore) barColor else VoiceInkColors.SurfaceBright
                    )
            )
        }
    }
}

/** One-line "A 3/5 · S 4/5" for the collapsed row; null when nothing is known. */
private fun compactScoreSummary(benchmark: ModelBenchmark?): String? {
    val accuracy = ModelScoring.accuracyScore(benchmark)
    val speed = ModelScoring.speedScore(benchmark)
    if (accuracy == null && speed == null) return null
    return listOfNotNull(
        accuracy?.let { "A $it/5" },
        speed?.let { "S $it/5" }
    ).joinToString(" · ")
}

private fun formatAccuracyDetail(benchmark: ModelBenchmark?): String {
    val wer = benchmark?.wer ?: return "N/A"
    val dataset = benchmark.werDataset
    val werText = String.format(Locale.US, "%.2f", wer)
    return if (dataset.isNullOrBlank()) {
        "WER $werText%"
    } else {
        "WER $werText% ($dataset)"
    }
}

private fun formatSpeedDetail(benchmark: ModelBenchmark?): String {
    benchmark?.rtfx?.let { rtfx ->
        val text = String.format(Locale.US, "%.0f", rtfx)
        return "RTFx $text"
    }
    benchmark?.avgSecPerFile?.let { seconds ->
        val text = String.format(Locale.US, "%.1f", seconds)
        return "Avg ${text}s/file"
    }
    benchmark?.relativeLatency?.let { rel ->
        val text = String.format(Locale.US, "%.1f", rel)
        return "Rel. latency ${text}x"
    }
    benchmark?.tokensPerSecond?.let { tps ->
        val tpsText = String.format(Locale.US, "%.1f", tps)
        val ttft = benchmark.ttftMs
        return if (ttft != null) {
            val ttftText = String.format(Locale.US, "%.0f", ttft)
            "TTFT ${ttftText}ms • ${tpsText} tok/s"
        } else {
            "${tpsText} tok/s"
        }
    }
    benchmark?.paramsM?.let { params ->
        return "Params ${params}M"
    }
    return "N/A"
}

// ============================================
// COMPACT USAGE CARD
// ============================================

@Composable
private fun CompactUsageCard(
    subscriptionTier: SubscriptionTier,
    localMinutesUsed: Float,
    cloudMinutesUsed: Float,
    onUpgradeClick: () -> Unit
) {
    val isPro = subscriptionTier == SubscriptionTier.PRO
    
    if (isPro) {
        // Pro users: just show a simple badge
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = VoiceInkColors.Primary.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    tint = VoiceInkColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pro • Unlimited usage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoiceInkColors.Primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        // Free users: show usage bars
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = VoiceInkColors.Surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Free Plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = VoiceInkColors.TextMuted
                    )
                    
                    Surface(
                        onClick = onUpgradeClick,
                        shape = RoundedCornerShape(8.dp),
                        color = VoiceInkColors.Primary
                    ) {
                        Text(
                            text = "Upgrade",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Local usage
                MiniUsageBar(
                    label = "Local",
                    used = localMinutesUsed,
                    limit = 60f,
                    color = VoiceInkColors.Secondary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cloud usage
                MiniUsageBar(
                    label = "Cloud",
                    used = cloudMinutesUsed,
                    limit = 5f,
                    color = VoiceInkColors.Primary
                )
            }
        }
    }
}

@Composable
private fun MiniUsageBar(
    label: String,
    used: Float,
    limit: Float,
    color: Color
) {
    val progress = (used / limit).coerceIn(0f, 1f)
    val isNearLimit = progress >= 0.8f
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoiceInkColors.TextMuted,
            modifier = Modifier.width(40.dp)
        )
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isNearLimit) VoiceInkColors.Warning else color,
            trackColor = VoiceInkColors.SurfaceBright
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${used.toInt()}/${limit.toInt()}m",
            style = MaterialTheme.typography.labelSmall,
            color = if (isNearLimit) VoiceInkColors.Warning else VoiceInkColors.TextMuted
        )
    }
}

// ============================================
// API KEY FIELD (Simplified)
// ============================================

/**
 * Where finished transcripts go. The Termux option is the Android equivalent of
 * Handy's `external_script`: hand the text to a script instead of typing it into
 * whatever happens to be focused.
 */
@Composable
private fun TranscriptDestinationSelector(
    selected: TranscriptDestination,
    onSelect: (TranscriptDestination) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        TranscriptDestination.entries.forEach { destination ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(destination) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = VoiceInkColors.Primary,
                        unselectedColor = VoiceInkColors.TextMuted
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoiceInkColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun PlainTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = VoiceInkColors.TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = VoiceInkColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VoiceInkColors.Primary,
                unfocusedBorderColor = VoiceInkColors.GlassBorder,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = VoiceInkColors.Primary,
                focusedTextColor = VoiceInkColors.TextPrimary,
                unfocusedTextColor = VoiceInkColors.TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    var isVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = VoiceInkColors.TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { 
                Text(placeholder, color = VoiceInkColors.TextMuted, style = MaterialTheme.typography.bodyMedium) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = VoiceInkColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VoiceInkColors.Primary,
                unfocusedBorderColor = VoiceInkColors.GlassBorder,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = VoiceInkColors.Primary,
                focusedTextColor = VoiceInkColors.TextPrimary,
                unfocusedTextColor = VoiceInkColors.TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ============================================
// LANGUAGE SELECTOR
// ============================================

@Composable
private fun LanguageSelector(
    selectedLanguageCode: String,
    onLanguageSelected: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedLanguage = WhisperLanguages.findByCode(selectedLanguageCode) ?: WhisperLanguages.AUTO_DETECT

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            WhisperLanguages.COMMON
        } else {
            WhisperLanguages.ALL.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = VoiceInkColors.Surface,
        tonalElevation = 1.dp,
        onClick = { expanded = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = VoiceInkColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = selectedLanguage.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = VoiceInkColors.TextPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoTooltip(
                    text = "Works for local Whisper models and cloud models. Auto-detect lets the model decide. For OpenAI, we send the language code; for Gemini we provide a language hint."
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = VoiceInkColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false; searchQuery = "" }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = VoiceInkColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search...", color = VoiceInkColors.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VoiceInkColors.Primary,
                            unfocusedBorderColor = VoiceInkColors.GlassBorder,
                            cursorColor = VoiceInkColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLanguages) { language ->
                            val isSelected = language.code == selectedLanguageCode
                            Surface(
                                onClick = {
                                    onLanguageSelected(language)
                                    expanded = false
                                    searchQuery = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) VoiceInkColors.Primary.copy(alpha = 0.1f) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = language.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) VoiceInkColors.Primary else VoiceInkColors.TextPrimary
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = VoiceInkColors.Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTooltip(
    text: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info",
                tint = VoiceInkColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextPrimary,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 280.dp)
            )
        }
    }
}

// ============================================
// PRO FEATURES MODAL
// ============================================

@Composable
private fun ProFeaturesModal(
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = VoiceInkColors.TextMuted)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(VoiceInkColors.Primary, VoiceInkColors.Secondary)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VoiceInk Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoiceInkColors.TextPrimary
                )

                Text(
                    text = "Unlock unlimited transcription",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoiceInkColors.TextMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                ProFeatureRow(Icons.Outlined.AllInclusive, "Unlimited transcription")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow(Icons.Outlined.AutoAwesome, "Auto-punctuation")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow(Icons.Outlined.Bolt, "Priority processing")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "$4.99/month",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoiceInkColors.TextPrimary
                )
                Text(
                    text = "or $39.99/year (save 33%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VoiceInkColors.Primary)
                ) {
                    Text("Subscribe", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Cancel anytime",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoiceInkColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun ProFeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = VoiceInkColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VoiceInkColors.TextPrimary)
    }
}

// ============================================
// LARGE MODEL DOWNLOAD DIALOG
// ============================================

@Composable
private fun LargeModelDownloadDialog(
    model: LocalModel,
    modelSize: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = VoiceInkColors.Warning,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Large Download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoiceInkColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${model.name} is ${formatSize(modelSize)}. WiFi recommended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoiceInkColors.TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VoiceInkColors.Primary)
                    ) {
                        Text("Download")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
