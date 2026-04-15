package com.example.deckbridge.data.hardware

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.deckbridge.R
import com.example.deckbridge.domain.hardware.CalibrationSessionUi
import com.example.deckbridge.domain.hardware.CalibrationStepModel
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.HardwareControlId
import com.example.deckbridge.domain.hardware.HardwareDiagSummary
import com.example.deckbridge.domain.hardware.HardwareHighlightKind
import com.example.deckbridge.domain.hardware.HardwareMirrorHighlight
import com.example.deckbridge.domain.hardware.KnobCalibration
import com.example.deckbridge.domain.hardware.PadCell
import com.example.deckbridge.domain.hardware.RawChannel
import com.example.deckbridge.domain.hardware.RawDiagnosticLine
import com.example.deckbridge.data.deck.DeckKnobIntentMapper
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardKeyFormatter
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

private const val CALIB_VERSION = 2
/** Mirror highlight duration — keep short for snappy Wi‑Fi debugging UX. */
private const val HIGHLIGHT_MS = 240L
private const val KNOB_PRESS_UP_MS = 115L

/** [MotionEvent.AXIS_HORIZONTAL_SCROLL] (value 10); use const for tooling that omits the field. */
private const val MOTION_AXIS_HORIZONTAL_SCROLL = 10

private data class KnobKeyMatch(
    val knob: HardwareControlId.Knob,
    val isPress: Boolean,
    /** Meaningful when [isPress] is false: true = CCW / “left”, false = CW / “right”. */
    val rotateCcw: Boolean?,
)

data class HardwareKeyHandleResult(
    val rawLine: RawDiagnosticLine,
    val consumeDeckKeyRouting: Boolean,
    val mirrorHighlight: HardwareMirrorHighlight?,
    val diagSummary: HardwareDiagSummary?,
    val completedCalibration: HardwareCalibrationConfig?,
    /** When set, repository may dispatch this intent (knob MVP mapping). */
    val knobDeckIntent: DeckButtonIntent? = null,
)

data class HardwareMotionHandleResult(
    val rawLine: RawDiagnosticLine,
    val mirrorHighlight: HardwareMirrorHighlight?,
    val diagSummary: HardwareDiagSummary?,
    val completedCalibration: HardwareCalibrationConfig?,
    val knobDeckIntent: DeckButtonIntent? = null,
)

class HardwareBridge(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val onCalibrationComplete: suspend (HardwareCalibrationConfig) -> Unit,
) {

    private var loadedConfig: HardwareCalibrationConfig? = null

    @Volatile
    private var deckKnobsLayout: DeckKnobsLayoutPersisted? = null

    fun setDeckKnobsLayout(layout: DeckKnobsLayoutPersisted) {
        deckKnobsLayout = layout
    }

    private val _calibrationSession = MutableStateFlow<CalibrationSessionUi?>(null)
    val calibrationSession: StateFlow<CalibrationSessionUi?> = _calibrationSession.asStateFlow()

    private var draft: CalibrationDraft? = null
    private var stepIndex: Int = 0

    private sealed class Step {
        data class Pad(val row: Int, val col: Int) : Step()
        data class KnobRotCcw(val index: Int) : Step()
        data class KnobRotCw(val index: Int) : Step()
        data class KnobPress(val index: Int) : Step()
    }

    /** 9 pads, then each knob: CCW → CW → press (all three knobs identical structure). */
    private val steps: List<Step> = buildList {
        for (r in 0..2) for (c in 0..2) add(Step.Pad(r, c))
        for (k in 0..2) {
            add(Step.KnobRotCcw(k))
            add(Step.KnobRotCw(k))
            add(Step.KnobPress(k))
        }
    }

    private data class CalibrationDraft(
        var deviceDescriptor: String?,
        var deviceVendorId: Int? = null,
        var deviceProductId: Int? = null,
        val pad: MutableMap<Int, PadCell> = mutableMapOf(),
        val knobs: Array<KnobDraft> = Array(3) { KnobDraft() },
    )

    private class KnobDraft {
        val rotateCcwKeys: MutableSet<Int> = mutableSetOf()
        val rotateCwKeys: MutableSet<Int> = mutableSetOf()
        var pressKey: Int? = null
        val motionPrints: MutableSet<String> = mutableSetOf()
    }

    fun setLoadedConfig(config: HardwareCalibrationConfig?) {
        loadedConfig = config
    }

    fun startCalibration() {
        draft = CalibrationDraft(deviceDescriptor = null)
        stepIndex = 0
        DeckBridgeLog.cal("wizard started totalSteps=${steps.size} (9 pads + 3 knobs × (CCW+CW+press))")
        emitSession(lastCapture = null, error = null, forceShow = true)
    }

    fun cancelCalibration() {
        draft = null
        stepIndex = 0
        _calibrationSession.value = null
        DeckBridgeLog.cal("wizard cancelled by user")
    }

    fun skipCurrentKnobPressIfApplicable(): Boolean {
        if (_calibrationSession.value == null) return false
        val step = steps.getOrNull(stepIndex) as? Step.KnobPress ?: return false
        DeckBridgeLog.cal("knob${step.index + 1} press step skipped (no Android key event from device)")
        advanceAfterCapture(appContext.getString(R.string.calibration_skipped_press_summary))
        return true
    }

    fun handleKeyEvent(event: KeyEvent): HardwareKeyHandleResult {
        val device = event.device ?: InputDevice.getDevice(event.deviceId)
        val snap = InputDeviceSnapshotFactory.from(device)
        val desc = snap.descriptor
        val rawSummary = buildString {
            append("KEY ")
            append(KeyboardKeyFormatter.keyCodeName(event.keyCode))
            append(" action=")
            append(if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP")
            append(" src=0x${event.source.toString(16)}")
        }
        val rawLine = RawDiagnosticLine(
            id = UUID.randomUUID().toString(),
            epochMs = System.currentTimeMillis(),
            channel = RawChannel.KEY,
            summary = rawSummary,
            deviceId = event.deviceId,
            deviceName = snap.name,
        )

        val session = _calibrationSession.value
        if (session != null && event.action == KeyEvent.ACTION_DOWN) {
            draft?.let { d ->
                if (d.deviceVendorId == null && snap.vendorId != null && snap.productId != null) {
                    d.deviceVendorId = snap.vendorId.takeIf { it != 0 }
                    d.deviceProductId = snap.productId.takeIf { it != 0 }
                }
            }
            processCalibrationKeyDown(event.keyCode, desc)
            return HardwareKeyHandleResult(
                rawLine = rawLine,
                consumeDeckKeyRouting = true,
                mirrorHighlight = null,
                diagSummary = null,
                completedCalibration = null,
            )
        }

        val cfg = loadedConfig
        if (cfg != null && cfg.isComplete) {
            if (event.action == KeyEvent.ACTION_UP) {
                val upMatch = matchKnobPressUp(cfg, event.keyCode, desc, snap.vendorId, snap.productId)
                if (upMatch != null) {
                    val until = System.currentTimeMillis() + KNOB_PRESS_UP_MS
                    val h = HardwareMirrorHighlight(
                        control = upMatch,
                        kind = HardwareHighlightKind.KNOB_PRESS_UP,
                        untilEpochMs = until,
                        rotationVisual = 0f,
                    )
                    return HardwareKeyHandleResult(
                        rawLine = rawLine,
                        consumeDeckKeyRouting = false,
                        mirrorHighlight = h,
                        diagSummary = HardwareDiagSummary(
                            controlLabel = labelFor(upMatch),
                            kind = "KNOB_PRESS_UP",
                            matchedAs = "calibration",
                            epochMs = rawLine.epochMs,
                            control = upMatch,
                        ),
                        completedCalibration = null,
                        knobDeckIntent = null,
                    )
                }
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                val match = matchPadKey(cfg, event.keyCode, desc, snap.vendorId, snap.productId)
                if (match != null) {
                    val until = System.currentTimeMillis() + HIGHLIGHT_MS
                    val h = HardwareMirrorHighlight(
                        control = match,
                        kind = HardwareHighlightKind.PAD_DOWN,
                        untilEpochMs = until,
                    )
                    return HardwareKeyHandleResult(
                        rawLine = rawLine,
                        consumeDeckKeyRouting = true,
                        mirrorHighlight = h,
                        diagSummary = HardwareDiagSummary(
                            controlLabel = labelFor(match),
                            kind = "PAD_DOWN",
                            matchedAs = "calibration",
                            epochMs = rawLine.epochMs,
                            control = match,
                        ),
                        completedCalibration = null,
                    )
                }
                val knobMatch = findKnobKeyMatch(cfg, event.keyCode, desc, snap.vendorId, snap.productId)
                if (knobMatch != null) {
                    val until = System.currentTimeMillis() + HIGHLIGHT_MS
                    val kind = if (knobMatch.isPress) {
                        HardwareHighlightKind.KNOB_PRESS_DOWN
                    } else {
                        if (knobMatch.rotateCcw == true) {
                            HardwareHighlightKind.KNOB_ROTATE_CCW
                        } else {
                            HardwareHighlightKind.KNOB_ROTATE_CW
                        }
                    }
                    val rot = when (kind) {
                        HardwareHighlightKind.KNOB_ROTATE_CCW -> -0.45f
                        HardwareHighlightKind.KNOB_ROTATE_CW -> 0.45f
                        else -> 0f
                    }
                    val h = HardwareMirrorHighlight(
                        control = knobMatch.knob,
                        kind = kind,
                        untilEpochMs = until,
                        rotationVisual = rot,
                    )
                    val deckIntent = deckIntentForKnobKey(knobMatch)
                    return HardwareKeyHandleResult(
                        rawLine = rawLine,
                        consumeDeckKeyRouting = knobMatch.isPress,
                        mirrorHighlight = h,
                        diagSummary = HardwareDiagSummary(
                            controlLabel = labelFor(knobMatch.knob),
                            kind = kind.name,
                            matchedAs = "calibration",
                            epochMs = rawLine.epochMs,
                            control = knobMatch.knob,
                        ),
                        completedCalibration = null,
                        knobDeckIntent = deckIntent,
                    )
                }
            }
        }

        return HardwareKeyHandleResult(
            rawLine = rawLine,
            consumeDeckKeyRouting = false,
            mirrorHighlight = null,
            diagSummary = null,
            completedCalibration = null,
        )
    }

    fun handleGenericMotionEvent(event: MotionEvent): HardwareMotionHandleResult {
        val device = event.device
        val snap = InputDeviceSnapshotFactory.from(device)
        val rawSummary = summarizeMotion(event)
        val rawLine = RawDiagnosticLine(
            id = UUID.randomUUID().toString(),
            epochMs = System.currentTimeMillis(),
            channel = RawChannel.MOTION,
            summary = rawSummary,
            deviceId = event.deviceId,
            deviceName = snap.name,
        )

        if (_calibrationSession.value != null && isSignificantMotion(event)) {
            val d = draft ?: return HardwareMotionHandleResult(rawLine, null, null, null)
            val step = steps.getOrNull(stepIndex) ?: return HardwareMotionHandleResult(rawLine, null, null, null)
            if (step is Step.KnobRotCcw || step is Step.KnobRotCw) {
                val idx = when (step) {
                    is Step.KnobRotCcw -> step.index
                    is Step.KnobRotCw -> step.index
                    else -> 0
                }
                val print = motionFingerprint(event)
                d.knobs[idx].motionPrints.add(print)
                if (d.deviceDescriptor == null) d.deviceDescriptor = snap.descriptor
                if (d.deviceVendorId == null && snap.vendorId != null && snap.productId != null) {
                    d.deviceVendorId = snap.vendorId.takeIf { it != 0 }
                    d.deviceProductId = snap.productId.takeIf { it != 0 }
                }
                advanceAfterCapture("Motion: $print")
                return HardwareMotionHandleResult(rawLine, null, null, null)
            }
        }

        val cfg = loadedConfig
        if (cfg != null && cfg.isComplete && isSignificantMotion(event)) {
            val print = motionFingerprint(event)
            val knob = matchKnobMotion(cfg, print, snap.descriptor, snap.vendorId, snap.productId)
            if (knob != null) {
                val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                val cw = scroll >= 0
                val until = System.currentTimeMillis() + HIGHLIGHT_MS
                val h = HardwareMirrorHighlight(
                    control = knob,
                    kind = if (cw) HardwareHighlightKind.KNOB_ROTATE_CW else HardwareHighlightKind.KNOB_ROTATE_CCW,
                    untilEpochMs = until,
                    rotationVisual = if (cw) 1f else -1f,
                )
                val deckIntent = deckKnobsLayout?.let { layout ->
                    DeckKnobIntentMapper.intentForRotate(layout, knob.index, ccw = !cw)
                }
                return HardwareMotionHandleResult(
                    rawLine = rawLine,
                    mirrorHighlight = h,
                    diagSummary = HardwareDiagSummary(
                        controlLabel = labelFor(knob),
                        kind = if (cw) "KNOB_CW" else "KNOB_CCW",
                        matchedAs = "motion:$print",
                        epochMs = rawLine.epochMs,
                        control = knob,
                    ),
                    completedCalibration = null,
                    knobDeckIntent = deckIntent,
                )
            }
        }

        return HardwareMotionHandleResult(rawLine, null, null, null)
    }

    private fun deckIntentForKnobKey(m: KnobKeyMatch): DeckButtonIntent? {
        val layout = deckKnobsLayout ?: return null
        return when {
            m.isPress -> DeckKnobIntentMapper.intentForPress(layout, m.knob.index)
            m.rotateCcw != null -> DeckKnobIntentMapper.intentForRotate(layout, m.knob.index, m.rotateCcw)
            else -> null
        }
    }

    private fun processCalibrationKeyDown(
        keyCode: Int,
        descriptor: String?,
    ) {
        val d = draft ?: return
        if (d.deviceDescriptor == null) d.deviceDescriptor = descriptor
        val step = steps.getOrNull(stepIndex) ?: return

        when (step) {
            is Step.Pad -> {
                if (d.pad.containsKey(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                d.pad[keyCode] = PadCell(step.row, step.col)
                DeckBridgeLog.cal("pad cell r=${step.row} c=${step.col} → ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
                advanceAfterCapture(KeyboardKeyFormatter.keyCodeName(keyCode))
            }
            is Step.KnobRotCcw -> {
                val kd = d.knobs[step.index]
                if (d.pad.containsKey(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                if (keyCode in kd.rotateCwKeys) {
                    setError(appContext.getString(R.string.calibration_error_knob_cw_key_on_ccw_step))
                    return
                }
                if (!kd.rotateCcwKeys.add(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                DeckBridgeLog.cal("knob${step.index + 1} CCW → ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
                advanceAfterCapture("CCW: ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
            }
            is Step.KnobRotCw -> {
                val kd = d.knobs[step.index]
                if (d.pad.containsKey(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                if (keyCode in kd.rotateCcwKeys) {
                    setError(appContext.getString(R.string.calibration_error_knob_ccw_key_on_cw_step))
                    return
                }
                if (!kd.rotateCwKeys.add(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                DeckBridgeLog.cal("knob${step.index + 1} CW → ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
                advanceAfterCapture("CW: ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
            }
            is Step.KnobPress -> {
                val kd = d.knobs[step.index]
                if (d.pad.containsKey(keyCode)) {
                    setError(appContext.getString(R.string.calibration_error_duplicate_key))
                    return
                }
                if (keyCode in kd.rotateCcwKeys || keyCode in kd.rotateCwKeys) {
                    setError(appContext.getString(R.string.calibration_error_press_same_as_rotate))
                    return
                }
                d.knobs.forEachIndexed { i, other ->
                    if (i != step.index && other.pressKey == keyCode) {
                        setError(appContext.getString(R.string.calibration_error_duplicate_key))
                        return
                    }
                }
                kd.pressKey = keyCode
                DeckBridgeLog.cal("knob${step.index + 1} press → ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
                advanceAfterCapture("Key: ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
            }
        }
    }

    private fun advanceAfterCapture(summary: String) {
        stepIndex++
        if (stepIndex >= steps.size) {
            draft?.let { d ->
                val cfg = buildConfig(d)
                loadedConfig = cfg
                draft = null
                stepIndex = 0
                _calibrationSession.value = null
                DeckBridgeLog.cal(
                    "wizard complete isComplete=${cfg.isComplete} desc=${cfg.deviceDescriptor?.take(48)} " +
                        "vid=${cfg.deviceVendorId} pid=${cfg.deviceProductId}",
                )
                scope.launch { onCalibrationComplete(cfg) }
            }
            return
        }
        emitSession(lastCapture = summary, error = null, forceShow = false)
    }

    private fun setError(msg: String) {
        DeckBridgeLog.cal("validation error: $msg")
        emitSession(lastCapture = null, error = msg, forceShow = false)
    }

    private fun emitSession(lastCapture: String?, error: String?, forceShow: Boolean) {
        val step = steps.getOrNull(stepIndex) ?: run {
            _calibrationSession.value = null
            return
        }
        val model: CalibrationStepModel = when (step) {
            is Step.Pad -> CalibrationStepModel.PadCell(step.row, step.col)
            is Step.KnobRotCcw -> CalibrationStepModel.KnobRotateCcw(step.index)
            is Step.KnobRotCw -> CalibrationStepModel.KnobRotateCw(step.index)
            is Step.KnobPress -> CalibrationStepModel.KnobPress(step.index)
        }
        val prev = _calibrationSession.value
        _calibrationSession.value = CalibrationSessionUi(
            stepIndex = stepIndex,
            totalSteps = steps.size,
            step = model,
            lastCapturedSummary = when {
                lastCapture != null -> lastCapture
                forceShow -> null
                else -> prev?.lastCapturedSummary
            },
            errorMessage = error,
        )
    }

    private fun buildConfig(d: CalibrationDraft): HardwareCalibrationConfig {
        val knobs = (0..2).map { i ->
            val kd = d.knobs[i]
            KnobCalibration(
                index = i,
                rotateCcwKeyCodes = kd.rotateCcwKeys.toSet(),
                rotateCwKeyCodes = kd.rotateCwKeys.toSet(),
                pressKeyCode = kd.pressKey,
                motionFingerprints = kd.motionPrints.toSet(),
            )
        }
        return HardwareCalibrationConfig(
            version = CALIB_VERSION,
            deviceDescriptor = d.deviceDescriptor,
            deviceVendorId = d.deviceVendorId,
            deviceProductId = d.deviceProductId,
            padKeyCodes = d.pad.toMap(),
            knobs = knobs,
        )
    }

    private fun matchPadKey(
        cfg: HardwareCalibrationConfig,
        keyCode: Int,
        descriptor: String?,
        vendorId: Int?,
        productId: Int?,
    ): HardwareControlId.PadKey? {
        if (!cfg.matchesDevice(descriptor, vendorId, productId)) return null
        val cell = cfg.padKeyCodes[keyCode] ?: return null
        return HardwareControlId.PadKey(cell.row, cell.col)
    }

    /** Press wins; then CCW-only, CW-only, or ambiguous membership (volume heuristics). */
    private fun findKnobKeyMatch(
        cfg: HardwareCalibrationConfig,
        keyCode: Int,
        descriptor: String?,
        vendorId: Int?,
        productId: Int?,
    ): KnobKeyMatch? {
        if (!cfg.matchesDevice(descriptor, vendorId, productId)) return null
        cfg.knobs.forEach { k ->
            if (k.pressKeyCode == keyCode) {
                return KnobKeyMatch(HardwareControlId.Knob(k.index), isPress = true, rotateCcw = null)
            }
        }
        cfg.knobs.forEach { k ->
            val inCcw = keyCode in k.rotateCcwKeyCodes
            val inCw = keyCode in k.rotateCwKeyCodes
            if (!inCcw && !inCw) return@forEach
            val ccw = when {
                inCcw && !inCw -> true
                !inCcw && inCw -> false
                else -> when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> true
                    KeyEvent.KEYCODE_VOLUME_UP -> false
                    else -> false
                }
            }
            return KnobKeyMatch(HardwareControlId.Knob(k.index), isPress = false, rotateCcw = ccw)
        }
        return null
    }

    private fun matchKnobPressUp(
        cfg: HardwareCalibrationConfig,
        keyCode: Int,
        descriptor: String?,
        vendorId: Int?,
        productId: Int?,
    ): HardwareControlId.Knob? {
        if (!cfg.matchesDevice(descriptor, vendorId, productId)) return null
        cfg.knobs.forEach { k ->
            if (k.pressKeyCode == keyCode) {
                return HardwareControlId.Knob(k.index)
            }
        }
        return null
    }

    private fun matchKnobMotion(
        cfg: HardwareCalibrationConfig,
        print: String,
        descriptor: String?,
        vendorId: Int?,
        productId: Int?,
    ): HardwareControlId.Knob? {
        if (!cfg.matchesDevice(descriptor, vendorId, productId)) return null
        cfg.knobs.forEach { k ->
            if (k.motionFingerprints.any { print.contains(it) || it.contains(print) }) {
                return HardwareControlId.Knob(k.index)
            }
        }
        return null
    }

    private fun labelFor(id: HardwareControlId): String = when (id) {
        is HardwareControlId.PadKey -> "Pad ${id.row + 1},${id.col + 1}"
        is HardwareControlId.Knob -> "Knob ${id.index + 1}"
    }

    private fun isSignificantMotion(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_SCROLL) return true
        val s = abs(ev.getAxisValue(MotionEvent.AXIS_SCROLL))
        val hs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            abs(ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL))
        } else {
            0f
        }
        return s > 0.02f || hs > 0.02f
    }

    private fun motionFingerprint(ev: MotionEvent): String {
        val sc = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
        val hsc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL)
        } else {
            0f
        }
        return "src=0x${ev.source.toString(16)}|sc=${"%.3f".format(sc)}|hsc=${"%.3f".format(hsc)}|a=${ev.actionMasked}"
    }

    private fun summarizeMotion(ev: MotionEvent): String = buildString {
        append("MOTION act=${ev.actionMasked} src=0x${ev.source.toString(16)}")
        append(" scroll=${"%.3f".format(ev.getAxisValue(MotionEvent.AXIS_SCROLL))}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append(" hscroll=${"%.3f".format(ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL))}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rotary = ev.source and InputDevice.SOURCE_ROTARY_ENCODER
            if (rotary != 0) append(" ROTARY_ENCODER")
        }
    }
}
