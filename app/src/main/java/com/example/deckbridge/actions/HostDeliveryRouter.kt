package com.example.deckbridge.actions

import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.lan.LanTransportDispatcher
import com.example.deckbridge.mac.MacBridgeDispatcher
import java.util.concurrent.atomic.AtomicReference

/**
 * Routes [dispatch] calls to the correct transport based on the active slot and its channel.
 *
 * - Windows slot → always [winLan]
 * - Mac slot, channel=LAN → [macLan]
 * - Mac slot, channel=MAC_BRIDGE → [mac]
 * - Any slot, USB_HID override → [hid]
 *
 * Both [channel] and [activeLan] are held in a single [AtomicReference] to a [DispatchTarget]
 * snapshot, so [dispatch] always reads a consistent pair — no TOCTOU race between reading the
 * channel and then reading the dispatcher.
 */
class HostDeliveryRouter(
    initialChannel: AtomicReference<HostDeliveryChannel>,
    initialLan: AtomicReference<LanTransportDispatcher>,
    private val hid: HidTransportDispatcher,
    private val mac: MacBridgeDispatcher,
) : ActionDispatcher {

    private data class DispatchTarget(
        val channel: HostDeliveryChannel,
        val lan: LanTransportDispatcher,
    )

    private val target = AtomicReference(
        DispatchTarget(initialChannel.get(), initialLan.get())
    )

    fun setActiveChannel(value: HostDeliveryChannel) {
        target.updateAndGet { it.copy(channel = value) }
    }

    fun setActiveLanDispatcher(dispatcher: LanTransportDispatcher) {
        target.updateAndGet { it.copy(lan = dispatcher) }
    }

    fun getChannel(): HostDeliveryChannel = target.get().channel

    override suspend fun dispatch(resolved: ResolvedAction): Result<Unit> {
        val snap = target.get()   // single atomic read — channel + lan are always consistent
        return when (snap.channel) {
            HostDeliveryChannel.LAN        -> snap.lan.dispatch(resolved)
            HostDeliveryChannel.USB_HID    -> hid.dispatch(resolved)
            HostDeliveryChannel.MAC_BRIDGE -> mac.dispatch(resolved)
        }
    }
}
