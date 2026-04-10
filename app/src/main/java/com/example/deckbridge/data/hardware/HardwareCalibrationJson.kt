package com.example.deckbridge.data.hardware

import com.example.deckbridge.domain.hardware.HardwareCalibrationConfig
import com.example.deckbridge.domain.hardware.KnobCalibration
import com.example.deckbridge.domain.hardware.PadCell
import org.json.JSONArray
import org.json.JSONObject

object HardwareCalibrationJson {

    fun encode(config: HardwareCalibrationConfig): String {
        val json = JSONObject()
        json.put("v", config.version)
        json.put("descriptor", config.deviceDescriptor ?: JSONObject.NULL)
        val pad = JSONObject()
        config.padKeyCodes.forEach { (code, cell) ->
            pad.put(code.toString(), "${cell.row},${cell.col}")
        }
        json.put("pad", pad)
        val knobs = JSONArray()
        config.knobs.sortedBy { it.index }.forEach { k ->
            val o = JSONObject()
            o.put("i", k.index)
            val rk = JSONArray()
            k.rotateKeyCodes.sorted().forEach { rk.put(it) }
            o.put("rotate", rk)
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
                val rotate = mutableSetOf<Int>()
                val rj = o.optJSONArray("rotate")
                if (rj != null) {
                    for (j in 0 until rj.length()) rotate.add(rj.getInt(j))
                }
                val press = if (o.has("press") && !o.isNull("press")) o.optInt("press") else null
                val mf = mutableSetOf<String>()
                val mj = o.optJSONArray("motion")
                if (mj != null) {
                    for (j in 0 until mj.length()) mf.add(mj.getString(j))
                }
                knobs.add(
                    KnobCalibration(
                        index = idx,
                        rotateKeyCodes = rotate,
                        pressKeyCode = press?.takeIf { it > 0 },
                        motionFingerprints = mf,
                    ),
                )
            }
            while (knobs.size < 3) {
                knobs.add(KnobCalibration(index = knobs.size))
            }
            HardwareCalibrationConfig(
                version = version,
                deviceDescriptor = descriptor,
                padKeyCodes = padMap,
                knobs = knobs.sortedBy { it.index }.take(3),
            )
        } catch (_: Exception) {
            null
        }
    }
}
