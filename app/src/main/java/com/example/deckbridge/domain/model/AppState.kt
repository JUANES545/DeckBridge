package com.example.deckbridge.domain.model

import com.example.deckbridge.profiles.Profile

/**
 * Coherent snapshot of what the home surface needs. Mirrors domain concepts without UI types.
 */
data class AppState(
    val hostPlatform: HostPlatform,
    val physicalKeyboard: PhysicalKeyboardStatus,
    val hostConnection: HostConnectionStatus,
    val activeProfile: Profile,
    val macroButtons: List<MacroButton>,
    val recentInputEvents: List<RecentInputEvent>,
    val physicalBindingsPreview: List<PhysicalKeyBinding>,
    val inputDiagnostics: InputDiagnostics,
    /** Single highlighted tile after hardware or touch activation. */
    val deckHighlight: DeckButtonHighlight?,
    /** Last deck button activations (touch vs hardware vs simulated). */
    val recentDeckActivations: List<DeckActivationLogEntry>,
    /** Short line for the “system status” strip (battery, permissions, etc. later). */
    val systemStatusLine: String,
)
