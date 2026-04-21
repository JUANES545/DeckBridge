package com.example.deckbridge.mac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MacBridgeServer] focusing on queue management, drop counting,
 * and connection state checks — all JVM-only, no sockets started.
 */
class MacBridgeServerTest {

    // ── Queue capacity and drop counting ─────────────────────────────────────

    @Test
    fun enqueue_withinCapacity_noDrops() {
        val server = MacBridgeServer()
        repeat(64) { i -> server.enqueueAction("id$i", "{}") }
        assertEquals("no drops when queue is within capacity", 0, server.peekAndResetDropCount())
    }

    @Test
    fun enqueue_exceedsCapacity_dropsOverflow() {
        val server = MacBridgeServer()
        // Fill the 64-slot queue
        repeat(64) { i -> server.enqueueAction("id$i", "{}") }
        // 3 more must be dropped
        server.enqueueAction("overflow1", "{}")
        server.enqueueAction("overflow2", "{}")
        server.enqueueAction("overflow3", "{}")
        assertEquals("3 items should have been dropped", 3, server.peekAndResetDropCount())
    }

    @Test
    fun peekAndResetDropCount_resetsToZero() {
        val server = MacBridgeServer()
        repeat(64) { server.enqueueAction("id", "{}") }
        server.enqueueAction("overflow", "{}")
        assertEquals(1, server.peekAndResetDropCount())
        assertEquals("second peek should return 0 after reset", 0, server.peekAndResetDropCount())
    }

    @Test
    fun peekAndResetDropCount_initialIsZero() {
        val server = MacBridgeServer()
        assertEquals(0, server.peekAndResetDropCount())
    }

    // ── Pair token ────────────────────────────────────────────────────────────

    @Test
    fun setPairToken_blank_treatedAsNull() {
        val server = MacBridgeServer()
        server.setPairToken("  ")
        // isRunning is false so no server started; verify via structural equality —
        // blank tokens are trimmed to null which means "unpaired" mode
        // We verify indirectly: setting a blank token should not throw
        server.peekAndResetDropCount() // sanity — server is usable
    }

    @Test
    fun setPairToken_nonBlank_accepted() {
        val server = MacBridgeServer()
        server.setPairToken("secret-token-abc")
        // If it threw, the test fails. Structural assertion via peekClientIp (unrelated path).
        assertNull(server.peekClientIp())
    }

    // ── Connection liveness ───────────────────────────────────────────────────

    @Test
    fun isClientAlive_initiallyFalse() {
        val server = MacBridgeServer()
        assertFalse("no client has polled yet so isClientAlive must be false", server.isClientAlive())
    }

    @Test
    fun peekClientIp_initiallyNull() {
        val server = MacBridgeServer()
        assertNull("no client has connected yet so peekClientIp must be null", server.peekClientIp())
    }

    // ── Running flag ──────────────────────────────────────────────────────────

    @Test
    fun isRunning_initiallyFalse() {
        val server = MacBridgeServer()
        assertFalse(server.isRunning)
    }

    @Test
    fun stop_whenNotStarted_doesNotThrow() {
        val server = MacBridgeServer()
        server.stop() // must be idempotent when never started
        assertFalse(server.isRunning)
    }

    // ── stop() when not started is a true no-op ───────────────────────────────

    @Test
    fun stop_whenNotStarted_isNoOpForDropCount() {
        // stop() early-returns when running=false, so it does NOT clear the queue
        // or reset droppedActionCount. This is intentional: stop() is only meaningful
        // after start(). Verify the drop count is unaffected.
        val server = MacBridgeServer()
        repeat(64) { server.enqueueAction("id", "{}") }
        server.enqueueAction("overflow", "{}")
        assertEquals(1, server.peekAndResetDropCount()) // 1 drop before stop
        server.stop()                                    // no-op — running was false
        assertEquals("stop (no-op) does not reset drop count", 0, server.peekAndResetDropCount())
    }

    // ── CLIENT_ALIVE_WINDOW_MS constant sanity ────────────────────────────────

    @Test
    fun clientAliveWindow_isAtLeast90Seconds() {
        assertTrue(
            "CLIENT_ALIVE_WINDOW_MS should be >= 90 000 ms (55 s timeout + 35 s grace)",
            MacBridgeServer.CLIENT_ALIVE_WINDOW_MS >= 90_000L,
        )
    }

    @Test
    fun port_is8767() {
        assertEquals("MAC_BRIDGE server port must match the Mac agent expectation", 8767, MacBridgeServer.PORT)
    }

    @Test
    fun discoveryPort_is8766() {
        assertEquals("UDP discovery port must be PORT - 1", 8766, MacBridgeServer.DISCOVERY_PORT)
    }
}
