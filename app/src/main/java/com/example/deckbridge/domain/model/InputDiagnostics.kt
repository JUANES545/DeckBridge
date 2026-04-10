package com.example.deckbridge.domain.model

/**
 * Last-seen input context while the app is used for debugging macro hardware.
 */
data class InputDiagnostics(
    val lastEventDevice: InputDeviceSnapshot?,
    val lastEventAtEpochMs: Long?,
    val lastMotion: KeyMotion?,
    val lastKeyCode: Int?,
    val lastClassification: KeyboardInputClassification?,
    /** External keyboards currently reported by [android.hardware.input.InputManager]. */
    val detectedExternalKeyboards: List<InputDeviceSnapshot>,
    val hintLine: String,
)
