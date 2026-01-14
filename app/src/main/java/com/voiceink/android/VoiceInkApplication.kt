package com.voiceink.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceInkApplication : Application() {
    // Note: Overlay service is started from MainActivity, not here.
    // Starting it from Application crashes when the IME service launches
    // (background foreground service restriction on Android 14+)
}
