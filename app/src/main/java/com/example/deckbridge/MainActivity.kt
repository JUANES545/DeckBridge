package com.example.deckbridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.deckbridge.service.DeckBridgeService
import com.example.deckbridge.ui.navigation.DeckBridgeDestinations
import com.example.deckbridge.ui.navigation.DeckBridgeNavHost
import com.example.deckbridge.ui.onboarding.OnboardingFlow
import com.example.deckbridge.logging.SessionFileLog
import com.example.deckbridge.ui.splash.BrandedSplashScreen
import com.example.deckbridge.ui.theme.DeckBridgeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var requestBtConnect: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once BLUETOOTH_CONNECT is granted, re-read the keyboard battery level immediately.
        requestBtConnect = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) appRepository().refreshAttachedKeyboards()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            DeckBridgeTheme {
                val repo = appRepository()
                val onboardingGate by repo.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)
                // Show splash until data is ready, but at least SPLASH_MIN_MS so the
                // animation has time to render. As soon as both conditions are met
                // (min time elapsed AND onboardingGate is not null) we fade out.
                var minElapsed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(700)
                    minElapsed = true
                }
                val showSplash = !minElapsed || onboardingGate == null
                Crossfade(
                    targetState = showSplash,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "deckbridge_splash",
                ) { splash ->
                    if (splash) {
                        BrandedSplashScreen(Modifier.fillMaxSize())
                    } else {
                        when (onboardingGate) {
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
                                val navController = rememberNavController()
                                DeckBridgeNavHost(
                                    navController = navController,
                                    modifier = Modifier.fillMaxSize(),
                                    startDestination = DeckBridgeDestinations.HOME,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stop background service — app is visible again.
        DeckBridgeService.stop(this)
        appRepository().refreshAttachedKeyboards()
        appRepository().refreshLanDiscoveryOnForeground()
        // BLUETOOTH_CONNECT is needed on API 31+ for keyboard battery level and keep-alive pings.
        // Request it proactively so the feature works without any extra UI step.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBtConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        SessionFileLog.flush()
        // Only start the background service when the app truly leaves the foreground.
        // Config changes (rotation, keyboard connect) also trigger onStop/onResume — skip those
        // to avoid the startForegroundService race condition on Samsung Exynos devices.
        if (!isChangingConfigurations) {
            DeckBridgeService.start(this)
        }
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
