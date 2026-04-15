package com.example.deckbridge.domain.deck

/**
 * Persisted classification for a grid tile. Dispatch still uses [com.example.deckbridge.domain.model.DeckButtonIntent].
 * [APP_LAUNCH] and [SCRIPT] are reserved for a future editor; they map to no-op execution for now.
 */
enum class DeckGridActionKind {
    TEXT,
    KEY,
    COMBO,
    MEDIA,
    NOOP,
    APP_LAUNCH,
    SCRIPT,
}
