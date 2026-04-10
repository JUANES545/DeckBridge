package com.example.deckbridge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.RawChannel
import com.example.deckbridge.domain.hardware.RawDiagnosticLine
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.HostPlatformSource
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onNavigateBack: () -> Unit,
    onRefreshKeyboards: () -> Unit,
    onHostAutoDetectChanged: (Boolean) -> Unit,
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
            SectionLabel(stringResource(R.string.settings_logcat_hint_title))
            LogcatHintCard()

            SectionLabel(stringResource(R.string.settings_section_session))
            LiveHardwareSessionCard(
                statusLine = state.systemStatusLine,
                diag = state.hardwareDiagSummary,
            )

            SectionLabel(stringResource(R.string.settings_calibration_map))
            CalibrationMapCard(calibration = state.hardwareCalibration)

            SectionLabel(stringResource(R.string.settings_section_keyboard_host))
            KeyboardHostCompactCard(state = state)

            SectionLabel(stringResource(R.string.settings_section_hid_host))
            HostHidTransportCard(
                state = state,
                onHostAutoDetectChanged = onHostAutoDetectChanged,
            )

            SectionLabel(stringResource(R.string.settings_raw_tail))
            RawTailCard(lines = state.rawInputDiagnostics)

            SectionLabel(stringResource(R.string.settings_activations_recent))
            DeckActivationsCompactCard(entries = state.recentDeckActivations.take(SETTINGS_ACTIVATIONS_VISIBLE))

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LogcatHintCard() {
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.settings_logcat_hint_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveHardwareSessionCard(
    statusLine: String,
    diag: HardwareDiagSummary?,
) {
    ElevatedInfoCard {
        Text(
            text = statusLine,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.diag_last_event_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        if (diag == null) {
            Text(
                text = stringResource(R.string.settings_last_control_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.settings_last_control_line,
                    diag.controlLabel,
                    diag.kind,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.diag_hint, diag.matchedAs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(diag.epochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun HostHidTransportCard(
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_hid_transport),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.hidTransport.summary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(
            label = stringResource(R.string.settings_hid_keyboard),
            value = stringResource(
                if (state.hidTransport.canSendKeyboard) R.string.settings_bind_yes else R.string.settings_bind_no,
            ),
            positive = state.hidTransport.canSendKeyboard,
        )
        Spacer(modifier = Modifier.height(4.dp))
        StatusRow(
            label = stringResource(R.string.settings_hid_consumer),
            value = stringResource(
                if (state.hidTransport.canSendMedia) R.string.settings_bind_yes else R.string.settings_bind_no,
            ),
            positive = state.hidTransport.canSendMedia,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.hidTransport.detail,
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
        Spacer(Modifier.height(4.dp))
        StatusRow(
            label = stringResource(R.string.label_usb_session),
            value = usbStatusLabel(state.hostConnection.usbState),
            positive = state.hostConnection.usbState == HostUsbConnectionState.READY,
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
private fun usbStatusLabel(state: HostUsbConnectionState): String = when (state) {
    HostUsbConnectionState.NOT_CONNECTED -> stringResource(R.string.usb_state_not_connected)
    HostUsbConnectionState.ATTACHED_IDLE -> stringResource(R.string.usb_state_attached_idle)
    HostUsbConnectionState.READY -> stringResource(R.string.usb_state_ready)
    HostUsbConnectionState.ERROR -> stringResource(R.string.usb_state_error)
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
    ButtonTriggerSource.SIMULATED -> stringResource(R.string.trigger_simulated_activation)
}

private fun formatTime(epochMs: Long): String {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private const val SETTINGS_ACTIVATIONS_VISIBLE = 8
private const val RAW_TAIL_LINES = 8
