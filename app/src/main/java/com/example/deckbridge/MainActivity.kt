package com.example.deckbridge

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.deckbridge.ui.navigation.DeckBridgeNavHost
import com.example.deckbridge.ui.theme.DeckBridgeTheme

class MainActivity : ComponentActivity() {
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
        appRepository().refreshAttachedKeyboards()
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
