package com.example.deckbridge.domain.hardware

/**
 * Wizard state exposed to [com.example.deckbridge.ui.calibration.CalibrationScreen].
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
    data class KnobRotate(val knobIndex: Int) : CalibrationStepModel()
    data class KnobPress(val knobIndex: Int) : CalibrationStepModel()
}
