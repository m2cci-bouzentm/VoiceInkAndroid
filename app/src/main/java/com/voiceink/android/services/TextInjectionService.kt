package com.voiceink.android.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accessibility Service for injecting transcribed text into focused input fields
 *
 * This service allows VoiceInk to insert transcribed text directly into any app's
 * text field (Messenger, WhatsApp, Notes, etc.) without needing to copy/paste.
 *
 * User must enable this service in Settings > Accessibility > VoiceInk
 */
class TextInjectionService : AccessibilityService() {

    companion object {
        private const val TAG = "TextInjectionService"
        
        // Double-press detection timing
        private const val DOUBLE_PRESS_TIMEOUT_MS = 400L

        // Singleton instance for checking service status and injecting text
        private var instance: TextInjectionService? = null

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
        
        // Volume shortcut enabled state
        private val _volumeShortcutEnabled = MutableStateFlow(true)
        val volumeShortcutEnabled: StateFlow<Boolean> = _volumeShortcutEnabled.asStateFlow()
        
        fun setVolumeShortcutEnabled(enabled: Boolean) {
            _volumeShortcutEnabled.value = enabled
        }

        /**
         * Check if the accessibility service is enabled
         */
        fun isServiceEnabled(): Boolean = instance != null

        /**
         * Inject text into the currently focused input field
         * @return true if injection was successful
         */
        fun injectText(text: String): Boolean {
            return instance?.performTextInjection(text) ?: false
        }

        /**
         * Clear the currently focused input field
         * @return true if clearing was successful
         */
        fun clearFocusedInput(): Boolean {
            return instance?.performClearInput() ?: false
        }

        /**
         * Open accessibility settings for user to enable the service
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    private var lastFocusedNode: AccessibilityNodeInfo? = null
    
    // Volume button double-press detection
    private var lastVolumeUpPressTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true

        // Configure the service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.DEFAULT

            notificationTimeout = 100
        }
        serviceInfo = info

        Log.d(TAG, "TextInjectionService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Track the currently focused view
                val source = event.source
                if (source != null && source.isEditable) {
                    lastFocusedNode?.recycle()
                    lastFocusedNode = source
                    Log.d(TAG, "Focused on editable view: ${source.className}")
                } else {
                    source?.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Clear focused node when window changes
                lastFocusedNode?.recycle()
                lastFocusedNode = null
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "TextInjectionService interrupted")
    }
    
    /**
     * Detect volume button presses for shortcuts.
     * Double-press Volume Up = Toggle recording via OverlayService
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || !_volumeShortcutEnabled.value) {
            return super.onKeyEvent(event)
        }
        
        // Only handle Volume Up key
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastVolumeUpPressTime < DOUBLE_PRESS_TIMEOUT_MS) {
                // Double-press detected!
                Log.d(TAG, "Double-press Volume Up detected - triggering recording")
                lastVolumeUpPressTime = 0L // Reset to prevent triple-press
                
                // Toggle recording via OverlayService
                triggerOverlayRecording()
                
                // Consume the event so volume doesn't change
                return true
            } else {
                lastVolumeUpPressTime = currentTime
            }
        }
        
        // Don't consume single presses - let volume change normally
        return super.onKeyEvent(event)
    }
    
    /**
     * Trigger recording toggle in OverlayService
     */
    private fun triggerOverlayRecording() {
        try {
            // Send broadcast to OverlayService to toggle recording
            val intent = Intent("com.voiceink.android.TOGGLE_RECORDING")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "Sent toggle recording broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger overlay recording", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isEnabled.value = false
        lastFocusedNode?.recycle()
        lastFocusedNode = null
        Log.d(TAG, "TextInjectionService destroyed")
    }

    /**
     * Clear the currently focused input field
     */
    private fun performClearInput(): Boolean {
        Log.d(TAG, "Attempting to clear focused input")

        val focusedNode = findFocusedEditableNode()

        if (focusedNode != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        ""
                    )
                    val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    Log.d(TAG, "Clear input result: $result")
                    if (!focusedNode.equals(lastFocusedNode)) {
                        focusedNode.recycle()
                    }
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear input", e)
            }
            if (!focusedNode.equals(lastFocusedNode)) {
                focusedNode.recycle()
            }
        }

        return false
    }

    /**
     * Inject text into the currently focused input field
     */
    private fun performTextInjection(text: String): Boolean {
        Log.d(TAG, "Attempting to inject text: ${text.take(50)}...")

        // Try to find the currently focused editable node
        val focusedNode = findFocusedEditableNode()

        if (focusedNode != null) {
            val success = insertTextIntoNode(focusedNode, text)
            if (!focusedNode.equals(lastFocusedNode)) {
                focusedNode.recycle()
            }
            if (success) {
                Log.d(TAG, "Text injection successful via direct insertion")
                return true
            }
        }

        // Fallback: Use clipboard + paste
        Log.d(TAG, "Falling back to clipboard paste method")
        return pasteViaClipboard(text)
    }

    /**
     * Find the currently focused editable node
     */
    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // First try the cached focused node
        val cachedNode = lastFocusedNode
        if (cachedNode != null) {
            try {
                cachedNode.refresh()
                if (cachedNode.isEditable && cachedNode.isFocused) {
                    return cachedNode
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cached node invalid", e)
            }
        }

        // Search for focused input in the current window
        val rootNode = rootInActiveWindow ?: return null

        return findEditableNode(rootNode)
    }

    /**
     * Recursively find an editable focused node
     */
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child)
            child.recycle()
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Insert text directly into a node (appends to existing text)
     */
    private fun insertTextIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Get existing text, being careful to exclude placeholder/hint text
                val existingText = getActualTextContent(node)
                
                // Build final text: append if there's existing content, otherwise just use new text
                val finalText = if (existingText.isNotEmpty()) {
                    // Add a space separator if existing text doesn't end with whitespace
                    if (existingText.last().isWhitespace()) {
                        "$existingText$text"
                    } else {
                        "$existingText $text"
                    }
                } else {
                    text
                }
                
                Log.d(TAG, "Existing text: '${existingText.take(30)}...', Final text: '${finalText.take(50)}...'")
                
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    finalText
                )

                val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "ACTION_SET_TEXT result: $result")
                result
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert text into node", e)
            false
        }
    }
    
    /**
     * Get the actual text content from a node, excluding placeholder/hint text.
     * Some apps incorrectly return hint text as node.text when the field is empty.
     */
    private fun getActualTextContent(node: AccessibilityNodeInfo): String {
        val nodeText = node.text?.toString() ?: return ""
        
        // If text is empty, return empty
        if (nodeText.isEmpty()) return ""
        
        // Check if the text matches the hint text (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hintText = node.hintText?.toString()
            if (hintText != null && nodeText == hintText) {
                Log.d(TAG, "Ignoring hint text: '$hintText'")
                return ""
            }
        }
        
        // Additional heuristic: common placeholder patterns to ignore
        val lowerText = nodeText.lowercase()
        val placeholderPatterns = listOf(
            "enter your", "type your", "write your", "add your",
            "enter a", "type a", "write a",
            "search", "message", "email", "password", "username",
            "what's on your mind", "what's happening",
            "api key", "enter key"
        )
        
        // If the text looks like a placeholder (short + matches pattern), ignore it
        if (nodeText.length < 50 && placeholderPatterns.any { lowerText.contains(it) }) {
            Log.d(TAG, "Ignoring likely placeholder: '$nodeText'")
            return ""
        }
        
        return nodeText
    }

    /**
     * Fallback: Copy to clipboard and simulate paste
     */
    private fun pasteViaClipboard(text: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VoiceInk Transcription", text)
            clipboard.setPrimaryClip(clip)

            // Try to paste
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val focusedNode = findFocusedEditableNode()
                if (focusedNode != null) {
                    val pasteResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    Log.d(TAG, "Paste action result: $pasteResult")
                    if (!focusedNode.equals(lastFocusedNode)) {
                        focusedNode.recycle()
                    }
                    return pasteResult
                }
            }

            // If paste fails, at least the text is in clipboard
            Log.d(TAG, "Text copied to clipboard, paste not possible")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard paste failed", e)
            false
        }
    }
}
