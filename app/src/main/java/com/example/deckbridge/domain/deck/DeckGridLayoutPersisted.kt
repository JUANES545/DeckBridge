package com.example.deckbridge.domain.deck

/** Persisted 3×3 macro grid. */
data class DeckGridLayoutPersisted(
    val buttons: List<DeckGridButtonPersisted>,
) {
    init {
        require(buttons.size == GRID_SLOT_COUNT) {
            "deck grid requires $GRID_SLOT_COUNT buttons, got ${buttons.size}"
        }
    }

    fun sortedButtons(): List<DeckGridButtonPersisted> = buttons.sortedBy { it.sortIndex }

    companion object {
        const val GRID_SLOT_COUNT: Int = 9
    }
}

/**
 * Versioned wrapper for DataStore JSON: grid + three knobs, each with CCW / CW / press actions.
 */
data class DeckPersistedSurface(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val grid: DeckGridLayoutPersisted,
    val knobs: DeckKnobsLayoutPersisted,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
