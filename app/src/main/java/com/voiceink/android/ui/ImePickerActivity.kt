package com.voiceink.android.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity

class ImePickerActivity : ComponentActivity() {

    private val finishHandler = android.os.Handler(mainLooper)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Minimal, transparent window that doesn't cover the screen.
        setContentView(View(this))
        window.setLayout(1, 1)
        window.setGravity(Gravity.TOP or Gravity.START)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        window.decorView.post { showPicker() }
    }

    override fun onResume() {
        super.onResume()
        showPicker()
    }

    private fun showPicker() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()

        // Close shortly after opening so we don't leave a hidden activity around.
        finishHandler.removeCallbacksAndMessages(null)
        finishHandler.postDelayed({ finish() }, 3000L)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
