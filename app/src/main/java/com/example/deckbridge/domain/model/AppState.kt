package com.example.deckbridge.domain.model

import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight
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
    /** Last logical control matched from calibration (debug strip). */
    val hardwareDiagSummary: HardwareDiagSummary?,
    /** Raw KEY/MOTION lines for deep debugging (capped in repository). */
    val rawInputDiagnostics: List<RawDiagnosticLine>,
    /** USB gadget HID transport (keyboard / consumer); usually unavailable without root + gadget. */
    val hidTransport: HidTransportUiState,
)
