package com.example.deckbridge.data.deck

import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

/** Editor dropdown options (label is a string resource id). */
data class DeckEditorIntentOption(
    val labelRes: Int,
    val intentId: String,
)

object DeckGridEditorCatalog {

    val editableKinds: List<DeckGridActionKind> = listOf(
        DeckGridActionKind.TEXT,
        DeckGridActionKind.KEY,
        DeckGridActionKind.COMBO,
        DeckGridActionKind.MEDIA,
        DeckGridActionKind.PAGE_NAV,
        DeckGridActionKind.NOOP,
    )

    val pageNavOptions: List<DeckEditorIntentOption> = listOf(
        DeckEditorIntentOption(R.string.grid_edit_page_nav_next, DeckButtonIntent.PageNav.Next.intentId),
        DeckEditorIntentOption(R.string.grid_edit_page_nav_prev, DeckButtonIntent.PageNav.Prev.intentId),
    )

    val keyOptions: List<DeckEditorIntentOption> = listOf(
        DeckEditorIntentOption(R.string.grid_edit_key_enter, DeckButtonIntent.SingleKey.Enter.intentId),
        DeckEditorIntentOption(R.string.grid_edit_key_escape, DeckButtonIntent.SingleKey.Escape.intentId),
    )

    val comboOptions: List<DeckEditorIntentOption> = listOf(
        DeckEditorIntentOption(R.string.deck_action_copy, DeckButtonIntent.KeyboardChord.Copy.intentId),
        DeckEditorIntentOption(R.string.deck_action_paste, DeckButtonIntent.KeyboardChord.Paste.intentId),
        DeckEditorIntentOption(R.string.deck_action_cut, DeckButtonIntent.KeyboardChord.Cut.intentId),
        DeckEditorIntentOption(R.string.deck_action_search, DeckButtonIntent.KeyboardChord.Search.intentId),
        DeckEditorIntentOption(R.string.deck_action_undo, DeckButtonIntent.KeyboardChord.Undo.intentId),
        DeckEditorIntentOption(R.string.deck_action_redo, DeckButtonIntent.KeyboardChord.Redo.intentId),
        DeckEditorIntentOption(R.string.deck_action_show_desktop, DeckButtonIntent.KeyboardChord.ShowDesktop.intentId),
        DeckEditorIntentOption(R.string.deck_action_screenshot, DeckButtonIntent.KeyboardChord.SnippingOverlay.intentId),
    )

    val mediaOptions: List<DeckEditorIntentOption> = listOf(
        DeckEditorIntentOption(R.string.grid_edit_media_mute, DeckButtonIntent.SystemMedia.Mute.intentId),
        DeckEditorIntentOption(R.string.grid_edit_media_play_pause, DeckButtonIntent.SystemMedia.PlayPause.intentId),
        DeckEditorIntentOption(R.string.grid_edit_media_vol_up, DeckButtonIntent.SystemMedia.VolumeUp.intentId),
        DeckEditorIntentOption(R.string.grid_edit_media_vol_down, DeckButtonIntent.SystemMedia.VolumeDown.intentId),
        DeckEditorIntentOption(R.string.grid_edit_media_prev, DeckButtonIntent.SystemMedia.PreviousTrack.intentId),
        DeckEditorIntentOption(R.string.grid_edit_media_next, DeckButtonIntent.SystemMedia.NextTrack.intentId),
    )

    fun defaultCellForKind(kind: DeckGridActionKind): DeckGridButtonPersisted =
        when (kind) {
            DeckGridActionKind.TEXT -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = kind,
                intentId = "deck.intent.inject_text",
                payload = mapOf("literal" to ""),
                iconToken = "text_jamon",
            )
            DeckGridActionKind.KEY -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = kind,
                intentId = DeckButtonIntent.SingleKey.Enter.intentId,
                iconToken = "keyboard_enter",
            )
            DeckGridActionKind.COMBO -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = kind,
                intentId = DeckButtonIntent.KeyboardChord.Paste.intentId,
                iconToken = "content_paste",
            )
            DeckGridActionKind.MEDIA -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = kind,
                intentId = DeckButtonIntent.SystemMedia.Mute.intentId,
                iconToken = "volume_mute",
            )
            DeckGridActionKind.PAGE_NAV -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = DeckGridActionKind.PAGE_NAV,
                intentId = DeckButtonIntent.PageNav.Next.intentId,
                iconToken = "next_track",
            )
            DeckGridActionKind.NOOP,
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            -> DeckGridButtonPersisted(
                id = "",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = DeckGridActionKind.NOOP,
                intentId = DeckButtonIntent.Noop.intentId,
                iconToken = null,
            )
        }

    fun mergeKindDefaults(base: DeckGridButtonPersisted, kind: DeckGridActionKind): DeckGridButtonPersisted {
        val safeKind = when (kind) {
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            -> DeckGridActionKind.NOOP
            else -> kind
        }
        val d = defaultCellForKind(safeKind)
        val literal = if (safeKind == DeckGridActionKind.TEXT) {
            base.payload["literal"].orEmpty().ifBlank { d.payload["literal"].orEmpty() }
        } else {
            ""
        }
        return base.copy(
            kind = safeKind,
            intentId = when (safeKind) {
                DeckGridActionKind.TEXT -> "deck.intent.inject_text"
                DeckGridActionKind.KEY -> base.intentId.takeIf { id -> keyOptions.any { it.intentId == id } }
                    ?: DeckButtonIntent.SingleKey.Enter.intentId
                DeckGridActionKind.COMBO -> base.intentId.takeIf { id -> comboOptions.any { it.intentId == id } }
                    ?: DeckButtonIntent.KeyboardChord.Paste.intentId
                DeckGridActionKind.MEDIA -> base.intentId.takeIf { id -> mediaOptions.any { it.intentId == id } }
                    ?: DeckButtonIntent.SystemMedia.Mute.intentId
                DeckGridActionKind.PAGE_NAV -> base.intentId.takeIf { id -> pageNavOptions.any { it.intentId == id } }
                    ?: DeckButtonIntent.PageNav.Next.intentId
                DeckGridActionKind.NOOP,
                DeckGridActionKind.APP_LAUNCH,
                DeckGridActionKind.SCRIPT,
                -> DeckButtonIntent.Noop.intentId
            },
            payload = if (safeKind == DeckGridActionKind.TEXT) mapOf("literal" to literal) else emptyMap(),
            iconToken = base.iconToken ?: d.iconToken,
        )
    }

    /** Same defaults as [mergeKindDefaults] for a knob binding slice (no label/icon in persisted knob actions). */
    fun mergeKindDefaultsForKnobAction(base: DeckKnobActionPersisted, kind: DeckGridActionKind): DeckKnobActionPersisted {
        val row = DeckGridButtonPersisted(
            id = "_knob_slice",
            sortIndex = 0,
            label = " ",
            subtitle = "",
            kind = base.kind,
            intentId = base.intentId,
            payload = base.payload,
            iconToken = null,
        )
        val merged = mergeKindDefaults(row, kind)
        return DeckKnobActionPersisted(
            kind = merged.kind,
            intentId = merged.intentId,
            payload = merged.payload,
        )
    }

    /** Curated icon tokens for the grid editor picker (glyph map in [com.example.deckbridge.ui.deck.deckGridSlotGlyph]). */
    data class DeckIconTokenOption(
        val token: String,
        val labelRes: Int,
    )

    val suggestedIconTokens: List<DeckIconTokenOption> = listOf(
        DeckIconTokenOption("text_jamon", R.string.grid_edit_kind_text),
        DeckIconTokenOption("search", R.string.deck_action_search),
        DeckIconTokenOption("content_paste", R.string.deck_action_paste),
        DeckIconTokenOption("content_copy", R.string.deck_action_copy),
        DeckIconTokenOption("content_cut", R.string.deck_action_cut),
        DeckIconTokenOption("show_desktop", R.string.deck_action_show_desktop),
        DeckIconTokenOption("undo", R.string.deck_action_undo),
        DeckIconTokenOption("redo", R.string.deck_action_redo),
        DeckIconTokenOption("keyboard_enter", R.string.deck_action_enter),
        DeckIconTokenOption("screen_snip", R.string.deck_action_screenshot),
        DeckIconTokenOption("volume_mute", R.string.deck_action_mute),
        DeckIconTokenOption("volume_up", R.string.grid_edit_media_vol_up),
        DeckIconTokenOption("volume_down", R.string.grid_edit_media_vol_down),
        DeckIconTokenOption("play_pause", R.string.grid_edit_media_play_pause),
        DeckIconTokenOption("previous_track", R.string.grid_edit_media_prev),
        DeckIconTokenOption("next_track", R.string.grid_edit_media_next),
    )
}
