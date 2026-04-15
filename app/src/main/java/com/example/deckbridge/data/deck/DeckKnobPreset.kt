package com.example.deckbridge.data.deck

import android.content.res.Resources
import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent

/**
 * Factory defaults for the three knobs (volume / clipboard / transport — same behavior as the original hardcoded map).
 */
object DeckKnobPreset {

    fun defaultKnobsFromResources(res: Resources): DeckKnobsLayoutPersisted {
        val top = DeckKnobPersisted(
            id = "knob_top",
            sortIndex = 0,
            label = res.getString(R.string.knob_top),
            subtitle = "",
            rotateCcw = action(DeckButtonIntent.SystemMedia.VolumeDown),
            rotateCw = action(DeckButtonIntent.SystemMedia.VolumeUp),
            press = action(DeckButtonIntent.SystemMedia.PlayPause),
            enabled = true,
            visible = true,
            iconToken = null,
        )
        val middle = DeckKnobPersisted(
            id = "knob_middle",
            sortIndex = 1,
            label = res.getString(R.string.knob_middle),
            subtitle = "",
            rotateCcw = action(DeckButtonIntent.KeyboardChord.Copy),
            rotateCw = action(DeckButtonIntent.KeyboardChord.Paste),
            press = action(DeckButtonIntent.KeyboardChord.Cut),
            enabled = true,
            visible = true,
            iconToken = null,
        )
        val bottom = DeckKnobPersisted(
            id = "knob_bottom",
            sortIndex = 2,
            label = res.getString(R.string.knob_bottom),
            subtitle = "",
            rotateCcw = action(DeckButtonIntent.SystemMedia.PreviousTrack),
            rotateCw = action(DeckButtonIntent.SystemMedia.NextTrack),
            press = action(DeckButtonIntent.SystemMedia.PlayPause),
            enabled = true,
            visible = true,
            iconToken = null,
        )
        return DeckKnobsLayoutPersisted(listOf(top, middle, bottom))
    }

    private fun action(intent: DeckButtonIntent): DeckKnobActionPersisted =
        DeckKnobActionPersisted(
            kind = kindForIntent(intent),
            intentId = intent.intentId,
            payload = DeckGridIntentCodec.payloadForIntent(intent),
        )

    private fun kindForIntent(intent: DeckButtonIntent): DeckGridActionKind = when (intent) {
        is DeckButtonIntent.InjectText -> DeckGridActionKind.TEXT
        is DeckButtonIntent.SingleKey -> DeckGridActionKind.KEY
        is DeckButtonIntent.KeyboardChord -> DeckGridActionKind.COMBO
        is DeckButtonIntent.SystemMedia -> DeckGridActionKind.MEDIA
        is DeckButtonIntent.Noop -> DeckGridActionKind.NOOP
    }
}
