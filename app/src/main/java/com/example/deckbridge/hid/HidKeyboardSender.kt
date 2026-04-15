package com.example.deckbridge.hid

import com.example.deckbridge.domain.model.HostPlatform

/**
 * Builds 8-byte boot keyboard reports (modifier, reserved, 6× keycodes) for [com.example.deckbridge.domain.model.ResolvedAction] chords.
 */
internal object HidKeyboardSender {

    private const val REPORT_LEN = 8

    fun bootReport(modifiers: Int, key0: Int): ByteArray =
        byteArrayOf(
            modifiers.toByte(),
            0,
            key0.toByte(),
            0,
            0,
            0,
            0,
            0,
        )

    fun releaseAll(): ByteArray = ByteArray(REPORT_LEN)

    /**
     * Returns press + release frames for the deck keyboard intent id and resolved host platform.
     */
    fun chordPressRelease(intentId: String, platform: HostPlatform): Pair<ByteArray, ByteArray>? {
        val mac = platform == HostPlatform.MAC
        val (mods, key) = when (intentId) {
            "deck.intent.copy" -> if (mac) HidKeyboardUsages.MOD_LEFT_GUI to HidKeyboardUsages.KEY_C
            else HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_C
            "deck.intent.paste" -> if (mac) HidKeyboardUsages.MOD_LEFT_GUI to HidKeyboardUsages.KEY_V
            else HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_V
            "deck.intent.cut" -> if (mac) HidKeyboardUsages.MOD_LEFT_GUI to HidKeyboardUsages.KEY_X
            else HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_X
            "deck.intent.search" -> if (mac) HidKeyboardUsages.MOD_LEFT_GUI to HidKeyboardUsages.KEY_F
            else HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_F
            "deck.intent.undo" -> if (mac) HidKeyboardUsages.MOD_LEFT_GUI to HidKeyboardUsages.KEY_Z
            else HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_Z
            "deck.intent.redo" -> if (mac) {
                (HidKeyboardUsages.MOD_LEFT_GUI or HidKeyboardUsages.MOD_LEFT_SHIFT) to HidKeyboardUsages.KEY_Z
            } else {
                HidKeyboardUsages.MOD_LEFT_CTRL to HidKeyboardUsages.KEY_Y
            }
            else -> return null
        }
        val down = bootReport(mods, key)
        val up = releaseAll()
        return down to up
    }
}
