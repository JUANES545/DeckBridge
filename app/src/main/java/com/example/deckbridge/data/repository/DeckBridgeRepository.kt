package com.example.deckbridge.data.repository

import android.view.KeyEvent
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry for app-wide snapshot; later splits into profile/input/usb sources.
 */
interface DeckBridgeRepository {
    val appState: StateFlow<AppState>

    /** Called from [android.app.Activity.dispatchKeyEvent] while the app is foreground. */
    fun notifyKeyEvent(event: KeyEvent)

    /** Refresh enumerated keyboards; invoke on resume and after hot-plug (future). */
    fun refreshAttachedKeyboards()

    /**
     * Single activation path for deck tiles (hardware binding, touch, or simulated).
     * Updates highlight, activation log, and dispatches [com.example.deckbridge.actions.ActionDispatcher].
     */
    fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource)

    /** Persists and reapplies deck layout for the selected host OS (Windows / macOS). */
    fun setHostPlatform(platform: HostPlatform)
}
