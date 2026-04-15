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
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    val isRunning: Boolean get() = running.get()

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) { acceptLoop() }
        DeckBridgeLog.lan("MacBridgeServer starting on port $PORT")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        serverSocket?.runCatching { close() }
        actionQueue.clear()
        DeckBridgeLog.lan("MacBridgeServer stopped")
    }

    /**
     * Enqueue a resolved action for the Mac to pick up on its next /action/next poll.
     * [LanActionJsonFactory.actionJsonOrNull] converts it; returns without queuing if unsupported.
     */
    fun enqueueAction(intentId: String, actionJson: String) {
        val offered = actionQueue.offer(actionJson)
        DeckBridgeLog.lan("MacBridge enqueue intent=$intentId queued=$offered queueSize=${actionQueue.size}")
    }

    // ── Server loop ──────────────────────────────────────────────────────────

    private fun acceptLoop() {
        try {
            serverSocket = ServerSocket(PORT).also { it.reuseAddress = true }
            DeckBridgeLog.lan("MacBridgeServer listening on 0.0.0.0:$PORT")
            while (running.get()) {
                val client = runCatching { serverSocket!!.accept() }.getOrNull() ?: break
                handleClientAsync(client)
            }
        } catch (e: Exception) {
            if (running.get()) DeckBridgeLog.lan("MacBridgeServer accept error: ${e.message}")
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

                val clientToken = headers[HEADER_PAIR_TOKEN.lowercase()]
                DeckBridgeLog.lan("MacBridge $method $path from ${socket.inetAddress.hostAddress}")

                when {
                    method == "GET" && path == "/health" -> handleHealth(out)
                    method == "GET" && path == "/action/next" ->
                        handleNextAction(out, clientToken, socket.inetAddress.hostAddress)
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
        private const val LONG_POLL_TIMEOUT_MS = 55_000
        /** A client is "alive" if it polled within this window (55s timeout + 35s grace). */
        const val CLIENT_ALIVE_WINDOW_MS = 90_000L
    }
}
