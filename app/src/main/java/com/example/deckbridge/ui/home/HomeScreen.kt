package com.example.deckbridge.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.DeckActivationLogEntry
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.HostUsbConnectionState
import com.example.deckbridge.domain.model.InputDiagnostics
import com.example.deckbridge.domain.model.InputEventSource
import com.example.deckbridge.domain.model.KeyMotion
import com.example.deckbridge.domain.model.KeyboardInputClassification
import com.example.deckbridge.domain.model.MacroButton
import com.example.deckbridge.domain.model.PhysicalKeyBinding
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.domain.model.RecentInputEvent
import com.example.deckbridge.ui.theme.DeckBridgeTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.section_system_status))
            SystemStatusCard(line = state.systemStatusLine)

            SectionLabel(stringResource(R.string.section_host_platform_deck))
            HostPlatformDeckSelector(
                selected = state.hostPlatform,
                onSelect = onHostPlatformSelected,
            )

            SectionLabel(stringResource(R.string.section_physical_keyboard))
            PhysicalKeyboardCard(
                status = state.physicalKeyboard.state,
                deviceName = state.physicalKeyboard.deviceName,
                detail = state.physicalKeyboard.detail,
                bindings = state.physicalBindingsPreview,
            )

            SectionLabel(stringResource(R.string.section_input_diagnostics))
            InputDiagnosticsCard(diagnostics = state.inputDiagnostics)

            SectionLabel(stringResource(R.string.section_host))
            HostStatusCard(
                platform = state.hostPlatform,
                usbState = state.hostConnection.usbState,
                hostLabel = state.hostConnection.hostLabel,
                detail = state.hostConnection.detail,
            )

            SectionLabel(stringResource(R.string.section_future_actions))
            FutureActionsCard()

            SectionLabel(stringResource(R.string.section_recent_events))
            RecentEventsCard(events = state.recentInputEvents)

            SectionLabel(stringResource(R.string.section_deck_activations))
            DeckActivationsCard(entries = state.recentDeckActivations)

            SectionLabel(stringResource(R.string.section_deck_preview))
            Text(
                text = stringResource(R.string.profile_active, state.activeProfile.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MacroButtonGrid(
                buttons = state.macroButtons,
                bindings = state.physicalBindingsPreview,
                highlight = state.deckHighlight,
                onButtonClick = onDeckButtonTapped,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SystemStatusCard(line: String) {
    ElevatedInfoCard {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HostPlatformDeckSelector(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.host_platform_deck_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = effective == HostPlatform.WINDOWS,
                onClick = { onSelect(HostPlatform.WINDOWS) },
                label = { Text(stringResource(R.string.platform_chip_windows)) },
            )
            FilterChip(
                selected = effective == HostPlatform.MAC,
                onClick = { onSelect(HostPlatform.MAC) },
                label = { Text(stringResource(R.string.platform_chip_mac)) },
            )
        }
    }
}

@Composable
private fun InputDiagnosticsCard(diagnostics: InputDiagnostics) {
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.diag_last_event_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = diagnostics.hintLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        val last = diagnostics.lastEventDevice
        if (last == null) {
            Text(
                text = stringResource(R.string.diag_none_yet),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.diag_device_name, last.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.diag_device_id, last.deviceId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (last.vendorId != null && last.productId != null) {
                Text(
                    text = stringResource(
                        R.string.diag_vendor_product,
                        last.vendorId,
                        last.productId,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.diag_vendor_product_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            val desc = last.descriptor
            Text(
                text = if (desc != null) {
                    stringResource(R.string.diag_descriptor, desc)
                } else {
                    stringResource(R.string.diag_descriptor_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.diag_sources_simple,
                    last.sourcesLabel,
                    last.sourcesFlags,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            diagnostics.lastClassification?.let { c ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.diag_classification,
                        classificationLabel(c),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (diagnostics.lastMotion != null && diagnostics.lastKeyCode != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.diag_motion_key,
                        motionLabel(diagnostics.lastMotion!!),
                        diagnostics.lastKeyCode!!,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.diag_external_list_title,
                diagnostics.detectedExternalKeyboards.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        if (diagnostics.detectedExternalKeyboards.isEmpty()) {
            Text(
                text = stringResource(R.string.diag_external_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            diagnostics.detectedExternalKeyboards.forEach { d ->
                Text(
                    text = stringResource(R.string.diag_external_line, d.name, d.deviceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhysicalKeyboardCard(
    status: PhysicalKeyboardConnectionState,
    deviceName: String?,
    detail: String,
    bindings: List<PhysicalKeyBinding>,
) {
    ElevatedInfoCard {
        StatusRow(
            label = stringResource(R.string.label_connection_state),
            value = keyboardStatusLabel(status),
            positive = status == PhysicalKeyboardConnectionState.CONNECTED,
        )
        if (deviceName != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.label_device_name, deviceName),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bindings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.bindings_preview_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            bindings.forEach { b ->
                val target = b.macroButtonId?.let { id ->
                    stringResource(R.string.binding_to_button, b.keyLabel, id)
                } ?: stringResource(R.string.binding_unassigned, b.keyLabel)
                Text(
                    text = target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HostStatusCard(
    platform: HostPlatform,
    usbState: HostUsbConnectionState,
    hostLabel: String,
    detail: String,
) {
    ElevatedInfoCard {
        StatusRow(
            label = stringResource(R.string.label_host_platform),
            value = platformLabel(platform),
            positive = platform != HostPlatform.UNKNOWN,
        )
        Spacer(Modifier.height(6.dp))
        StatusRow(
            label = stringResource(R.string.label_usb_session),
            value = usbStatusLabel(usbState),
            positive = usbState == HostUsbConnectionState.READY,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.label_host_name, hostLabel),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FutureActionsCard() {
    ElevatedInfoCard {
        Text(
            text = stringResource(R.string.future_actions_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeckActivationsCard(entries: List<DeckActivationLogEntry>) {
    ElevatedInfoCard {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.deck_activations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.deck_activation_line,
                        entry.buttonLabel,
                        entry.resolvedShortcut,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.deck_activation_platform,
                        deckPlatformLabel(entry.hostPlatform),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.deck_activation_resolved,
                        entry.resolvedShortcut,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.deck_activation_intent_line, entry.intentLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (entry.physicalKeyLabel != null) {
                        stringResource(R.string.deck_tile_physical, entry.physicalKeyLabel)
                    } else {
                        stringResource(R.string.deck_tile_physical_none)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun RecentEventsCard(events: List<RecentInputEvent>) {
    ElevatedInfoCard {
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.recent_events_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            events.forEachIndexed { index, ev ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.event_line,
                                ev.keyLabel,
                                motionLabel(ev.motion),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                R.string.event_detail_line,
                                ev.keyCode,
                                ev.keyCodeName,
                                ev.device.deviceId,
                                classificationLabel(ev.classification),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.diag_device_name,
                                ev.device.name,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = eventSourceLabel(ev.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatTime(ev.occurredAtEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroButtonGrid(
    buttons: List<MacroButton>,
    bindings: List<PhysicalKeyBinding>,
    highlight: DeckButtonHighlight?,
    onButtonClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { btn ->
                    val keyLabel = bindings.firstOrNull { it.macroButtonId == btn.id }?.keyLabel
                    MacroButtonTile(
                        button = btn,
                        physicalKeyLabel = keyLabel,
                        isHighlighted = highlight?.buttonId == btn.id,
                        onClick = { onButtonClick(btn.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MacroButtonTile(
    button: MacroButton,
    physicalKeyLabel: String?,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 0.96f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "deckTileScale",
    )
    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = modifier
            .widthIn(min = 0.dp)
            .scale(scale)
            .then(
                if (isHighlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 6.dp else 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = deckIconGlyph(button.iconToken),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = button.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = button.resolvedShortcut,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (physicalKeyLabel != null) {
                    stringResource(R.string.deck_tile_physical, physicalKeyLabel)
                } else {
                    stringResource(R.string.deck_tile_physical_none)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun deckIconGlyph(iconToken: String?): String = when (iconToken) {
    "content_copy" -> "⎘"
    "content_paste" -> "📋"
    "volume_up" -> "🔊"
    "play_pause" -> "⏯"
    "search" -> "🔍"
    "content_cut" -> "✂"
    "undo" -> "↩"
    "redo" -> "↪"
    "volume_mute" -> "🔇"
    else -> "◇"
}

@Composable
private fun ElevatedInfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
private fun motionLabel(motion: KeyMotion): String = when (motion) {
    KeyMotion.DOWN -> stringResource(R.string.motion_down)
    KeyMotion.UP -> stringResource(R.string.motion_up)
}

@Composable
private fun classificationLabel(classification: KeyboardInputClassification): String = when (classification) {
    KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD ->
        stringResource(R.string.class_external_hw)
    KeyboardInputClassification.BUILT_IN_HARDWARE_KEYBOARD ->
        stringResource(R.string.class_built_in)
    KeyboardInputClassification.SOFTWARE_OR_VIRTUAL_KEYBOARD ->
        stringResource(R.string.class_software)
    KeyboardInputClassification.NON_KEYBOARD_OR_UNKNOWN ->
        stringResource(R.string.class_unknown)
}

@Composable
private fun eventSourceLabel(source: InputEventSource): String =
    when (source) {
        InputEventSource.HARDWARE ->
            stringResource(R.string.source_hardware)
        InputEventSource.SIMULATED ->
            stringResource(R.string.source_simulated)
    }

@Composable
private fun deckPlatformLabel(platform: HostPlatform): String = when (platform) {
    HostPlatform.WINDOWS -> stringResource(R.string.platform_windows)
    HostPlatform.MAC -> stringResource(R.string.platform_mac)
    HostPlatform.UNKNOWN -> stringResource(R.string.platform_unknown)
}

@Composable
private fun activationSourceLabel(source: ButtonTriggerSource): String = when (source) {
    ButtonTriggerSource.TOUCH -> stringResource(R.string.trigger_touch)
    ButtonTriggerSource.HARDWARE_KEY -> stringResource(R.string.trigger_hardware_key)
    ButtonTriggerSource.SIMULATED -> stringResource(R.string.trigger_simulated_activation)
}

private fun formatTime(epochMs: Long): String {
    val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())
    return fmt.format(Date(epochMs))
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    DeckBridgeTheme(dynamicColor = false) {
        HomeScreen(
            state = MockAppStateFactory.initial(1_700_000_000_000L),
            onDeckButtonTapped = {},
            onHostPlatformSelected = {},
        )
    }
}
