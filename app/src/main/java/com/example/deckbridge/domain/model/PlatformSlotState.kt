package com.example.deckbridge.domain.model

/**
 * Connection state for one platform slot (Windows PC or Mac).
 * Both slots are tracked simultaneously in [AppState]; dispatch goes to the active one.
 */
data class PlatformSlotState(
    val host: String = "",
    val port: Int = 8765,
    /** Delivery channel for this slot. Windows slot is always LAN; Mac slot may be LAN or MAC_BRIDGE. */
    val channel: HostDeliveryChannel = HostDeliveryChannel.LAN,
    /** null = not yet probed, true = reachable, false = unreachable. */
    val healthOk: Boolean? = null,
    val healthRetrying: Boolean = false,
    val healthDetail: String? = null,
    val trustOk: Boolean = true,
    val pairActive: Boolean = false,
    val pairTokenValid: Boolean? = null,
    // Mac Bridge — only meaningful when channel == MAC_BRIDGE
    val macBridgeClientAlive: Boolean = false,
    val macBridgeClientIp: String? = null,
    val macBridgeServerRunning: Boolean = false,
    val macBridgeActionDropped: Boolean = false,
    /** Local WiFi IP of this Android device — shown in Settings so the user can type it in the Mac agent. */
    val macBridgeServerLocalIp: String? = null,
    /** Audio output devices reported by the Mac agent via POST /state. Empty until first push. */
    val audioOutputs: List<AudioOutputDevice> = emptyList(),
)
