package com.example.deckbridge.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the device is on external power (USB / AC / wireless dock) or actively charging,
 * updated on [Intent.ACTION_BATTERY_CHANGED]. When [enabled] is false, returns false and does not register a receiver.
 */
@Composable
fun rememberDeviceCharging(enabled: Boolean): Boolean {
    if (!enabled) return false
    val context = LocalContext.current
    var charging by remember { mutableStateOf(readChargingNow(context)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                charging = isChargingFromBatteryIntent(intent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        charging = readChargingNow(context)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    return charging
}

private fun readChargingNow(context: Context): Boolean {
    val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    } else {
        @Suppress("DEPRECATION")
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    return isChargingFromBatteryIntent(sticky)
}

private fun isChargingFromBatteryIntent(intent: Intent?): Boolean {
    if (intent == null || intent.action != Intent.ACTION_BATTERY_CHANGED) return false
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    if (status == BatteryManager.BATTERY_STATUS_CHARGING) return true
    // Still on external power when the battery reports “full” — keep subtle motion while plugged in.
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return plugged != 0
}
