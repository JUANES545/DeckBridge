package com.example.deckbridge.input

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Conservative gate: only keyboard-sourced events, skipping auto-repeat DOWN bursts.
 * Virtual keyboards may still pass when they report SOURCE_KEYBOARD; classification surfaces that.
 */
internal object KeyboardCaptureRules {

    fun shouldRecord(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
            return false
        }
        return event.isFromSource(InputDevice.SOURCE_KEYBOARD)
    }
}
