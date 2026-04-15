package com.example.deckbridge.data.deck

import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

object DeckKnobIntentMapper {

    fun intentForRotate(layout: DeckKnobsLayoutPersisted, knobIndex: Int, ccw: Boolean): DeckButtonIntent? {
        val knob = layout.knobAt(knobIndex) ?: return null
        if (!knob.enabled || !knob.visible) return null
        val slice = if (ccw) knob.rotateCcw else knob.rotateCw
        return DeckGridIntentCodec.intentFromKnobAction(slice)
    }

    fun intentForPress(layout: DeckKnobsLayoutPersisted, knobIndex: Int): DeckButtonIntent? {
        val knob = layout.knobAt(knobIndex) ?: return null
        if (!knob.enabled || !knob.visible) return null
        return DeckGridIntentCodec.intentFromKnobAction(knob.press)
    }
}
