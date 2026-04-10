package com.example.deckbridge.host

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.deckbridge.domain.model.HostPlatform

/**
 * Best-effort host OS detection for a phone tethered as **USB device** to a PC.
 *
 * Standard Android exposes **no reliable API** for “Windows vs macOS” on the upstream host.
 * We use USB connection stickiness + honest UNKNOWN, and reserve stronger signals for future
 * companion tooling (serial, AOA vendor messages, etc.).
 */
object HostOsDetector {

    /**
     * Same value as [android.hardware.usb.UsbManager.ACTION_USB_STATE]; literal avoids
     * unresolved stubs on some compileSdk revisions.
     */
    const val USB_STATE_INTENT_ACTION: String = "android.hardware.usb.action.USB_STATE"

    data class Result(
        val platform: HostPlatform,
        val usbConnectedToHost: Boolean,
        val detailKey: DetailKey,
    )

    enum class DetailKey {
        USB_NOT_CONNECTED,
        USB_CONNECTED_OS_UNKNOWN,
    }

    fun peekUsbConnected(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    null,
                    IntentFilterCompat.usbState(),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                @Suppress("DEPRECATION")
                context.applicationContext.registerReceiver(null, IntentFilterCompat.usbState())
            }
            intent?.getBooleanExtra("connected", false) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun detect(usbConnected: Boolean): Result {
        if (!usbConnected) {
            return Result(
                platform = HostPlatform.UNKNOWN,
                usbConnectedToHost = false,
                detailKey = DetailKey.USB_NOT_CONNECTED,
            )
        }
        return Result(
            platform = HostPlatform.UNKNOWN,
            usbConnectedToHost = true,
            detailKey = DetailKey.USB_CONNECTED_OS_UNKNOWN,
        )
    }
}

private object IntentFilterCompat {
    fun usbState() = IntentFilter(HostOsDetector.USB_STATE_INTENT_ACTION)
}
