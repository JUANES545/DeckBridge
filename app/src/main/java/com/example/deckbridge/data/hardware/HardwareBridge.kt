package com.example.deckbridge.data.hardware

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.example.deckbridge.input.InputDeviceSnapshotFactory
import com.example.deckbridge.input.KeyboardKeyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

private const val CALIB_VERSION = 1
private const val HIGHLIGHT_MS = 400L

/** [MotionEvent.AXIS_HORIZONTAL_SCROLL] (value 10); use const for tooling that omits the field. */
private const val MOTION_AXIS_HORIZONTAL_SCROLL = 10

data class HardwareKeyHandleResult(
    val rawLine: RawDiagnosticLine,
    /** When true, repository should not run legacy F-key → deck slot routing for this event. */
    val consumeDeckKeyRouting: Boolean,
    val mirrorHighlight: HardwareMirrorHighlight?,
    val diagSummary: HardwareDiagSummary?,
    val completedCalibration: HardwareCalibrationConfig?,
)

data class HardwareMotionHandleResult(
    val rawLine: RawDiagnosticLine,
    val mirrorHighlight: HardwareMirrorHighlight?,
    val diagSummary: HardwareDiagSummary?,
    val completedCalibration: HardwareCalibrationConfig?,
)

/**
 * Raw input → calibration wizard OR logical [HardwareControlId] → mirror highlights.
 * Keeps Android specifics out of domain except for stored primitives.
 */
class HardwareBridge(
    private val scope: CoroutineScope,
    private val onCalibrationComplete: suspend (HardwareCalibrationConfig) -> Unit,
) {

    private var loadedConfig: HardwareCalibrationConfig? = null

    private val _calibrationSession = MutableStateFlow<CalibrationSessionUi?>(null)
    val calibrationSession: StateFlow<CalibrationSessionUi?> = _calibrationSession.asStateFlow()

    private var draft: CalibrationDraft? = null
    private var stepIndex: Int = 0

    private sealed class Step {
        data class Pad(val row: Int, val col: Int) : Step()
        data class KnobRot(val index: Int) : Step()
        data class KnobPress(val index: Int) : Step()
    }

    private val steps: List<Step> = buildList {
        for (r in 0..2) for (c in 0..2) add(Step.Pad(r, c))
        for (k in 0..2) add(Step.KnobRot(k))
        for (k in 0..2) add(Step.KnobPress(k))
    }

    private data class CalibrationDraft(
        var deviceDescriptor: String?,
        val pad: MutableMap<Int, PadCell> = mutableMapOf(),
        val knobs: Array<KnobDraft> = Array(3) { KnobDraft() },
    )

    private class KnobDraft {
        val rotateKeys: MutableSet<Int> = mutableSetOf()
        var pressKey: Int? = null
        val motionPrints: MutableSet<String> = mutableSetOf()
    }

    fun setLoadedConfig(config: HardwareCalibrationConfig?) {
        loadedConfig = config
    }

    fun startCalibration() {
        draft = CalibrationDraft(deviceDescriptor = null)
        stepIndex = 0
        emitSession(lastCapture = null, error = null, forceShow = true)
    }

    fun cancelCalibration() {
        draft = null
        stepIndex = 0
        _calibrationSession.value = null
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
            val outcome = processCalibrationKeyDown(event.keyCode, desc)
            return HardwareKeyHandleResult(
                rawLine = rawLine,
                consumeDeckKeyRouting = true,
                mirrorHighlight = outcome.first,
                diagSummary = outcome.second,
                completedCalibration = outcome.third,
            )
        }

        val cfg = loadedConfig
        if (cfg != null && cfg.isComplete && event.action == KeyEvent.ACTION_DOWN) {
            val match = matchPadKey(cfg, event.keyCode, desc)
            if (match != null) {
                val h = HardwareMirrorHighlight(
                    control = match,
                    kind = HardwareHighlightKind.PAD_DOWN,
                    untilEpochMs = System.currentTimeMillis() + HIGHLIGHT_MS,
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
                    ),
                    completedCalibration = null,
                )
            }
            val knob = matchKnobKey(cfg, event.keyCode, desc, press = true, rotate = true)
            if (knob != null) {
                val kc = cfg.knobs.getOrNull(knob.index)
                val isPress = kc?.pressKeyCode == event.keyCode
                val kind = if (isPress) {
                    HardwareHighlightKind.KNOB_PRESS_DOWN
                } else {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_DOWN -> HardwareHighlightKind.KNOB_ROTATE_CCW
                        else -> HardwareHighlightKind.KNOB_ROTATE_CW
                    }
                }
                val rot = when (kind) {
                    HardwareHighlightKind.KNOB_ROTATE_CCW -> -0.45f
                    HardwareHighlightKind.KNOB_ROTATE_CW -> 0.45f
                    else -> 0f
                }
                val h = HardwareMirrorHighlight(
                    control = knob,
                    kind = kind,
                    untilEpochMs = System.currentTimeMillis() + HIGHLIGHT_MS,
                    rotationVisual = rot,
                )
                return HardwareKeyHandleResult(
                    rawLine = rawLine,
                    consumeDeckKeyRouting = false,
                    mirrorHighlight = h,
                    diagSummary = HardwareDiagSummary(
                        controlLabel = labelFor(knob),
                        kind = kind.name,
                        matchedAs = "calibration",
                        epochMs = rawLine.epochMs,
                    ),
                    completedCalibration = null,
                )
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
            if (step is Step.KnobRot) {
                val print = motionFingerprint(event)
                d.knobs[step.index].motionPrints.add(print)
                if (d.deviceDescriptor == null) d.deviceDescriptor = snap.descriptor
                advanceAfterCapture("Motion: $print")
                return HardwareMotionHandleResult(rawLine, null, null, null)
            }
        }

        val cfg = loadedConfig
        if (cfg != null && cfg.isComplete && isSignificantMotion(event)) {
            val print = motionFingerprint(event)
            val knob = matchKnobMotion(cfg, print, snap.descriptor)
            if (knob != null) {
                val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                val cw = scroll >= 0
                val h = HardwareMirrorHighlight(
                    control = knob,
                    kind = if (cw) HardwareHighlightKind.KNOB_ROTATE_CW else HardwareHighlightKind.KNOB_ROTATE_CCW,
                    untilEpochMs = System.currentTimeMillis() + HIGHLIGHT_MS,
                    rotationVisual = if (cw) 1f else -1f,
                )
                return HardwareMotionHandleResult(
                    rawLine = rawLine,
                    mirrorHighlight = h,
                    diagSummary = HardwareDiagSummary(
                        controlLabel = labelFor(knob),
                        kind = if (cw) "KNOB_CW" else "KNOB_CCW",
                        matchedAs = "motion:$print",
                        epochMs = rawLine.epochMs,
                    ),
                    completedCalibration = null,
                )
            }
        }

        return HardwareMotionHandleResult(rawLine, null, null, null)
    }

    private fun processCalibrationKeyDown(
        keyCode: Int,
        descriptor: String?,
    ): Triple<HardwareMirrorHighlight?, HardwareDiagSummary?, HardwareCalibrationConfig?> {
        val d = draft ?: return Triple(null, null, null)
        if (d.deviceDescriptor == null) d.deviceDescriptor = descriptor
        val step = steps.getOrNull(stepIndex) ?: return Triple(null, null, completeDraftAndPersist(d))

        when (step) {
            is Step.Pad -> {
                if (d.pad.containsKey(keyCode)) {
                    setError("This key is already assigned. Press another.")
                    return Triple(null, null, null)
                }
                d.pad[keyCode] = PadCell(step.row, step.col)
                advanceAfterCapture(KeyboardKeyFormatter.keyCodeName(keyCode))
            }
            is Step.KnobRot -> {
                d.knobs[step.index].rotateKeys.add(keyCode)
                advanceAfterCapture("Key: ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
            }
            is Step.KnobPress -> {
                d.knobs[step.index].pressKey = keyCode
                advanceAfterCapture("Key: ${KeyboardKeyFormatter.keyCodeName(keyCode)}")
            }
        }
        return Triple(null, null, null)
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
                scope.launch { onCalibrationComplete(cfg) }
            }
            return
        }
        emitSession(lastCapture = summary, error = null, forceShow = false)
    }

    private fun setError(msg: String) {
        emitSession(lastCapture = null, error = msg, forceShow = false)
    }

    private fun emitSession(lastCapture: String?, error: String?, forceShow: Boolean) {
        val step = steps.getOrNull(stepIndex) ?: run {
            _calibrationSession.value = null
            return
        }
        val model: CalibrationStepModel = when (step) {
            is Step.Pad -> CalibrationStepModel.PadCell(step.row, step.col)
            is Step.KnobRot -> CalibrationStepModel.KnobRotate(step.index)
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

    private fun completeDraftAndPersist(d: CalibrationDraft): HardwareCalibrationConfig {
        val cfg = buildConfig(d)
        loadedConfig = cfg
        draft = null
        stepIndex = 0
        _calibrationSession.value = null
        scope.launch { onCalibrationComplete(cfg) }
        return cfg
    }

    private fun buildConfig(d: CalibrationDraft): HardwareCalibrationConfig {
        val knobs = (0..2).map { i ->
            val kd = d.knobs[i]
            KnobCalibration(
                index = i,
                rotateKeyCodes = kd.rotateKeys.toSet(),
                pressKeyCode = kd.pressKey,
                motionFingerprints = kd.motionPrints.toSet(),
            )
        }
        return HardwareCalibrationConfig(
            version = CALIB_VERSION,
            deviceDescriptor = d.deviceDescriptor,
            padKeyCodes = d.pad.toMap(),
            knobs = knobs,
        )
    }

    private fun matchPadKey(cfg: HardwareCalibrationConfig, keyCode: Int, descriptor: String?): HardwareControlId.PadKey? {
        if (!cfg.matchesDevice(descriptor)) return null
        val cell = cfg.padKeyCodes[keyCode] ?: return null
        return HardwareControlId.PadKey(cell.row, cell.col)
    }

    private fun matchKnobKey(
        cfg: HardwareCalibrationConfig,
        keyCode: Int,
        descriptor: String?,
        press: Boolean,
        rotate: Boolean,
    ): HardwareControlId.Knob? {
        if (!cfg.matchesDevice(descriptor)) return null
        cfg.knobs.forEach { k ->
            if (rotate && keyCode in k.rotateKeyCodes) return HardwareControlId.Knob(k.index)
            if (press && k.pressKeyCode == keyCode) return HardwareControlId.Knob(k.index)
        }
        return null
    }

    private fun matchKnobMotion(cfg: HardwareCalibrationConfig, print: String, descriptor: String?): HardwareControlId.Knob? {
        if (!cfg.matchesDevice(descriptor)) return null
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
        val hs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            abs(ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL))
        } else {
            0f
        }
        return s > 0.02f || hs > 0.02f
    }

    private fun motionFingerprint(ev: MotionEvent): String {
        val sc = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
        val hsc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL)
        } else {
            0f
        }
        return "src=0x${ev.source.toString(16)}|sc=${"%.3f".format(sc)}|hsc=${"%.3f".format(hsc)}|a=${ev.actionMasked}"
    }

    private fun summarizeMotion(ev: MotionEvent): String = buildString {
        append("MOTION act=${ev.actionMasked} src=0x${ev.source.toString(16)}")
        append(" scroll=${"%.3f".format(ev.getAxisValue(MotionEvent.AXIS_SCROLL))}")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            append(" hscroll=${"%.3f".format(ev.getAxisValue(MOTION_AXIS_HORIZONTAL_SCROLL))}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rotary = ev.source and InputDevice.SOURCE_ROTARY_ENCODER
            if (rotary != 0) append(" ROTARY_ENCODER")
        }
    }
}
