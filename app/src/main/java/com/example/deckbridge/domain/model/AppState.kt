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
 */
data class AppState(
    val hostPlatform: HostPlatform,
    /** How [hostPlatform] was chosen (manual chips vs automatic detection). */
    val hostPlatformSource: HostPlatformSource,
    /** Short explanation for Settings (detection result or manual hint). */
    val hostDetectionDetail: String,
    val physicalKeyboard: PhysicalKeyboardStatus,
    val hostConnection: HostConnectionStatus,
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
    /** Short line for the “system status” strip (battery, permissions, etc. later). */
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
    /** USB gadget HID transport (keyboard / consumer); usually unavailable without root + gadget. */
    val hidTransport: HidTransportUiState,
    /** When true, DeckBridge may write to `/dev/hidg*` (still requires gadget nodes + permissions). */
    val hidPcModeEnabled: Boolean,
    /** `su` works for this app (e.g. KernelSU grant). */
    val privilegedShellAvailable: Boolean,
    /** Same one-line snapshot as `[DBG]` in Logcat (HID + root + USB cable). */
    val hidDebugLine: String,
    /** User preference: send actions over LAN HTTP agent vs USB gadget HID. */
    val hostDeliveryChannel: HostDeliveryChannel,
    /** PC LAN agent hostname or IPv4 (no auth in prototype). */
    val lanServerHost: String,
    val lanServerPort: Int,
    /** Result of last manual health check in this session; null = not tested yet. */
    val lanHealthOk: Boolean?,
    /** Last `/health` failure message (Logcat + Settings) when [lanHealthOk] is false. */
    val lanHealthDetail: String?,
    /** True when a LAN pair token is persisted for the current saved host (UX: “already linked on this phone”). */
    val lanPersistedPairActive: Boolean,
    /**
     * Last `/health` evaluation of `pairing.pair_token_valid` when the client sent [LanHostClient]’s pair token;
     * null if the server omitted the field (unauthenticated probe or older agent).
     */
    val lanPairTokenValid: Boolean?,
    /**
     * Application-side “we may treat this PC as linked for deck + gate”: false after the host rejects the token
     * until the user re-pairs or uses “Forget link”.
     */
    val lanTrustOk: Boolean,
    /** Subtle animated grid background on the home dashboard (persisted). */
    val animatedBackgroundMode: AnimatedBackgroundMode,
    /** IP of the Mac agent currently long-polling the bridge server; null = no active client. */
    val macBridgeClientIp: String? = null,
    /** True when a Mac agent polled /action/next within the last ~90 s. */
    val macBridgeClientAlive: Boolean = false,
)
