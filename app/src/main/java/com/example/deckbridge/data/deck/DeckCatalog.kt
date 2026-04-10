package com.example.deckbridge.data.deck

import android.view.KeyEvent
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.model.HostConnectionStatus
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.MacroButton
import com.example.deckbridge.domain.model.PhysicalKeyBinding
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.profiles.Profile

/**
 * Static F1–F9 deck layout, profiles, and bindings. Resolution for display/dispatch is driven by [HostPlatform].
 */
object DeckCatalog {

    const val PROFILE_WINDOWS_ID = "profile_windows"
    const val PROFILE_MAC_ID = "profile_mac"

    private data class Slot(
        val id: String,
        val label: String,
        val intent: DeckButtonIntent,
        val sortIndex: Int,
        val iconToken: String?,
    )

    private val slots = listOf(
        Slot("btn_copy", "Copy", DeckButtonIntent.KeyboardChord.Copy, 0, "content_copy"),
        Slot("btn_paste", "Paste", DeckButtonIntent.KeyboardChord.Paste, 1, "content_paste"),
        Slot("btn_vol_up", "Vol +", DeckButtonIntent.SystemMedia.VolumeUp, 2, "volume_up"),
        Slot("btn_play", "Play / Pause", DeckButtonIntent.SystemMedia.PlayPause, 3, "play_pause"),
        Slot("btn_search", "Search", DeckButtonIntent.KeyboardChord.Search, 4, "search"),
        Slot("btn_cut", "Cut", DeckButtonIntent.KeyboardChord.Cut, 5, "content_cut"),
        Slot("btn_undo", "Undo", DeckButtonIntent.KeyboardChord.Undo, 6, "undo"),
        Slot("btn_redo", "Redo", DeckButtonIntent.KeyboardChord.Redo, 7, "redo"),
        Slot("btn_mute", "Mute", DeckButtonIntent.SystemMedia.Mute, 8, "volume_mute"),
    )

    fun profileFor(platform: HostPlatform): Profile {
        val p = platform.coerce()
        return when (p) {
            HostPlatform.MAC -> Profile(id = PROFILE_MAC_ID, name = "macOS", isDefault = true)
            HostPlatform.WINDOWS -> Profile(id = PROFILE_WINDOWS_ID, name = "Windows", isDefault = true)
            HostPlatform.UNKNOWN -> Profile(id = PROFILE_WINDOWS_ID, name = "Windows", isDefault = true)
        }
    }

    fun profileIdFor(platform: HostPlatform): String = profileFor(platform).id

    fun macroButtonsFor(platform: HostPlatform): List<MacroButton> {
        val p = platform.coerce()
        return slots.map { slot ->
            val resolved = PlatformActionResolver.resolve(slot.intent, p)
            MacroButton(
                id = slot.id,
                label = slot.label,
                intent = slot.intent,
                sortIndex = slot.sortIndex,
                iconToken = slot.iconToken,
                resolvedShortcut = resolved.shortcutDisplay,
            )
        }.sortedBy { it.sortIndex }
    }

    fun physicalBindingsFor(platform: HostPlatform): List<PhysicalKeyBinding> {
        val pid = profileIdFor(platform)
        return listOf(
            PhysicalKeyBinding("bind_f1", KeyEvent.KEYCODE_F1, "F1", "btn_copy", pid),
            PhysicalKeyBinding("bind_f2", KeyEvent.KEYCODE_F2, "F2", "btn_paste", pid),
            PhysicalKeyBinding("bind_f3", KeyEvent.KEYCODE_F3, "F3", "btn_vol_up", pid),
            PhysicalKeyBinding("bind_f4", KeyEvent.KEYCODE_F4, "F4", "btn_play", pid),
            PhysicalKeyBinding("bind_f5", KeyEvent.KEYCODE_F5, "F5", "btn_search", pid),
            PhysicalKeyBinding("bind_f6", KeyEvent.KEYCODE_F6, "F6", "btn_cut", pid),
            PhysicalKeyBinding("bind_f7", KeyEvent.KEYCODE_F7, "F7", "btn_undo", pid),
            PhysicalKeyBinding("bind_f8", KeyEvent.KEYCODE_F8, "F8", "btn_redo", pid),
            PhysicalKeyBinding("bind_f9", KeyEvent.KEYCODE_F9, "F9", "btn_mute", pid),
        )
    }

    /** Baseline [AppState] before runtime/input overlays (used by mocks + cold start). */
    fun baseDeckAppState(hostPlatform: HostPlatform): AppState {
        val p = hostPlatform.coerce()
        return AppState(
            hostPlatform = p,
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = "",
            ),
            hostConnection = HostConnectionStatus(
                usbState = HostUsbConnectionState.NOT_CONNECTED,
                hostLabel = "Work PC (Win 11)",
                detail = "USB host/agent not implemented yet · live status will appear here",
            ),
            activeProfile = profileFor(p),
            macroButtons = macroButtonsFor(p),
            recentInputEvents = emptyList(),
            physicalBindingsPreview = physicalBindingsFor(p),
            inputDiagnostics = InputDiagnostics(
                lastEventDevice = null,
                lastEventAtEpochMs = null,
                lastMotion = null,
                lastKeyCode = null,
                lastClassification = null,
                detectedExternalKeyboards = emptyList(),
                hintLine = "",
            ),
            deckHighlight = null,
            recentDeckActivations = emptyList(),
            systemStatusLine = "",
        )
    }

    fun withHostPlatform(prev: AppState, platform: HostPlatform): AppState {
        val p = platform.coerce()
        return prev.copy(
            hostPlatform = p,
            activeProfile = profileFor(p),
            macroButtons = macroButtonsFor(p),
            physicalBindingsPreview = physicalBindingsFor(p),
        )
    }

    private fun HostPlatform.coerce(): HostPlatform = when (this) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> this
    }
}
