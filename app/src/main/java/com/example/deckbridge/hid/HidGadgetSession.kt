package com.example.deckbridge.hid

import com.example.deckbridge.domain.model.HidTransportPhase
import com.example.deckbridge.domain.model.HidTransportUiState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Probes and writes Linux **USB gadget** HID device nodes (`/dev/hidg*`).
 *
 * Stock Android phones in USB device mode do **not** expose these to apps; a rooted/custom build with
 * `usb_f_hid` / `configfs` is required for real host output.
 */
class HidGadgetSession(
    var keyboardDevicePath: String = HidTransportUiState.DEFAULT_KEYBOARD_PATH,
    var consumerDevicePath: String = HidTransportUiState.DEFAULT_CONSUMER_PATH,
) {

    data class ProbeResult(
        val phase: HidTransportPhase,
        val keyboardPath: String,
        val consumerPath: String,
        /** Node present on filesystem (may still be non-writable). */
        val keyboardExists: Boolean,
        val consumerExists: Boolean,
        val keyboardWritable: Boolean,
        val consumerWritable: Boolean,
        val lastError: String?,
    )

    private val zeroKeyboard = ByteArray(8)
    private val zeroConsumer = byteArrayOf(0, 0, 0)

    fun probe(): ProbeResult {
        val kFile = File(keyboardDevicePath)
        val cFile = File(consumerDevicePath)
        val kExists = kFile.exists()
        val cExists = cFile.exists()
        if (!kExists && !cExists) {
            return ProbeResult(
                phase = HidTransportPhase.NO_NODES,
                keyboardPath = keyboardDevicePath,
                consumerPath = consumerDevicePath,
                keyboardExists = false,
                consumerExists = false,
                keyboardWritable = false,
                consumerWritable = false,
                lastError = null,
            )
        }
        var lastErr: String? = null
        val kOk = kExists && tryWrite(keyboardDevicePath, zeroKeyboard).also {
            if (kExists && !it.second) lastErr = it.first
        }.second
        val cOk = cExists && tryWrite(consumerDevicePath, zeroConsumer).also {
            if (cExists && !it.second) lastErr = it.first ?: lastErr
        }.second

        val phase = when {
            kOk && cOk -> HidTransportPhase.KEYBOARD_AND_MEDIA_READY
            kOk -> HidTransportPhase.KEYBOARD_READY
            kExists || cExists -> {
                val denied = (lastErr ?: "").contains("EACCES", ignoreCase = true) ||
                    (lastErr ?: "").contains("Permission denied", ignoreCase = true)
                if (denied) HidTransportPhase.ACCESS_DENIED else HidTransportPhase.ERROR
            }
            else -> HidTransportPhase.NO_NODES
        }
        return ProbeResult(
            phase = phase,
            keyboardPath = keyboardDevicePath,
            consumerPath = consumerDevicePath,
            keyboardExists = kExists,
            consumerExists = cExists,
            keyboardWritable = kOk,
            consumerWritable = cOk,
            lastError = lastErr,
        )
    }

    fun sendKeyboardReport(report: ByteArray): Result<Unit> {
        if (report.size != 8) return Result.failure(IllegalArgumentException("keyboard report must be 8 bytes"))
        return writePath(keyboardDevicePath, report)
    }

    fun sendConsumerReport(report: ByteArray): Result<Unit> {
        if (report.isEmpty()) return Result.failure(IllegalArgumentException("empty consumer report"))
        return writePath(consumerDevicePath, report)
    }

    private fun tryWrite(path: String, bytes: ByteArray): Pair<String?, Boolean> {
        return try {
            FileOutputStream(path).use { it.write(bytes); it.flush() }
            null to true
        } catch (e: IOException) {
            (e.message ?: e.javaClass.simpleName) to false
        } catch (e: SecurityException) {
            (e.message ?: "SecurityException") to false
        }
    }

    private fun writePath(path: String, bytes: ByteArray): Result<Unit> =
        try {
            FileOutputStream(path).use { it.write(bytes); it.flush() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
