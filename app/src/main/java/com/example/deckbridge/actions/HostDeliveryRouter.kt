package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.lan.LanTransportDispatcher
import com.example.deckbridge.mac.MacBridgeDispatcher
import java.util.concurrent.atomic.AtomicReference

/**
 * Routes [dispatch] to the LAN HTTP pipeline, USB gadget HID, or Mac bridge server,
 * based on user preference.
 */
class HostDeliveryRouter(
    private val channel: AtomicReference<HostDeliveryChannel>,
    private val lan: LanTransportDispatcher,
    private val hid: HidTransportDispatcher,
    private val mac: MacBridgeDispatcher,
) : ActionDispatcher {

    fun setChannel(value: HostDeliveryChannel) {
        channel.set(value)
    }

    fun getChannel(): HostDeliveryChannel = channel.get()

    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> = when (channel.get()) {
        HostDeliveryChannel.LAN -> lan.dispatch(resolved)
        HostDeliveryChannel.USB_HID -> hid.dispatch(resolved)
        HostDeliveryChannel.MAC_BRIDGE -> mac.dispatch(resolved)
    }
}
