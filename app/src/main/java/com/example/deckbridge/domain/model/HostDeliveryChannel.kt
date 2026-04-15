package com.example.deckbridge.domain.model

/**
 * How resolved deck actions reach the PC. [LAN] uses the small HTTP agent on the host; [USB_HID]
 * uses the Linux USB gadget path when available. macOS agent support is planned; the enum is host-agnostic.
 */
enum class HostDeliveryChannel {
    LAN,
    USB_HID,
    /** Android runs the HTTP server; Mac connects outbound (bypasses corporate VPN inbound block). */
    MAC_BRIDGE,
    ;

    companion object {
        fun fromPersisted(raw: String?): HostDeliveryChannel = when (raw?.uppercase()) {
            "USB_HID" -> USB_HID
            "MAC_BRIDGE" -> MAC_BRIDGE
            else -> LAN
        }
    }

    fun persisted(): String = name
}
