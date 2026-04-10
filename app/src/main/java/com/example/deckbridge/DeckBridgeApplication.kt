package com.example.deckbridge

import android.app.Application
import com.example.deckbridge.actions.LoggingActionDispatcher
import com.example.deckbridge.data.preferences.deckBridgePreferences
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.data.repository.DeckBridgeRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DeckBridgeApplication : Application() {

    /** Shared scope for deck highlight timers and simulated action dispatch. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: DeckBridgeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DeckBridgeRepositoryImpl(
            appContext = this,
            externalScope = applicationScope,
            actionDispatcher = LoggingActionDispatcher(),
            dataStore = deckBridgePreferences(),
        )
        repository.refreshAttachedKeyboards()
    }
}
