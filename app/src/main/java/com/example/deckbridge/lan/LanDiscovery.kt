package com.example.deckbridge.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.example.deckbridge.domain.model.LanDiscoveredAgent
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections

/**
 * Finds the DeckBridge PC agent on the same LAN via UDP broadcast (port [DISCOVERY_PORT]).
 * The Windows `server.py` must be running with its discovery thread active.
 *
 * On Android 6+, the datagram socket is bound to [ConnectivityManager.activeNetwork] so outbound
 * broadcast uses the same interface as Wi‑Fi (fixes “no reply” when the default route was wrong).
 */
object LanDiscovery {

    const val DISCOVERY_PORT: Int = 8766

    /**
     * Default UDP listen window for the PC agent list screen. Keep UI hint strings in sync (seconds).
     */
    const val SCAN_TOTAL_MS_DEFAULT: Long = 12_000L

    private const val MAGIC = "DECKBRIDGE_DISCOVER_v1"
    private const val SOCKET_TIMEOUT_MS = 3500
    private const val MAX_PACKET = 1024

    /** Resend discovery while listening (many routers / stacks drop the first UDP broadcast). */
    private const val SCAN_RESEND_INTERVAL_MS = 1_100L

    private fun magicPayload(): ByteArray = MAGIC.toByteArray(Charsets.UTF_8)

    private fun collectBroadcastTargets(): LinkedHashMap<String, Int> {
        val targets = LinkedHashMap<String, Int>()
        targets["255.255.255.255"] = DISCOVERY_PORT
        for (host in broadcastAddressesIpv4()) {
            targets[host] = DISCOVERY_PORT
        }
        return targets
    }

    /**
     * Binds [socket] to the process default network (Wi‑Fi when active) so UDP broadcast leaves the
     * correct interface. Safe no-op on API < 23 or if binding fails.
     */
    private fun bindDatagramSocketToActiveNetwork(context: Context, socket: DatagramSocket) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            if (network == null) {
                DeckBridgeLog.lan("discovery: no activeNetwork — check Wi-Fi / airplane mode")
                return
            }
            val caps = cm.getNetworkCapabilities(network)
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val eth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            if (!wifi && !eth) {
                DeckBridgeLog.lan(
                    "discovery: activeNetwork is not Wi-Fi/Ethernet (caps=$caps) — bind may still help for VPN/split",
                )
            }
            network.bindSocket(socket)
            DeckBridgeLog.lan("discovery: DatagramSocket bound to activeNetwork=$network wifi=$wifi")
        }.onFailure { e ->
            DeckBridgeLog.lan("discovery: bindSocket failed (${e.javaClass.simpleName}: ${e.message}) — continuing")
        }
    }

    private fun sendDiscoveryBurst(
        socket: DatagramSocket,
        payload: ByteArray,
        targets: Map<String, Int>,
        burstLabel: String,
    ) {
        for ((host, port) in targets) {
            try {
                val packet = DatagramPacket(
                    payload,
                    payload.size,
                    InetAddress.getByName(host),
                    port,
                )
                socket.send(packet)
                DeckBridgeLog.lan("discovery send $burstLabel → $host:$port ($MAGIC ${payload.size} B)")
            } catch (e: Exception) {
                DeckBridgeLog.lan("discovery send $burstLabel → $host:$port FAILED: ${e.message}")
            }
        }
    }

    private fun parseDiscoveredAgent(o: JSONObject, fallbackHttpPort: Int): LanDiscoveredAgent? {
        if (!o.optBoolean("ok", false)) return null
        val ip = o.optString("ip", "").trim()
        if (ip.isEmpty()) return null
        val httpPort = o.optInt("port", fallbackHttpPort).coerceIn(1, 65_535)
        val os = o.optString("agent_os", "").trim().takeIf { it.isNotEmpty() }
        return LanDiscoveredAgent(address = ip, httpPort = httpPort, displayName = null, agentOs = os)
    }

    /**
     * @param fallbackHttpPort used if the JSON response omits `port`
     * @return discovered agent or null if no agent answered in time
     */
    suspend fun tryDiscover(context: Context, fallbackHttpPort: Int): LanDiscoveredAgent? = withContext(Dispatchers.IO) {
        val payload = magicPayload()
        val targets = collectBroadcastTargets()
        DeckBridgeLog.lan(
            "discovery tryDiscover targets=${targets.keys.joinToString()} port=$DISCOVERY_PORT payload=$MAGIC",
        )
        DatagramSocket().use { socket ->
            bindDatagramSocketToActiveNetwork(context, socket)
            socket.broadcast = true
            socket.reuseAddress = true
            socket.soTimeout = SOCKET_TIMEOUT_MS
            repeat(3) { i ->
                sendDiscoveryBurst(socket, payload, targets, "burst${i + 1}")
                if (i < 2) delay(200)
            }
            val buf = ByteArray(MAX_PACKET)
            val recv = DatagramPacket(buf, buf.size)
            try {
                socket.receive(recv)
            } catch (_: SocketTimeoutException) {
                DeckBridgeLog.lan("discovery tryDiscover: timeout (${SOCKET_TIMEOUT_MS}ms) no UDP reply")
                return@withContext null
            }
            val json = String(recv.data, recv.offset, recv.length, Charsets.UTF_8).trim()
            DeckBridgeLog.lan("discovery tryDiscover: reply from ${recv.address?.hostAddress}:${recv.port} len=${recv.length} body=${json.take(200)}")
            val o = runCatching { JSONObject(json) }.getOrNull()
            if (o == null) {
                DeckBridgeLog.lan("discovery tryDiscover: JSON parse failed")
                return@withContext null
            }
            val agent = parseDiscoveredAgent(o, fallbackHttpPort)
            if (agent == null) {
                DeckBridgeLog.lan("discovery tryDiscover: invalid agent JSON")
                return@withContext null
            }
            DeckBridgeLog.lan(
                "discovery tryDiscover: OK ip=${agent.address} httpPort=${agent.httpPort} agent_os=${agent.agentOs ?: "—"}",
            )
            agent
        }
    }

    /**
     * Broadcasts periodically while listening until [totalListenMs] elapses.
     * Deduplicates by IP (last port wins).
     */
    suspend fun scanAgents(
        context: Context,
        fallbackHttpPort: Int,
        totalListenMs: Long = SCAN_TOTAL_MS_DEFAULT,
        perPollTimeoutMs: Int = 550,
    ): List<LanDiscoveredAgent> = withContext(Dispatchers.IO) {
        val tScan0 = SystemClock.elapsedRealtime()
        val deadline = tScan0 + totalListenMs
        val found = linkedMapOf<String, LanDiscoveredAgent>()
        val payload = magicPayload()
        val targets = collectBroadcastTargets()
        DeckBridgeLog.lan(
            "discovery scanAgents START listen=${totalListenMs}ms pollTimeout=${perPollTimeoutMs}ms " +
                "resend=${SCAN_RESEND_INTERVAL_MS}ms targets=${targets.keys.joinToString()} magic=$MAGIC",
        )
        DatagramSocket().use { socket ->
            bindDatagramSocketToActiveNetwork(context, socket)
            socket.broadcast = true
            socket.reuseAddress = true
            socket.soTimeout = perPollTimeoutMs.coerceAtLeast(100)
            var nextBurstAt = SystemClock.elapsedRealtime()
            var burstIndex = 0
            val buf = ByteArray(MAX_PACKET)
            while (SystemClock.elapsedRealtime() < deadline) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextBurstAt) {
                    burstIndex += 1
                    sendDiscoveryBurst(socket, payload, targets, "scan#$burstIndex")
                    nextBurstAt = now + SCAN_RESEND_INTERVAL_MS
                }
                val recv = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(recv)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val json = String(recv.data, recv.offset, recv.length, Charsets.UTF_8).trim()
                DeckBridgeLog.lan(
                    "discovery scanAgents: packet from ${recv.address?.hostAddress}:${recv.port} len=${recv.length} preview=${json.take(180)}",
                )
                val o = runCatching { JSONObject(json) }.getOrNull()
                if (o == null) {
                    DeckBridgeLog.lan("discovery scanAgents: skip (invalid JSON)")
                    continue
                }
                val agent = parseDiscoveredAgent(o, fallbackHttpPort) ?: run {
                    DeckBridgeLog.lan("discovery scanAgents: skip (invalid payload)")
                    continue
                }
                DeckBridgeLog.lan(
                    "discovery scanAgents: agent ip=${agent.address} http=${agent.httpPort} agent_os=${agent.agentOs ?: "—"}",
                )
                found[agent.address] = agent
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - tScan0
        if (found.isEmpty()) {
            DeckBridgeLog.lan(
                "discovery scanAgents END elapsed=${elapsed}ms agents=0 (UDP silent — Wi-Fi same LAN, AP isolation, PC UDP $DISCOVERY_PORT inbound)",
            )
        } else {
            DeckBridgeLog.lan("discovery scanAgents END elapsed=${elapsed}ms agents=${found.size} ips=${found.keys.joinToString()}")
        }
        found.values.toList()
    }

    private fun broadcastAddressesIpv4(): Set<String> {
        val out = linkedSetOf<String>()
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val bc = ia.broadcast ?: continue
                    val a = bc.hostAddress ?: continue
                    if (a != "0.0.0.0" && !a.contains(":")) {
                        out.add(a)
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (out.isNotEmpty()) {
            DeckBridgeLog.lan("discovery subnet broadcasts: ${out.joinToString()}")
        } else {
            DeckBridgeLog.lan("discovery subnet broadcasts: (none from NetworkInterface — still using 255.255.255.255)")
        }
        return out
    }
}
