package com.example.deckbridge.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HostDeliveryChannelTest {

    @Test
    fun fromPersisted_null_returnsLan() {
        assertEquals(HostDeliveryChannel.LAN, HostDeliveryChannel.fromPersisted(null))
    }

    @Test
    fun fromPersisted_empty_returnsLan() {
        assertEquals(HostDeliveryChannel.LAN, HostDeliveryChannel.fromPersisted(""))
    }

    @Test
    fun fromPersisted_unknown_returnsLan() {
        assertEquals(HostDeliveryChannel.LAN, HostDeliveryChannel.fromPersisted("BLUETOOTH"))
    }

    @Test
    fun fromPersisted_lan_returnsLan() {
        assertEquals(HostDeliveryChannel.LAN, HostDeliveryChannel.fromPersisted("LAN"))
    }

    @Test
    fun fromPersisted_macBridge_returnsMacBridge() {
        assertEquals(HostDeliveryChannel.MAC_BRIDGE, HostDeliveryChannel.fromPersisted("MAC_BRIDGE"))
    }

    @Test
    fun fromPersisted_macBridge_caseInsensitive() {
        assertEquals(HostDeliveryChannel.MAC_BRIDGE, HostDeliveryChannel.fromPersisted("mac_bridge"))
    }

    @Test
    fun fromPersisted_usbHid_fallsBackToLan() {
        assertEquals(HostDeliveryChannel.LAN, HostDeliveryChannel.fromPersisted("USB_HID"))
    }

    @Test
    fun persisted_roundTrip_allChannels() {
        for (channel in HostDeliveryChannel.entries) {
            assertEquals(
                "fromPersisted(persisted($channel)) should return $channel",
                channel,
                HostDeliveryChannel.fromPersisted(channel.persisted()),
            )
        }
    }

    // ── PlatformSlotState defaults ────────────────────────────────────────────

    @Test
    fun platformSlotState_defaults_sanitized() {
        val slot = PlatformSlotState()
        assertEquals("", slot.host)
        assertEquals(8765, slot.port)
        assertEquals(HostDeliveryChannel.LAN, slot.channel)
        assertEquals(null, slot.healthOk)
        assertEquals(false, slot.healthRetrying)
        assertEquals(true, slot.trustOk)
        assertEquals(false, slot.macBridgeClientAlive)
        assertEquals(false, slot.macBridgeServerRunning)
        assertEquals(null, slot.macBridgeClientIp)
    }

    @Test
    fun platformSlotState_macBridgeSlot_correctDefaults() {
        val slot = PlatformSlotState(channel = HostDeliveryChannel.MAC_BRIDGE)
        assertEquals(HostDeliveryChannel.MAC_BRIDGE, slot.channel)
        assertFalse(slot.macBridgeClientAlive)
        assertFalse(slot.macBridgeServerRunning)
    }

    // ── Slot health dot color logic (state-machine coverage) ─────────────────
    // Tests the expected color code outputs by replicating the slotHealthDotColor
    // state machine from DashboardChrome without importing Compose.

    private val GREEN  = 0xFF2EE6A0L
    private val AMBER  = 0xFFFFB020L
    private val RED    = 0xFFFF6B5AL
    private val GREY   = 0xFF5A5A68L

    /** Replicates slotHealthDotColor() return value as an ARGB Long for comparison. */
    private fun slotHealthColor(slot: PlatformSlotState): Long = when {
        slot.channel == HostDeliveryChannel.MAC_BRIDGE && slot.macBridgeClientAlive -> GREEN
        slot.channel == HostDeliveryChannel.MAC_BRIDGE && slot.macBridgeServerRunning -> AMBER
        slot.channel == HostDeliveryChannel.MAC_BRIDGE -> GREY
        slot.host.isBlank() -> GREY
        !slot.trustOk -> RED
        slot.healthOk == true -> GREEN
        slot.healthRetrying -> AMBER
        slot.healthOk == false -> RED
        else -> AMBER
    }

    @Test
    fun slotHealth_macBridge_clientAlive_green() {
        assertEquals(GREEN, slotHealthColor(PlatformSlotState(
            channel = HostDeliveryChannel.MAC_BRIDGE, macBridgeClientAlive = true)))
    }

    @Test
    fun slotHealth_macBridge_serverRunning_amber() {
        assertEquals(AMBER, slotHealthColor(PlatformSlotState(
            channel = HostDeliveryChannel.MAC_BRIDGE, macBridgeServerRunning = true)))
    }

    @Test
    fun slotHealth_macBridge_inactive_grey() {
        assertEquals(GREY, slotHealthColor(PlatformSlotState(
            channel = HostDeliveryChannel.MAC_BRIDGE)))
    }

    @Test
    fun slotHealth_lan_noHost_grey() {
        assertEquals(GREY, slotHealthColor(PlatformSlotState(host = "")))
    }

    @Test
    fun slotHealth_lan_trustFailed_red() {
        assertEquals(RED, slotHealthColor(PlatformSlotState(host = "10.0.0.1", trustOk = false)))
    }

    @Test
    fun slotHealth_lan_healthOk_green() {
        assertEquals(GREEN, slotHealthColor(PlatformSlotState(host = "10.0.0.1", healthOk = true)))
    }

    @Test
    fun slotHealth_lan_healthRetrying_amber() {
        assertEquals(AMBER, slotHealthColor(PlatformSlotState(
            host = "10.0.0.1", healthOk = false, healthRetrying = true)))
    }

    @Test
    fun slotHealth_lan_offline_red() {
        assertEquals(RED, slotHealthColor(PlatformSlotState(host = "10.0.0.1", healthOk = false)))
    }
}

private fun assertFalse(value: Boolean) = org.junit.Assert.assertFalse(value)
