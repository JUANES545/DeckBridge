package com.example.deckbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.deckbridge.host.HostOsDetector
import com.example.deckbridge.ui.navigation.DeckBridgeNavHost
import com.example.deckbridge.ui.theme.DeckBridgeTheme

class MainActivity : ComponentActivity() {

    private val usbStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appRepository().refreshHostAndTransport()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeckBridgeTheme {
                val navController = rememberNavController()
                DeckBridgeNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HostOsDetector.USB_STATE_INTENT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbStateReceiver, filter)
        }
        appRepository().refreshAttachedKeyboards()
        appRepository().refreshHostAndTransport()
    }

    override fun onPause() {
        try {
            unregisterReceiver(usbStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Not registered
        }
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        appRepository().notifyKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        appRepository().notifyGenericMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    private fun appRepository() = (application as DeckBridgeApplication).repository
}
