package com.example.deckbridge.domain.model

/**
 * User-facing intent of a deck tile. [PlatformActionResolver] maps this + [HostPlatform] to a [ResolvedAction].
 */
sealed class DeckButtonIntent {
    abstract val intentId: String

    sealed class KeyboardChord : DeckButtonIntent() {
        data object Copy : KeyboardChord() {
            override val intentId: String = "deck.intent.copy"
        }

        data object Paste : KeyboardChord() {
            override val intentId: String = "deck.intent.paste"
        }

        data object Cut : KeyboardChord() {
            override val intentId: String = "deck.intent.cut"
        }

        data object Search : KeyboardChord() {
            override val intentId: String = "deck.intent.search"
        }

        data object Undo : KeyboardChord() {
            override val intentId: String = "deck.intent.undo"
        }

        data object Redo : KeyboardChord() {
            override val intentId: String = "deck.intent.redo"
        }

        /** Windows ⊞+D / macOS Mission Control–style shortcut (best-effort on Mac). */
        data object ShowDesktop : KeyboardChord() {
            override val intentId: String = "deck.intent.show_desktop"
        }

        /** Windows Snipping Tool overlay (⊞+Shift+S). */
        data object SnippingOverlay : KeyboardChord() {
            override val intentId: String = "deck.intent.snipping_overlay"
        }
    }

    /** Types literal text on the host (LAN primary). */
    data class InjectText(val literal: String) : DeckButtonIntent() {
        override val intentId: String = "deck.intent.inject_text"
    }

    sealed class SingleKey : DeckButtonIntent() {
        data object Enter : SingleKey() {
            override val intentId: String = "deck.intent.key.enter"
        }

        data object Escape : SingleKey() {
            override val intentId: String = "deck.intent.key.escape"
        }
    }

    sealed class SystemMedia : DeckButtonIntent() {
        data object VolumeUp : SystemMedia() {
            override val intentId: String = "deck.intent.media.vol_up"
        }

        data object VolumeDown : SystemMedia() {
            override val intentId: String = "deck.intent.media.vol_down"
        }

        data object PlayPause : SystemMedia() {
            override val intentId: String = "deck.intent.media.play_pause"
        }

        data object PreviousTrack : SystemMedia() {
            override val intentId: String = "deck.intent.media.prev_track"
        }

        data object NextTrack : SystemMedia() {
            override val intentId: String = "deck.intent.media.next_track"
        }

        data object Mute : SystemMedia() {
            override val intentId: String = "deck.intent.media.mute"
        }
    }

    /** Placeholder / reserved actions; host delivery treats as no-op. */
    data object Noop : DeckButtonIntent() {
        override val intentId: String = "deck.intent.noop"
    }
}
