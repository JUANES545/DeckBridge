package com.example.deckbridge.domain.hardware

/**
 * Wizard state for the hardware calibration flow (see `ui.calibration.CalibrationScreen`).
 */
data class CalibrationSessionUi(
    val stepIndex: Int,
    val totalSteps: Int,
    val step: CalibrationStepModel,
    val lastCapturedSummary: String?,
    val errorMessage: String?,
)

sealed class CalibrationStepModel {
    data class PadCell(val row: Int, val col: Int) : CalibrationStepModel()
    data class KnobRotateCcw(val knobIndex: Int) : CalibrationStepModel()
    data class KnobRotateCw(val knobIndex: Int) : CalibrationStepModel()
    data class KnobPress(val knobIndex: Int) : CalibrationStepModel()
}
