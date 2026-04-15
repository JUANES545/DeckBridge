package com.example.deckbridge.domain.deck

/**
 * Persisted configuration for one physical knob (0 = top … 2 = bottom).
 */
data class DeckKnobPersisted(
    val id: String,
    val sortIndex: Int,
    val label: String,
    val subtitle: String = "",
    val rotateCcw: DeckKnobActionPersisted,
    val rotateCw: DeckKnobActionPersisted,
    val press: DeckKnobActionPersisted,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    /** Reserved for a future knob glyph in the mirror UI. */
    val iconToken: String? = null,
)
