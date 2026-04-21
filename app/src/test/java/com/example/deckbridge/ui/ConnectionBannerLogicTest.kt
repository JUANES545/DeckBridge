package com.example.deckbridge.ui

import com.example.deckbridge.domain.model.HostDeliveryChannel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the [showBanner] decision logic in HomeScreen.
 *
 * The logic is replicated here as a pure function so it can be tested without
 * Compose or Android. When the real logic changes, update [showBanner] below to
 * match — test failures will then surface the regression.
 *
 * Logic mirrors HomeScreen.kt:
 *   val showBanner = !bannerDismissed && (
 *       (channel == LAN && (host.isBlank() || !trustOk || healthOk == false)) ||
 *       (channel == MAC_BRIDGE && !macBridgeClientAlive)
 *   )
 */
class ConnectionBannerLogicTest {

    // Pure replica of the HomeScreen showBanner condition
    private fun showBanner(
        dismissed: Boolean,
        channel: HostDeliveryChannel,
        lanHost: String = "",
        lanTrustOk: Boolean = true,
        lanHealthOk: Boolean? = null,
        macBridgeClientAlive: Boolean = false,
    ): Boolean = !dismissed && (
        (channel == HostDeliveryChannel.LAN && (
            lanHost.isBlank() || !lanTrustOk || lanHealthOk == false
        )) ||
        (channel == HostDeliveryChannel.MAC_BRIDGE && !macBridgeClientAlive)
    )

    // ── LAN cases ─────────────────────────────────────────────────────────────

    @Test
    fun lan_noHost_showsBanner() {
        assertTrue(showBanner(dismissed = false, channel = HostDeliveryChannel.LAN, lanHost = ""))
    }

    @Test
    fun lan_trustFailed_showsBanner() {
        assertTrue(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.LAN,
            lanHost = "192.168.1.10",
            lanTrustOk = false,
        ))
    }

    @Test
    fun lan_healthOkFalse_showsBanner() {
        assertTrue(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.LAN,
            lanHost = "192.168.1.10",
            lanTrustOk = true,
            lanHealthOk = false,
        ))
    }

    @Test
    fun lan_healthOkTrue_noBanner() {
        assertFalse(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.LAN,
            lanHost = "192.168.1.10",
            lanTrustOk = true,
            lanHealthOk = true,
        ))
    }

    @Test
    fun lan_healthOkNull_noBanner() {
        // null = still probing, don't alarm the user yet
        assertFalse(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.LAN,
            lanHost = "192.168.1.10",
            lanTrustOk = true,
            lanHealthOk = null,
        ))
    }

    // ── MAC_BRIDGE cases ──────────────────────────────────────────────────────

    @Test
    fun macBridge_clientNotAlive_showsBanner() {
        assertTrue(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.MAC_BRIDGE,
            macBridgeClientAlive = false,
        ))
    }

    @Test
    fun macBridge_clientAlive_noBanner() {
        assertFalse(showBanner(
            dismissed = false,
            channel = HostDeliveryChannel.MAC_BRIDGE,
            macBridgeClientAlive = true,
        ))
    }

    // ── Dismissed flag suppresses everything ─────────────────────────────────

    @Test
    fun dismissed_suppressesLanBanner() {
        assertFalse(showBanner(
            dismissed = true,
            channel = HostDeliveryChannel.LAN,
            lanHost = "",
        ))
    }

    @Test
    fun dismissed_suppressesMacBridgeBanner() {
        assertFalse(showBanner(
            dismissed = true,
            channel = HostDeliveryChannel.MAC_BRIDGE,
            macBridgeClientAlive = false,
        ))
    }

}
