package com.example.deckbridge.data.deck

import android.content.res.Resources
import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckGridLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

/**
 * **Preset** (factory default): not persisted until first save. Used to seed DataStore and tests.
 * Order: 1 Jamón, 2 Search, 3 Paste, 4 Show desktop, 5 Undo, 6 Redo, 7 Mute, 8 Enter, 9 Screenshot.
 */
object DeckGridPreset {

    private data class SlotDef(
        val id: String,
        val labelRes: Int,
        val subtitleRes: Int,
        val intent: DeckButtonIntent,
        val kind: DeckGridActionKind,
        val iconToken: String?,
    )

    private val defaultSlots: List<SlotDef> = listOf(
        SlotDef(
            id = "btn_jamon",
            labelRes = R.string.deck_action_jamon,
            subtitleRes = R.string.deck_subtitle_jamon,
            intent = DeckButtonIntent.InjectText("Jamón 123"),
            kind = DeckGridActionKind.TEXT,
            iconToken = "text_jamon",
        ),
        SlotDef(
            id = "btn_search",
            labelRes = R.string.deck_action_search,
            subtitleRes = R.string.deck_subtitle_search,
            intent = DeckButtonIntent.KeyboardChord.Search,
            kind = DeckGridActionKind.COMBO,
            iconToken = "search",
        ),
        SlotDef(
            id = "btn_paste",
            labelRes = R.string.deck_action_paste,
            subtitleRes = R.string.deck_subtitle_paste,
            intent = DeckButtonIntent.KeyboardChord.Paste,
            kind = DeckGridActionKind.COMBO,
            iconToken = "content_paste",
        ),
        SlotDef(
            id = "btn_show_desktop",
            labelRes = R.string.deck_action_show_desktop,
            subtitleRes = R.string.deck_subtitle_show_desktop,
            intent = DeckButtonIntent.KeyboardChord.ShowDesktop,
            kind = DeckGridActionKind.COMBO,
            iconToken = "show_desktop",
        ),
        SlotDef(
            id = "btn_undo",
            labelRes = R.string.deck_action_undo,
            subtitleRes = R.string.deck_subtitle_undo,
            intent = DeckButtonIntent.KeyboardChord.Undo,
            kind = DeckGridActionKind.COMBO,
            iconToken = "undo",
        ),
        SlotDef(
            id = "btn_redo",
            labelRes = R.string.deck_action_redo,
            subtitleRes = R.string.deck_subtitle_redo,
            intent = DeckButtonIntent.KeyboardChord.Redo,
            kind = DeckGridActionKind.COMBO,
            iconToken = "redo",
        ),
        SlotDef(
            id = "btn_mute",
            labelRes = R.string.deck_action_mute,
            subtitleRes = R.string.deck_subtitle_mute,
            intent = DeckButtonIntent.SystemMedia.Mute,
            kind = DeckGridActionKind.MEDIA,
            iconToken = "volume_mute",
        ),
        SlotDef(
            id = "btn_enter",
            labelRes = R.string.deck_action_enter,
            subtitleRes = R.string.deck_subtitle_enter,
            intent = DeckButtonIntent.SingleKey.Enter,
            kind = DeckGridActionKind.KEY,
            iconToken = "keyboard_enter",
        ),
        SlotDef(
            id = "btn_snip",
            labelRes = R.string.deck_action_screenshot,
            subtitleRes = R.string.deck_subtitle_screenshot,
            intent = DeckButtonIntent.KeyboardChord.SnippingOverlay,
            kind = DeckGridActionKind.COMBO,
            iconToken = "screen_snip",
        ),
    )

    fun defaultLayoutFromResources(res: Resources): DeckGridLayoutPersisted {
        require(defaultSlots.size == DeckGridLayoutPersisted.GRID_SLOT_COUNT)
        return DeckGridLayoutPersisted(
            buttons = defaultSlots.mapIndexed { index, def ->
                DeckGridButtonPersisted(
                    id = def.id,
                    sortIndex = index,
                    label = res.getString(def.labelRes),
                    subtitle = res.getString(def.subtitleRes),
                    kind = def.kind,
                    intentId = def.intent.intentId,
                    payload = DeckGridIntentCodec.payloadForIntent(def.intent),
                    iconToken = def.iconToken,
                    enabled = true,
                    visible = true,
                )
            },
        )
    }
}
