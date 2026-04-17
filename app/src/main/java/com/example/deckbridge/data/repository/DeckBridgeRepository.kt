package com.example.deckbridge.data.repository

import android.view.KeyEvent
import android.view.MotionEvent
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.LanAgentListScanState
import com.example.deckbridge.lan.LanAgentProbeSnapshot
import com.example.deckbridge.lan.LanPairingSessionCreated
import com.example.deckbridge.lan.LanPairingSessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry for app-wide snapshot; later splits into profile/input/usb sources.
 */
interface DeckBridgeRepository {
    val appState: StateFlow<AppState>

    /**
     * First-launch onboarding: `null` until read from DataStore, then `false` = show flow, `true` = skip.
     */
    val onboardingComplete: StateFlow<Boolean?>

    /** Persists onboarding done and hides the flow on next frame. */
    fun markOnboardingFinished()

    /**
     * `null` until read from DataStore (with migration). `false` = user must pass the PC link gate (LAN healthy or explicit skip).
     * `true` = go straight to the deck after onboarding.
     */
    val skipInitialPcConnect: StateFlow<Boolean?>

    /**
     * When true, the app should open on the connect graph until LAN health succeeds or [markSkipInitialPcConnect] is used.
     */
    val initialConnectGateActive: StateFlow<Boolean>

    /** Persist skipping the initial full-screen PC link step (e.g. “Continue to deck” or healthy LAN). */
    fun markSkipInitialPcConnect(skip: Boolean = true)

    /**
     * After first-run onboarding, [consumePostOnboardingOpenPcConnect] can send the user to the PC connection flow.
     */
    fun requestPostOnboardingOpenPcConnect()

    /** One-shot: if user chose “Add computer” on onboarding, navigate to PC connect. */
    fun consumePostOnboardingOpenPcConnect(): Boolean

    /**
     * UDP multi-reply scan for LAN agents (UI list). Independent of [refreshLanDiscoveryOnForeground] single pick.
     */
    val lanAgentListScanState: StateFlow<LanAgentListScanState>

    fun refreshLanAgentListScan()

    /** Wizard session while learning pad + knobs; null when idle. */
    val calibrationSession: StateFlow<CalibrationSessionUi?>

    /** Called from [android.app.Activity.dispatchKeyEvent] while the app is foreground. */
    fun notifyKeyEvent(event: KeyEvent)

    /** Called from [android.app.Activity.dispatchGenericMotionEvent] for scroll / rotary diagnostics. */
    fun notifyGenericMotionEvent(event: MotionEvent)

    /** Refresh enumerated keyboards; invoke on resume and after hot-plug (future). */
    fun refreshAttachedKeyboards()

    /**
     * Single activation path for deck tiles (hardware binding, touch, or simulated).
     * Updates highlight, activation log, and dispatches [com.example.deckbridge.actions.ActionDispatcher].
     */
    fun triggerDeckButton(buttonId: String, source: ButtonTriggerSource)

    /** Persists and reapplies deck layout for the selected host OS (Windows / macOS). */
    fun setHostPlatform(platform: HostPlatform)

    /**
     * Persists [platform], disables auto-detect, reapplies deck + LAN slot, then probes `/health` when delivery is LAN.
     * Used when a pairing deeplink includes `os=` so trust/host land on the correct slot before [syncLanEndpointForPairing].
     */
    suspend fun syncHostPlatformForPairing(platform: HostPlatform)

    /** When true, host platform follows [HostOsDetector] (usually UNKNOWN + manual hint). */
    fun setHostAutoDetect(enabled: Boolean)

    /** Re-probes USB gadget HID nodes and host USB stickiness; call on resume / USB events. */
    fun refreshHostAndTransport()

    /**
     * When enabled, the app may send to `/dev/hidg*` when nodes exist (Settings “Modo HID al PC”).
     * Persisted; default on first install follows privileged shell availability.
     */
    fun setHidPcModeEnabled(enabled: Boolean)

    /** Persisted: subtle animated home background (always / while charging / off). */
    fun setAnimatedBackgroundMode(mode: AnimatedBackgroundMode)
    fun setAnimatedBackgroundTheme(theme: AnimatedBackgroundTheme)

    /** Persisted: route deck actions to the LAN HTTP agent vs USB gadget HID. */
    fun setHostDeliveryChannel(channel: HostDeliveryChannel, skipLanDiscovery: Boolean = false)

    /** Persisted LAN agent address (hostname or IPv4) and TCP port (default 8765). */
    fun setLanEndpoint(host: String, port: Int)

    /**
     * Applies LAN host/port, updates [lanHostClient] and [appState] immediately.
     * @param clearPairToken when true (default), clears persisted pair token (new pairing flow).
     */
    suspend fun syncLanEndpointForPairing(host: String, port: Int, clearPairToken: Boolean = true)

    /** Same as [syncLanEndpointForPairing] but keeps the saved pair token (quick reconnect to same PC). */
    suspend fun applyLanEndpointPreservingPairToken(host: String, port: Int)

    /** GET /health + host pairing status for a list row (does not switch the app’s active endpoint permanently). */
    suspend fun probeLanAgent(host: String, port: Int): LanAgentProbeSnapshot

    /** Runs the same LAN `/health` probe as foreground refresh, updating [AppState.lanHealthOk]. */
    suspend fun probeLanHealthNow()

    /** Probes `GET /health` on the configured LAN agent; updates [AppState.lanHealthOk]. */
    fun testLanHealth()

    /** Stable id sent to the PC agent during pairing (persisted). */
    suspend fun getOrCreateLanMobileDeviceId(): String

    /** POST /v1/pairing/sessions on the current LAN endpoint. */
    suspend fun startLanPairingSession(mobileDisplayName: String): Result<LanPairingSessionCreated>

    /** GET /v1/pairing/sessions/{id} */
    suspend fun getLanPairingSessionStatus(sessionId: String): Result<LanPairingSessionStatus>

    /** POST …/cancel */
    suspend fun cancelLanPairingSession(sessionId: String): Result<Unit>

    /** POST …/claim — bind this phone to a PC-created QR invite session. */
    suspend fun claimLanPairingSession(sessionId: String, mobileDisplayName: String): Result<Unit>

    /** Persists opaque [pairToken] and attaches it to subsequent LAN /action and /health calls. */
    suspend fun persistLanPairToken(pairToken: String)

    /** Clears saved pair token (e.g. user switches PC or pairing aborted). */
    suspend fun clearLanPairToken()

    /**
     * Clears the persisted pair token and resets the “skip initial PC connect” flag so the user can
     * re-pair from the connection flow (saved host/port are kept for convenience).
     */
    fun forgetTrustedLanHostLink()

    /** Persisted LAN endpoint for a specific platform slot (not necessarily the active one). */
    fun setLanEndpointForPlatform(platform: HostPlatform, host: String, port: Int)

    /** Probes /health for the given platform slot (does not change active endpoint). */
    fun testLanHealthForPlatform(platform: HostPlatform)

    /** Clears pair token and trust for the given platform slot. */
    fun forgetTrustedLanHostLinkForPlatform(platform: HostPlatform)

    /** Sets the Mac slot's delivery channel (LAN or MAC_BRIDGE). */
    fun setMacSlotChannel(channel: HostDeliveryChannel)

    /**
     * When delivery is LAN: UDP discovery for the PC agent, then `/health`.
     * Debounced; call from [android.app.Activity.onResume] so reopening the app re-syncs after DHCP changes.
     */
    fun refreshLanDiscoveryOnForeground()

    /** Starts the guided hardware calibration wizard (foreground + physical device). */
    fun startHardwareCalibration()

    /** Abandons in-progress calibration without changing saved mapping. */
    fun cancelHardwareCalibration()

    /**
     * If the wizard is on a **knob press** step and the hardware sends no key, advance without a press mapping.
     * @return true if a step was skipped.
     */
    fun skipKnobPressCalibrationStep(): Boolean

    /**
     * Mirror UI: user dragged an on-screen knob (rotation only; press is not simulated).
     * @param knobIndex 0 = top, 1 = middle, 2 = bottom (same as hardware).
     * @param clockwise one detent in the clockwise direction (finger swipe up).
     */
    fun onMirrorKnobTouchRotate(knobIndex: Int, clockwise: Boolean)

    /** Loads one cell from persisted deck JSON for the grid editor. */
    suspend fun getDeckGridButton(buttonId: String): DeckGridButtonPersisted?

    /** Validates, writes JSON, and refreshes [appState] macro grid + F-key bindings. */
    suspend fun updateDeckGridButton(cell: DeckGridButtonPersisted): Result<Unit>

    /** Replaces one cell with the factory preset for the same id (keeps [DeckGridButtonPersisted.sortIndex]). */
    suspend fun resetDeckGridButtonToDefault(buttonId: String): Result<Unit>

    /** Loads one knob from persisted deck JSON for the knob editor. */
    suspend fun getDeckKnob(knobId: String): DeckKnobPersisted?

    /** Validates, writes JSON, and refreshes [appState.deckKnobs] + hardware bridge mapping. */
    suspend fun updateDeckKnob(knob: DeckKnobPersisted): Result<Unit>

    /** Replaces one knob with the factory preset for the same id (keeps [DeckKnobPersisted.sortIndex]). */
    suspend fun resetDeckKnobToDefault(knobId: String): Result<Unit>

    /**
     * Release system resources held by the repository (e.g. [android.hardware.input.InputManager]
     * listener). Call when the owning lifecycle is permanently destroyed.
     */
    fun cleanup()
}
