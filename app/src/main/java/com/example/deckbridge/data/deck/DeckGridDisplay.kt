package com.example.deckbridge.data.deck

import android.content.res.Resources
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.MacroButton

/** Fills [MacroButton.resolvedShortcut] from persisted subtitles + platform resolver. */
object DeckGridDisplay {

    fun applyResolvedShortcuts(buttons: List<MacroButton>, platform: HostPlatform, res: Resources): List<MacroButton> {
        val p = platform.coerceForDeckData()
        return buttons.map { mb ->
            val resolved = PlatformActionResolver.resolve(mb.intent, p)
            val line = when (p) {
                HostPlatform.MAC -> resolved.shortcutDisplay
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN ->
                    mb.windowsSubtitle.ifBlank { resolved.shortcutDisplay }
            }
            mb.copy(resolvedShortcut = line)
        }
    }
}
