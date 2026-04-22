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
            is DeckButtonIntent.KeyboardChord.ShowDesktop -> chord(
                intent = intent,
                displayName = "Show desktop",
                p = p,
                windows = "Win+D",
                mac = "⌃↑",
            )
            is DeckButtonIntent.KeyboardChord.SnippingOverlay -> chord(
                intent = intent,
                displayName = "Screenshot",
                p = p,
                windows = "Win+Shift+S",
                mac = "⌘⇧4",
            )
            is DeckButtonIntent.InjectText -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "Text",
                platform = p,
                shortcutDisplay = "LAN · \"${intent.literal}\"",
                kind = ResolvedActionKind.TEXT,
                textPayload = intent.literal,
                keyToken = null,
            )
            is DeckButtonIntent.SingleKey.Enter -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "Enter",
                platform = p,
                shortcutDisplay = "Enter",
                kind = ResolvedActionKind.KEY,
                textPayload = null,
                keyToken = "enter",
            )
            is DeckButtonIntent.SingleKey.Escape -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "Escape",
                platform = p,
                shortcutDisplay = "Esc",
                kind = ResolvedActionKind.KEY,
                textPayload = null,
                keyToken = "escape",
            )
            is DeckButtonIntent.SystemMedia.VolumeUp -> media(
                intent = intent,
                displayName = "Vol+",
                p = p,
                label = "Media · Volume up",
            )
            is DeckButtonIntent.SystemMedia.VolumeDown -> media(
                intent = intent,
                displayName = "Vol−",
                p = p,
                label = "Media · Volume down",
            )
            is DeckButtonIntent.SystemMedia.PlayPause -> media(
                intent = intent,
                displayName = "Play/Pause",
                p = p,
                label = "Media · Play/Pause",
            )
            is DeckButtonIntent.SystemMedia.PreviousTrack -> media(
                intent = intent,
                displayName = "Previous",
                p = p,
                label = "Media · Previous track",
            )
            is DeckButtonIntent.SystemMedia.NextTrack -> media(
                intent = intent,
                displayName = "Next",
                p = p,
                label = "Media · Next track",
            )
            is DeckButtonIntent.SystemMedia.Mute -> media(
                intent = intent,
                displayName = "Mute",
                p = p,
                label = "Media · Mute",
            )
            is DeckButtonIntent.PageNav.Next -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "Next page",
                platform = p,
                shortcutDisplay = "Page →",
                kind = ResolvedActionKind.NOOP,
            )
            is DeckButtonIntent.PageNav.Prev -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "Prev page",
                platform = p,
                shortcutDisplay = "Page ←",
                kind = ResolvedActionKind.NOOP,
            )
            DeckButtonIntent.Noop -> ResolvedAction(
                intentId = intent.intentId,
                intentDisplayName = "No-op",
                platform = p,
                shortcutDisplay = "—",
                kind = ResolvedActionKind.NOOP,
                textPayload = null,
                keyToken = null,
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
        textPayload = null,
        keyToken = null,
    )

    private fun HostPlatform.coerceForDeck(): HostPlatform = when (this) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> this
    }
}
