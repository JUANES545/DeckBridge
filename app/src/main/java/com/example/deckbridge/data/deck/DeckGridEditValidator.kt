package com.example.deckbridge.data.deck

import androidx.annotation.StringRes
import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

/**
 * Returns a string resource id for a user-visible error, or `null` if the cell can be persisted.
 */
object DeckGridEditValidator {

    private val allowedKeyIntentIds = setOf(
        DeckButtonIntent.SingleKey.Enter.intentId,
        DeckButtonIntent.SingleKey.Escape.intentId,
    )

    private val allowedComboIntentIds = setOf(
        DeckButtonIntent.KeyboardChord.Copy.intentId,
        DeckButtonIntent.KeyboardChord.Paste.intentId,
        DeckButtonIntent.KeyboardChord.Cut.intentId,
        DeckButtonIntent.KeyboardChord.Search.intentId,
        DeckButtonIntent.KeyboardChord.Undo.intentId,
        DeckButtonIntent.KeyboardChord.Redo.intentId,
        DeckButtonIntent.KeyboardChord.ShowDesktop.intentId,
        DeckButtonIntent.KeyboardChord.SnippingOverlay.intentId,
    )

    private val allowedMediaIntentIds = setOf(
        DeckButtonIntent.SystemMedia.VolumeUp.intentId,
        DeckButtonIntent.SystemMedia.VolumeDown.intentId,
        DeckButtonIntent.SystemMedia.PlayPause.intentId,
        DeckButtonIntent.SystemMedia.PreviousTrack.intentId,
        DeckButtonIntent.SystemMedia.NextTrack.intentId,
        DeckButtonIntent.SystemMedia.Mute.intentId,
    )

    @StringRes
    fun validate(cell: DeckGridButtonPersisted): Int? {
        if (cell.label.isBlank()) return R.string.grid_edit_err_label_empty
        when (cell.kind) {
            DeckGridActionKind.TEXT -> {
                if (cell.intentId != "deck.intent.inject_text") {
                    return R.string.grid_edit_err_type_payload
                }
                if (cell.payload["literal"].orEmpty().isBlank()) return R.string.grid_edit_err_text_empty
            }
            DeckGridActionKind.KEY -> {
                if (cell.intentId !in allowedKeyIntentIds) return R.string.grid_edit_err_key_invalid
            }
            DeckGridActionKind.COMBO -> {
                if (cell.intentId !in allowedComboIntentIds) return R.string.grid_edit_err_combo_invalid
            }
            DeckGridActionKind.MEDIA -> {
                if (cell.intentId !in allowedMediaIntentIds) return R.string.grid_edit_err_media_invalid
            }
            DeckGridActionKind.NOOP -> {
                if (cell.intentId != DeckButtonIntent.Noop.intentId) {
                    return R.string.grid_edit_err_type_payload
                }
            }
            DeckGridActionKind.PAGE_NAV -> {
                val allowed = setOf(DeckButtonIntent.PageNav.Next.intentId, DeckButtonIntent.PageNav.Prev.intentId)
                if (cell.intentId !in allowed) return R.string.grid_edit_err_type_payload
            }
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            -> return R.string.grid_edit_err_reserved_kind
        }
        return null
    }
}
