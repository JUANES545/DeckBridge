package com.example.deckbridge.domain.model

enum class PhysicalKeyboardConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class PhysicalKeyboardStatus(
    val state: PhysicalKeyboardConnectionState,
    val deviceName: String?,
    val detail: String,
    /** Battery level 0–100, or null when unavailable (API < 31 or device doesn't report). */
    val batteryLevel: Int? = null,
)

