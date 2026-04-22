package com.example.deckbridge.data.deck

import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

/**
 * Maps persisted [DeckGridButtonPersisted] rows to runtime [DeckButtonIntent].
 * [DeckGridActionKind.APP_LAUNCH] / [DeckGridActionKind.SCRIPT] resolve to [DeckButtonIntent.Noop] until implemented.
 */
object DeckGridIntentCodec {

    fun intentFromPersisted(button: DeckGridButtonPersisted): DeckButtonIntent {
        when (button.kind) {
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            DeckGridActionKind.NOOP,
            -> return DeckButtonIntent.Noop
            DeckGridActionKind.PAGE_NAV -> return when (button.intentId) {
                DeckButtonIntent.PageNav.Prev.intentId -> DeckButtonIntent.PageNav.Prev
                else -> DeckButtonIntent.PageNav.Next
            }
            else -> Unit
        }
        return intentFromId(button.intentId, button.payload) ?: DeckButtonIntent.Noop
    }

    fun intentFromKnobAction(action: DeckKnobActionPersisted): DeckButtonIntent =
        intentFromPersisted(
            DeckGridButtonPersisted(
                id = "_knob_action",
                sortIndex = 0,
                label = "",
                subtitle = "",
                kind = action.kind,
                intentId = action.intentId,
                payload = action.payload,
                iconToken = null,
                enabled = true,
                visible = true,
            ),
        )

    fun intentFromId(intentId: String, payload: Map<String, String>): DeckButtonIntent? = when (intentId) {
        "deck.intent.inject_text" -> {
            val lit = payload["literal"].orEmpty()
            DeckButtonIntent.InjectText(lit)
        }
        DeckButtonIntent.KeyboardChord.Copy.intentId -> DeckButtonIntent.KeyboardChord.Copy
        DeckButtonIntent.KeyboardChord.Paste.intentId -> DeckButtonIntent.KeyboardChord.Paste
        DeckButtonIntent.KeyboardChord.Cut.intentId -> DeckButtonIntent.KeyboardChord.Cut
        DeckButtonIntent.KeyboardChord.Search.intentId -> DeckButtonIntent.KeyboardChord.Search
        DeckButtonIntent.KeyboardChord.Undo.intentId -> DeckButtonIntent.KeyboardChord.Undo
        DeckButtonIntent.KeyboardChord.Redo.intentId -> DeckButtonIntent.KeyboardChord.Redo
        DeckButtonIntent.KeyboardChord.ShowDesktop.intentId -> DeckButtonIntent.KeyboardChord.ShowDesktop
        DeckButtonIntent.KeyboardChord.SnippingOverlay.intentId -> DeckButtonIntent.KeyboardChord.SnippingOverlay
        DeckButtonIntent.SingleKey.Enter.intentId -> DeckButtonIntent.SingleKey.Enter
        DeckButtonIntent.SingleKey.Escape.intentId -> DeckButtonIntent.SingleKey.Escape
        DeckButtonIntent.SystemMedia.VolumeUp.intentId -> DeckButtonIntent.SystemMedia.VolumeUp
        DeckButtonIntent.SystemMedia.VolumeDown.intentId -> DeckButtonIntent.SystemMedia.VolumeDown
        DeckButtonIntent.SystemMedia.PlayPause.intentId -> DeckButtonIntent.SystemMedia.PlayPause
        DeckButtonIntent.SystemMedia.PreviousTrack.intentId -> DeckButtonIntent.SystemMedia.PreviousTrack
        DeckButtonIntent.SystemMedia.NextTrack.intentId -> DeckButtonIntent.SystemMedia.NextTrack
        DeckButtonIntent.SystemMedia.Mute.intentId -> DeckButtonIntent.SystemMedia.Mute
        DeckButtonIntent.PageNav.Next.intentId -> DeckButtonIntent.PageNav.Next
        DeckButtonIntent.PageNav.Prev.intentId -> DeckButtonIntent.PageNav.Prev
        DeckButtonIntent.Noop.intentId -> DeckButtonIntent.Noop
        else -> null
    }

    fun payloadForIntent(intent: DeckButtonIntent): Map<String, String> = when (intent) {
        is DeckButtonIntent.InjectText -> mapOf("literal" to intent.literal)
        else -> emptyMap()
    }
}
