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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.voiceink.android.data.model.DownloadState
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.ModelProvider
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.services.OverlayService
import com.voiceink.android.services.TextInjectionService
import com.voiceink.android.ui.theme.VoiceInkColors
import com.voiceink.android.data.subscription.SubscriptionTier
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showProModal by remember { mutableStateOf(false) }

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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
                hasOverlayPermission = Settings.canDrawOverlays(context)
                
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiceInkColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                SettingsHeader(onBackClick = onNavigateBack)
            }

            // Usage & Subscription Section
            item {
                SectionHeader(
                    title = "Usage & Subscription",
                    icon = Icons.Outlined.Star
                )
            }

            item {
                UsageCard(
                    subscriptionTier = uiState.subscriptionTier,
                    localMinutesUsed = uiState.usageStats.localMinutesUsed,
                    cloudMinutesUsed = uiState.usageStats.cloudMinutesUsed,
                    lastResetTimestamp = uiState.usageStats.lastResetTimestamp,
                    onUpgradeClick = { showProModal = true }
                )
            }

            // Features Section
            item {
                SectionHeader(
                    title = "Features",
                    icon = Icons.Outlined.TouchApp
                )
            }

            item {
                SettingsCard {
                    FeatureToggleItem(
                        title = "Floating Button",
                        description = if (uiState.isOverlayEnabled && hasOverlayPermission)
                            "Record from any app with one tap"
                        else if (!hasOverlayPermission)
                            "Requires overlay permission"
                        else
                            "Show floating record button",
                        isEnabled = uiState.isOverlayEnabled && hasOverlayPermission,
                        onToggle = { enabled ->
                            if (enabled) {
                                if (hasOverlayPermission) {
                                    viewModel.setOverlayEnabled(true)
                                    OverlayService.start(context)
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
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = VoiceInkColors.GlassBorder
                    )
                    
                    FeatureToggleItem(
                        title = "Text Injection",
                        description = if (uiState.isAccessibilityEnabled)
                            "Auto-insert text at cursor"
                        else
                            "Enable accessibility service",
                        isEnabled = uiState.isAccessibilityEnabled,
                        showButton = !uiState.isAccessibilityEnabled,
                        buttonText = "Setup",
                        onButtonClick = { TextInjectionService.openAccessibilitySettings(context) },
                        onToggle = {}
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = VoiceInkColors.GlassBorder
                    )

                    FeatureToggleItem(
                        title = "Auto-Punctuation",
                        description = if (uiState.geminiApiKey.isBlank() && uiState.subscriptionTier != SubscriptionTier.PRO)
                            "Requires Gemini API key or Pro subscription"
                        else
                            "Uses Gemini to add punctuation & formatting",
                        isEnabled = uiState.isAutoPunctuationEnabled &&
                            (uiState.geminiApiKey.isNotBlank() || uiState.subscriptionTier == SubscriptionTier.PRO),
                        onToggle = { enabled ->
                            if (uiState.geminiApiKey.isNotBlank() || uiState.subscriptionTier == SubscriptionTier.PRO) {
                                viewModel.setAutoPunctuationEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // Models Section
            item {
                SectionHeader(
                    title = "Transcription Models",
                    icon = Icons.Outlined.Memory
                )
            }

            items(PredefinedModels.allModels) { model ->
                val isLocalModel = model is LocalModel
                ModelCard(
                    model = model,
                    isSelected = model.id == uiState.selectedModelId,
                    isDownloaded = model.id in uiState.downloadedModels,
                    downloadState = uiState.downloadStates[model.id] ?: DownloadState.Idle,
                    onClick = {
                        if (!isLocalModel || model.id in uiState.downloadedModels) {
                            viewModel.selectModel(model.id)
                        }
                    },
                    onDownloadClick = {
                        if (model is LocalModel) viewModel.downloadModel(model)
                    },
                    onDeleteClick = {
                        if (model is LocalModel) viewModel.deleteModel(model)
                    },
                    modelSize = if (model is LocalModel) viewModel.getModelSize(model) else 0L
                )
            }

            // API Keys Section
            item {
                SectionHeader(
                    title = "API Keys",
                    icon = Icons.Outlined.Key
                )
            }

            item {
                SettingsCard {
                    PremiumApiKeyField(
                        label = "Gemini API Key",
                        value = uiState.geminiApiKey,
                        onValueChange = viewModel::setGeminiApiKey,
                        placeholder = "Enter your Gemini API key"
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    PremiumApiKeyField(
                        label = "OpenAI API Key",
                        value = uiState.openaiApiKey,
                        onValueChange = viewModel::setOpenaiApiKey,
                        placeholder = "Enter your OpenAI API key"
                    )
                }
            }

            // Info Card
            item {
                InfoCard()
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .background(VoiceInkColors.SurfaceLight, CircleShape)
        ) {
            Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = VoiceInkColors.TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = VoiceInkColors.TextPrimary
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = VoiceInkColors.Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoiceInkColors.Primary
        )
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, VoiceInkColors.GlassBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

@Composable
private fun FeatureToggleItem(
    title: String,
    description: String,
    isEnabled: Boolean,
    showButton: Boolean = false,
    buttonText: String = "",
    onButtonClick: () -> Unit = {},
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VoiceInkColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextMuted
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        if (showButton) {
            Surface(
                onClick = onButtonClick,
                shape = RoundedCornerShape(12.dp),
                color = VoiceInkColors.Primary
            ) {
                Text(
                    text = buttonText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VoiceInkColors.Primary,
                    uncheckedThumbColor = VoiceInkColors.TextMuted,
                    uncheckedTrackColor = VoiceInkColors.SurfaceBright
                )
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: TranscriptionModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modelSize: Long
) {
    val isLocalModel = model is LocalModel
    val isDownloading = downloadState is DownloadState.Downloading || downloadState is DownloadState.Extracting
    val canSelect = !isLocalModel || isDownloaded

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) VoiceInkColors.Primary else VoiceInkColors.GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = canSelect, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                VoiceInkColors.Primary.copy(alpha = 0.1f)
            else
                VoiceInkColors.Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = when {
                            model.provider == ModelProvider.LOCAL -> VoiceInkColors.Secondary.copy(alpha = 0.2f)
                            model.provider == ModelProvider.GEMINI -> Color(0xFF4285F4).copy(alpha = 0.2f)
                            model.provider == ModelProvider.OPENAI -> Color(0xFF10A37F).copy(alpha = 0.2f)
                            else -> VoiceInkColors.SurfaceLight
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocalModel) Icons.Outlined.Memory else Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = when {
                        model.provider == ModelProvider.LOCAL -> VoiceInkColors.Secondary
                        model.provider == ModelProvider.GEMINI -> Color(0xFF4285F4)
                        model.provider == ModelProvider.OPENAI -> Color(0xFF10A37F)
                        else -> VoiceInkColors.TextMuted
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = VoiceInkColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when {
                        model.provider == ModelProvider.LOCAL -> if (modelSize > 0) "Offline • ${formatSize(modelSize)}" else "Offline"
                        model.provider == ModelProvider.GEMINI -> "Google Cloud"
                        model.provider == ModelProvider.OPENAI -> "OpenAI Cloud"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceInkColors.TextMuted
                )

                // Download progress
                AnimatedVisibility(visible = isDownloading) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (downloadState is DownloadState.Downloading)
                                    downloadState.progress / 100f
                                else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = VoiceInkColors.Primary,
                            trackColor = VoiceInkColors.SurfaceBright
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (downloadState is DownloadState.Downloading)
                                "Downloading ${downloadState.progress}%"
                            else "Extracting...",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoiceInkColors.Primary
                        )
                    }
                }

                if (downloadState is DownloadState.Error) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceInkColors.Error
                    )
                }
            }

            // Actions - show for local models only
            if (isLocalModel) {
                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = VoiceInkColors.Primary
                        )
                    }
                    isDownloaded -> {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = VoiceInkColors.Error
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                tint = VoiceInkColors.Primary
                            )
                        }
                    }
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
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
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumApiKeyField(
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
            color = VoiceInkColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { 
                Text(
                    placeholder,
                    color = VoiceInkColors.TextMuted
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        imageVector = if (isVisible)
                            Icons.Default.VisibilityOff
                        else
                            Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide" else "Show",
                        tint = VoiceInkColors.TextMuted
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VoiceInkColors.Primary,
                unfocusedBorderColor = VoiceInkColors.GlassBorder,
                focusedContainerColor = VoiceInkColors.SurfaceLight,
                unfocusedContainerColor = VoiceInkColors.SurfaceLight,
                cursorColor = VoiceInkColors.Primary,
                focusedTextColor = VoiceInkColors.TextPrimary,
                unfocusedTextColor = VoiceInkColors.TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .border(1.dp, VoiceInkColors.GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Getting API Keys",
                style = MaterialTheme.typography.titleSmall,
                color = VoiceInkColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Gemini: aistudio.google.com\nOpenAI: platform.openai.com",
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextMuted
            )
        }
    }
}

@Composable
private fun UsageCard(
    subscriptionTier: SubscriptionTier,
    localMinutesUsed: Float,
    cloudMinutesUsed: Float,
    lastResetTimestamp: Long,
    onUpgradeClick: () -> Unit
) {
    val isPro = subscriptionTier == SubscriptionTier.PRO
    val localLimit = if (isPro) Float.MAX_VALUE else 60f
    val cloudLimit = if (isPro) Float.MAX_VALUE else 5f

    // Calculate next reset date (first of next month)
    val nextResetDate = remember(lastResetTimestamp) {
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val resetDateStr = dateFormat.format(Date(nextResetDate))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(
                width = 1.dp,
                brush = if (isPro) Brush.horizontalGradient(
                    listOf(VoiceInkColors.Primary, VoiceInkColors.Secondary)
                ) else Brush.horizontalGradient(
                    listOf(VoiceInkColors.GlassBorder, VoiceInkColors.GlassBorder)
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Plan badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPro) VoiceInkColors.Primary else VoiceInkColors.SurfaceBright
                    ) {
                        Text(
                            text = if (isPro) "PRO" else "FREE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPro) Color.White else VoiceInkColors.TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isPro) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlimited usage",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoiceInkColors.Primary
                        )
                    }
                }

                if (!isPro) {
                    Surface(
                        onClick = onUpgradeClick,
                        shape = RoundedCornerShape(12.dp),
                        color = VoiceInkColors.Primary
                    ) {
                        Text(
                            text = "Upgrade",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isPro) {
                Spacer(modifier = Modifier.height(20.dp))

                // Local usage
                UsageProgressRow(
                    label = "Local transcription",
                    used = localMinutesUsed,
                    limit = localLimit,
                    color = VoiceInkColors.Secondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Cloud usage
                UsageProgressRow(
                    label = "Cloud transcription",
                    used = cloudMinutesUsed,
                    limit = cloudLimit,
                    color = VoiceInkColors.Primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reset date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Resets on",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoiceInkColors.TextMuted
                    )
                    Text(
                        text = resetDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = VoiceInkColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageProgressRow(
    label: String,
    used: Float,
    limit: Float,
    color: Color
) {
    val progress = (used / limit).coerceIn(0f, 1f)
    val isNearLimit = progress >= 0.8f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextSecondary
            )
            Text(
                text = "${String.format("%.1f", used)} / ${limit.toInt()} min",
                style = MaterialTheme.typography.bodySmall,
                color = if (isNearLimit) VoiceInkColors.Warning else VoiceInkColors.TextMuted,
                fontWeight = if (isNearLimit) FontWeight.Medium else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isNearLimit) VoiceInkColors.Warning else color,
            trackColor = VoiceInkColors.SurfaceBright
        )
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
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = VoiceInkColors.Surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = VoiceInkColors.TextMuted
                        )
                    }
                }

                // Pro badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(VoiceInkColors.Primary, VoiceInkColors.Secondary)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                // Features list
                ProFeatureItem(
                    icon = Icons.Outlined.AllInclusive,
                    title = "Unlimited Transcription",
                    description = "No monthly limits on local or cloud"
                )

                Spacer(modifier = Modifier.height(12.dp))

                ProFeatureItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Auto-Punctuation",
                    description = "AI-powered punctuation & formatting"
                )

                Spacer(modifier = Modifier.height(12.dp))

                ProFeatureItem(
                    icon = Icons.Outlined.Bolt,
                    title = "Priority Processing",
                    description = "Faster cloud transcription"
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Price
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

                // Subscribe button
                Button(
                    onClick = onSubscribe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoiceInkColors.Primary
                    )
                ) {
                    Text(
                        text = "Subscribe to Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cancel anytime. Restore purchases available.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoiceInkColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = VoiceInkColors.Primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VoiceInkColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = VoiceInkColors.TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VoiceInkColors.TextMuted
            )
        }
    }
}
