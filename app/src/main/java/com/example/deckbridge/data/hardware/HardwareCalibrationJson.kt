package com.example.deckbridge.data.hardware

import android.view.KeyEvent
import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.KnobCalibration
import com.example.deckbridge.domain.hardware.PadCell
import org.json.JSONArray
import org.json.JSONObject

private const val JSON_VERSION = 2

object HardwareCalibrationJson {

    fun encode(config: HardwareCalibrationConfig): String {
        val json = JSONObject()
        json.put("v", JSON_VERSION)
        json.put("descriptor", config.deviceDescriptor ?: JSONObject.NULL)
        if (config.deviceVendorId != null && config.deviceVendorId != 0) {
            json.put("vendor_id", config.deviceVendorId)
        } else {
            json.put("vendor_id", JSONObject.NULL)
        }
        if (config.deviceProductId != null && config.deviceProductId != 0) {
            json.put("product_id", config.deviceProductId)
        } else {
            json.put("product_id", JSONObject.NULL)
        }
        val pad = JSONObject()
        config.padKeyCodes.forEach { (code, cell) ->
            pad.put(code.toString(), "${cell.row},${cell.col}")
        }
        json.put("pad", pad)
        val knobs = JSONArray()
        config.knobs.sortedBy { it.index }.forEach { k ->
            val o = JSONObject()
            o.put("i", k.index)
            val ccw = JSONArray()
            k.rotateCcwKeyCodes.sorted().forEach { ccw.put(it) }
            o.put("rotate_ccw", ccw)
            val cw = JSONArray()
            k.rotateCwKeyCodes.sorted().forEach { cw.put(it) }
            o.put("rotate_cw", cw)
            if (k.pressKeyCode != null) o.put("press", k.pressKeyCode) else o.put("press", JSONObject.NULL)
            val mf = JSONArray()
            k.motionFingerprints.sorted().forEach { mf.put(it) }
            o.put("motion", mf)
            knobs.put(o)
        }
        json.put("knobs", knobs)
        return json.toString()
    }

    fun decode(raw: String?): HardwareCalibrationConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            val version = json.optInt("v", 1)
            val descriptor = json.optString("descriptor", "").takeIf { it.isNotBlank() && it != "null" }
            val vendorId = if (json.has("vendor_id") && !json.isNull("vendor_id")) {
                json.optInt("vendor_id", 0).takeIf { it != 0 }
            } else {
                null
            }
            val productId = if (json.has("product_id") && !json.isNull("product_id")) {
                json.optInt("product_id", 0).takeIf { it != 0 }
            } else {
                null
            }
            val padObj = json.optJSONObject("pad") ?: JSONObject()
            val padMap = mutableMapOf<Int, PadCell>()
            padObj.keys().forEach { key ->
                val parts = padObj.getString(key).split(",")
                if (parts.size == 2) {
                    padMap[key.toInt()] = PadCell(parts[0].toInt(), parts[1].toInt())
                }
            }
            val knobsArr = json.optJSONArray("knobs") ?: JSONArray()
            val knobs = mutableListOf<KnobCalibration>()
            for (i in 0 until knobsArr.length()) {
                val o = knobsArr.getJSONObject(i)
                val idx = o.optInt("i", i)
                val press = if (o.has("press") && !o.isNull("press")) o.optInt("press") else null
                val mf = mutableSetOf<String>()
                val mj = o.optJSONArray("motion")
                if (mj != null) {
                    for (j in 0 until mj.length()) mf.add(mj.getString(j))
                }

                val ccw = mutableSetOf<Int>()
                val cw = mutableSetOf<Int>()
                val ccwArr = o.optJSONArray("rotate_ccw")
                val cwArr = o.optJSONArray("rotate_cw")
                if (ccwArr != null || cwArr != null) {
                    if (ccwArr != null) for (j in 0 until ccwArr.length()) ccw.add(ccwArr.getInt(j))
                    if (cwArr != null) for (j in 0 until cwArr.length()) cw.add(cwArr.getInt(j))
                } else {
                    migrateV1RotateKeys(o.optJSONArray("rotate"), ccw, cw)
                }

                knobs.add(
                    KnobCalibration(
                        index = idx,
                        rotateCcwKeyCodes = ccw,
                        rotateCwKeyCodes = cw,
                        pressKeyCode = press?.takeIf { it > 0 },
                        motionFingerprints = mf,
                    ),
                )
            }
            while (knobs.size < 3) {
                knobs.add(KnobCalibration(index = knobs.size))
            }
            HardwareCalibrationConfig(
                version = version.coerceIn(1, JSON_VERSION),
                deviceDescriptor = descriptor,
                deviceVendorId = vendorId,
                deviceProductId = productId,
                padKeyCodes = padMap,
                knobs = knobs.sortedBy { it.index }.take(3),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** v1 JSON had a single `rotate` array — split volume keys; unknown keys go to both sides (ambiguous). */
    private fun migrateV1RotateKeys(rotate: JSONArray?, ccw: MutableSet<Int>, cw: MutableSet<Int>) {
        if (rotate == null) return
        for (j in 0 until rotate.length()) {
            val key = rotate.getInt(j)
            when (key) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> ccw.add(key)
                KeyEvent.KEYCODE_VOLUME_UP -> cw.add(key)
                else -> {
                    ccw.add(key)
                    cw.add(key)
                }
            }
        }
    }
}
