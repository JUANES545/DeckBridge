package com.example.deckbridge.domain.deck

/**
 * Multi-page deck: a list of grids the user can swipe between.
 * Knobs are global and live in [DeckMultiPageSurface]; they are not per-page.
 */
data class DeckPagesPersisted(
    val pages: List<DeckGridLayoutPersisted>,
    val activePageIndex: Int,
) {
    init {
        require(pages.isNotEmpty()) { "at least one page required" }
        require(activePageIndex in pages.indices) {
            "activePageIndex $activePageIndex out of range 0..${pages.size - 1}"
        }
    }

    val activePage: DeckGridLayoutPersisted get() = pages[activePageIndex]

    companion object {
        const val MAX_PAGES = 10
    }
}
