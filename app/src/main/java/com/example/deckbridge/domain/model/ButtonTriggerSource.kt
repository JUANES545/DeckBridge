package com.example.deckbridge.domain.model

/**
 * Where a deck button activation was initiated. Extensible for host agent / automation later.
 */
enum class ButtonTriggerSource {
    /** User tapped the Compose tile. */
    TOUCH,

    /** Matched [PhysicalKeyBinding] from a hardware [android.view.KeyEvent]. */
    HARDWARE_KEY,

    /** Calibrated encoder / knob mapping (persisted [com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted]). */
    HARDWARE_KNOB,

    /** Same intent as [HARDWARE_KNOB] but initiated by dragging the on-screen mirror knob. */
    TOUCH_MIRROR_KNOB,

    /** Synthetic / tests / future in-app replay. */
    SIMULATED,
}
