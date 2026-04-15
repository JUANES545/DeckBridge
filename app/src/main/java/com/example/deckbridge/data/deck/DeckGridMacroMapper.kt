package com.example.deckbridge.data.deck

import com.example.deckbridge.domain.deck.DeckGridLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.model.MacroButton

/** Builds runtime grid tiles from persisted layout (knobs stay out of this path for now). */
object DeckGridMacroMapper {

    fun toMacroButtons(grid: DeckGridLayoutPersisted): List<MacroButton> =
        grid.sortedButtons().map { cell ->
            val intent = DeckGridIntentCodec.intentFromPersisted(cell)
            MacroButton(
                id = cell.id,
                label = cell.label,
                intent = intent,
                sortIndex = cell.sortIndex,
                iconToken = cell.iconToken,
                resolvedShortcut = "",
                windowsSubtitle = cell.subtitle,
                enabled = cell.enabled,
                visible = cell.visible,
            )
        }
}
