package com.voiceink.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to the shared Downloads folder.
 *
 * Android sandboxes logcat to the reading app's own logs, so a crash here is
 * invisible from anywhere else on the device — including a terminal. Dropping
 * the stack trace somewhere shared makes it readable without a host machine,
 * which matters because `adb pair` does not work from this phone's Termux.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val FILENAME_PREFIX = "voiceink-crash"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to record crash", e)
            }
            // Always chain: swallowing this would leave the process wedged
            // instead of dying, which is worse than the crash.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "$FILENAME_PREFIX-$stamp.txt"

        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }

        val body = buildString {
            appendLine("time:    $stamp")
            appendLine("thread:  ${thread.name}")
            appendLine("version: $version")
            appendLine("device:  ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
            appendLine()
            append(StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(body.toByteArray())
                }
                Log.e(TAG, "Crash written to Downloads/$name")
                return
            }
        }

        // Pre-Q, or MediaStore refused the insert.
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(dir, name).writeText(body)
        Log.e(TAG, "Crash written to ${File(dir, name).absolutePath}")
    }
}
