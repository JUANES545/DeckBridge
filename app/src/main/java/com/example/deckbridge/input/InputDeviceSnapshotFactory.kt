package com.example.deckbridge.input

import android.os.Build
import android.view.InputDevice
import com.example.deckbridge.domain.model.InputDeviceSnapshot
import com.example.deckbridge.domain.model.KeyboardInputClassification

internal object InputDeviceSnapshotFactory {

    fun from(device: InputDevice?): InputDeviceSnapshot {
        if (device == null) {
            return InputDeviceSnapshot(
                deviceId = -1,
                name = "Unknown",
                descriptor = null,
                vendorId = null,
                productId = null,
                sourcesFlags = 0,
                sourcesLabel = "—",
                isExternal = false,
                isVirtual = false,
            )
        }
        val vendorId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            device.vendorId.takeIf { it != 0 }
        } else {
            null
        }
        val productId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            device.productId.takeIf { it != 0 }
        } else {
            null
        }

        return InputDeviceSnapshot(
            deviceId = device.id,
            name = device.name ?: "Unnamed",
            descriptor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                device.descriptor
            } else {
                null
            },
            vendorId = vendorId,
            productId = productId,
            sourcesFlags = device.sources,
            sourcesLabel = describeSources(device.sources),
            isExternal = try {
                device.isExternal
            } catch (_: Exception) {
                false
            },
            isVirtual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                device.isVirtual
            } else {
                false
            },
        )
    }

    fun classify(snapshot: InputDeviceSnapshot): KeyboardInputClassification {
        if (snapshot.deviceId < 0) return KeyboardInputClassification.NON_KEYBOARD_OR_UNKNOWN
        val hasKeyboardSource = snapshot.sourcesFlags and InputDevice.SOURCE_KEYBOARD != 0
        if (!hasKeyboardSource) return KeyboardInputClassification.NON_KEYBOARD_OR_UNKNOWN
        if (snapshot.isVirtual) return KeyboardInputClassification.SOFTWARE_OR_VIRTUAL_KEYBOARD
        return if (snapshot.isExternal) {
            KeyboardInputClassification.EXTERNAL_HARDWARE_KEYBOARD
        } else {
            KeyboardInputClassification.BUILT_IN_HARDWARE_KEYBOARD
        }
    }

    private fun describeSources(sources: Int): String {
        val parts = mutableListOf<String>()
        if (sources and InputDevice.SOURCE_KEYBOARD != 0) parts += "keyboard"
        if (sources and InputDevice.SOURCE_MOUSE != 0) parts += "mouse"
        if (sources and InputDevice.SOURCE_TOUCHSCREEN != 0) parts += "touchscreen"
        if (sources and InputDevice.SOURCE_TOUCHPAD != 0) parts += "touchpad"
        if (sources and InputDevice.SOURCE_JOYSTICK != 0) parts += "joystick"
        if (sources and InputDevice.SOURCE_GAMEPAD != 0) parts += "gamepad"
        if (sources and InputDevice.SOURCE_STYLUS != 0) parts += "stylus"
        if (sources and InputDevice.SOURCE_DPAD != 0) parts += "dpad"
        return if (parts.isEmpty()) "none" else parts.joinToString(", ")
    }
}
