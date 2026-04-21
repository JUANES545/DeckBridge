package com.example.deckbridge.ui.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.RawChannel
import com.example.deckbridge.domain.hardware.RawDiagnosticLine
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.PlatformSlotState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onNavigateBack: () -> Unit,
    onOpenAddAnotherHost: () -> Unit,
    onRefreshKeyboards: () -> Unit,
    onHostAutoDetectChanged: (Boolean) -> Unit,
    onHostDeliveryChannelChanged: (HostDeliveryChannel) -> Unit = {},
    onApplyLanEndpoint: (String, Int) -> Unit = { _, _ -> },
    onTestLanHealth: () -> Unit = {},
    onForgetLanLink: () -> Unit = {},
    onApplyWindowsEndpoint: (String, Int) -> Unit = { _, _ -> },
    onTestWindowsHealth: () -> Unit = {},
    onForgetWindowsLink: () -> Unit = {},
    onApplyMacEndpoint: (String, Int) -> Unit = { _, _ -> },
    onTestMacHealth: () -> Unit = {},
    onForgetMacLink: () -> Unit = {},
    onSetMacSlotChannel: (HostDeliveryChannel) -> Unit = {},
    onOpenHardwareCalibration: () -> Unit = {},
    onAnimatedBackgroundModeChanged: (AnimatedBackgroundMode) -> Unit = {},
    onAnimatedBackgroundThemeChanged: (AnimatedBackgroundTheme) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onKeepKeyboardAwakeChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        onRefreshKeyboards()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Connect to PC ────────────────────────────────────────────────
            AddAnotherHostSettingsCard(onClick = onOpenAddAnotherHost)

            // ── Background service (required for deck to work) ───────────────
            SectionLabel(stringResource(R.string.settings_section_background))
            AccessibilityServiceCard(onOpenAccessibilitySettings = onOpenAccessibilitySettings)

            // ── Appearance ───────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_animated_background))
            AnimatedBackgroundPreferenceCard(
                selectedMode = state.animatedBackgroundMode,
                selectedTheme = state.animatedBackgroundTheme,
                onSelectMode = onAnimatedBackgroundModeChanged,
                onSelectTheme = onAnimatedBackgroundThemeChanged,
            )

            // ── Hardware calibration ─────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_calibration_wizard_section))
            Text(
                text = stringResource(R.string.settings_calibration_wizard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenHardwareCalibration,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(stringResource(R.string.settings_calibration_wizard_button))
            }

            // ── Keyboard & host ──────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_keyboard_host))
            KeyboardHostCompactCard(state = state)

            if (state.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED) {
                SectionLabel(stringResource(R.string.settings_section_keep_keyboard))
                KeepKeyboardAwakeCard(
                    keepAwake = state.keepKeyboardAwake,
                    onChanged = onKeepKeyboardAwakeChanged,
                )
            }

            // ── PC connections ───────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_windows_pc))
            SlotCard(
                slot = state.windowsSlot,
                showChannelSelector = false,
                onApply = onApplyWindowsEndpoint,
                onTest = onTestWindowsHealth,
                onForget = onForgetWindowsLink,
                onSetChannel = {},
            )

            SectionLabel(stringResource(R.string.settings_section_mac))
            SlotCard(
                slot = state.macSlot,
                showChannelSelector = true,
                onApply = onApplyMacEndpoint,
                onTest = onTestMacHealth,
                onForget = onForgetMacLink,
                onSetChannel = onSetMacSlotChannel,
                bridgeServerLocalIp = state.macSlot.macBridgeServerLocalIp,
            )

            // ── Advanced (collapsible) ───────────────────────────────────────
            AdvancedSection(state = state, onHostAutoDetectChanged = onHostAutoDetectChanged)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddAnotherHostSettingsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_add_another_host_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_add_another_host_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.alpha(0.88f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AnimatedBackgroundPreferenceCard(
    selectedMode: AnimatedBackgroundMode,
    selectedTheme: AnimatedBackgroundTheme,
    onSelectMode: (AnimatedBackgroundMode) -> Unit,
    onSelectTheme: (AnimatedBackgroundTheme) -> Unit,
) {
    ElevatedInfoCard {
        // — When to activate —
        Text(
            text = stringResource(R.string.settings_animated_bg_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        AnimatedBackgroundRadioRow(
            label = stringResource(R.string.settings_animated_bg_always),
            chosen = selectedMode == AnimatedBackgroundMode.ALWAYS,
            onClick = { onSelectMode(AnimatedBackgroundMode.ALWAYS) },
        )
        AnimatedBackgroundRadioRow(
            label = stringResource(R.string.settings_animated_bg_charging),
            chosen = selectedMode == AnimatedBackgroundMode.WHEN_CHARGING,
            onClick = { onSelectMode(AnimatedBackgroundMode.WHEN_CHARGING) },
        )
        AnimatedBackgroundRadioRow(
            label = stringResource(R.string.settings_animated_bg_off),
            chosen = selectedMode == AnimatedBackgroundMode.OFF,
            onClick = { onSelectMode(AnimatedBackgroundMode.OFF) },
        )

        // — Theme picker (only shown when animation is enabled) —
        if (selectedMode != AnimatedBackgroundMode.OFF) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_animated_bg_theme_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            AnimatedBackgroundThemeChips(
                selected = selectedTheme,
                onSelect = onSelectTheme,
            )
        }
    }
}

@Composable
private fun AnimatedBackgroundThemeChips(
    selected: AnimatedBackgroundTheme,
    onSelect: (AnimatedBackgroundTheme) -> Unit,
) {
    val themes = listOf(
        AnimatedBackgroundTheme.GRID_PULSE to stringResource(R.string.settings_animated_bg_theme_pulse),
        AnimatedBackgroundTheme.PARTICLES  to stringResource(R.string.settings_animated_bg_theme_particles),
        AnimatedBackgroundTheme.AURORA     to stringResource(R.string.settings_animated_bg_theme_aurora),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        themes.forEach { (theme, label) ->
            val isSelected = selected == theme
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(theme) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AnimatedBackgroundRadioRow(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = chosen,
            onClick = onClick,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun CalibrationMapCard(calibration: HardwareCalibrationConfig?) {
    ElevatedInfoCard {
        if (calibration == null) {
            Text(
                text = stringResource(R.string.hardware_calibrated_no),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ElevatedInfoCard
        }
        val desc = calibration.deviceDescriptor
        Text(
            text = if (desc.isNullOrBlank()) {
                stringResource(R.string.settings_calibration_device_unknown)
            } else {
                stringResource(R.string.settings_calibration_device, desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (calibration.deviceVendorId == null || calibration.deviceProductId == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_calibration_bt_identity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_calibration_pads, calibration.padKeyCodes.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        calibration.knobs.sortedBy { it.index }.forEach { k ->
            if (k.index > 0) Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.settings_knob_binding_line,
                    knobTitle(k.index),
                    k.rotateCcwKeyCodes.size,
                    k.rotateCwKeyCodes.size,
                    k.motionFingerprints.size,
                    if (k.pressKeyCode != null) {
                        stringResource(R.string.settings_bind_yes)
                    } else {
                        stringResource(R.string.settings_bind_no)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun knobTitle(index: Int): String = when (index) {
    0 -> stringResource(R.string.knob_top)
    1 -> stringResource(R.string.knob_middle)
    2 -> stringResource(R.string.knob_bottom)
    else -> stringResource(R.string.knob_generic, index + 1)
}

@Composable
private fun SlotCard(
    slot: PlatformSlotState,
    showChannelSelector: Boolean,
    onApply: (String, Int) -> Unit,
    onTest: () -> Unit,
    onForget: () -> Unit,
    onSetChannel: (HostDeliveryChannel) -> Unit,
    bridgeServerLocalIp: String? = null,
) {
    var hostDraft by remember { mutableStateOf(slot.host) }
    var portDraft by remember { mutableStateOf(slot.port.toString()) }
    LaunchedEffect(slot.host, slot.port) {
        hostDraft = slot.host
        portDraft = slot.port.toString()
    }

    ElevatedInfoCard {
        if (showChannelSelector) {
            Text(
                text = stringResource(R.string.settings_lan_channel_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_mac_channel_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetChannel(HostDeliveryChannel.LAN) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = slot.channel == HostDeliveryChannel.LAN,
                    onClick = { onSetChannel(HostDeliveryChannel.LAN) },
                )
                Text(
                    text = stringResource(R.string.settings_mac_channel_lan),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetChannel(HostDeliveryChannel.MAC_BRIDGE) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = slot.channel == HostDeliveryChannel.MAC_BRIDGE,
                    onClick = { onSetChannel(HostDeliveryChannel.MAC_BRIDGE) },
                )
                Text(
                    text = stringResource(R.string.settings_mac_channel_bridge),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        val isBridgeChannel = slot.channel == HostDeliveryChannel.MAC_BRIDGE
        if (isBridgeChannel && bridgeServerLocalIp != null) {
            Text(
                text = stringResource(R.string.settings_mac_bridge_android_ip_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bridgeServerLocalIp,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (!isBridgeChannel) {
            OutlinedTextField(
                value = hostDraft,
                onValueChange = { hostDraft = it },
                label = { Text(stringResource(R.string.settings_lan_host_label)) },
                placeholder = { Text(stringResource(R.string.settings_lan_host_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = portDraft,
                onValueChange = { v -> portDraft = v.filter { it.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.settings_lan_port_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        val p = portDraft.toIntOrNull()?.coerceIn(1, 65_535) ?: slot.port
                        onApply(hostDraft.trim(), p)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_lan_apply))
                }
                Button(onClick = onTest) {
                    Text(stringResource(R.string.settings_lan_test_health))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when (slot.healthOk) {
                null -> stringResource(R.string.settings_slot_not_configured)
                true -> stringResource(R.string.settings_slot_healthy)
                false -> if (slot.healthRetrying) stringResource(R.string.settings_slot_retrying) else stringResource(R.string.settings_slot_offline)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (slot.trustOk) {
                stringResource(R.string.settings_lan_trust_ok)
            } else {
                stringResource(R.string.settings_slot_trust_lost)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (slot.trustOk) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (slot.pairActive) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_lan_forget_link))
            }
        }
        if (slot.healthOk == false && !slot.healthDetail.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = slot.healthDetail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_lan_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AdvancedSection(
    state: AppState,
    onHostAutoDetectChanged: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "AVANZADO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HostAutoDetectCard(
                state = state,
                onHostAutoDetectChanged = onHostAutoDetectChanged,
            )
            SectionLabel(stringResource(R.string.settings_calibration_map))
            CalibrationMapCard(calibration = state.hardwareCalibration)
            SectionLabel(stringResource(R.string.settings_activations_recent))
            DeckActivationsCompactCard(entries = state.recentDeckActivations.take(SETTINGS_ACTIVATIONS_VISIBLE))
            RawTailCard(lines = state.rawInputDiagnostics)
        }
    }
}

@Composable
private fun HostAutoDetectCard(
    state: AppState,
    onHostAutoDetectChanged: (Boolean) -> Unit,
) {
    ElevatedInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_host_auto_detect),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).widthIn(max = 220.dp),
            )
            Switch(
                checked = state.hostPlatformSource == HostPlatformSource.AUTOMATIC,
                onCheckedChange = onHostAutoDetectChanged,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_host_auto_detect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (state.hostPlatformSource == HostPlatformSource.AUTOMATIC) {
                stringResource(R.string.settings_host_source_auto)
            } else {
                stringResource(R.string.settings_host_source_manual)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.hostDetectionDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyboardHostCompactCard(state: AppState) {
    ElevatedInfoCard {
        StatusRow(
            label = stringResource(R.string.label_connection_state),
            value = keyboardStatusLabel(state.physicalKeyboard.state),
            positive = state.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED,
        )
        state.physicalKeyboard.deviceName?.let { name ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.label_device_name, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        StatusRow(
            label = stringResource(R.string.label_host_platform),
            value = platformLabel(state.hostPlatform),
            positive = state.hostPlatform != HostPlatform.UNKNOWN,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.diag_external_list_title, state.inputDiagnostics.detectedExternalKeyboards.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeepKeyboardAwakeCard(
    keepAwake: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    ElevatedInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_keep_keyboard_awake_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_keep_keyboard_awake_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = keepAwake,
                onCheckedChange = onChanged,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_keep_keyboard_awake_experimental),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RawTailCard(lines: List<RawDiagnosticLine>) {
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.settings_raw_tail_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.raw_diagnostics_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lines.take(RAW_TAIL_LINES).forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(5.dp))
                val ch = when (line.channel) {
                    RawChannel.KEY -> "KEY"
                    RawChannel.MOTION -> "MOTION"
                }
                Text(
                    text = stringResource(R.string.raw_diagnostics_line, ch, line.summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeckActivationsCompactCard(entries: List<DeckActivationLogEntry>) {
    ElevatedInfoCard {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.deck_activations_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.deck_activation_line,
                        entry.buttonLabel,
                        entry.resolvedShortcut,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = activationSourceLabel(entry.source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatTime(entry.occurredAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ElevatedInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    positive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (positive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun keyboardStatusLabel(status: PhysicalKeyboardConnectionState): String = when (status) {
    PhysicalKeyboardConnectionState.DISCONNECTED -> stringResource(R.string.kb_state_disconnected)
    PhysicalKeyboardConnectionState.CONNECTING -> stringResource(R.string.kb_state_connecting)
    PhysicalKeyboardConnectionState.CONNECTED -> stringResource(R.string.kb_state_connected)
    PhysicalKeyboardConnectionState.ERROR -> stringResource(R.string.kb_state_error)
}

@Composable
private fun platformLabel(platform: HostPlatform): String = when (platform) {
    HostPlatform.WINDOWS -> stringResource(R.string.platform_windows)
    HostPlatform.MAC -> stringResource(R.string.platform_mac)
    HostPlatform.UNKNOWN -> stringResource(R.string.platform_unknown)
}

@Composable
private fun activationSourceLabel(source: ButtonTriggerSource): String = when (source) {
    ButtonTriggerSource.TOUCH -> stringResource(R.string.trigger_touch)
    ButtonTriggerSource.HARDWARE_KEY -> stringResource(R.string.trigger_hardware_key)
    ButtonTriggerSource.HARDWARE_KNOB -> stringResource(R.string.trigger_hardware_knob)
    ButtonTriggerSource.TOUCH_MIRROR_KNOB -> stringResource(R.string.trigger_touch_mirror_knob)
    ButtonTriggerSource.SIMULATED -> stringResource(R.string.trigger_simulated_activation)
}

private fun formatTime(epochMs: Long): String {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return fmt.format(Date(epochMs))
}

@Composable
private fun AccessibilityServiceCard(onOpenAccessibilitySettings: () -> Unit) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isEnabled = isAccessibilityServiceEnabled(context)
    }
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.settings_accessibility_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_accessibility_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isEnabled) {
                    stringResource(R.string.settings_accessibility_status_on)
                } else {
                    stringResource(R.string.settings_accessibility_status_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_accessibility_open))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_accessibility_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    val componentName = ComponentName(context, "com.example.deckbridge.accessibility.DeckBridgeAccessibilityService")
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.let { si -> si.packageName == componentName.packageName && si.name == componentName.className } }
}

private const val SETTINGS_ACTIVATIONS_VISIBLE = 8
private const val RAW_TAIL_LINES = 8
