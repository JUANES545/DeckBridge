package com.example.deckbridge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(
    private val repository: DeckBridgeRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.appState

    fun onDeckButtonTapped(buttonId: String) {
        repository.triggerDeckButton(buttonId, ButtonTriggerSource.TOUCH)
    }

    fun onHostPlatformSelected(platform: HostPlatform) {
        repository.setHostPlatform(platform)
    }

    fun refreshAttachedKeyboards() {
        repository.refreshAttachedKeyboards()
    }

    fun setHostAutoDetect(enabled: Boolean) {
        repository.setHostAutoDetect(enabled)
    }

    fun refreshHostAndTransport() {
        repository.refreshHostAndTransport()
    }

    companion object {
        fun factory(repository: DeckBridgeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java))
                    return MainViewModel(repository) as T
                }
            }
    }
}
