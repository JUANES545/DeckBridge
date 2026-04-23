package com.example.deckbridge.mac

import com.example.deckbridge.lan.LanActionJsonFactory
import com.example.deckbridge.lan.LanHostClient.Companion.HEADER_PAIR_TOKEN
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight HTTP server (port [PORT]) for the inverted Mac bridge architecture.
 *
 * Instead of Android connecting to the Mac agent (blocked by GlobalProtect VPN on corporate Macs),
 * the Mac agent connects *outbound* to this server — transparent to CrowdStrike / GlobalProtect.
 *
 * Protocol:
 *   GET  /health          → {"ok":true,"device":"android","bridge_port":8767,"paired":<bool>}
 *   GET  /action/next     → long-poll (≤55 s); returns {"ok":true,"action":{...}|null}
 *                           Requires X-DeckBridge-Pair-Token header when paired.
 *   POST /state           → Mac agent pushes {"audio_outputs":[...]} on connect and on change.
 *                           Fires [onStateUpdate] callback; returns {"ok":true}.
 *
 * Pairing: uses the existing MAC-slot pair token from DataStore (established via QR flow,
 * optionally over ADB reverse for first-time corporate-Mac setup).
 *
 * Thread model: one daemon thread per accepted connection (short-lived for /health,
 * long-lived for /action/next).  ServerSocket accept loop runs in an IO coroutine.
 */
class MacBridgeServer {

    private val pairToken = AtomicReference<String?>(null)
    private val lastPollMs = AtomicLong(0L)
    private val lastClientIp = AtomicReference<String?>(null)

    /** Update the MAC pair token used to authenticate polling Mac agents. */
    fun setPairToken(token: String?) {
        pairToken.set(token?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** IP of the last Mac agent that polled /action/next, or null if none ever connected. */
    fun peekClientIp(): String? = lastClientIp.get()

    /** True when a Mac agent polled within the last [CLIENT_ALIVE_WINDOW_MS]. */
    fun isClientAlive(): Boolean =
        lastPollMs.get() > 0L &&
            System.currentTimeMillis() - lastPollMs.get() < CLIENT_ALIVE_WINDOW_MS

    private val actionQueue = ArrayBlockingQueue<String>(64)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val droppedActionCount = AtomicInteger(0)
    private val discoverySocket = AtomicReference<DatagramSocket?>(null)

    /** Called when the Mac agent POSTs /state; receives the raw JSON body string. */
    @Volatile var onStateUpdate: ((String) -> Unit)? = null

    val isRunning: Boolean get() = running.get()

    /** Returns the number of actions dropped due to a full queue since the last call, then resets to 0. */
    fun peekAndResetDropCount(): Int = droppedActionCount.getAndSet(0)

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) { acceptLoop() }
        scope.launch(Dispatchers.IO) { discoveryLoop() }
        DeckBridgeLog.lan("MacBridgeServer starting on port $PORT")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        serverSocket?.runCatching { close() }
        discoverySocket.getAndSet(null)?.runCatching { close() }
        actionQueue.clear()
        droppedActionCount.set(0)
        DeckBridgeLog.lan("MacBridgeServer stopped")
    }

    /**
     * Enqueue a resolved action for the Mac to pick up on its next /action/next poll.
     * [LanActionJsonFactory.actionJsonOrNull] converts it; returns without queuing if unsupported.
     */
    fun enqueueAction(intentId: String, actionJson: String) {
        val offered = actionQueue.offer(actionJson)
        if (!offered) droppedActionCount.incrementAndGet()
        DeckBridgeLog.lan("MacBridge enqueue intent=$intentId queued=$offered queueSize=${actionQueue.size}${if (!offered) " ⚠ DROPPED" else ""}")
    }

    // ── Server loop ──────────────────────────────────────────────────────────

    private fun acceptLoop() {
        // Outer loop: retry binding on failure (e.g. port in TIME_WAIT from previous session).
        while (running.get()) {
            try {
                // reuseAddress MUST be set before bind; setting it after (as in new ServerSocket(port))
                // has no effect on the current socket and leaves the port stuck in TIME_WAIT.
                val sock = ServerSocket()
                sock.reuseAddress = true
                sock.bind(InetSocketAddress(PORT))
                serverSocket = sock
                DeckBridgeLog.lan("MacBridgeServer listening on 0.0.0.0:$PORT")
                while (running.get()) {
                    val client = runCatching { serverSocket!!.accept() }.getOrNull() ?: break
                    handleClientAsync(client)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    DeckBridgeLog.lan("MacBridgeServer bind/accept error: ${e.message} — retry in 3 s")
                    runCatching { Thread.sleep(3_000) }
                }
            } finally {
                serverSocket?.runCatching { close() }
                serverSocket = null
            }
        }
    }

    private fun handleClientAsync(socket: Socket) {
        Thread({
            try {
                socket.soTimeout = LONG_POLL_TIMEOUT_MS + 8_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val out = PrintWriter(socket.getOutputStream(), false)

                // Parse request line
                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "GET" }
                val path = parts.getOrElse(1) { "/" }.substringBefore("?")

                // Parse headers
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }

                // Read body for POST requests
                val body = if (method == "POST") {
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    if (len > 0) {
                        val buf = CharArray(len.coerceAtMost(131_072))
                        val read = reader.read(buf, 0, buf.size)
                        if (read > 0) String(buf, 0, read) else ""
                    } else ""
                } else ""

                val clientToken = headers[HEADER_PAIR_TOKEN.lowercase()]
                DeckBridgeLog.lan("MacBridge $method $path from ${socket.inetAddress.hostAddress}")

                when {
                    method == "GET" && path == "/health" -> handleHealth(out)
                    method == "GET" && path == "/action/next" ->
                        handleNextAction(out, clientToken, socket.inetAddress.hostAddress)
                    method == "POST" && path == "/state" ->
                        handleStateUpdate(out, body, clientToken)
                    else -> sendJson(out, 404, """{"ok":false,"error":"not_found"}""")
                }
            } catch (e: Exception) {
                DeckBridgeLog.lan("MacBridge handler error: ${e.message}")
            } finally {
                socket.runCatching { close() }
            }
        }, "mac-bridge-handler").also { it.isDaemon = true; it.start() }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private fun handleHealth(out: PrintWriter) {
        val body = JSONObject()
            .put("ok", true)
            .put("device", "android")
            .put("bridge_port", PORT)
            .put("paired", pairToken.get() != null)
            .toString()
        sendJson(out, 200, body)
    }

    private fun handleNextAction(out: PrintWriter, clientToken: String?, clientIp: String?) {
        val pairToken = pairToken.get()
        if (pairToken != null && clientToken != pairToken) {
            sendJson(out, 401, """{"ok":false,"error":"invalid_token"}""")
            return
        }
        // Record this poll for connection-status tracking
        lastPollMs.set(System.currentTimeMillis())
        if (clientIp != null) lastClientIp.set(clientIp)
        // Block until action available or timeout (normal)
        val action = actionQueue.poll(LONG_POLL_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        if (action == null) {
            sendJson(out, 200, """{"ok":true,"action":null}""")
        } else {
            sendJson(out, 200, """{"ok":true,"action":$action}""")
        }
    }

    private fun handleStateUpdate(out: PrintWriter, body: String, clientToken: String?) {
        val token = pairToken.get()
        if (token != null && clientToken != token) {
            sendJson(out, 401, """{"ok":false,"error":"invalid_token"}""")
            return
        }
        DeckBridgeLog.lan("MacBridge POST /state body_len=${body.length}")
        onStateUpdate?.invoke(body)
        sendJson(out, 200, """{"ok":true}""")
    }

    // ── UDP discovery responder ───────────────────────────────────────────────

    /**
     * Listens on UDP port [DISCOVERY_PORT] for `DECKBRIDGE_DISCOVER_v1` broadcasts from the Mac
     * agent and replies with the phone's WiFi IP + [PORT], so the Mac can find the Android without
     * manual config when ADB is unavailable.
     */
    private fun discoveryLoop() {
        try {
            val sock = DatagramSocket(DISCOVERY_PORT).also {
                it.reuseAddress = true
                it.soTimeout = 500
            }
            discoverySocket.set(sock)
            val buf = ByteArray(256)
            val recv = DatagramPacket(buf, buf.size)
            DeckBridgeLog.lan("MacBridgeServer UDP discovery listening on port $DISCOVERY_PORT")
            while (running.get()) {
                try {
                    sock.receive(recv)
                    val body = String(recv.data, recv.offset, recv.length, Charsets.UTF_8).trim()
                    if (body == DISCOVERY_MAGIC) {
                        val myIp = localWifiIp() ?: continue
                        val reply = """{"ok":true,"ip":"$myIp","port":$PORT,"agent_os":"android"}"""
                            .toByteArray(Charsets.UTF_8)
                        sock.send(DatagramPacket(reply, reply.size, recv.address, recv.port))
                        DeckBridgeLog.lan("MacBridge discovery: replied ip=$myIp to ${recv.address.hostAddress}")
                    }
                } catch (_: SocketTimeoutException) { /* loop tick — check running.get() */ }
            }
        } catch (e: Exception) {
            if (running.get()) DeckBridgeLog.lan("MacBridge discovery loop error: ${e.message}")
        } finally {
            discoverySocket.getAndSet(null)?.runCatching { close() }
        }
    }

    /** First non-loopback, non-link-local IPv4 address — the phone's WiFi IP. */
    fun localWifiIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { addr ->
                val h = addr.hostAddress ?: return@firstOrNull false
                !addr.isLoopbackAddress && !addr.isLinkLocalAddress && !h.contains(':')
            }
            ?.hostAddress
    }.getOrNull()

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun sendJson(out: PrintWriter, code: Int, body: String) {
        val statusText = when (code) {
            200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        out.print("HTTP/1.1 $code $statusText\r\n")
        out.print("Content-Type: application/json; charset=utf-8\r\n")
        out.print("Content-Length: ${bytes.size}\r\n")
        out.print("Connection: close\r\n")
        out.print("\r\n")
        out.print(body)
        out.flush()
    }

    companion object {
        const val PORT = 8767
        /** UDP port on which the Android server listens for Mac agent discovery broadcasts. */
        const val DISCOVERY_PORT = 8766
        private const val DISCOVERY_MAGIC = "DECKBRIDGE_DISCOVER_v1"
        private const val LONG_POLL_TIMEOUT_MS = 55_000
        /** A client is "alive" if it polled within this window (55s timeout + 35s grace). */
        const val CLIENT_ALIVE_WINDOW_MS = 90_000L
    }
}
