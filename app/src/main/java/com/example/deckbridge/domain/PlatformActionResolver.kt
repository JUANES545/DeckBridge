package com.example.deckbridge.domain

import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind

/**
 * Maps a [DeckButtonIntent] to a [ResolvedAction] for the active host OS.
 *
 * **Redo:** Windows uses **Ctrl+Y** (common US layout). macOS uses **⌘⇧Z** (standard redo).
 * Media shortcuts are labeled generically until HID maps them to scan codes.
 */
object PlatformActionResolver {

    fun resolve(intent: DeckButtonIntent, platform: HostPlatform): ResolvedAction {
        val p = platform.coerceForDeck()
        return when (intent) {
            is DeckButtonIntent.KeyboardChord.Copy -> chord(
                intent = intent,
                displayName = "Copy",
                p = p,
                windows = "Ctrl+C",
                mac = "⌘C",
            )
            is DeckButtonIntent.KeyboardChord.Paste -> chord(
                intent = intent,
                displayName = "Paste",
                p = p,
                windows = "Ctrl+V",
                mac = "⌘V",
            )
            is DeckButtonIntent.KeyboardChord.Cut -> chord(
                intent = intent,
                displayName = "Cut",
                p = p,
                windows = "Ctrl+X",
                mac = "⌘X",
            )
            is DeckButtonIntent.KeyboardChord.Search -> chord(
                intent = intent,
                displayName = "Search",
                p = p,
                windows = "Ctrl+F",
                mac = "⌘F",
            )
            is DeckButtonIntent.KeyboardChord.Undo -> chord(
                intent = intent,
                displayName = "Undo",
                p = p,
                windows = "Ctrl+Z",
                mac = "⌘Z",
            )
            is DeckButtonIntent.KeyboardChord.Redo -> chord(
                intent = intent,
                displayName = "Redo",
                p = p,
                windows = "Ctrl+Y",
                mac = "⌘⇧Z",
            )
            is DeckButtonIntent.SystemMedia.VolumeUp -> media(
                intent = intent,
                displayName = "Vol+",
                p = p,
                label = "Media · Volume up",
            )
            is DeckButtonIntent.SystemMedia.PlayPause -> media(
                intent = intent,
                displayName = "Play/Pause",
                p = p,
                label = "Media · Play/Pause",
            )
            is DeckButtonIntent.SystemMedia.Mute -> media(
                intent = intent,
                displayName = "Mute",
                p = p,
                label = "Media · Mute",
            )
        }
    }

    private fun chord(
        intent: DeckButtonIntent,
        displayName: String,
        p: HostPlatform,
        windows: String,
        mac: String,
    ): ResolvedAction {
        val line = when (p) {
            HostPlatform.MAC -> mac
            HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> windows
        }
        return ResolvedAction(
            intentId = intent.intentId,
            intentDisplayName = displayName,
            platform = p,
            shortcutDisplay = line,
            kind = ResolvedActionKind.KEY_CHORD,
        )
    }

    private fun media(
        intent: DeckButtonIntent,
        displayName: String,
        p: HostPlatform,
        label: String,
    ): ResolvedAction = ResolvedAction(
        intentId = intent.intentId,
        intentDisplayName = displayName,
        platform = p,
        shortcutDisplay = label,
        kind = ResolvedActionKind.SYSTEM_MEDIA,
    )

    private fun HostPlatform.coerceForDeck(): HostPlatform = when (this) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> this
    }
}
