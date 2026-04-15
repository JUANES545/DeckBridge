package com.example.deckbridge.domain.pairing

import com.example.deckbridge.domain.model.HostPlatform
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Parses QR / deep-link payloads for LAN bootstrap + optional existing pairing session.
 *
 * Forms:
 * - `deckbridge://pair?host=192.168.1.10&port=8765`
 * - `deckbridge://pair?h=192.168.1.10&p=8765&sid=ps_abc&v=1`
 * - `https://example.com/pair?h=...` (same query keys)
 *
 * Query keys:
 * - `host` / `h` — agent IP or hostname (required)
 * - `port` / `p` — HTTP port (default 8765)
 * - `sid` / `session` — optional pairing session id if the QR was printed after the PC created a session
 * - `n` / `name` — optional PC display label (UI only)
 * - `os` — optional hint for Android: `mac` / `darwin` → switch host platform to Mac before bootstrap;
 *   `win` / `windows` → Windows (LAN slot + deck shortcuts).
 * - `v` — optional protocol version; **only `1` is accepted** when present (unknown → parse fails).
 */
object DeckbridgePairingPayload {

    data class Bootstrap(
        val host: String,
        val port: Int,
        /** If set, Android polls this session instead of POSTing a new one (must still match this agent). */
        val sessionId: String? = null,
        /** Optional hostname / label from the PC QR (for UI). */
        val hostDisplayName: String? = null,
        /** From `os=` in the deeplink; applied before LAN bootstrap so the correct host slot is used. */
        val suggestedHostPlatform: HostPlatform? = null,
    )

    fun parse(raw: String): Bootstrap? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return tryParseUri(trimmed)
            ?: tryParseIpv4(trimmed)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val utf8 = StandardCharsets.UTF_8
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) continue
            val idx = part.indexOf('=')
            val encKey = if (idx >= 0) part.substring(0, idx) else part
            val encVal = if (idx >= 0) part.substring(idx + 1) else ""
            val k = runCatching { URLDecoder.decode(encKey, utf8) }.getOrElse { encKey }.trim()
            if (k.isEmpty()) continue
            val v = runCatching { URLDecoder.decode(encVal, utf8) }.getOrElse { encVal }
            out[k] = v
        }
        return out
    }

    private fun tryParseUri(s: String): Bootstrap? {
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "deckbridge" && scheme != "http" && scheme != "https") return null
        val rawQuery = uri.rawQuery ?: uri.query ?: return null
        val q = parseQueryParams(rawQuery)
        fun qp(vararg keys: String): String? {
            for (k in keys) {
                q[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return null
        }
        val h = qp("host", "h") ?: return null
        val host = h.trim()
        if (host.isEmpty()) return null
        val p = qp("port", "p")
        val port = p?.toIntOrNull()?.coerceIn(1, 65_535) ?: 8765
        val sid = qp("sid", "session")?.trim()?.takeIf { it.isNotEmpty() }
        val name = qp("n", "name")?.trim()?.takeIf { it.isNotEmpty() }
        val osRaw = qp("os")?.lowercase()
        val suggested = when (osRaw) {
            "mac", "darwin", "osx" -> HostPlatform.MAC
            "win", "windows" -> HostPlatform.WINDOWS
            else -> null
        }
        val v = qp("v")
        if (!v.isNullOrEmpty() && v != "1") {
            return null
        }
        return Bootstrap(host, port, sid, name, suggested)
    }

    private val ipv4WithOptionalPort = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?::(\d+))?$""")

    private fun tryParseIpv4(s: String): Bootstrap? {
        val m = ipv4WithOptionalPort.matchEntire(s) ?: return null
        val host = m.groupValues[1]
        val port = m.groupValues[2].toIntOrNull()?.coerceIn(1, 65_535) ?: 8765
        return Bootstrap(host, port, null, null, null)
    }

    /** Minimal QR text for bootstrap (host + port + optional session + optional display name). */
    fun toQrPayload(host: String, port: Int, sessionId: String? = null, hostDisplayName: String? = null): String {
        val utf8 = StandardCharsets.UTF_8
        fun enc(s: String): String = URLEncoder.encode(s, utf8).replace("+", "%20")
        val sid = sessionId?.trim()?.takeIf { it.isNotEmpty() }
        val n = hostDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        val parts = mutableListOf(
            "h=${enc(host)}",
            "p=$port",
            "v=1",
        )
        if (sid != null) parts.add("sid=${enc(sid)}")
        if (n != null) parts.add("n=${enc(n)}")
        return "deckbridge://pair?" + parts.joinToString("&")
    }
}
