package com.example.deckbridge.data.deck

import android.content.res.Resources
import android.view.KeyEvent
import com.example.deckbridge.R
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.domain.model.HidTransportUiState
import com.example.deckbridge.domain.model.HostConnectionStatus
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.MacroButton
import com.example.deckbridge.domain.model.PhysicalKeyBinding
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.profiles.Profile

/**
 * Static F1–F9 deck layout, profiles, and bindings. Resolution for display/dispatch is driven by [HostPlatform].
 * User-visible labels come from string resources via [Resources].
 */
object DeckCatalog {

    const val PROFILE_WINDOWS_ID = "profile_windows"
    const val PROFILE_MAC_ID = "profile_mac"

    private data class Slot(
        val id: String,
        val labelResId: Int,
        val intent: DeckButtonIntent,
        val sortIndex: Int,
        val iconToken: String?,
    )

    private val slots = listOf(
        Slot("btn_copy", R.string.deck_action_copy, DeckButtonIntent.KeyboardChord.Copy, 0, "content_copy"),
        Slot("btn_paste", R.string.deck_action_paste, DeckButtonIntent.KeyboardChord.Paste, 1, "content_paste"),
        Slot("btn_vol_up", R.string.deck_action_vol_up, DeckButtonIntent.SystemMedia.VolumeUp, 2, "volume_up"),
        Slot("btn_play", R.string.deck_action_play_pause, DeckButtonIntent.SystemMedia.PlayPause, 3, "play_pause"),
        Slot("btn_search", R.string.deck_action_search, DeckButtonIntent.KeyboardChord.Search, 4, "search"),
        Slot("btn_cut", R.string.deck_action_cut, DeckButtonIntent.KeyboardChord.Cut, 5, "content_cut"),
        Slot("btn_undo", R.string.deck_action_undo, DeckButtonIntent.KeyboardChord.Undo, 6, "undo"),
        Slot("btn_redo", R.string.deck_action_redo, DeckButtonIntent.KeyboardChord.Redo, 7, "redo"),
        Slot("btn_mute", R.string.deck_action_mute, DeckButtonIntent.SystemMedia.Mute, 8, "volume_mute"),
    )

    fun profileFor(platform: HostPlatform, res: Resources): Profile = when (platform) {
        HostPlatform.MAC -> Profile(id = PROFILE_MAC_ID, name = res.getString(R.string.profile_name_mac), isDefault = true)
        HostPlatform.WINDOWS -> Profile(id = PROFILE_WINDOWS_ID, name = res.getString(R.string.profile_name_windows), isDefault = true)
        HostPlatform.UNKNOWN -> Profile(
            id = PROFILE_WINDOWS_ID,
            name = res.getString(R.string.profile_name_unknown),
            isDefault = true,
        )
    }

    fun profileIdFor(platform: HostPlatform): String = when (platform.coerceForDeckData()) {
        HostPlatform.MAC -> PROFILE_MAC_ID
        HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> PROFILE_WINDOWS_ID
    }

    /** @throws android.content.res.Resources.NotFoundException if [res] is invalid */
    fun macroButtonsFor(platform: HostPlatform, res: Resources): List<MacroButton> {
        val p = platform.coerceForDeckData()
        return slots.map { slot ->
            val resolved = PlatformActionResolver.resolve(slot.intent, p)
            MacroButton(
                id = slot.id,
                label = res.getString(slot.labelResId),
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
    fun baseDeckAppState(hostPlatform: HostPlatform, res: Resources): AppState {
        return AppState(
            hostPlatform = hostPlatform,
            hostPlatformSource = HostPlatformSource.MANUAL,
            hostDetectionDetail = res.getString(R.string.host_detect_manual),
            physicalKeyboard = PhysicalKeyboardStatus(
                state = PhysicalKeyboardConnectionState.DISCONNECTED,
                deviceName = null,
                detail = "",
            ),
            hostConnection = HostConnectionStatus(
                usbState = HostUsbConnectionState.NOT_CONNECTED,
                hostLabel = res.getString(R.string.host_default_label),
                detail = res.getString(R.string.host_usb_detail_placeholder),
            ),
            activeProfile = profileFor(hostPlatform, res),
            macroButtons = macroButtonsFor(hostPlatform, res),
            physicalBindingsPreview = physicalBindingsFor(hostPlatform),
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
            hardwareCalibration = null,
            hardwareMirrorHighlight = null,
            hardwareDiagSummary = null,
            rawInputDiagnostics = emptyList(),
            hidTransport = HidTransportUiState.initial(
                summary = res.getString(R.string.hid_transport_summary_not_probed),
                detail = res.getString(R.string.hid_transport_detail_default),
            ),
        )
    }

    fun withHostPlatform(prev: AppState, platform: HostPlatform, res: Resources): AppState {
        return prev.copy(
            hostPlatform = platform,
            activeProfile = profileFor(platform, res),
            macroButtons = macroButtonsFor(platform, res),
            physicalBindingsPreview = physicalBindingsFor(platform),
        )
    }
}
