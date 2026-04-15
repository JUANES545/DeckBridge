@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.ui.hardware.HardwareMirrorPanel
import com.example.deckbridge.ui.hardware.MirrorLayoutDensity
import com.example.deckbridge.ui.hardware.MirrorPadSlot
import com.example.deckbridge.ui.hardware.MirrorPanelChrome
import com.example.deckbridge.ui.onboarding.OnboardingTheme
import com.example.deckbridge.ui.theme.DeckBridgeTheme
@Composable
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onDeckButtonLongPress: (String) -> Unit = {},
    onMirrorKnobTouchRotate: (Int, Boolean) -> Unit = { _, _ -> },
    /** Long press on a mirror knob (index 0..2) opens its editor. */
    onMirrorKnobLongPress: (Int) -> Unit = {},
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenSettings: () -> Unit = {},
    showLanLinkLostDialog: Boolean = false,
    onLanLinkLostGoToConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val surface = Color(0xFF050508)
    val charging = rememberDeviceCharging(state.animatedBackgroundMode == AnimatedBackgroundMode.WHEN_CHARGING)
    val showEnergyPulses = when (state.animatedBackgroundMode) {
        AnimatedBackgroundMode.ALWAYS -> true
        AnimatedBackgroundMode.WHEN_CHARGING -> charging
        AnimatedBackgroundMode.OFF -> false
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = surface,
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            DashboardHexBackground(Modifier.fillMaxSize())
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showEnergyPulses) {
                            DashboardEnergyPulsesOverlay(Modifier.fillMaxSize())
                        }
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            knobMirrorRotation = state.knobMirrorRotation,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = mirrorPadSlots(state),
                            deckKnobs = state.deckKnobs,
                            hostPlatform = state.hostPlatform,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            maxContentWidth = null,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.DashboardLandscape,
                            chrome = MirrorPanelChrome.Dashboard,
                            deckHighlight = state.deckHighlight,
                            onPadCellTapped = onDeckButtonTapped,
                            onPadCellLongPress = onDeckButtonLongPress,
                            onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                            onMirrorKnobLongPress = onMirrorKnobLongPress,
                        )
                    }
                    DashboardLandscapeChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onHostPlatformSelected = onHostPlatformSelected,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                ) {
                    DashboardTopChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showEnergyPulses) {
                            DashboardEnergyPulsesOverlay(Modifier.fillMaxSize())
                        }
                        HardwareMirrorPanel(
                            calibration = state.hardwareCalibration,
                            highlight = state.hardwareMirrorHighlight,
                            knobMirrorRotation = state.knobMirrorRotation,
                            diagSummary = state.hardwareDiagSummary,
                            padSlots = mirrorPadSlots(state),
                            deckKnobs = state.deckKnobs,
                            hostPlatform = state.hostPlatform,
                            modifier = Modifier.fillMaxWidth(),
                            maxContentWidth = null,
                            showKnobRoleHints = false,
                            layoutDensity = MirrorLayoutDensity.DashboardPortrait,
                            chrome = MirrorPanelChrome.Dashboard,
                            deckHighlight = state.deckHighlight,
                            onPadCellTapped = onDeckButtonTapped,
                            onPadCellLongPress = onDeckButtonLongPress,
                            onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                            onMirrorKnobLongPress = onMirrorKnobLongPress,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    PlatformSegmentedControl(
                        selected = state.hostPlatform,
                        onSelect = onHostPlatformSelected,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
            }
            if (showLanLinkLostDialog) {
                LanLinkLostDialog(onGoConnect = onLanLinkLostGoToConnect)
            }
        }
    }
}

@Composable
private fun LanLinkLostDialog(onGoConnect: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest = { },
        shape = shape,
        containerColor = OnboardingTheme.card,
        titleContentColor = OnboardingTheme.textPrimary,
        textContentColor = OnboardingTheme.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.home_lan_link_lost_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.home_lan_link_lost_body),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start,
            )
        },
        confirmButton = {
            TextButton(onClick = onGoConnect) {
                Text(
                    stringResource(R.string.home_lan_link_lost_go_connect),
                    color = OnboardingTheme.accent,
                )
            }
        },
    )
}

private fun mirrorPadSlots(state: AppState) =
    state.macroButtons
        .sortedBy { it.sortIndex }
        .map { btn ->
            MirrorPadSlot(
                title = if (btn.visible) btn.label else "—",
                shortcutHint = if (btn.visible) btn.resolvedShortcut else "",
                iconToken = if (btn.visible) btn.iconToken else null,
                deckButtonId = if (btn.visible && btn.enabled) btn.id else "",
                editTargetButtonId = if (btn.visible) btn.id else null,
            )
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
