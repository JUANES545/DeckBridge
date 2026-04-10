package com.example.deckbridge.domain.hardware

/**
 * Ephemeral UI state for the hardware mirror panel (knobs + pad).
 */
enum class HardwareHighlightKind {
    PAD_DOWN,
    PAD_UP,
    KNOB_PRESS_DOWN,
    KNOB_PRESS_UP,
    KNOB_ROTATE_CW,
    KNOB_ROTATE_CCW,
}

data class HardwareMirrorHighlight(
    val control: HardwareControlId,
    val kind: HardwareHighlightKind,
    val untilEpochMs: Long,
    /** Visual hint for knob rotation (-1..1 typical). */
    val rotationVisual: Float = 0f,
)
