package com.example.deckbridge.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
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
    Text(
        text = stepInstruction(session.step),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
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
    if (session.step is CalibrationStepModel.KnobPress) {
        OutlinedButton(
            onClick = { onSkipKnobPress() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.calibration_skip_press_button))
        }
    }
    Text(
        text = stringResource(R.string.calibration_motion_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

@Composable
private fun knobName(index: Int): String = when (index) {
    0 -> stringResource(R.string.knob_top)
    1 -> stringResource(R.string.knob_middle)
    2 -> stringResource(R.string.knob_bottom)
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
