package com.example.deckbridge.domain.hardware

/**
 * Single line in the technical raw log (keys + motion).
 */
data class RawDiagnosticLine(
    val id: String,
    val epochMs: Long,
    val channel: RawChannel,
    val summary: String,
    val deviceId: Int,
    val deviceName: String,
)

enum class RawChannel {
    KEY,
    MOTION,
}
