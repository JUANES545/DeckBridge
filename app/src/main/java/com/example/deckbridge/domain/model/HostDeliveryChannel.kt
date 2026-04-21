package com.example.deckbridge.domain.model

/**
 * How resolved deck actions reach the PC. [LAN] uses the small HTTP agent on the host.
 * [MAC_BRIDGE] runs the HTTP server on Android; Mac connects outbound (bypasses corporate VPN
 * inbound block).
 */
enum class HostDeliveryChannel {
    LAN,
    /** Android runs the HTTP server; Mac connects outbound (bypasses corporate VPN inbound block). */
    MAC_BRIDGE,
    ;

    companion object {
        fun fromPersisted(raw: String?): HostDeliveryChannel = when (raw?.uppercase()) {
            "MAC_BRIDGE" -> MAC_BRIDGE
            else -> LAN
        }
    }

    fun persisted(): String = name
}
