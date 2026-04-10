package com.example.deckbridge.input

import android.os.Build
import android.view.KeyEvent
import com.example.deckbridge.domain.model.KeyMotion
import java.util.Locale

internal object KeyboardKeyFormatter {

    fun motionOf(action: Int): KeyMotion? = when (action) {
        KeyEvent.ACTION_DOWN -> KeyMotion.DOWN
        KeyEvent.ACTION_UP -> KeyMotion.UP
        else -> null
    }

    fun keyCodeName(keyCode: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            KeyEvent.keyCodeToString(keyCode)
        } else {
            "KEYCODE_$keyCode"
        }
    }

    fun friendlyLabel(keyCode: Int): String {
        return when (keyCode) {
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> {
                val n = keyCode - KeyEvent.KEYCODE_F1 + 1
                "F$n"
            }
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_DEL -> "Backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"
            KeyEvent.KEYCODE_DPAD_UP -> "D-pad ↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad ↓"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad ←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad →"
            else -> keyCodeName(keyCode)
                .removePrefix("KEYCODE_")
                .replace('_', ' ')
                .lowercase(Locale.ROOT)
                .replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
        }
    }
}
