package com.example.deckbridge.domain.model

import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight
import com.example.deckbridge.domain.hardware.KnobMirrorRotationAccum
import com.example.deckbridge.domain.hardware.RawDiagnosticLine
import com.example.deckbridge.profiles.Profile


/**
 * Coherent snapshot of what the home surface needs. Mirrors domain concepts without UI types.
 *
 * LAN/Mac Bridge connection state is split into [windowsSlot] and [macSlot] so both PCs are
 * monitored simultaneously. The backward-compat flat fields (e.g. [lanServerHost]) are computed
 * from [activeSlot] and require no changes in most UI code.
 */
data class AppState(
    val hostPlatform: HostPlatform,
    /** How [hostPlatform] was chosen (manual chips vs automatic detection). */
    val hostPlatformSource: HostPlatformSource,
    /** Short explanation for Settings (detection result or manual hint). */
    val hostDetectionDetail: String,
    val physicalKeyboard: PhysicalKeyboardStatus,

    val activeProfile: Profile,
    val macroButtons: List<MacroButton>,
    /** Persisted knob row (CCW / CW / press actions); labels feed the mirror UI. */
    val deckKnobs: DeckKnobsLayoutPersisted,
    val physicalBindingsPreview: List<PhysicalKeyBinding>,
    val inputDiagnostics: InputDiagnostics,
    /** Single highlighted tile after hardware or touch activation. */
    val deckHighlight: DeckButtonHighlight?,
    /** Last deck button activations (touch vs hardware vs simulated). */
    val recentDeckActivations: List<DeckActivationLogEntry>,
    /** Short line for the "system status" strip. */
    val systemStatusLine: String,
    /** Loaded hardware calibration (pad + knobs); null until learned or cleared. */
    val hardwareCalibration: HardwareCalibrationConfig?,
    /** Ephemeral highlight on the hardware mirror panel (pad / knob). */
    val hardwareMirrorHighlight: HardwareMirrorHighlight?,
    /** Persistent per-knob rotation (degrees) for mirror UI pointer / dial position. */
    val knobMirrorRotation: KnobMirrorRotationAccum,
    /** Last logical control matched from calibration (debug strip). */
    val hardwareDiagSummary: HardwareDiagSummary?,
    /** Raw KEY/MOTION lines for deep debugging (capped in repository). */
    val rawInputDiagnostics: List<RawDiagnosticLine>,
    /** Subtle animated grid background on the home dashboard (persisted). */
    val animatedBackgroundMode: AnimatedBackgroundMode,
    val animatedBackgroundTheme: AnimatedBackgroundTheme = AnimatedBackgroundTheme.GRID_PULSE,
    /** Briefly true after a deck action could not be delivered to the host. Auto-clears. */
    val lastActionFailed: Boolean = false,

    /** Index of the currently visible deck page (0-based). */
    val activeDeckPageIndex: Int = 0,
    /** Total number of deck pages available. */
    val deckPageCount: Int = 1,
    /** Macro buttons for every page (index = page index); empty list = only active page available. */
    val deckPages: List<List<MacroButton>> = emptyList(),
    /** Optional user-defined name per page (null = unnamed); index matches [deckPages]. */
    val deckPageNames: List<String?> = emptyList(),

    // ── Per-slot connection state ─────────────────────────────────────────────
    /** Windows PC slot connection state (always LAN channel). */
    val windowsSlot: PlatformSlotState = PlatformSlotState(channel = HostDeliveryChannel.LAN),
    /** Mac slot connection state (LAN or Mac Bridge). */
    val macSlot: PlatformSlotState = PlatformSlotState(channel = HostDeliveryChannel.MAC_BRIDGE),
    /** When true the repository maintains a GATT keep-alive ping to prevent the BT keyboard from sleeping. */
    val keepKeyboardAwake: Boolean = false,
) {
    // ── Active slot ──────────────────────────────────────────────────────────
    val activeSlot: PlatformSlotState get() = when (hostPlatform.coerceForDeckData()) {
        HostPlatform.MAC -> macSlot
        else -> windowsSlot
    }

    // ── Backward-compat computed properties (read from active slot) ──────────
    val hostDeliveryChannel: HostDeliveryChannel get() = activeSlot.channel
    val lanServerHost: String get() = activeSlot.host
    val lanServerPort: Int get() = activeSlot.port
    val lanHealthOk: Boolean? get() = activeSlot.healthOk
    val lanHealthRetrying: Boolean get() = activeSlot.healthRetrying
    val lanHealthDetail: String? get() = activeSlot.healthDetail
    val lanTrustOk: Boolean get() = activeSlot.trustOk
    val lanPersistedPairActive: Boolean get() = activeSlot.pairActive
    val lanPairTokenValid: Boolean? get() = activeSlot.pairTokenValid
    val macBridgeClientAlive: Boolean get() = macSlot.macBridgeClientAlive
    val macBridgeClientIp: String? get() = macSlot.macBridgeClientIp
    val macBridgeServerRunning: Boolean get() = macSlot.macBridgeServerRunning
    val macBridgeActionDropped: Boolean get() = macSlot.macBridgeActionDropped
}
