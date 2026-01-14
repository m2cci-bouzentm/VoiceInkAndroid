package com.voiceink.android

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.services.OverlayService
import com.voiceink.android.ui.navigation.VoiceInkNavHost
import com.voiceink.android.ui.theme.VoiceInkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Auto-start overlay service if enabled
        startOverlayServiceIfEnabled()
        
        setContent {
            VoiceInkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceInkNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check and start overlay when returning to app (e.g., after granting permission)
        startOverlayServiceIfEnabled()
    }

    private fun startOverlayServiceIfEnabled() {
        lifecycleScope.launch {
            val overlayEnabled = settingsRepository.overlayEnabled.first()
            val hasPermission = Settings.canDrawOverlays(this@MainActivity)
            
            if (overlayEnabled && hasPermission && !OverlayService.isRunning()) {
                OverlayService.start(this@MainActivity)
            }
        }
    }
}
