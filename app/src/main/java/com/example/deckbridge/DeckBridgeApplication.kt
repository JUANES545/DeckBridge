package com.example.deckbridge

import android.app.Application
import com.example.deckbridge.actions.HostDeliveryRouter
import com.example.deckbridge.actions.HidTransportDispatcher
import com.example.deckbridge.actions.LoggingActionDispatcher
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.hid.HidGadgetSession
import com.example.deckbridge.lan.LanHostClient
import com.example.deckbridge.lan.LanTransportDispatcher
import com.example.deckbridge.mac.MacBridgeDispatcher
import com.example.deckbridge.mac.MacBridgeServer
import com.example.deckbridge.data.preferences.deckBridgePreferences
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.data.repository.DeckBridgeRepositoryImpl
import com.example.deckbridge.logging.DeckBridgeLog
import com.example.deckbridge.logging.SessionFileLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference

class DeckBridgeApplication : Application() {

    /** Shared scope for deck highlight timers and simulated action dispatch. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: DeckBridgeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        SessionFileLog.init(this)
        DeckBridgeLog.state("Application onCreate · DeckBridge sessionFile=${SessionFileLog.currentFileOrNull()?.absolutePath}")
        val hidGadgetSession = HidGadgetSession()
        val loggingDispatcher = LoggingActionDispatcher()
        val hidTransportDispatcher = HidTransportDispatcher(hidGadgetSession, loggingDispatcher)
        val lanHostClient = LanHostClient()
        val lanTransportDispatcher = LanTransportDispatcher(lanHostClient, loggingDispatcher)
        val macBridgeServer = MacBridgeServer()
        val macBridgeDispatcher = MacBridgeDispatcher(macBridgeServer)
        val hostDeliveryChannelRef = AtomicReference(HostDeliveryChannel.LAN)
        val hostDeliveryRouter = HostDeliveryRouter(
            hostDeliveryChannelRef,
            lanTransportDispatcher,
            hidTransportDispatcher,
            macBridgeDispatcher,
        )
        repository = DeckBridgeRepositoryImpl(
            appContext = this,
            externalScope = applicationScope,
            hostDeliveryRouter = hostDeliveryRouter,
            lanHostClient = lanHostClient,
            hidTransportDispatcher = hidTransportDispatcher,
            hidGadgetSession = hidGadgetSession,
            macBridgeServer = macBridgeServer,
            dataStore = deckBridgePreferences(),
        )
        repository.refreshAttachedKeyboards()
    }

    override fun onTrimMemory(level: Int) {
        SessionFileLog.flush()
        super.onTrimMemory(level)
    }
}
