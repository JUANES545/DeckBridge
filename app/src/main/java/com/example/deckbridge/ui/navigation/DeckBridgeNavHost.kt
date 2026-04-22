package com.example.deckbridge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.provider.Settings
import com.example.deckbridge.DeckBridgeApplication
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.example.deckbridge.ui.calibration.CalibrationScreen
import com.example.deckbridge.ui.connect.PcConnectionHomeScreen
import com.example.deckbridge.ui.connect.PcConnectionViewModel
import com.example.deckbridge.ui.connect.QrScanScreen
import com.example.deckbridge.ui.deck.GridButtonEditScreen
import com.example.deckbridge.ui.deck.GridButtonEditViewModel
import com.example.deckbridge.ui.deck.KnobEditScreen
import com.example.deckbridge.ui.deck.KnobEditViewModel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.ui.home.HomeScreen
import com.example.deckbridge.ui.home.MainViewModel
import com.example.deckbridge.logging.DeckBridgeLog
import com.example.deckbridge.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.collect

object DeckBridgeDestinations {
    const val HOME = "home"
    const val CALIBRATION = "calibration"
    const val SETTINGS = "settings"
    const val GRID_EDIT = "grid_edit/{buttonId}"
    const val KNOB_EDIT = "knob_edit/{knobId}"
    /** Nested graph: `0` = first-run / gate flow, `1` = “add another computer” from Settings. */
    const val CONNECT_GRAPH = "connect_graph/{addHostFlow}"
    const val CONNECT_HOME = "connect_home"
    const val CONNECT_QR = "connect_qr"

    fun gridEditRoute(buttonId: String): String = "grid_edit/$buttonId"

    fun knobEditRoute(knobId: String): String = "knob_edit/$knobId"

    fun connectGraphRoute(addAnotherHost: Boolean): String =
        "connect_graph/${if (addAnotherHost) 1 else 0}"
}

@Composable
fun DeckBridgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = DeckBridgeDestinations.HOME,
) {
    val app = LocalContext.current.applicationContext as DeckBridgeApplication
    val repository = app.repository
    val updateManager = app.updateManager
    val gateActive by repository.initialConnectGateActive.collectAsStateWithLifecycle()
    val onLeaveConnectionGate = {
        repository.markSkipInitialPcConnect(true)
    }

    LaunchedEffect(Unit) {
        if (repository.consumePostOnboardingOpenPcConnect()) {
            navController.navigate(DeckBridgeDestinations.connectGraphRoute(addAnotherHost = false)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(DeckBridgeDestinations.HOME) {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(repository, updateManager))
            val state by viewModel.state.collectAsStateWithLifecycle()
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // Auto-retry download if user returns from "Install unknown apps" Settings screen
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED) viewModel.retryUpdateAfterPermissionGrant()
            }
            val defaultKnobIds = remember { listOf("knob_top", "knob_middle", "knob_bottom") }
            HomeScreen(
                state = state,
                updateState = updateState,
                onUpdateTapped = { viewModel.onUpdateTapped(context) },
                onUpdatePermissionTapped = { viewModel.onUpdatePermissionTapped(context) },
                onInstallTapped = { uri -> viewModel.onInstallTapped(context, uri) },
                onUpdateDismissed = viewModel::onUpdateDismissed,
                onDeckButtonTapped = viewModel::onDeckButtonTapped,
                onDeckButtonLongPress = { buttonId ->
                    navController.navigate(DeckBridgeDestinations.gridEditRoute(buttonId))
                },
                onMirrorKnobTouchRotate = viewModel::onMirrorKnobTouchRotate,
                onMirrorKnobLongPress = { knobIndex ->
                    if (knobIndex in 0..2) {
                        val knobId = state.deckKnobs.knobAt(knobIndex)?.id ?: defaultKnobIds[knobIndex]
                        navController.navigate(DeckBridgeDestinations.knobEditRoute(knobId))
                    }
                },
                onHostPlatformSelected = viewModel::onHostPlatformSelected,
                onOpenSettings = { navController.navigate(DeckBridgeDestinations.SETTINGS) },
                onGoToConnect = {
                    navController.navigate(DeckBridgeDestinations.connectGraphRoute(addAnotherHost = false)) {
                        launchSingleTop = true
                    }
                },
                onSetActivePage = viewModel::setActiveDeckPage,
                onAddPage = viewModel::addDeckPage,
                onDuplicatePage = { viewModel.duplicateDeckPage(state.activeDeckPageIndex) },
                onDeletePage = { viewModel.deleteDeckPage(state.activeDeckPageIndex) },
                onReorderPage = viewModel::reorderDeckPages,
                onUpdatePageName = { index, name -> viewModel.updateDeckPageName(index, name) },
            )
        }
        composable(DeckBridgeDestinations.SETTINGS) {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(repository, updateManager))
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            SettingsScreen(
                state = state,
                onNavigateBack = { navController.popBackStack() },
                onOpenAddAnotherHost = {
                    DeckBridgeLog.state("nav Settings → connect (add another host, addHostFlow=1)")
                    navController.navigate(DeckBridgeDestinations.connectGraphRoute(addAnotherHost = true)) {
                        launchSingleTop = true
                    }
                },
                onRefreshKeyboards = { viewModel.refreshAttachedKeyboards() },
                onHostAutoDetectChanged = viewModel::setHostAutoDetect,
                onApplyWindowsEndpoint = { h, p -> viewModel.applyLanEndpointForPlatform(HostPlatform.WINDOWS, h, p) },
                onTestWindowsHealth = { viewModel.testLanHealthForPlatform(HostPlatform.WINDOWS) },
                onForgetWindowsLink = { viewModel.forgetLanLinkForPlatform(HostPlatform.WINDOWS) },
                onApplyMacEndpoint = { h, p -> viewModel.applyLanEndpointForPlatform(HostPlatform.MAC, h, p) },
                onTestMacHealth = { viewModel.testLanHealthForPlatform(HostPlatform.MAC) },
                onForgetMacLink = { viewModel.forgetLanLinkForPlatform(HostPlatform.MAC) },
                onSetMacSlotChannel = viewModel::setMacSlotChannel,
                onOpenHardwareCalibration = { navController.navigate(DeckBridgeDestinations.CALIBRATION) },
                onAnimatedBackgroundModeChanged = viewModel::setAnimatedBackgroundMode,
                onAnimatedBackgroundThemeChanged = viewModel::setAnimatedBackgroundTheme,
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                onKeepKeyboardAwakeChanged = viewModel::setKeepKeyboardAwake,
            )
        }
        composable(DeckBridgeDestinations.CALIBRATION) {
            CalibrationScreen(
                onNavigateBack = {
                    repository.cancelHardwareCalibration()
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = DeckBridgeDestinations.GRID_EDIT,
            arguments = listOf(
                navArgument("buttonId") { type = NavType.StringType },
            ),
        ) { entry ->
            val buttonId = entry.arguments?.getString("buttonId") ?: return@composable
            val gridVm: GridButtonEditViewModel = viewModel(
                key = "grid_edit_$buttonId",
                factory = GridButtonEditViewModel.factory(repository, buttonId),
            )
            val mainVm: MainViewModel = viewModel(factory = MainViewModel.factory(repository, updateManager))
            val deckState by mainVm.state.collectAsStateWithLifecycle()
            GridButtonEditScreen(
                viewModel = gridVm,
                hostPlatform = deckState.hostPlatform,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = DeckBridgeDestinations.KNOB_EDIT,
            arguments = listOf(
                navArgument("knobId") { type = NavType.StringType },
            ),
        ) { entry ->
            val knobId = entry.arguments?.getString("knobId") ?: return@composable
            val knobVm: KnobEditViewModel = viewModel(
                key = "knob_edit_$knobId",
                factory = KnobEditViewModel.factory(repository, knobId),
            )
            val mainVm: MainViewModel = viewModel(factory = MainViewModel.factory(repository, updateManager))
            val deckState by mainVm.state.collectAsStateWithLifecycle()
            KnobEditScreen(
                viewModel = knobVm,
                hostPlatform = deckState.hostPlatform,
                onBack = { navController.popBackStack() },
            )
        }
        navigation(
            route = DeckBridgeDestinations.CONNECT_GRAPH,
            startDestination = DeckBridgeDestinations.CONNECT_HOME,
            arguments = listOf(
                navArgument("addHostFlow") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) {
            composable(DeckBridgeDestinations.CONNECT_HOME) { entry: NavBackStackEntry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry(DeckBridgeDestinations.CONNECT_GRAPH) }
                val connectVm: PcConnectionViewModel = viewModel(
                    viewModelStoreOwner = graphEntry,
                    factory = PcConnectionViewModel.factory(repository, graphEntry),
                )
                PcConnectionHomeScreen(
                    viewModel = connectVm,
                    onBack = {
                        if (gateActive) {
                            onLeaveConnectionGate()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onOpenQr = { navController.navigate(DeckBridgeDestinations.CONNECT_QR) },
                    onContinueWithoutPcLink = if (gateActive) onLeaveConnectionGate else null,
                )
            }
            composable(DeckBridgeDestinations.CONNECT_QR) { entry: NavBackStackEntry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry(DeckBridgeDestinations.CONNECT_GRAPH) }
                val connectVm: PcConnectionViewModel = viewModel(
                    viewModelStoreOwner = graphEntry,
                    factory = PcConnectionViewModel.factory(repository, graphEntry),
                )
                LaunchedEffect(connectVm) {
                    connectVm.qrNavOut.collect {
                        navController.popBackStack()
                        connectVm.clearQrPhaseMessage()
                    }
                }
                QrScanScreen(
                    viewModel = connectVm,
                    onBack = { navController.popBackStack() },
                    onDecoded = { raw -> connectVm.submitQrScan(raw) },
                )
            }
        }
    }
}
