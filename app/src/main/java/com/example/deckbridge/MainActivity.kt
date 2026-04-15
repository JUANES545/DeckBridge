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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.deckbridge.host.HostOsDetector
import com.example.deckbridge.ui.navigation.DeckBridgeDestinations
import com.example.deckbridge.ui.navigation.DeckBridgeNavHost
import com.example.deckbridge.ui.onboarding.OnboardingFlow
import com.example.deckbridge.ui.onboarding.OnboardingTheme
import com.example.deckbridge.logging.SessionFileLog
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
                val repo = appRepository()
                val onboardingGate by repo.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)
                when (onboardingGate) {
                    null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OnboardingTheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = OnboardingTheme.accent)
                        }
                    }
                    false -> {
                        OnboardingFlow(
                            onFinished = { repo.markOnboardingFinished() },
                            onRequestAddComputer = {
                                repo.requestPostOnboardingOpenPcConnect()
                                repo.markOnboardingFinished()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        val skip by repo.skipInitialPcConnect.collectAsStateWithLifecycle(initialValue = null)
                        val gate by repo.initialConnectGateActive.collectAsStateWithLifecycle(initialValue = false)
                        when (skip) {
                            null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(OnboardingTheme.background),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = OnboardingTheme.accent)
                                }
                            }
                            else -> {
                                key(gate) {
                                    val navController = rememberNavController()
                                    DeckBridgeNavHost(
                                        navController = navController,
                                        modifier = Modifier.fillMaxSize(),
                                        startDestination = if (gate) {
                                            DeckBridgeDestinations.connectGraphRoute(addAnotherHost = false)
                                        } else {
                                            DeckBridgeDestinations.HOME
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
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
        appRepository().refreshLanDiscoveryOnForeground()
    }

    override fun onPause() {
        try {
            unregisterReceiver(usbStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Not registered
        }
        super.onPause()
    }

    override fun onStop() {
        SessionFileLog.flush()
        super.onStop()
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
