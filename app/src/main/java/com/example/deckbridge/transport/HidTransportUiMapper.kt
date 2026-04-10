package com.example.deckbridge.transport

import android.content.res.Resources
import com.example.deckbridge.R
import com.example.deckbridge.domain.model.HidTransportPhase
import com.example.deckbridge.domain.model.HidTransportUiState
import com.example.deckbridge.hid.HidGadgetSession

object HidTransportUiMapper {

    fun toUiState(probe: HidGadgetSession.ProbeResult, res: Resources): HidTransportUiState {
        val summary = when (probe.phase) {
            HidTransportPhase.NOT_PROBED -> res.getString(R.string.hid_transport_summary_not_probed)
            HidTransportPhase.PROBING -> res.getString(R.string.hid_transport_summary_probing)
            HidTransportPhase.NO_NODES -> res.getString(R.string.hid_transport_summary_no_nodes)
            HidTransportPhase.ACCESS_DENIED -> res.getString(R.string.hid_transport_summary_denied)
            HidTransportPhase.KEYBOARD_READY -> res.getString(R.string.hid_transport_summary_keyboard_ready)
            HidTransportPhase.KEYBOARD_AND_MEDIA_READY ->
                res.getString(R.string.hid_transport_summary_full_ready)
            HidTransportPhase.ERROR -> res.getString(R.string.hid_transport_summary_error)
        }
        val detail = buildDetail(probe, res)
        return HidTransportUiState(
            phase = probe.phase,
            summary = summary,
            detail = detail,
            keyboardDevicePath = probe.keyboardPath,
            consumerDevicePath = probe.consumerPath,
            canSendKeyboard = probe.keyboardWritable,
            canSendMedia = probe.consumerWritable,
            lastError = probe.lastError,
        )
    }

    private fun buildDetail(probe: HidGadgetSession.ProbeResult, res: Resources): String {
        val base = res.getString(R.string.hid_transport_detail_default)
        val err = probe.lastError?.let { "\n${res.getString(R.string.hid_transport_last_error, it)}" } ?: ""
        val paths = "\n${res.getString(R.string.hid_transport_paths, probe.keyboardPath, probe.consumerPath)}"
        val probeLine = "\n${res.getString(
            R.string.hid_transport_nodes_line,
            nodeStateLabel(probe.keyboardExists, probe.keyboardWritable, res),
            nodeStateLabel(probe.consumerExists, probe.consumerWritable, res),
        )}"
        return base + paths + probeLine + err
    }

    private fun nodeStateLabel(exists: Boolean, writable: Boolean, res: Resources): String = when {
        !exists -> res.getString(R.string.hid_node_state_missing)
        writable -> res.getString(R.string.hid_node_state_writable)
        else -> res.getString(R.string.hid_node_state_present_blocked)
    }
}
