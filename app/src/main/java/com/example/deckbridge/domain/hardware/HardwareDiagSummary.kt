package com.example.deckbridge.domain.hardware

/**
 * Last matched logical control for quick debugging on Home.
 */
data class HardwareDiagSummary(
    val controlLabel: String,
    val kind: String,
    val matchedAs: String,
    val epochMs: Long,
)
