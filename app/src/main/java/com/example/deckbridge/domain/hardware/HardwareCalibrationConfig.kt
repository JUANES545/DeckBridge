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
    /** KeyCodes observed during “rotate” (e.g. volume up/down). */
    val rotateKeyCodes: Set<Int> = emptySet(),
    val pressKeyCode: Int? = null,
    /** Opaque fingerprints from [MotionEvent] capture, e.g. "src=4194304|axis=9|dir=+". */
    val motionFingerprints: Set<String> = emptySet(),
) {
    val hasAnyBinding: Boolean
        get() = rotateKeyCodes.isNotEmpty() || pressKeyCode != null || motionFingerprints.isNotEmpty()
}
