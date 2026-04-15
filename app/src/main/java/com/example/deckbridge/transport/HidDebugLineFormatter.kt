package com.example.deckbridge.transport

import com.example.deckbridge.hid.HidGadgetSession

/**
 * Single-line snapshot for Logcat `[DBG]` and Settings — same string in both places.
 */
object HidDebugLineFormatter {

    fun format(
        privilegedShellOk: Boolean,
        hidPcModeOn: Boolean,
        usbCableToHost: Boolean,
        probe: HidGadgetSession.ProbeResult,
    ): String {
        val k = nodeVerb(probe.keyboardExists, probe.keyboardWritable)
        val c = nodeVerb(probe.consumerExists, probe.consumerWritable)
        val sendKbd = hidPcModeOn && probe.keyboardWritable
        val sendMedia = hidPcModeOn && probe.consumerWritable
        return buildString {
            append("su=")
            append(if (privilegedShellOk) "ok" else "no")
            append(" | hidPcMode=")
            append(if (hidPcModeOn) "on" else "off")
            append(" | usbCable=")
            append(if (usbCableToHost) "yes" else "no")
            append(" | phase=")
            append(probe.phase.name)
            append(" | hidg0=")
            append(k)
            append(" | hidg1=")
            append(c)
            append(" | sendKbd=")
            append(if (sendKbd) "yes" else "no")
            append(" | sendMedia=")
            append(if (sendMedia) "yes" else "no")
            if (probe.lastError != null) {
                append(" | err=")
                append(probe.lastError.take(120))
            }
        }
    }

    private fun nodeVerb(exists: Boolean, writable: Boolean): String = when {
        !exists -> "absent"
        writable -> "ok"
        else -> "denied"
    }
}
