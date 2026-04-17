package com.example.deckbridge.lan

import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.ResolvedAction
import com.example.deckbridge.domain.model.ResolvedActionKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds JSON bodies for POST `/action` on the PC LAN agent from [ResolvedAction].
 *
 * Contract (v1):
 * - `{ "type": "combo", "keys": ["ctrl","c"] }` — chord (modifiers + key tokens).
 * - `{ "type": "media", "action": "vol_up" | … }` — consumer media keys.
 * - `{ "type": "text", "text": "…" }` — Unicode text typing.
 * - `{ "type": "key", "key": "enter" | … }` — single key tap.
 *
 * All deterministic actions (media + named combos) are pre-serialised once at object
 * init time and stored in [jsonCache]. Per-keystroke dispatch for those actions is then
 * a single [HashMap.get] lookup — no [JSONObject]/[JSONArray] allocations on the hot path.
 * Variable actions (text, arbitrary key tokens) are serialised on demand as before.
 */
object LanActionJsonFactory {

    private const val MAX_TEXT_CHARS = 2_000

    /**
     * Pre-built JSON strings for every known deterministic action.
     * Keys:
     *   - `"m:<intentId>"` for media actions
     *   - `"c:<intentId>:<PLATFORM>"` for combos (platform = [HostPlatform.coerceForDeckData].name)
     */
    private val jsonCache: Map<String, String> = buildJsonCache()

    fun actionJsonOrNull(resolved: ResolvedAction): String? {
        return when (resolved.kind) {
            ResolvedActionKind.KEY_CHORD  -> comboJson(resolved)
            ResolvedActionKind.SYSTEM_MEDIA -> mediaJson(resolved.intentId)
            ResolvedActionKind.TEXT       -> textJson(resolved.textPayload)
            ResolvedActionKind.KEY        -> keyJson(resolved.keyToken)
            ResolvedActionKind.NOOP       -> null
        }
    }

    // ── hot-path dispatch ──────────────────────────────────────────────────────

    private fun comboJson(resolved: ResolvedAction): String? {
        val platform = resolved.platform.coerceForDeckData()
        // Cache hit covers all named intents — O(1), zero allocations.
        return jsonCache["c:${resolved.intentId}:${platform.name}"]
            ?: buildComboJson(resolved.intentId, resolved.platform)   // dynamic fallback (rare)
    }

    private fun mediaJson(intentId: String): String? =
        jsonCache["m:$intentId"]   // always a cache hit for known media intents

    // ── variable actions — serialised on demand ────────────────────────────────

    private fun textJson(text: String?): String? {
        val t = text?.take(MAX_TEXT_CHARS)?.ifBlank { null } ?: return null
        return JSONObject()
            .put("type", "text")
            .put("text", t)
            .toString()
    }

    private fun keyJson(token: String?): String? {
        val k = token?.trim()?.lowercase()?.ifBlank { null } ?: return null
        return JSONObject()
            .put("type", "key")
            .put("key", k)
            .toString()
    }

    // ── intent resolution (used by cache builder + dynamic fallback) ───────────

    private fun buildComboJson(intentId: String, platform: HostPlatform): String? {
        val keys = comboKeysFor(intentId, platform) ?: return null
        return JSONObject()
            .put("type", "combo")
            .put("keys", JSONArray(keys))
            .toString()
    }

    private fun mediaActionFor(intentId: String): String? = when (intentId) {
        "deck.intent.media.vol_up"     -> "vol_up"
        "deck.intent.media.vol_down"   -> "vol_down"
        "deck.intent.media.play_pause" -> "play_pause"
        "deck.intent.media.prev_track" -> "prev_track"
        "deck.intent.media.next_track" -> "next_track"
        "deck.intent.media.mute"       -> "mute"
        else -> null
    }

    private fun comboKeysFor(intentId: String, platform: HostPlatform): List<String>? {
        val p = platform.coerceForDeckData()
        return when (intentId) {
            "deck.intent.copy" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "c")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "c")
            }
            "deck.intent.paste" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "v")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "v")
            }
            "deck.intent.cut" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "x")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "x")
            }
            "deck.intent.search" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "f")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "f")
            }
            "deck.intent.undo" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "z")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "z")
            }
            "deck.intent.redo" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "shift", "z")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("ctrl", "y")
            }
            "deck.intent.show_desktop" -> when (p) {
                HostPlatform.MAC                              -> listOf("ctrl", "up")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("win", "d")
            }
            "deck.intent.snipping_overlay" -> when (p) {
                HostPlatform.MAC                              -> listOf("cmd", "shift", "4")
                HostPlatform.WINDOWS, HostPlatform.UNKNOWN   -> listOf("win", "shift", "s")
            }
            else -> null
        }
    }

    // ── cache construction (runs once at class load) ───────────────────────────

    private fun buildJsonCache(): Map<String, String> {
        val map = HashMap<String, String>(32)

        // Media — 6 fixed entries
        val mediaIntents = listOf(
            "deck.intent.media.vol_up",
            "deck.intent.media.vol_down",
            "deck.intent.media.play_pause",
            "deck.intent.media.prev_track",
            "deck.intent.media.next_track",
            "deck.intent.media.mute",
        )
        for (intentId in mediaIntents) {
            val action = mediaActionFor(intentId) ?: continue
            map["m:$intentId"] = JSONObject()
                .put("type", "media")
                .put("action", action)
                .toString()
        }

        // Combos — 8 intents × 2 effective platforms (WINDOWS + MAC) = 16 entries
        val comboIntents = listOf(
            "deck.intent.copy",
            "deck.intent.paste",
            "deck.intent.cut",
            "deck.intent.search",
            "deck.intent.undo",
            "deck.intent.redo",
            "deck.intent.show_desktop",
            "deck.intent.snipping_overlay",
        )
        for (platform in listOf(HostPlatform.WINDOWS, HostPlatform.MAC)) {
            for (intentId in comboIntents) {
                val keys = comboKeysFor(intentId, platform) ?: continue
                map["c:$intentId:${platform.name}"] = JSONObject()
                    .put("type", "combo")
                    .put("keys", JSONArray(keys))
                    .toString()
            }
        }

        return map
    }
}
