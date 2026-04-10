package com.example.deckbridge.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.PhysicalKeyboardConnectionState
import com.example.deckbridge.ui.hardware.HardwareMirrorPanel
import com.example.deckbridge.ui.hardware.MirrorLayoutDensity
import com.example.deckbridge.ui.hardware.MirrorPadSlot
import com.example.deckbridge.ui.theme.DeckBridgeTheme
@Composable
@Suppress("UNUSED_PARAMETER")
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenHardwareCalibration: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        val config = LocalConfiguration.current
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wideLayout = config.screenWidthDp >= 600

        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(
                    horizontal = if (landscape) 12.dp else 16.dp,
                    vertical = if (landscape) 4.dp else 8.dp,
                ),
        ) {
            val scrollState = rememberScrollState()
            val mirrorMaxWidthPortrait = minOf(
                maxWidth - 8.dp,
                540.dp,
            ).coerceAtLeast(280.dp)

            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LandscapeSideRail(
                        state = state,
                        onHostPlatformSelected = onHostPlatformSelected,
                        onOpenSettings = onOpenSettings,
                        onOpenHardwareCalibration = onOpenHardwareCalibration,
                        modifier = Modifier
                            .widthIn(min = 128.dp, max = 148.dp)
                            .fillMaxHeight(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = mirrorPadSlots(state),
                            hostPlatform = state.hostPlatform,
                            modifier = Modifier.fillMaxWidth(),
                            maxContentWidth = null,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.LandscapeSidebar,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    HomeProductBar(
                        state = state,
                        onHostPlatformSelected = onHostPlatformSelected,
                        onOpenSettings = onOpenSettings,
                        landscapeOrWide = wideLayout,
                        compactToolbar = false,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = mirrorPadSlots(state),
                            hostPlatform = state.hostPlatform,
                            maxContentWidth = mirrorMaxWidthPortrait,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.Comfortable,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onOpenHardwareCalibration,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = mirrorMaxWidthPortrait),
                        ) {
                            Text(stringResource(R.string.home_open_calibration))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeSideRail(
    state: AppState,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHardwareCalibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, outline, RoundedCornerShape(16.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SideRailDeviceCard(state = state)
            VerticalPlatformSelector(
                selected = state.hostPlatform,
                onSelect = onHostPlatformSelected,
            )
            TextButton(
                onClick = onOpenHardwareCalibration,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 2.dp, horizontal = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_open_calibration),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.fab_open_settings),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SideRailDeviceCard(state: AppState) {
    val connected = state.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VerticalPlatformSelector(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    val trackShape = RoundedCornerShape(12.dp)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, outline, trackShape)
            .clip(trackShape)
            .background(track)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        VerticalPlatformRow(
            iconPainter = painterResource(R.drawable.ic_platform_windows),
            label = stringResource(R.string.platform_chip_windows),
            selected = effective == HostPlatform.WINDOWS,
            onClick = { onSelect(HostPlatform.WINDOWS) },
        )
        VerticalPlatformRow(
            iconVector = Icons.Outlined.LaptopMac,
            label = stringResource(R.string.platform_chip_mac),
            selected = effective == HostPlatform.MAC,
            onClick = { onSelect(HostPlatform.MAC) },
        )
    }
}

@Composable
private fun VerticalPlatformRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
) {
    check(iconPainter != null || iconVector != null)
    val segShape = RoundedCornerShape(8.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val stroke = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(segShape)
            .then(
                if (selected) Modifier.border(1.dp, stroke, segShape) else Modifier,
            )
            .background(bg)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            iconVector != null -> Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
            else -> Icon(
                painter = checkNotNull(iconPainter),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun mirrorPadSlots(state: AppState) =
    state.macroButtons
        .sortedBy { it.sortIndex }
        .map { btn ->
            MirrorPadSlot(
                title = btn.label,
                shortcutHint = btn.resolvedShortcut,
                iconToken = btn.iconToken,
            )
        }

@Composable
private fun HomeProductBar(
    state: AppState,
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenSettings: () -> Unit,
    landscapeOrWide: Boolean,
    compactToolbar: Boolean,
    modifier: Modifier = Modifier,
) {
    val barHeight = if (compactToolbar) 36.dp else 44.dp
    val barSpacing = if (compactToolbar) 8.dp else 10.dp
    if (landscapeOrWide) {
        Row(
            modifier = modifier.height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
        ) {
            CompactDevicePill(
                state = state,
                compact = compactToolbar,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            PlatformSegmentedControl(
                selected = state.hostPlatform,
                onSelect = onHostPlatformSelected,
                compact = compactToolbar,
                modifier = Modifier.widthIn(min = 176.dp, max = 280.dp),
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(barHeight),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.fab_open_settings),
                    modifier = Modifier.size(if (compactToolbar) 22.dp else 24.dp),
                )
            }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactDevicePill(
                    state = state,
                    compact = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.fab_open_settings),
                    )
                }
            }
            PlatformSegmentedControl(
                selected = state.hostPlatform,
                onSelect = onHostPlatformSelected,
                compact = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactDevicePill(
    state: AppState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val connected = state.physicalKeyboard.state == PhysicalKeyboardConnectionState.CONNECTED
    val pillShape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    Surface(
        modifier = modifier,
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (compact) 0.dp else 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(
                    horizontal = if (compact) 10.dp else 12.dp,
                    vertical = 0.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 7.dp else 8.dp)
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
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlatformSegmentedControl(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    val trackShape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    val controlHeight = if (compact) 36.dp else 44.dp
    val inset = if (compact) 3.dp else 4.dp
    val gap = if (compact) 3.dp else 4.dp

    Row(
        modifier = modifier
            .height(controlHeight)
            .border(1.dp, outline, trackShape)
            .clip(trackShape)
            .background(track)
            .padding(inset),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        PlatformSegment(
            iconPainter = painterResource(R.drawable.ic_platform_windows),
            label = stringResource(R.string.platform_chip_windows),
            selected = effective == HostPlatform.WINDOWS,
            onClick = { onSelect(HostPlatform.WINDOWS) },
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        PlatformSegment(
            iconVector = Icons.Outlined.LaptopMac,
            label = stringResource(R.string.platform_chip_mac),
            selected = effective == HostPlatform.MAC,
            onClick = { onSelect(HostPlatform.MAC) },
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlatformSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
) {
    val segShape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val stroke = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    check(iconPainter != null || iconVector != null)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(segShape)
            .then(
                if (selected) {
                    Modifier.border(1.dp, stroke, segShape)
                } else {
                    Modifier
                },
            )
            .background(bg)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val iconSize = if (compact) 17.dp else 20.dp
        when {
            iconVector != null -> Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
            else -> Icon(
                painter = checkNotNull(iconPainter),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
