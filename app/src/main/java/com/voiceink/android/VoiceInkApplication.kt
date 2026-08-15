package com.voiceink.android

import android.app.Application
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceInkApplication : Application() {
    // Note: Overlay service is started from MainActivity, not here.
    // Starting it from Application crashes when the IME service launches
    // (background foreground service restriction on Android 14+)

    override fun onCreate() {
        super.onCreate()
        // Debug only: logcat is sandboxed per-app, so without this a crash on
        // the device is unreadable from anywhere but a host machine.
        // Uses FLAG_DEBUGGABLE rather than BuildConfig, matching the rest of
        // the codebase — BuildConfig generation is not enabled for this module.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            CrashLogger.install(this)
        }
    }
}
