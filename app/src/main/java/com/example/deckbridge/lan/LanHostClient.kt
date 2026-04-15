package com.example.deckbridge.lan

import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of GET /health against the client’s current endpoint (includes optional token validation). */
data class LanHealthResult(
    val httpOk: Boolean,
    /** Failure reason when [httpOk] is false; null on success. */
    val detail: String?,
    /** Present when the request included a pair token and the JSON carried `pairing.pair_token_valid`. */
    val pairTokenValid: Boolean?,
)

/**
 * HTTP client for the PC LAN agent: health, pairing v1, and POST /action.
 */
class LanHostClient {

    @Volatile
    var host: String = ""
        private set

    @Volatile
    var port: Int = DEFAULT_PORT
        private set

    @Volatile
    var pairToken: String? = null
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun updateEndpoint(host: String, port: Int) {
        this.host = host.trim()
        this.port = port.coerceIn(1, 65535)
    }

    fun setPairToken(token: String?) {
        pairToken = token?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun baseUrlOrNull(): String? {
        if (host.isBlank()) return null
        return "http://${this.host}:$port"
    }

    private fun Request.Builder.addPairAuth(): Request.Builder = apply {
        pairToken?.let { header(HEADER_PAIR_TOKEN, it) }
    }

    suspend fun getHealthDetailed(): LanHealthResult = withContext(Dispatchers.IO) {
        val base = baseUrlOrNull() ?: return@withContext LanHealthResult(false, "Sin host", null)
        runCatching {
            val req = Request.Builder().url("$base/health").get().addPairAuth().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext LanHealthResult(false, "HTTP ${resp.code}", null)
                }
                val body = resp.body?.string().orEmpty()
                val root = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext LanHealthResult(false, "JSON inválido", null)
                if (!root.optBoolean("ok")) {
                    return@withContext LanHealthResult(false, "Respuesta sin ok=true", null)
                }
                root.optString("agent_os", "").trim().takeIf { it.isNotEmpty() }?.let { os ->
                    DeckBridgeLog.lan("health agent_os=$os url=$base")
                }
                root.optJSONObject("lan_discovery")?.let { d ->
                    DeckBridgeLog.lan(
                        "health lan_discovery listening=${d.optBoolean("listening")} replies=${d.optInt("replies_sent")} " +
                            "ignoredMagic=${d.optInt("packets_ignored_wrong_magic")} sendErr=${d.optInt("send_errors")} " +
                            "lastClient=${d.optString("last_client")}",
                    )
                }
                val pairing = root.optJSONObject("pairing")
                val ptv: Boolean? = pairing?.let { p ->
                    if (p.has("pair_token_valid") && !p.isNull("pair_token_valid")) {
                        p.getBoolean("pair_token_valid")
                    } else {
                        null
                    }
                }
                LanHealthResult(true, null, ptv)
            }
        }.getOrElse { e ->
            val msg = (e.message ?: e.javaClass.simpleName).take(200)
            DeckBridgeLog.lan("health FAIL $msg")
            LanHealthResult(false, msg, null)
        }
    }

    /** @param detail human-readable failure (truncated); null on success */
    suspend fun getHealthResult(): Pair<Boolean, String?> {
        val r = getHealthDetailed()
        return Pair(r.httpOk, r.detail)
    }

    suspend fun postAction(jsonBody: String): Result<Unit> = withContext(Dispatchers.IO) {
        val base = baseUrlOrNull() ?: return@withContext Result.failure(IllegalStateException("no host"))
        runCatching {
            val body = jsonBody.toRequestBody(JSON_MEDIA)
            val req = Request.Builder().url("$base/action").post(body).addPairAuth().build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code} ${resp.body?.string()?.take(120)}"))
            }
        }.getOrElse { e ->
            DeckBridgeLog.lan("post FAIL ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createPairingSession(
        mobileDeviceId: String,
        mobileDisplayName: String,
    ): Result<LanPairingSessionCreated> = withContext(Dispatchers.IO) {
        val base = baseUrlOrNull() ?: return@withContext Result.failure(IllegalStateException("no host"))
        runCatching {
            val jo = JSONObject()
                .put("mobile_device_id", mobileDeviceId)
                .put("mobile_display_name", mobileDisplayName)
            val body = jo.toString().toRequestBody(JSON_MEDIA)
            val req = Request.Builder().url("$base/v1/pairing/sessions").post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} $text"))
                }
                val j = JSONObject(text)
                if (!j.optBoolean("ok")) {
                    return@withContext Result.failure(IllegalStateException(j.optString("error", "pairing_start_failed")))
                }
                val created = LanPairingSessionCreated(
                    sessionId = j.getString("session_id"),
                    pairingCode = j.getString("pairing_code"),
                    expiresAtEpochMs = j.getLong("expires_at_ms"),
                )
                DeckBridgeLog.lan(
                    "pairing POST /sessions ok session_id=${created.sessionId} expires_at_ms=${created.expiresAtEpochMs}",
                )
                Result.success(created)
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun claimPairingSession(
        sessionId: String,
        mobileDeviceId: String,
        mobileDisplayName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val base = baseUrlOrNull() ?: return@withContext Result.failure(IllegalStateException("no host"))
        runCatching {
            val jo = JSONObject()
                .put("mobile_device_id", mobileDeviceId)
                .put("mobile_display_name", mobileDisplayName)
            val body = jo.toString().toRequestBody(JSON_MEDIA)
            val url = "$base/v1/pairing/sessions/${sessionId.trim()}/claim"
            val req = Request.Builder().url(url).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val apiErr = runCatching { JSONObject(text).optString("error") }
                        .getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    val msg = apiErr?.let { "HTTP ${resp.code} error=$it" } ?: "HTTP ${resp.code} $text"
                    DeckBridgeLog.lan("pairing POST …/claim FAIL $msg")
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val j = JSONObject(text)
                if (!j.optBoolean("ok")) {
                    val err = j.optString("error", "claim_failed")
                    return@withContext Result.failure(IllegalStateException("error=$err"))
                }
                DeckBridgeLog.lan("pairing POST …/claim ok session_id=${sessionId.trim()}")
                Result.success(Unit)
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun getPairingSession(sessionId: String): Result<LanPairingSessionStatus> =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrNull() ?: return@withContext Result.failure(IllegalStateException("no host"))
            runCatching {
                val req = Request.Builder().url("$base/v1/pairing/sessions/${sessionId.trim()}").get().build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} $text"))
                    }
                    val j = JSONObject(text)
                    if (!j.optBoolean("ok")) {
                        return@withContext Result.failure(IllegalStateException(j.optString("error")))
                    }
                    Result.success(
                        LanPairingSessionStatus(
                            sessionId = j.optString("session_id", sessionId),
                            status = j.getString("status"),
                            pairingCode = j.optString("pairing_code").takeIf { it.isNotEmpty() },
                            pairToken = j.optString("pair_token").takeIf { it.isNotEmpty() },
                            mobileDeviceId = j.optString("mobile_device_id").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }.getOrElse { Result.failure(it) }
        }

    /**
     * One-shot reachability + pairing summary for a candidate host (does not change [host]/[port]).
     */
    suspend fun probeAt(host: String, port: Int, pairTokenForProbe: String? = null): LanAgentProbeSnapshot =
        withContext(Dispatchers.IO) {
        val h = host.trim()
        val p = port.coerceIn(1, 65_535)
        if (h.isEmpty()) {
            return@withContext LanAgentProbeSnapshot(false, "empty host", false, false, null)
        }
        val base = "http://$h:$p"
        val tok = pairTokenForProbe?.trim()?.takeIf { it.isNotEmpty() }
        runCatching {
            val healthReq = Request.Builder().url("$base/health").get().apply {
                tok?.let { header(HEADER_PAIR_TOKEN, it) }
            }.build()
            http.newCall(healthReq).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext LanAgentProbeSnapshot(false, "HTTP ${resp.code}", false, false, null)
                }
                val j = JSONObject(text)
                if (!j.optBoolean("ok")) {
                    return@withContext LanAgentProbeSnapshot(false, "not ok", false, false, null)
                }
                val agentOs = j.optString("agent_os", "").trim().takeIf { it.isNotEmpty() }
                val pairing = j.optJSONObject("pairing")
                val paired = pairing?.optBoolean("paired") ?: false
                val actionReq = pairing?.optBoolean("action_requires_pair_token") ?: false
                val pairTokenValid: Boolean? = pairing?.let { po ->
                    if (tok != null && po.has("pair_token_valid") && !po.isNull("pair_token_valid")) {
                        po.getBoolean("pair_token_valid")
                    } else {
                        null
                    }
                }
                var deviceId: String? = null
                if (paired || actionReq) {
                    runCatching {
                        http.newCall(Request.Builder().url("$base/v1/pairing/host/status").get().build())
                            .execute().use { r2 ->
                                if (r2.isSuccessful) {
                                    val j2 = JSONObject(r2.body?.string().orEmpty())
                                    val pd = j2.optJSONObject("paired_device")
                                    if (pd != null) {
                                        deviceId = pd.optString("mobile_device_id").takeIf { it.isNotEmpty() }
                                    }
                                }
                            }
                    }
                }
                LanAgentProbeSnapshot(
                    healthOk = true,
                    healthDetail = null,
                    serverReportsPaired = paired,
                    actionRequiresPairToken = actionReq,
                    pairedDeviceIdOnHost = deviceId,
                    pairTokenValid = pairTokenValid,
                    agentOs = agentOs,
                )
            }
        }.getOrElse { e ->
            LanAgentProbeSnapshot(false, (e.message ?: e.javaClass.simpleName).take(160), false, false, null)
        }
    }

    suspend fun cancelPairingSession(sessionId: String, mobileDeviceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrNull() ?: return@withContext Result.failure(IllegalStateException("no host"))
            runCatching {
                val jo = JSONObject().put("mobile_device_id", mobileDeviceId)
                val body = jo.toString().toRequestBody(JSON_MEDIA)
                val url = "$base/v1/pairing/sessions/${sessionId.trim()}/cancel"
                val req = Request.Builder().url(url).post(body).build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${resp.code} ${resp.body?.string()}"),
                        )
                    }
                    DeckBridgeLog.lan("pairing POST …/cancel ok session_id=${sessionId.trim()}")
                    Result.success(Unit)
                }
            }.getOrElse { Result.failure(it) }
        }

    companion object {
        const val DEFAULT_PORT: Int = 8765
        const val HEADER_PAIR_TOKEN: String = "X-DeckBridge-Pair-Token"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
