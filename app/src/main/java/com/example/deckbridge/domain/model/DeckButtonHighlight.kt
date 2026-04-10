package com.example.deckbridge.domain.model

/**
 * Ephemeral UI state: which deck tile should appear pressed. Cleared by the repository after [DECK_HIGHLIGHT_DURATION_MS].
 */
data class DeckButtonHighlight(
    val buttonId: String,
)

const val DECK_HIGHLIGHT_DURATION_MS: Long = 220L
