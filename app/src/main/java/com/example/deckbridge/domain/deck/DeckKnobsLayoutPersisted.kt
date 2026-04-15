package com.example.deckbridge.domain.deck

data class DeckKnobsLayoutPersisted(
    val knobs: List<DeckKnobPersisted>,
) {
    init {
        require(knobs.size == KNOB_COUNT) {
            "deck knobs require $KNOB_COUNT knobs, got ${knobs.size}"
        }
    }

    fun sortedKnobs(): List<DeckKnobPersisted> = knobs.sortedBy { it.sortIndex }

    fun knobAt(index: Int): DeckKnobPersisted? = sortedKnobs().getOrNull(index)

    companion object {
        const val KNOB_COUNT: Int = 3
    }
}
