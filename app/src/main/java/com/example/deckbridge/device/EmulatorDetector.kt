package com.example.deckbridge.device

import android.os.Build

/** Heuristic for AVD / SDK images — LAN IPs like 192.168.x.x often do not reach the host PC. */
object EmulatorDetector {

    fun isProbablyEmulator(): Boolean {
        val fp = Build.FINGERPRINT
        val model = Build.MODEL.lowercase()
        val manu = Build.MANUFACTURER.lowercase()
        val prod = Build.PRODUCT.lowercase()
        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            manu.contains("genymotion") ||
            prod.contains("sdk_gphone") ||
            prod.contains("google_sdk") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }
}
