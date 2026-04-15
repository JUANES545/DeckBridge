package com.example.deckbridge.domain.model

/**
 * How resolved deck actions reach the PC. [LAN] uses the small HTTP agent on the host; [USB_HID]
 * uses the Linux USB gadget path when available. macOS agent support is planned; the enum is host-agnostic.
 */
enum class HostDeliveryChannel {
    LAN,
    USB_HID,
    ;

    companion object {
        fun fromPersisted(raw: String?): HostDeliveryChannel = when (raw?.uppercase()) {
            "USB_HID" -> USB_HID
            else -> LAN
        }
    }

    fun persisted(): String = name
}
