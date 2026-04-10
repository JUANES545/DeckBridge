package com.example.deckbridge.domain.model

/**
 * Immutable snapshot of [android.view.InputDevice] fields useful for debugging and future filtering
 * (e.g. lock to a specific vendor/product or descriptor).
 */
data class InputDeviceSnapshot(
    val deviceId: Int,
    val name: String,
    /** Stable OS-level id when available; useful to pin “the macro pad”. */
    val descriptor: String?,
    val vendorId: Int?,
    val productId: Int?,
    /** Raw [android.view.InputDevice.getSources] bitmask for tooling. */
    val sourcesFlags: Int,
    /** Short human summary of sources (keyboard, joystick, etc.). */
    val sourcesLabel: String,
    val isExternal: Boolean,
    /** API 29+ virtual devices (often software paths). */
    val isVirtual: Boolean,
)
