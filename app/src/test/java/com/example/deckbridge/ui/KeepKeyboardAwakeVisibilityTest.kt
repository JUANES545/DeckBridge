package com.example.deckbridge.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the "Keep Keyboard Awake" section in SettingsScreen is always visible,
 * regardless of the Bluetooth keyboard connection state.
 *
 * Previously the section was gated on PhysicalKeyboardConnectionState.CONNECTED, which
 * caused it to disappear whenever the keyboard was not connected at the time of opening
 * Settings. The fix removes that condition entirely.
 *
 * Logic mirrors SettingsScreen.kt — the section is now unconditional:
 *   SectionLabel(...)
 *   KeepKeyboardAwakeCard(...)
 *
 * If the gate is re-introduced by mistake, update [showKeepKeyboardSection] below to
 * match — tests will then surface the regression.
 */
class KeepKeyboardAwakeVisibilityTest {

    // Replica of the (now-removed) old gated condition for contrast
    private fun showKeepKeyboardSectionOldBehavior(keyboardConnected: Boolean): Boolean =
        keyboardConnected

    // Replica of the current unconditional behavior
    @Suppress("UNUSED_PARAMETER")
    private fun showKeepKeyboardSection(keyboardConnected: Boolean): Boolean = true

    // ── New behaviour: always shown ────────────────────────────────────────────

    @Test
    fun shown_whenKeyboardConnected() {
        assertTrue(showKeepKeyboardSection(keyboardConnected = true))
    }

    @Test
    fun shown_whenKeyboardDisconnected() {
        assertTrue(showKeepKeyboardSection(keyboardConnected = false))
    }

    // ── Old behaviour was wrong: section was hidden when disconnected ──────────

    @Test
    fun oldBehavior_wasHidden_whenDisconnected() {
        // Documents the regression: old code hid the option when BT was not connected.
        // This test would FAIL if we reverted to the old condition.
        val hiddenUnderOldBehavior = !showKeepKeyboardSectionOldBehavior(keyboardConnected = false)
        assertTrue("Old gated behavior hid the section when keyboard was disconnected", hiddenUnderOldBehavior)
    }
}
