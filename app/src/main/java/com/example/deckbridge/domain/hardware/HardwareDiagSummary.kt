package com.example.deckbridge.domain.hardware

/**
 * Last matched logical control for quick debugging on Home.
 * [control] lets the UI show user-facing copy (action names, knob position) without parsing [controlLabel].
 */
data class HardwareDiagSummary(
    val controlLabel: String,
    val kind: String,
    val matchedAs: String,
    val epochMs: Long,
    val control: HardwareControlId? = null,
)
