package com.example.deckbridge.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deckbridge.DeckBridgeApplication
import com.example.deckbridge.R
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.hardware.CalibrationStepModel
import com.example.deckbridge.ui.theme.DeckBridgeTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as DeckBridgeApplication
    val repository = app.repository
    val session by repository.calibrationSession.collectAsStateWithLifecycle()
    var hadSession by remember { mutableStateOf(false) }
    var starterSent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!starterSent) {
            repository.startHardwareCalibration()
            starterSent = true
        }
    }

    LaunchedEffect(session) {
        if (session != null) hadSession = true
    }

    BackHandler { onNavigateBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.calibration_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.calibration_back))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            session?.let { s ->
                CalibrationActiveContent(
                    session = s,
                    onSkipKnobPress = { repository.skipKnobPressCalibrationStep() },
                )
            } ?: run {
                if (hadSession) {
                    Text(
                        text = stringResource(R.string.calibration_saved_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.calibration_starting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.calibration_exit))
            }
        }
    }
}

@Composable
private fun CalibrationActiveContent(
    session: CalibrationSessionUi,
    onSkipKnobPress: () -> Boolean,
) {
    val isKnobStep = session.step !is CalibrationStepModel.PadCell
    val isRotationStep = session.step is CalibrationStepModel.KnobRotateCcw ||
        session.step is CalibrationStepModel.KnobRotateCw

    // ── Progress ──────────────────────────────────────────────────────────────
    val progress = (session.stepIndex + 1).toFloat() / session.totalSteps.toFloat()
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(
            R.string.calibration_step_counter,
            session.stepIndex + 1,
            session.totalSteps,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ── Knob section header ───────────────────────────────────────────────────
    // Shown on every knob step so the user always knows they're in the knob phase.
    if (isKnobStep) {
        // stepIndex 9..11 → knob 1, 12..14 → knob 2, 15..17 → knob 3
        val knobNumber = (session.stepIndex - 9) / 3 + 1
        // 0 = CCW, 1 = CW, 2 = press
        val knobActionIndex = when (session.step) {
            is CalibrationStepModel.KnobRotateCcw -> 0
            is CalibrationStepModel.KnobRotateCw  -> 1
            is CalibrationStepModel.KnobPress      -> 2
            else -> -1
        }
        HorizontalDivider(Modifier.fillMaxWidth())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.calibration_knob_section_header),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.calibration_knob_progress, knobNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        // Per-knob action progress: ↺ CCW · ↻ CW · ● Press
        KnobActionRow(activeIndex = knobActionIndex)
    }

    // ── Step instruction ──────────────────────────────────────────────────────
    Text(
        text = stepInstruction(session.step),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )

    // ── Feedback ──────────────────────────────────────────────────────────────
    session.lastCapturedSummary?.let { last ->
        Text(
            text = stringResource(R.string.calibration_last_capture, last),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    session.errorMessage?.let { err ->
        Text(
            text = err,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // ── Skip press (only on KnobPress step) ───────────────────────────────────
    if (session.step is CalibrationStepModel.KnobPress) {
        OutlinedButton(
            onClick = { onSkipKnobPress() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.calibration_skip_press_button))
        }
    }

    // ── Rotation hint (only on CCW / CW steps) ────────────────────────────────
    // The original motion hint was shown on every step (including pad steps) which
    // was confusing. Now it only appears when the user actually needs to rotate a knob.
    if (isRotationStep) {
        Text(
            text = stringResource(R.string.calibration_motion_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Three-action progress indicator for a single knob: ↺ (CCW) · ↻ (CW) · ● (Press).
 * [activeIndex] is 0/1/2; completed actions are shown filled, pending ones outlined.
 */
@Composable
private fun KnobActionRow(activeIndex: Int) {
    val actions = listOf("↺", "↻", "●")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { idx, symbol ->
            val isActive = idx == activeIndex
            val isDone = idx < activeIndex
            val bgColor = when {
                isActive -> MaterialTheme.colorScheme.primary
                isDone   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else     -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                isActive -> MaterialTheme.colorScheme.primary
                isDone   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else     -> MaterialTheme.colorScheme.outline
            }
            val textColor = when {
                isActive -> MaterialTheme.colorScheme.onPrimary
                isDone   -> MaterialTheme.colorScheme.onPrimary
                else     -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }
            if (idx < actions.lastIndex) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun stepInstruction(step: CalibrationStepModel): String = when (step) {
    is CalibrationStepModel.PadCell -> stringResource(
        R.string.calibration_step_pad,
        step.row + 1,
        step.col + 1,
    )
    is CalibrationStepModel.KnobRotateCcw -> stringResource(
        R.string.calibration_step_knob_ccw,
        knobName(step.knobIndex),
    )
    is CalibrationStepModel.KnobRotateCw -> stringResource(
        R.string.calibration_step_knob_cw,
        knobName(step.knobIndex),
    )
    is CalibrationStepModel.KnobPress -> stringResource(
        R.string.calibration_step_knob_press,
        knobName(step.knobIndex),
    )
}

/**
 * Physical position name for the knob during calibration.
 * Uses "Knob 1 (top)" instead of the app function label ("Volume") so the user
 * can match it to the physical knob on their device regardless of how they've
 * configured the knob actions.
 */
@Composable
private fun knobName(index: Int): String = when (index) {
    0 -> stringResource(R.string.calibration_knob_name_1)
    1 -> stringResource(R.string.calibration_knob_name_2)
    2 -> stringResource(R.string.calibration_knob_name_3)
    else -> stringResource(R.string.knob_generic, index + 1)
}

@Preview(showBackground = true)
@Composable
private fun CalibrationScreenPreview() {
    DeckBridgeTheme(dynamicColor = false) {
        CalibrationActiveContent(
            session = CalibrationSessionUi(
                stepIndex = 2,
                totalSteps = 18,
                step = CalibrationStepModel.PadCell(0, 2),
                lastCapturedSummary = "KEYCODE_F4",
                errorMessage = null,
            ),
            onSkipKnobPress = { false },
        )
    }
}
