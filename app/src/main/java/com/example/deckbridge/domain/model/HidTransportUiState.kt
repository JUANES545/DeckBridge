package com.example.deckbridge.domain.model

/**
 * Observable HID gadget transport for Settings / diagnostics.
 *
 * Real USB HID to a tethered PC requires a **USB gadget** with HID function (root / custom kernel /
 * `configfs`). Consumer stock phones expose **no** `/dev/hidg*` to apps — [canSendKeyboard] stays false
 * and dispatch falls back to logging.
 */
data class HidTransportUiState(
    val phase: HidTransportPhase,
    /** One-line status for compact UI. */
    val summary: String,
    /** Human-readable detail (paths, errno, requirements). */
    val detail: String,
    val keyboardDevicePath: String,
    val consumerDevicePath: String,
    val canSendKeyboard: Boolean,
    val canSendMedia: Boolean,
    val lastError: String?,
) {
    companion object {
        fun initial(summary: String, detail: String) = HidTransportUiState(
            phase = HidTransportPhase.NOT_PROBED,
            summary = summary,
            detail = detail,
            keyboardDevicePath = DEFAULT_KEYBOARD_PATH,
            consumerDevicePath = DEFAULT_CONSUMER_PATH,
            canSendKeyboard = false,
            canSendMedia = false,
            lastError = null,
        )

        const val DEFAULT_KEYBOARD_PATH: String = "/dev/hidg0"
        const val DEFAULT_CONSUMER_PATH: String = "/dev/hidg1"
    }
}

enum class HidTransportPhase {
    NOT_PROBED,
    PROBING,
    /** No gadget nodes (typical retail device). */
    NO_NODES,
    /** Nodes exist but app cannot open (permission / SELinux). */
    ACCESS_DENIED,
    /** At least keyboard gadget writable. */
    KEYBOARD_READY,
    /** Keyboard + consumer gadget writable. */
    KEYBOARD_AND_MEDIA_READY,
    ERROR,
}
