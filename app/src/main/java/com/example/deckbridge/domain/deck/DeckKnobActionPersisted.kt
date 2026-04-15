package com.example.deckbridge.domain.deck

/**
 * One configurable action on a deck knob (CCW, CW, or press). Same [kind]/[intentId]/[payload]
 * contract as [DeckGridButtonPersisted] so [com.example.deckbridge.data.deck.DeckGridIntentCodec] can decode it.
 */
data class DeckKnobActionPersisted(
    val kind: DeckGridActionKind,
    val intentId: String,
    val payload: Map<String, String> = emptyMap(),
)
