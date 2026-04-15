@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.deckbridge.ui.hardware

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.ui.deck.deckGridSlotGlyph
import kotlin.math.min
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.data.deck.DeckKnobIntentMapper
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonHighlight
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareHighlightKind
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight
import com.example.deckbridge.domain.hardware.KnobMirrorRotationAccum

/**
 * Dashboard 3×3 pad cell side length: does **not** grow with extra horizontal space in landscape.
 *
 * 1. [deviceShortestSide] is `min(screenWidthDp, screenHeightDp)` (invariant when the device rotates).
 * 2. Subtract mirror padding, an estimate of home gutters, the knob column, and inter-column gaps
 *    to get a **canonical** cell size that matches portrait and landscape.
 * 3. [mirrorBoxMaxWidth]/[mirrorBoxMaxHeight] clamp that value so the grid never overflows the
 *    actual mirror [BoxWithConstraints] (multi-window, tablets, etc.).
 */
private fun dashboardPadCellSideDp(
    mirrorBoxMaxWidth: Dp,
    mirrorBoxMaxHeight: Dp,
    deviceShortestSide: Dp,
    tokens: MirrorLayoutTokens,
): Dp {
    val gap = tokens.padCellGap
    val knobPad = tokens.knobPadRowGap
    val knobTrack = tokens.dashboardKnobTrackWidth()
    val mirrorPadHorizontal = tokens.paddingHorizontal * 2
    /** Outer home padding (portrait / landscape): conservative but tighter than before. */
    val homeGutterHorizontal = 12.dp

    val fromDeviceShortSide =
        (deviceShortestSide - homeGutterHorizontal - mirrorPadHorizontal - knobTrack - knobPad - gap * 2) / 3f

    val fromMirrorBox = minOf(
        (mirrorBoxMaxWidth - knobTrack - knobPad - gap * 2) / 3f,
        (mirrorBoxMaxHeight - gap * 2) / 3f,
    )

    return minOf(fromDeviceShortSide, fromMirrorBox).coerceIn(38.dp, 132.dp)
}

/**
 * Landscape dashboard: size each tile from **available mirror height** (3 rows + 2 gaps) so the
 * grid uses the full vertical canvas; if that row would not fit in [mirrorBoxMaxWidth], fall back
 * to the width-limited cell. Keeps knobs + grid as one horizontally centered group.
 */
private fun dashboardPadCellSideLandscapeFillHeightDp(
    mirrorBoxMaxWidth: Dp,
    mirrorBoxMaxHeight: Dp,
    tokens: MirrorLayoutTokens,
): Dp {
    val gap = tokens.padCellGap
    val knobPad = tokens.knobPadRowGap
    val knobTrack = tokens.dashboardKnobTrackWidth()
    val cellFromHeight = (mirrorBoxMaxHeight - gap * 2) / 3f
    val rowWidthIfFullHeight = knobTrack + knobPad + cellFromHeight * 3f + gap * 2
    val cell = if (rowWidthIfFullHeight <= mirrorBoxMaxWidth) {
        cellFromHeight
    } else {
        (mirrorBoxMaxWidth - knobTrack - knobPad - gap * 2) / 3f
    }
    return cell.coerceIn(38.dp, 180.dp)
}

@Composable
fun HardwareMirrorPanel(
    calibration: HardwareCalibrationConfig?,
    highlight: HardwareMirrorHighlight?,
    knobMirrorRotation: KnobMirrorRotationAccum,
    diagSummary: HardwareDiagSummary?,
    padSlots: List<MirrorPadSlot>,
    deckKnobs: DeckKnobsLayoutPersisted,
    hostPlatform: HostPlatform,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp? = 520.dp,
    showKnobRoleHints: Boolean = true,
    layoutDensity: MirrorLayoutDensity = MirrorLayoutDensity.Comfortable,
    chrome: MirrorPanelChrome = MirrorPanelChrome.Standard,
    deckHighlight: DeckButtonHighlight? = null,
    onPadCellTapped: ((String) -> Unit)? = null,
    /** Long-press on a pad cell with a deck id opens the grid editor (tap still runs the action). */
    onPadCellLongPress: ((String) -> Unit)? = null,
    /** (knobIndex 0..2, clockwise). Press is not simulated. */
    onMirrorKnobTouchRotate: ((Int, Boolean) -> Unit)? = null,
    /** Long press on the dial opens the knob editor (index 0..2). Normal drag still rotates. */
    onMirrorKnobLongPress: ((Int) -> Unit)? = null,
) {
    val tokens = layoutDensity.toTokens()
    val now = System.currentTimeMillis()
    val activeHighlight = highlight?.takeIf { it.untilEpochMs > now }
    val slots = if (padSlots.size >= 9) padSlots.take(9) else {
        padSlots + List(9 - padSlots.size) {
            MirrorPadSlot("", "—", null)
        }
    }
    val cardShape = RoundedCornerShape(tokens.cardCorner)
    val showDiagnosticsHeader = chrome == MirrorPanelChrome.Standard

    val widthMod = Modifier
        .fillMaxWidth()
        .then(
            if (maxContentWidth != null) {
                Modifier.widthIn(max = maxContentWidth)
            } else {
                Modifier
            },
        )

    val useKnobColumnFixedSpacing = tokens.knobColumnInterItemSpacing.value > 0f

    @Composable
    fun PadGridCells(
        padCellModifier: (Int, Int) -> Modifier,
    ) {
        // Dashboard: intrinsic width only so perillas+grid stay centered (fillMaxWidth would stretch
        // rows and pin tiles to the start). Standard layout uses its own weighted rows.
        Column(
            modifier = Modifier.wrapContentWidth(),
            verticalArrangement = Arrangement.spacedBy(tokens.padCellGap),
        ) {
            for (r in 0..2) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.padCellGap),
                ) {
                    for (c in 0..2) {
                        val idx = r * 3 + c
                        val slot = slots[idx]
                        PadKeyTile(
                            slot = slot,
                            padId = HardwareControlId.PadKey(r, c),
                            tileIndex = idx,
                            activeHighlight = activeHighlight,
                            deckActiveId = deckHighlight?.buttonId,
                            useControlSurfaceStyle = chrome == MirrorPanelChrome.Dashboard,
                            onDeckTapped = if (onPadCellTapped != null && slot.deckButtonId.isNotBlank()) {
                                { onPadCellTapped(slot.deckButtonId) }
                            } else {
                                null
                            },
                            onDeckLongPress = run {
                                val target = (slot.editTargetButtonId ?: slot.deckButtonId).takeIf { it.isNotBlank() }
                                if (onPadCellLongPress != null && target != null) {
                                    { onPadCellLongPress(target) }
                                } else {
                                    null
                                }
                            },
                            tokens = tokens,
                            modifier = padCellModifier(r, c),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun KnobColumn(
        modifier: Modifier = Modifier,
        verticalArrangement: Arrangement.Vertical,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) { index ->
                KnobVisual(
                    index = index,
                    label = knobDisplayLabel(deckKnobs, index),
                    roleHint = knobDisplayRoleHint(deckKnobs, index),
                    accumulatedRotationDeg = knobMirrorRotation.degreesFor(index),
                    activeHighlight = activeHighlight,
                    showRoleHint = showKnobRoleHints,
                    tokens = tokens,
                    onMirrorDragRotate = if (onMirrorKnobTouchRotate != null) {
                        { cw -> onMirrorKnobTouchRotate(index, cw) }
                    } else {
                        null
                    },
                    onMirrorKnobLongPress = if (onMirrorKnobLongPress != null) {
                        { onMirrorKnobLongPress(index) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    @Composable
    fun KnobsAndPadRow(modifier: Modifier = Modifier) {
        if (chrome == MirrorPanelChrome.Dashboard) {
            val configuration = LocalConfiguration.current
            val deviceShortestSide = min(configuration.screenWidthDp, configuration.screenHeightDp).dp
            val knobTrackWidth = tokens.dashboardKnobTrackWidth()
            val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            BoxWithConstraints(
                modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val cellSide = if (landscape) {
                    dashboardPadCellSideLandscapeFillHeightDp(
                        mirrorBoxMaxWidth = maxWidth,
                        mirrorBoxMaxHeight = maxHeight,
                        tokens = tokens,
                    )
                } else {
                    dashboardPadCellSideDp(
                        mirrorBoxMaxWidth = maxWidth,
                        mirrorBoxMaxHeight = maxHeight,
                        deviceShortestSide = deviceShortestSide,
                        tokens = tokens,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.knobPadRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KnobColumn(
                        modifier = Modifier.width(knobTrackWidth),
                        verticalArrangement = Arrangement.spacedBy(tokens.knobColumnInterItemSpacing),
                    )
                    PadGridCells { _, _ ->
                        Modifier.size(cellSide)
                    }
                }
            }
        } else {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.knobPadRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val padWeight = 1f - tokens.knobColumnWeight
                KnobColumn(
                    modifier = Modifier
                        .weight(tokens.knobColumnWeight)
                        .then(
                            if (useKnobColumnFixedSpacing) {
                                Modifier
                            } else {
                                Modifier.fillMaxHeight()
                            },
                        ),
                    verticalArrangement = if (useKnobColumnFixedSpacing) {
                        Arrangement.spacedBy(tokens.knobColumnInterItemSpacing)
                    } else {
                        Arrangement.SpaceEvenly
                    },
                )
                Column(
                    modifier = Modifier.weight(padWeight),
                    verticalArrangement = Arrangement.spacedBy(tokens.padCellGap),
                ) {
                    for (r in 0..2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.padCellGap),
                        ) {
                            for (c in 0..2) {
                                val idx = r * 3 + c
                                val slot = slots[idx]
                                PadKeyTile(
                                    slot = slot,
                                    padId = HardwareControlId.PadKey(r, c),
                                    tileIndex = idx,
                                    activeHighlight = activeHighlight,
                                    deckActiveId = deckHighlight?.buttonId,
                                    useControlSurfaceStyle = chrome == MirrorPanelChrome.Dashboard,
                                    onDeckTapped = if (onPadCellTapped != null && slot.deckButtonId.isNotBlank()) {
                                        { onPadCellTapped(slot.deckButtonId) }
                                    } else {
                                        null
                                    },
                                    onDeckLongPress = run {
                                        val target = (slot.editTargetButtonId ?: slot.deckButtonId).takeIf { it.isNotBlank() }
                                        if (onPadCellLongPress != null && target != null) {
                                            { onPadCellLongPress(target) }
                                        } else {
                                            null
                                        }
                                    },
                                    tokens = tokens,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(tokens.padCellAspectRatio),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PanelInner() {
        val dashboard = chrome == MirrorPanelChrome.Dashboard
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (dashboard) Modifier.fillMaxHeight() else Modifier)
                .then(
                    if (chrome == MirrorPanelChrome.Standard) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                ),
                            ),
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(
                    horizontal = tokens.paddingHorizontal,
                    vertical = tokens.paddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(tokens.sectionGap),
        ) {
            if (showDiagnosticsHeader) {
                LastInteractionLine(
                    calibration = calibration,
                    diagSummary = diagSummary,
                    padSlots = slots,
                    deckKnobs = deckKnobs,
                    hostPlatform = hostPlatform,
                    compact = !showKnobRoleHints,
                    denseTypography = tokens.statusIsDenseTypography,
                    headerExtraDense = tokens.headerExtraDense,
                )
            }
            if (dashboard) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    KnobsAndPadRow()
                }
            } else {
                KnobsAndPadRow()
            }
        }
    }

    when (chrome) {
        MirrorPanelChrome.Standard -> {
            Card(
                modifier = modifier.then(widthMod),
                shape = cardShape,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = tokens.cardElevation,
                    pressedElevation = tokens.cardElevation,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                PanelInner()
            }
        }
        MirrorPanelChrome.Dashboard -> {
            Box(
                modifier = modifier
                    .then(widthMod)
                    .fillMaxHeight(),
            ) {
                PanelInner()
            }
        }
    }
}

@Composable
private fun LastInteractionLine(
    calibration: HardwareCalibrationConfig?,
    diagSummary: HardwareDiagSummary?,
    padSlots: List<MirrorPadSlot>,
    deckKnobs: DeckKnobsLayoutPersisted,
    hostPlatform: HostPlatform,
    compact: Boolean = false,
    denseTypography: Boolean = false,
    headerExtraDense: Boolean = false,
) {
    val calibOk = calibration != null && calibration.isComplete
    val statusStyle = when {
        headerExtraDense -> MaterialTheme.typography.labelSmall
        denseTypography -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelLarge
    }
    val detailStyle = when {
        headerExtraDense -> MaterialTheme.typography.labelSmall
        denseTypography -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.bodySmall
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            when {
                headerExtraDense -> 6.dp
                denseTypography -> 8.dp
                else -> 12.dp
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (calibOk) {
                stringResource(R.string.mirror_status_ready)
            } else {
                stringResource(R.string.mirror_status_setup)
            },
            style = statusStyle,
            color = if (calibOk) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        diagSummary?.let { s ->
            Text(
                text = userFacingInteractionLine(s, padSlots, deckKnobs, hostPlatform),
                style = detailStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun userFacingInteractionLine(
    summary: HardwareDiagSummary,
    padSlots: List<MirrorPadSlot>,
    deckKnobs: DeckKnobsLayoutPersisted,
    hostPlatform: HostPlatform,
): String {
    val controlName = when (val c = summary.control) {
        is HardwareControlId.PadKey -> {
            val i = c.row * 3 + c.col
            padSlots.getOrNull(i)?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.mirror_pad_fallback)
        }
        is HardwareControlId.Knob -> knobDisplayLabel(deckKnobs, c.index)
        null -> summary.controlLabel
    }
    val action = when (val c = summary.control) {
        is HardwareControlId.Knob -> {
            val intent = when (summary.kind) {
                "KNOB_ROTATE_CCW", "KNOB_CCW" ->
                    DeckKnobIntentMapper.intentForRotate(deckKnobs, c.index, ccw = true)
                "KNOB_ROTATE_CW", "KNOB_CW" ->
                    DeckKnobIntentMapper.intentForRotate(deckKnobs, c.index, ccw = false)
                "KNOB_PRESS_DOWN" -> DeckKnobIntentMapper.intentForPress(deckKnobs, c.index)
                else -> null
            }
            when {
                intent != null -> PlatformActionResolver.resolve(intent, hostPlatform).shortcutDisplay
                summary.kind == "KNOB_PRESS_UP" -> stringResource(R.string.mirror_action_knob_release)
                else -> when (summary.kind) {
                    "KNOB_ROTATE_CW", "KNOB_CW" -> stringResource(R.string.mirror_action_knob_cw)
                    "KNOB_ROTATE_CCW", "KNOB_CCW" -> stringResource(R.string.mirror_action_knob_ccw)
                    else -> summary.kind
                }
            }
        }
        else -> when (summary.kind) {
            "PAD_DOWN" -> stringResource(R.string.mirror_action_pad_press)
            "KNOB_PRESS_DOWN" -> stringResource(R.string.mirror_action_knob_press)
            "KNOB_PRESS_UP" -> stringResource(R.string.mirror_action_knob_release)
            "KNOB_ROTATE_CW", "KNOB_CW" -> stringResource(R.string.mirror_action_knob_cw)
            "KNOB_ROTATE_CCW", "KNOB_CCW" -> stringResource(R.string.mirror_action_knob_ccw)
            else -> summary.kind
        }
    }
    return stringResource(R.string.mirror_last_interaction, controlName, action)
}

@Composable
private fun knobDisplayLabel(layout: DeckKnobsLayoutPersisted, index: Int): String {
    val fromData = layout.knobAt(index)?.label?.trim().orEmpty()
    if (fromData.isNotEmpty()) return fromData
    return knobUserLabel(index)
}

@Composable
private fun knobDisplayRoleHint(layout: DeckKnobsLayoutPersisted, index: Int): String {
    val fromData = layout.knobAt(index)?.subtitle?.trim().orEmpty()
    if (fromData.isNotEmpty()) return fromData
    return knobRoleHint(index)
}

@Composable
private fun knobUserLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.knob_top)
    1 -> stringResource(R.string.knob_middle)
    2 -> stringResource(R.string.knob_bottom)
    else -> stringResource(R.string.knob_generic, index + 1)
}

@Composable
private fun knobRoleHint(index: Int): String = when (index) {
    // Default role hints when the persisted knob subtitle is empty (factory preset).
    0 -> stringResource(R.string.mirror_knob_role_top)
    1 -> stringResource(R.string.mirror_knob_role_middle)
    2 -> stringResource(R.string.mirror_knob_role_bottom)
    else -> ""
}

@Composable
private fun KnobVisual(
    index: Int,
    label: String,
    roleHint: String,
    accumulatedRotationDeg: Float,
    activeHighlight: HardwareMirrorHighlight?,
    showRoleHint: Boolean = true,
    tokens: MirrorLayoutTokens,
    /** Finger up → clockwise (true); finger down → CCW (false). */
    onMirrorDragRotate: ((clockwise: Boolean) -> Unit)? = null,
    onMirrorKnobLongPress: (() -> Unit)? = null,
) {
    val knobId = HardwareControlId.Knob(index)
    val h = activeHighlight
    val isOn = h?.control == knobId
    val kind = if (isOn) h?.kind else null
    val isPressDown = kind == HardwareHighlightKind.KNOB_PRESS_DOWN
    val isPressUp = kind == HardwareHighlightKind.KNOB_PRESS_UP
    val isRotate = isOn && !isPressDown && !isPressUp

    val scale by animateFloatAsState(
        targetValue = when {
            isPressDown -> 0.90f
            isPressUp -> 0.98f
            isRotate -> 1.07f
            else -> 1f
        },
        animationSpec = when {
            isRotate -> spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
            isPressUp -> tween(durationMillis = 95, easing = FastOutSlowInEasing)
            else -> spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            )
        },
        label = "knobScale$index",
    )
    val displayedRotation by animateFloatAsState(
        targetValue = accumulatedRotationDeg,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "knobRot$index",
    )
    val innerDotScale by animateFloatAsState(
        targetValue = when {
            isPressDown -> 1.15f
            isPressUp -> 1.22f
            isRotate -> 1f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (isPressUp) 110 else 140, easing = FastOutSlowInEasing),
        label = "knobInner$index",
    )

    val ringColor = when {
        !isOn -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        isPressDown -> MaterialTheme.colorScheme.tertiary
        isPressUp -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    }
    val rimArcColor = MaterialTheme.colorScheme.primary
    val denseKnobs = tokens.statusIsDenseTypography
    val primary = tokens.useDistinctPrimaryKnob && index == 0
    val outer = if (primary) tokens.knobPrimaryOuter else tokens.knobOuterBox
    val inner = if (primary) tokens.knobPrimaryInner else tokens.knobInner
    val dot = if (primary) tokens.knobPrimaryDot else tokens.knobDot
    val arcStroke = if (primary) tokens.knobPrimaryArcStroke else tokens.knobArcStroke
    val ringW = if (primary) tokens.knobPrimaryBorder else tokens.knobBorder
    val shDown = when {
        primary -> if (denseKnobs) 10.dp else 14.dp
        denseKnobs -> 6.dp
        else -> 14.dp
    }
    val shUp = when {
        primary -> if (denseKnobs) 6.dp else 9.dp
        denseKnobs -> 4.dp
        else -> 9.dp
    }
    val shRot = when {
        primary -> if (denseKnobs) 9.dp else 13.dp
        denseKnobs -> 5.dp
        else -> 13.dp
    }
    val shOn = when {
        primary -> if (denseKnobs) 8.dp else 11.dp
        denseKnobs -> 5.dp
        else -> 11.dp
    }
    val shIdle = when {
        primary -> if (denseKnobs) 3.dp else 4.dp
        denseKnobs -> 2.dp
        else -> 4.dp
    }

    val dragStepPx = with(LocalDensity.current) { 40.dp.toPx() }
    val rotateStepCallback = rememberUpdatedState(onMirrorDragRotate)
    val view = LocalView.current
    val longPressInteraction = remember(index) { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.knobStackSpacing),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(outer)
                .then(
                    if (onMirrorDragRotate != null) {
                        Modifier.pointerInput(dragStepPx, index) {
                            var accumulated = 0f
                            detectVerticalDragGestures { change, dragAmount ->
                                val cb = rotateStepCallback.value ?: return@detectVerticalDragGestures
                                change.consume()
                                accumulated += dragAmount
                                while (accumulated <= -dragStepPx) {
                                    cb(true)
                                    accumulated += dragStepPx
                                }
                                while (accumulated >= dragStepPx) {
                                    cb(false)
                                    accumulated -= dragStepPx
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onMirrorKnobLongPress != null) {
                        Modifier.combinedClickable(
                            interactionSource = longPressInteraction,
                            indication = null,
                            onClick = {},
                            onLongClickLabel = stringResource(R.string.knob_edit_title),
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onMirrorKnobLongPress()
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = arcStroke.toPx()
                val r = size.minDimension / 2f - stroke * 0.5f
                val center = Offset(size.width / 2f, size.height / 2f)
                val pointerBase = -90f + displayedRotation
                val rimAlpha = if (isRotate) 0.55f else 0.38f
                drawArc(
                    color = rimArcColor.copy(alpha = rimAlpha),
                    startAngle = pointerBase - 22f,
                    sweepAngle = 44f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = stroke),
                )
            }
            Box(
                modifier = Modifier
                    .size(inner)
                    .scale(scale)
                    .rotate(displayedRotation)
                    .shadow(
                        elevation = when {
                            isPressDown -> shDown
                            isPressUp -> shUp
                            isRotate -> shRot
                            isOn -> shOn
                            else -> shIdle
                        },
                        shape = CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = when {
                                isPressDown -> 0.5f
                                isPressUp -> 0.28f
                                isRotate -> 0.42f
                                isOn -> 0.35f
                                else -> 0.15f
                            },
                        ),
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (isOn) {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                ),
                            )
                        },
                    )
                    .border(ringW, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val pointerAlpha = if (isOn) 0.88f else 0.55f
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = inner * 0.07f)
                        .width(3.dp)
                        .height(inner * 0.14f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = pointerAlpha),
                        ),
                )
                val innerDotColor = when {
                    isPressUp -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    isPressDown -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f)
                    isOn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .size(dot)
                        .scale(innerDotScale)
                        .clip(CircleShape)
                        .background(color = innerDotColor),
                )
            }
        }
        Text(
            text = label,
            style = if (primary && tokens.useDistinctPrimaryKnob) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            fontWeight = if (primary && tokens.useDistinctPrimaryKnob) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showRoleHint && roleHint.isNotBlank()) {
            Text(
                text = roleHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(
                    max = when {
                        primary -> 88.dp
                        denseKnobs -> 72.dp
                        else -> 92.dp
                    },
                ),
            )
        }
    }
}

@Composable
private fun PadKeyTile(
    slot: MirrorPadSlot,
    padId: HardwareControlId.PadKey,
    tileIndex: Int,
    activeHighlight: HardwareMirrorHighlight?,
    deckActiveId: String?,
    useControlSurfaceStyle: Boolean,
    onDeckTapped: (() -> Unit)?,
    onDeckLongPress: (() -> Unit)? = null,
    tokens: MirrorLayoutTokens,
    modifier: Modifier = Modifier,
) {
    val hardwareOn = activeHighlight?.control == padId
    val deckOn = deckActiveId != null && deckActiveId == slot.deckButtonId && slot.deckButtonId.isNotBlank()
    val isOn = hardwareOn || deckOn
    val scale by animateFloatAsState(
        targetValue = if (isOn) 0.94f else 1f,
        animationSpec = tween(
            durationMillis = if (isOn) 95 else 380,
            easing = FastOutSlowInEasing,
        ),
        label = "padScale${padId.row}${padId.col}",
    )
    val shape = RoundedCornerShape(tokens.padCorner)
    val densePads = tokens.statusIsDenseTypography
    val shadowActive = if (densePads) 8.dp else 14.dp
    val shadowIdle = if (densePads) 2.dp else 3.dp
    val glyphStyle = when {
        tokens.padGlyphProminent && densePads -> MaterialTheme.typography.titleLarge
        tokens.padTitleMinimal && densePads -> MaterialTheme.typography.titleSmall
        densePads -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.headlineSmall
    }
    val titleTypo = if (tokens.padTitleMinimal) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    val baseBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeBg = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        0.35f,
    )
    val borderActive = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    val borderIdle = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    val idleBrush = if (useControlSurfaceStyle) {
        controlSurfacePadIdleBrush(tileIndex)
    } else {
        null
    }
    val activeBrush = if (useControlSurfaceStyle) {
        controlSurfacePadActiveBrush(tileIndex)
    } else {
        null
    }
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current

    BoxWithConstraints(
        modifier = modifier
            .scale(scale)
            .then(
                when {
                    onDeckTapped != null && onDeckLongPress != null -> {
                        Modifier.combinedClickable(
                            interactionSource = interaction,
                            indication = ripple(bounded = true),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onDeckTapped()
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onDeckLongPress()
                            },
                        )
                    }
                    onDeckTapped != null -> {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = ripple(bounded = true),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onDeckTapped()
                            },
                        )
                    }
                    else -> Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (isOn) shadowActive else shadowIdle,
                    shape = shape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isOn) 0.5f else 0.08f),
                )
                .clip(shape)
                .background(
                    brush = when {
                        useControlSurfaceStyle && isOn -> checkNotNull(activeBrush)
                        useControlSurfaceStyle -> checkNotNull(idleBrush)
                        isOn -> Brush.linearGradient(listOf(activeBg, activeBg))
                        else -> Brush.linearGradient(listOf(baseBg, baseBg))
                    },
                )
                .border(
                    width = if (isOn) tokens.padBorderActive else tokens.padBorderIdle,
                    color = when {
                        useControlSurfaceStyle && isOn -> Color.White.copy(alpha = 0.45f)
                        useControlSurfaceStyle -> Color.White.copy(alpha = 0.1f)
                        isOn -> borderActive
                        else -> borderIdle
                    },
                    shape = shape,
                )
                .padding(
                    horizontal = tokens.padPaddingH,
                    vertical = tokens.padPaddingV,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tokens.padColumnGap),
            ) {
                // Media / thumbnail region — replace with AsyncImage / SubcomposeAsyncImage later
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(tokens.padMediaCorner))
                        .background(
                            brush = if (useControlSurfaceStyle) {
                                controlSurfacePadMediaBrush(tileIndex, isOn)
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (isOn) 0.22f else 0.08f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = if (isOn) 0.12f else 0.05f),
                                    ),
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = deckGridSlotGlyph(slot.iconToken),
                        style = glyphStyle,
                        color = if (useControlSurfaceStyle) {
                            Color.White.copy(alpha = 0.92f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Text(
                    text = slot.title.ifBlank { "—" },
                    style = titleTypo,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (useControlSurfaceStyle) {
                        Color.White.copy(alpha = 0.96f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = slot.shortcutHint.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (useControlSurfaceStyle) {
                        Color.White.copy(alpha = 0.62f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun controlSurfacePadIdleBrush(index: Int): Brush {
    val palettes = listOf(
        listOf(Color(0xFF181820), Color(0xFF0A0A10)),
        listOf(Color(0xFF16161E), Color(0xFF0B0B12)),
        listOf(Color(0xFF1A1A24), Color(0xFF08080E)),
        listOf(Color(0xFF14141A), Color(0xFF0D0D14)),
    )
    val colors = palettes[index % palettes.size]
    return Brush.linearGradient(colors)
}

private fun controlSurfacePadActiveBrush(index: Int): Brush {
    val palettes = listOf(
        listOf(Color(0xFF3554FF), Color(0xFFC83DDC)),
        listOf(Color(0xFF2A6CFF), Color(0xFFFF6A3D)),
        listOf(Color(0xFF00A3FF), Color(0xFF38FFB0)),
        listOf(Color(0xFF6B4DFF), Color(0xFFFF4D8D)),
    )
    val colors = palettes[index % palettes.size]
    return Brush.linearGradient(colors)
}

private fun controlSurfacePadMediaBrush(index: Int, isOn: Boolean): Brush {
    if (isOn) {
        return Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f),
            ),
        )
    }
    return Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.03f),
        ),
    )
}

