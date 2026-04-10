package com.example.deckbridge.hid

/**
 * USB HID keyboard page (0x07) modifier byte + key usages for boot-compatible 8-byte reports.
 * @see USB HID Usage Tables — Keyboard/Keypad Page
 */
internal object HidKeyboardUsages {
    const val MOD_LEFT_CTRL: Int = 0x01
    const val MOD_LEFT_SHIFT: Int = 0x02
    const val MOD_LEFT_ALT: Int = 0x04
    const val MOD_LEFT_GUI: Int = 0x08

    const val KEY_A: Int = 0x04
    const val KEY_C: Int = 0x06
    const val KEY_F: Int = 0x09
    const val KEY_V: Int = 0x19
    const val KEY_X: Int = 0x1B
    const val KEY_Z: Int = 0x1A
    const val KEY_Y: Int = 0x1C
}
