package com.example.deckbridge.domain.hardware

/**
 * Persisted mapping from raw Android input to [HardwareControlId].
 * [padKeyCodes]: physical keyCode → grid cell (row-major learning order).
 * [knobs]: per-knob optional keyCodes for rotation / press plus opaque motion fingerprints for diagnostics.
 */
data class HardwareCalibrationConfig(
    val version: Int = 1,
    /** Preferred lock to the calibrated device; matching falls back to keyCode-only if null. */
    val deviceDescriptor: String?,
    val padKeyCodes: Map<Int, PadCell>,
    val knobs: List<KnobCalibration>,
) {
    val isComplete: Boolean
        get() = padKeyCodes.size == 9 && knobs.size == 3 && knobs.all { it.hasAnyBinding }

    fun matchesDevice(descriptor: String?): Boolean {
        if (deviceDescriptor.isNullOrBlank()) return true
        if (descriptor.isNullOrBlank()) return true
        return deviceDescriptor == descriptor
    }
}

data class PadCell(val row: Int, val col: Int)

data class KnobCalibration(
    val index: Int,
    /** KeyCodes learned for counter‑clockwise / “left” rotation on this knob. */
    val rotateCcwKeyCodes: Set<Int> = emptySet(),
    /** KeyCodes learned for clockwise / “right” rotation on this knob. */
    val rotateCwKeyCodes: Set<Int> = emptySet(),
    val pressKeyCode: Int? = null,
    /** Opaque fingerprints from [MotionEvent] capture, e.g. "src=4194304|axis=9|dir=+". */
    val motionFingerprints: Set<String> = emptySet(),
) {
    val hasAnyBinding: Boolean
        get() = rotateCcwKeyCodes.isNotEmpty() || rotateCwKeyCodes.isNotEmpty() ||
            pressKeyCode != null || motionFingerprints.isNotEmpty()
}
