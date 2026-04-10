package com.example.deckbridge.domain.model

enum class InputEventKind {
    PRESSED,
    RELEASED,
}

enum class InputEventSource {
    /** Delivered through Activity key dispatch (hardware path). */
    HARDWARE,
    /** Synthetic data for previews or tests. */
    SIMULATED,
}

/**
 * Normalized keyboard activity line.
 */
data class RecentInputEvent(
    val id: String,
    val occurredAtEpochMs: Long,
    val motion: KeyMotion,
    val keyCode: Int,
    /** Short label such as F1, Enter. */
    val keyLabel: String,
    /** Platform name such as KEYCODE_F1 (API 29+) or KEYCODE_n fallback on older APIs. */
    val keyCodeName: String,
    val device: InputDeviceSnapshot,
    val classification: KeyboardInputClassification,
    val source: InputEventSource,
) {
    val kind: InputEventKind
        get() = if (motion == KeyMotion.UP) {
            InputEventKind.RELEASED
        } else {
            InputEventKind.PRESSED
        }
}
