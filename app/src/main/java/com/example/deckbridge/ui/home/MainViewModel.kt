package com.example.deckbridge.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.ButtonTriggerSource
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.update.AppUpdateManager
import com.example.deckbridge.update.UpdateState
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(
    private val repository: DeckBridgeRepository,
    private val updateManager: AppUpdateManager,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.appState
    val updateState: StateFlow<UpdateState> = updateManager.state

    fun onUpdateTapped(context: Context) = updateManager.startDownloadOrRequestPermission(context)
    fun onUpdatePermissionTapped(context: Context) {
        updateManager.openInstallPermissionSettings(context)
    }
    fun onInstallTapped(context: Context, uri: Uri) = updateManager.launchInstall(context, uri)
    fun onUpdateDismissed() = updateManager.dismiss()
    fun retryUpdateAfterPermissionGrant() = updateManager.retryAfterPermissionGrant()

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

    fun applyLanEndpointForPlatform(platform: HostPlatform, host: String, port: Int) {
        repository.setLanEndpointForPlatform(platform, host, port)
    }

    fun testLanHealthForPlatform(platform: HostPlatform) {
        repository.testLanHealthForPlatform(platform)
    }

    fun forgetLanLinkForPlatform(platform: HostPlatform) {
        repository.forgetTrustedLanHostLinkForPlatform(platform)
    }

    fun setMacSlotChannel(channel: HostDeliveryChannel) {
        repository.setMacSlotChannel(channel)
    }

    fun setAnimatedBackgroundMode(mode: AnimatedBackgroundMode) {
        repository.setAnimatedBackgroundMode(mode)
    }

    fun setAnimatedBackgroundTheme(theme: AnimatedBackgroundTheme) {
        repository.setAnimatedBackgroundTheme(theme)
    }

    companion object {
        fun factory(
            repository: DeckBridgeRepository,
            updateManager: AppUpdateManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java))
                    return MainViewModel(repository, updateManager) as T
                }
            }
    }
}
