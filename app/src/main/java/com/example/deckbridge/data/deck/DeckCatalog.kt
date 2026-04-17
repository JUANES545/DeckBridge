package com.example.deckbridge.data.deck

import android.content.res.Resources
import android.view.KeyEvent
import com.example.deckbridge.R
import com.example.deckbridge.domain.deck.DeckGridLayoutPersisted
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.hardware.KnobMirrorRotationAccum
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HidTransportUiState
import com.example.deckbridge.domain.model.HostConnectionStatus
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.PhysicalKeyBinding
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardStatus
import com.example.deckbridge.domain.model.PlatformSlotState
import com.example.deckbridge.profiles.Profile

/**
 * Profiles, F-key bindings, and baseline [AppState] shell.
 * The **3×3 grid body** comes from [DeckGridPreset] + persisted JSON (see repository bootstrap);
 * knobs default from [DeckKnobPreset] until persisted surface is applied.
 */
object DeckCatalog {

    const val PROFILE_WINDOWS_ID = "profile_windows"
    const val PROFILE_MAC_ID = "profile_mac"

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

    /**
     * F1–F9 → grid cells in **visual order** (sort index 0 = top-left).
     */
    fun physicalBindingsForFKeys(platform: HostPlatform, macroButtonIdsInGridOrder: List<String>): List<PhysicalKeyBinding> {
        require(macroButtonIdsInGridOrder.size == DeckGridLayoutPersisted.GRID_SLOT_COUNT)
        val pid = profileIdFor(platform)
        val keyCodes = intArrayOf(
            KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_F2,
            KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5,
            KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_F7,
            KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9,
        )
        return macroButtonIdsInGridOrder.mapIndexed { i, macroId ->
            PhysicalKeyBinding(
                id = "bind_f${i + 1}",
                keyCode = keyCodes[i],
                keyLabel = "F${i + 1}",
                macroButtonId = macroId,
                profileId = pid,
            )
        }
    }

    /** Baseline [AppState] before runtime/input overlays (mocks + cold start shell). Grid uses preset until repository applies persisted surface. */
    fun baseDeckAppState(hostPlatform: HostPlatform, res: Resources): AppState {
        val layout = DeckGridPreset.defaultLayoutFromResources(res)
        val knobs: DeckKnobsLayoutPersisted = DeckKnobPreset.defaultKnobsFromResources(res)
        val macroButtons = DeckGridDisplay.applyResolvedShortcuts(
            DeckGridMacroMapper.toMacroButtons(layout),
            hostPlatform,
            res,
        )
        val bindings = physicalBindingsForFKeys(
            hostPlatform,
            layout.sortedButtons().map { it.id },
        )
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
            macroButtons = macroButtons,
            deckKnobs = knobs,
            physicalBindingsPreview = bindings,
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
            knobMirrorRotation = KnobMirrorRotationAccum(),
            hardwareDiagSummary = null,
            rawInputDiagnostics = emptyList(),
            hidTransport = HidTransportUiState.initial(
                summary = res.getString(R.string.hid_transport_summary_not_probed),
                detail = res.getString(R.string.hid_transport_detail_default),
            ),
            hidPcModeEnabled = false,
            privilegedShellAvailable = false,
            hidDebugLine = "",
            animatedBackgroundMode = AnimatedBackgroundMode.WHEN_CHARGING,
            windowsSlot = PlatformSlotState(channel = HostDeliveryChannel.LAN),
            macSlot = PlatformSlotState(channel = HostDeliveryChannel.MAC_BRIDGE),
        )
    }

    fun withHostPlatform(prev: AppState, platform: HostPlatform, res: Resources): AppState {
        val orderedIds = prev.macroButtons.sortedBy { it.sortIndex }.map { it.id }
        return prev.copy(
            hostPlatform = platform,
            activeProfile = profileFor(platform, res),
            macroButtons = DeckGridDisplay.applyResolvedShortcuts(prev.macroButtons, platform, res),
            physicalBindingsPreview = physicalBindingsForFKeys(platform, orderedIds),
        )
    }
}
