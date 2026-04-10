package com.example.deckbridge.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.MacroButton
import com.example.deckbridge.domain.model.PhysicalKeyBinding
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.ui.hardware.HardwareMirrorPanel
import com.example.deckbridge.ui.hardware.MirrorPadSlot
import com.example.deckbridge.ui.theme.DeckBridgeTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenHardwareCalibration: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.fab_open_settings),
                )
            }
        },
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
        val mirrorPadSlots = state.macroButtons
            .sortedBy { it.sortIndex }
            .map { btn ->
                MirrorPadSlot(
                    title = btn.label,
                    shortcutHint = btn.resolvedShortcut,
                    iconToken = btn.iconToken,
                )
            }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeviceStatusStrip(state = state)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HardwareMirrorPanel(
                    calibration = state.hardwareCalibration,
                    highlight = state.hardwareMirrorHighlight,
                    diagSummary = state.hardwareDiagSummary,
                    padSlots = mirrorPadSlots,
                    hostPlatform = state.hostPlatform,
                )
            }

            Text(
                text = stringResource(R.string.hardware_mirror_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            FilledTonalButton(
                onClick = onOpenHardwareCalibration,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_open_calibration))
            }

            SectionLabel(
                text = stringResource(R.string.section_host_platform_deck),
                modifier = Modifier.fillMaxWidth(),
            )
            HostPlatformDeckSelector(
                selected = state.hostPlatform,
                onSelect = onHostPlatformSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(
                text = stringResource(R.string.section_deck_preview),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.profile_active, state.activeProfile.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                MacroButtonGrid(
                    buttons = state.macroButtons,
                    bindings = state.physicalBindingsPreview,
                    highlight = state.deckHighlight,
                    onButtonClick = onDeckButtonTapped,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DeviceStatusStrip(state: AppState) {
    val connected = state.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (connected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            },
                        ),
                )
                Text(
                    text = state.physicalKeyboard.deviceName
                        ?: stringResource(R.string.status_strip_keyboard_off),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = stringResource(
                    R.string.status_strip_platform,
                    platformLabel(state.hostPlatform),
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
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
private fun HostPlatformDeckSelector(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    ElevatedInfoCard(modifier = modifier) {
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
private fun platformLabel(platform: HostPlatform): String = when (platform) {
    HostPlatform.WINDOWS -> stringResource(R.string.platform_windows)
    HostPlatform.MAC -> stringResource(R.string.platform_mac)
    HostPlatform.UNKNOWN -> stringResource(R.string.platform_unknown)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    DeckBridgeTheme(dynamicColor = false) {
        val res = LocalContext.current.resources
        HomeScreen(
            state = MockAppStateFactory.initial(1_700_000_000_000L, res),
            onDeckButtonTapped = {},
            onHostPlatformSelected = {},
        )
    }
}
