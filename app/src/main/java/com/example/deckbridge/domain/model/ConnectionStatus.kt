package com.example.deckbridge.domain.model

enum class PhysicalKeyboardConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

enum class HostUsbConnectionState {
    /** No USB host session (future milestone). */
    NOT_CONNECTED,
    /** Cable present but handshake not done. */
    ATTACHED_IDLE,
    /** Ready to exchange actions with the desktop agent. */
    READY,
    ERROR,
}

data class PhysicalKeyboardStatus(
    val state: PhysicalKeyboardConnectionState,
    val deviceName: String?,
    val detail: String,
)

data class HostConnectionStatus(
    val usbState: HostUsbConnectionState,
    /** Friendly name shown in UI until real discovery exists. */
    val hostLabel: String,
    val detail: String,
)
