package com.example.deckbridge.hid

/**
 * USB HID Consumer Control page (0x0C) — 16-bit usages.
 * Actual wire format depends on the gadget’s **report descriptor**; we use a common 16-bit LE layout.
 */
internal object HidConsumerUsages {
    const val MUTE: Int = 0x00E2
    const val VOLUME_INCREMENT: Int = 0x00E9
    const val VOLUME_DECREMENT: Int = 0x00EA
    const val SCAN_NEXT_TRACK: Int = 0x00B5
    const val SCAN_PREVIOUS_TRACK: Int = 0x00B6
    const val PLAY_PAUSE: Int = 0x00CD
}
