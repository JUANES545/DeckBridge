package com.example.deckbridge.domain.deck

/**
 * Versioned wrapper stored in DataStore (key "deck_pages_layout_json").
 *
 * Replaces the single-grid [DeckPersistedSurface] as the primary persistence format.
 * Knobs are shared across all pages.  The legacy single-grid key ("deck_grid_layout_json")
 * is read once for migration and then superseded by this format.
 */
data class DeckMultiPageSurface(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val pages: DeckPagesPersisted,
    val knobs: DeckKnobsLayoutPersisted,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 4
    }
}
