package com.example.deckbridge.hid

/**
 * Minimal consumer-control report: reportId + 16-bit usage (LE).
 * Must match the **consumer HID interface** loaded on the gadget (`hidg1` or custom path).
 */
internal object HidMediaSender {

    private const val REPORT_ID: Byte = 0x01

    fun consumerPressRelease(usage16: Int): Pair<ByteArray, ByteArray> {
        val lo = (usage16 and 0xFF).toByte()
        val hi = ((usage16 shr 8) and 0xFF).toByte()
        val down = byteArrayOf(REPORT_ID, lo, hi)
        val up = byteArrayOf(REPORT_ID, 0, 0)
        return down to up
    }

    fun reportForIntentId(intentId: String): Pair<ByteArray, ByteArray>? {
        val u = when (intentId) {
            "deck.intent.media.vol_up" -> HidConsumerUsages.VOLUME_INCREMENT
            "deck.intent.media.vol_down" -> HidConsumerUsages.VOLUME_DECREMENT
            "deck.intent.media.mute" -> HidConsumerUsages.MUTE
            "deck.intent.media.play_pause" -> HidConsumerUsages.PLAY_PAUSE
            "deck.intent.media.prev_track" -> HidConsumerUsages.SCAN_PREVIOUS_TRACK
            "deck.intent.media.next_track" -> HidConsumerUsages.SCAN_NEXT_TRACK
            else -> return null
        }
        return consumerPressRelease(u)
    }
}
