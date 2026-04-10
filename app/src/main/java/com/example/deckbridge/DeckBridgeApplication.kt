package com.example.deckbridge

import android.app.Application
import com.example.deckbridge.actions.HidTransportDispatcher
import com.example.deckbridge.actions.LoggingActionDispatcher
import com.example.deckbridge.hid.HidGadgetSession
import com.example.deckbridge.data.preferences.deckBridgePreferences
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.data.repository.DeckBridgeRepositoryImpl
import com.example.deckbridge.logging.DeckBridgeLog
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
        DeckBridgeLog.state("Application onCreate · DeckBridge")
        val hidGadgetSession = HidGadgetSession()
        val loggingDispatcher = LoggingActionDispatcher()
        val hidTransportDispatcher = HidTransportDispatcher(hidGadgetSession, loggingDispatcher)
        repository = DeckBridgeRepositoryImpl(
            appContext = this,
            externalScope = applicationScope,
            hidTransportDispatcher = hidTransportDispatcher,
            hidGadgetSession = hidGadgetSession,
            dataStore = deckBridgePreferences(),
        )
        repository.refreshAttachedKeyboards()
    }
}
