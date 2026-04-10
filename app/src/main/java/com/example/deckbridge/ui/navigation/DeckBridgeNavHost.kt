package com.example.deckbridge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.deckbridge.DeckBridgeApplication
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.deckbridge.ui.home.HomeScreen
import com.example.deckbridge.ui.home.MainViewModel

object DeckBridgeDestinations {
    const val HOME = "home"
}

@Composable
fun DeckBridgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = DeckBridgeDestinations.HOME,
        modifier = modifier,
    ) {
        composable(DeckBridgeDestinations.HOME) {
            val app = LocalContext.current.applicationContext as DeckBridgeApplication
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(app.repository))
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onDeckButtonTapped = viewModel::onDeckButtonTapped,
                onHostPlatformSelected = viewModel::onHostPlatformSelected,
            )
        }
    }
}
