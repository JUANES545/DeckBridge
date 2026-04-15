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
    /**
     * USB vs Bluetooth often use different [deviceDescriptor] strings for the same physical deck.
     * When both are set and match the event device, treat as the same hardware even if descriptor differs.
     */
    val deviceVendorId: Int? = null,
    val deviceProductId: Int? = null,
    val padKeyCodes: Map<Int, PadCell>,
    val knobs: List<KnobCalibration>,
) {
    val isComplete: Boolean
        get() = padKeyCodes.size == 9 && knobs.size == 3 && knobs.all { it.hasAnyBinding }

    fun matchesDevice(descriptor: String?, vendorId: Int? = null, productId: Int? = null): Boolean {
        if (deviceDescriptor.isNullOrBlank()) return true
        if (descriptor.isNullOrBlank()) return true
        if (deviceDescriptor == descriptor) return true
        val cv = deviceVendorId?.takeIf { it != 0 }
        val cp = deviceProductId?.takeIf { it != 0 }
        val ev = vendorId?.takeIf { it != 0 }
        val ep = productId?.takeIf { it != 0 }
        if (cv != null && cp != null && ev != null && ep != null && cv == ev && cp == ep) {
            return true
        }
        return false
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
