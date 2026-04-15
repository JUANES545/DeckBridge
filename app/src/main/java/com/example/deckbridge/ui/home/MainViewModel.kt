@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.deckbridge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class MainViewModel(
    private val repository: DeckBridgeRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.appState

    /**
     * LAN delivery with a saved host but no successful `/health` yet — shown after a short delay to avoid flicker on resume.
     */
    val showLanLinkLostDialog: StateFlow<Boolean> = repository.appState
        .map { s ->
            s.hostDeliveryChannel == HostDeliveryChannel.LAN &&
                s.lanServerHost.isNotBlank() &&
                (s.lanHealthOk != true || !s.lanTrustOk)
        }
        .distinctUntilChanged()
        .transformLatest { lost ->
            if (lost) {
                delay(1_000L)
                emit(true)
            } else {
                emit(false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onDeckButtonTapped(buttonId: String) {
        repository.triggerDeckButton(buttonId, ButtonTriggerSource.TOUCH)
    }

    fun onMirrorKnobTouchRotate(knobIndex: Int, clockwise: Boolean) {
        repository.onMirrorKnobTouchRotate(knobIndex, clockwise)
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

    fun setHidPcModeEnabled(enabled: Boolean) {
        repository.setHidPcModeEnabled(enabled)
    }

    fun setHostDeliveryChannel(channel: HostDeliveryChannel) {
        repository.setHostDeliveryChannel(channel)
    }

    fun applyLanEndpoint(host: String, port: Int) {
        repository.setLanEndpoint(host, port)
    }

    fun testLanHealth() {
        repository.testLanHealth()
    }

    fun forgetTrustedLanHostLink() {
        repository.forgetTrustedLanHostLink()
    }

    fun setAnimatedBackgroundMode(mode: AnimatedBackgroundMode) {
        repository.setAnimatedBackgroundMode(mode)
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
