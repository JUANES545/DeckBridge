package com.example.deckbridge.data.deck

import androidx.annotation.StringRes
import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted

/**
 * Validates a full knob row for persistence; each binding reuses [DeckGridEditValidator] rules.
 */
object DeckKnobEditValidator {

    @StringRes
    fun validate(knob: DeckKnobPersisted): Int? {
        if (knob.label.isBlank()) return R.string.grid_edit_err_label_empty
        validateAction(knob.rotateCcw)?.let { return it }
        validateAction(knob.rotateCw)?.let { return it }
        validateAction(knob.press)?.let { return it }
        return null
    }

    @StringRes
    private fun validateAction(action: DeckKnobActionPersisted): Int? {
        val synthetic = DeckGridButtonPersisted(
            id = "_knob_binding",
            sortIndex = 0,
            label = "_",  // non-blank placeholder; knob actions have no label requirement
            subtitle = "",
            kind = action.kind,
            intentId = action.intentId,
            payload = action.payload,
            iconToken = null,
            enabled = true,
            visible = true,
        )
        return DeckGridEditValidator.validate(synthetic)
    }
}
