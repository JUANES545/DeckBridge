package com.example.deckbridge.domain.pairing

import com.example.deckbridge.domain.model.HostPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeckbridgePairingPayloadTest {

    @Test
    fun parse_osMac_selectsMacSlotHint() {
        val raw =
            "deckbridge://pair?h=192.168.1.9&p=8765&os=mac&v=1&sid=ps_test"
        val ep = DeckbridgePairingPayload.parse(raw)
        assertNotNull(ep)
        assertEquals("192.168.1.9", ep!!.host)
        assertEquals(8765, ep.port)
        assertEquals("ps_test", ep.sessionId)
        assertEquals(HostPlatform.MAC, ep.suggestedHostPlatform)
    }

    @Test
    fun parse_osDarwin_sameAsMac() {
        val ep = DeckbridgePairingPayload.parse(
            "deckbridge://pair?h=10.0.0.2&p=9000&os=darwin&v=1",
        )
        assertNotNull(ep)
        assertEquals(HostPlatform.MAC, ep!!.suggestedHostPlatform)
    }

    @Test
    fun parse_osWindows_selectsWindows() {
        val ep = DeckbridgePairingPayload.parse(
            "deckbridge://pair?h=192.168.1.10&p=8765&os=windows&v=1",
        )
        assertNotNull(ep)
        assertEquals(HostPlatform.WINDOWS, ep!!.suggestedHostPlatform)
    }

    @Test
    fun parse_unknownVersionRejected() {
        assertNull(
            DeckbridgePairingPayload.parse(
                "deckbridge://pair?h=1.2.3.4&p=1&v=99",
            ),
        )
    }
}
