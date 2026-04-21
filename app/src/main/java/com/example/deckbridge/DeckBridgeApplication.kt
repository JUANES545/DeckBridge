package com.example.deckbridge

import android.app.Application
import com.example.deckbridge.actions.HostDeliveryRouter
import com.example.deckbridge.actions.LoggingActionDispatcher
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.lan.LanCircuitBreaker
import com.example.deckbridge.lan.LanHostClient
import com.example.deckbridge.lan.LanTransportDispatcher
import com.example.deckbridge.mac.MacBridgeDispatcher
import com.example.deckbridge.mac.MacBridgeServer
import com.example.deckbridge.data.preferences.deckBridgePreferences
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.data.repository.DeckBridgeRepositoryImpl
import com.example.deckbridge.logging.DeckBridgeLog
import com.example.deckbridge.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference

class DeckBridgeApplication : Application() {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: DeckBridgeRepository
        private set

    lateinit var updateManager: AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        DeckBridgeLog.state("Application onCreate · DeckBridge")

        val loggingDispatcher = LoggingActionDispatcher()

        // One LAN client + dispatcher per platform slot
        val winLanClient = LanHostClient()
        val macLanClient = LanHostClient()
        val winCircuitBreaker = LanCircuitBreaker()
        val macCircuitBreaker = LanCircuitBreaker()
        val winLanDispatcher = LanTransportDispatcher(winLanClient, loggingDispatcher, winCircuitBreaker)
        val macLanDispatcher = LanTransportDispatcher(macLanClient, loggingDispatcher, macCircuitBreaker)

        val macBridgeServer = MacBridgeServer()
        val macBridgeDispatcher = MacBridgeDispatcher(macBridgeServer)

        // Router starts pointing to Windows LAN (default active slot = WINDOWS)
        val channelRef = AtomicReference(HostDeliveryChannel.LAN)
        val activeLanRef = AtomicReference<LanTransportDispatcher>(winLanDispatcher)
        val hostDeliveryRouter = HostDeliveryRouter(channelRef, activeLanRef, macBridgeDispatcher)

        repository = DeckBridgeRepositoryImpl(
            appContext = this,
            externalScope = applicationScope,
            hostDeliveryRouter = hostDeliveryRouter,
            winLanClient = winLanClient,
            macLanClient = macLanClient,
            winLanDispatcher = winLanDispatcher,
            macLanDispatcher = macLanDispatcher,
            winCircuitBreaker = winCircuitBreaker,
            macCircuitBreaker = macCircuitBreaker,
            macBridgeServer = macBridgeServer,
            dataStore = deckBridgePreferences(),
        )
        repository.refreshAttachedKeyboards()

        updateManager = AppUpdateManager(appContext = this, scope = applicationScope)
        updateManager.checkForUpdate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}
