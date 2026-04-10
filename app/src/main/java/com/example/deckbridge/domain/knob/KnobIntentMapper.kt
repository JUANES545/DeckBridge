package com.example.deckbridge.domain.knob

import com.example.deckbridge.domain.model.DeckButtonIntent

/**
 * MVP mapping from physical knob index (0 = top, 1 = middle, 2 = bottom) to deck intents.
 * Replace with user-editable config later without changing calibration matching.
 */
object KnobIntentMapper {

    fun intentForRotate(knobIndex: Int, ccw: Boolean): DeckButtonIntent? = when (knobIndex) {
        0 -> if (ccw) DeckButtonIntent.SystemMedia.VolumeDown else DeckButtonIntent.SystemMedia.VolumeUp
        1 -> if (ccw) DeckButtonIntent.KeyboardChord.Copy else DeckButtonIntent.KeyboardChord.Paste
        2 -> if (ccw) DeckButtonIntent.SystemMedia.PreviousTrack else DeckButtonIntent.SystemMedia.NextTrack
        else -> null
    }

    fun intentForPress(knobIndex: Int): DeckButtonIntent? = when (knobIndex) {
        0 -> DeckButtonIntent.SystemMedia.PlayPause
        else -> null
    }
}
