package com.example.deckbridge.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.example.deckbridge.BuildConfig
import com.example.deckbridge.logging.DeckBridgeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer APK, manages the download via [DownloadManager],
 * and exposes the full update lifecycle as [UpdateState].
 *
 * Usage:
 *  1. Call [checkForUpdate] once at startup (fire-and-forget; silent on any error).
 *  2. Observe [state]; render UI accordingly.
 *  3. On user tap → [startDownloadOrRequestPermission].
 *  4. If [UpdateState.NeedsPermission] → call [openInstallPermissionSettings], then
 *     [startDownloadOrRequestPermission] again after the user returns.
 *  5. On [UpdateState.ReadyToInstall] → call [launchInstall].
 */
class AppUpdateManager(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("deckbridge_update", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Fire-and-forget. Never throws; any error is logged silently. */
    fun checkForUpdate() {
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/JUANES545/DeckBridge/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@launch
                    resp.body?.string() ?: return@launch
                }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").trimStart('v')
                if (tagName.isBlank()) return@launch
                if (!isNewerVersion(tagName, BuildConfig.VERSION_NAME)) return@launch
                if (getDismissedVersion() == tagName) return@launch

                val assets = json.optJSONArray("assets") ?: return@launch
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrBlank()) return@launch

                val info = UpdateInfo(
                    latestVersion = tagName,
                    apkDownloadUrl = apkUrl,
                    releaseUrl = json.optString("html_url"),
                )
                DeckBridgeLog.state("update: v$tagName available (current=${BuildConfig.VERSION_NAME})")
                _state.value = UpdateState.Available(info)
            } catch (e: Exception) {
                DeckBridgeLog.state("update: check skipped (${e.javaClass.simpleName}: ${e.message?.take(80)})")
            }
        }
    }

    /** Dismiss for this version — won't show again until a newer release is found. */
    fun dismiss() {
        currentInfo()?.let { setDismissedVersion(it.latestVersion) }
        _state.value = UpdateState.Dismissed
    }

    /**
     * Starts the download if the app has install permission; otherwise transitions to
     * [UpdateState.NeedsPermission] so the UI can guide the user to Settings.
     */
    fun startDownloadOrRequestPermission(context: Context) {
        val info = currentInfo() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        startDownload(info)
    }

    /** Open the system screen where the user can grant "Install unknown apps" for this app. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Call when the Activity resumes after the user visited permission Settings.
     * No-op unless state is [UpdateState.NeedsPermission] and permission is now granted.
     */
    fun retryAfterPermissionGrant() {
        // Only act when the user explicitly went to grant install permission and came back.
        // Do NOT trigger on plain Available state — that would start a download on every resume.
        val info = (_state.value as? UpdateState.NeedsPermission)?.info ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
        ) {
            startDownload(info)
        }
    }

    /** Trigger the system APK installer for a completed download. */
    fun launchInstall(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available      -> s.info
        is UpdateState.NeedsPermission -> s.info
        is UpdateState.Downloading    -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else                          -> null
    }

    private fun startDownload(info: UpdateInfo) {
        try {
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(info.apkDownloadUrl))
                .setTitle("DeckBridge ${info.latestVersion}")
                .setDescription("Descargando actualización…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(
                    appContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    "DeckBridge-${info.latestVersion}.apk",
                )
                .setMimeType("application/vnd.android.package-archive")
            val downloadId = dm.enqueue(req)
            _state.value = UpdateState.Downloading(info, -1f)
            DeckBridgeLog.state("update: download enqueued id=$downloadId")
            pollDownload(dm, downloadId, info)
        } catch (e: Exception) {
            DeckBridgeLog.state("update: startDownload failed — ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            // Keep state as Available so the user can retry manually; never crash the app.
        }
    }

    private fun pollDownload(dm: DownloadManager, downloadId: Long, info: UpdateInfo) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(600)
                val finished = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    ?.use { c ->
                        if (!c.moveToFirst()) return@use false
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val dlBytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = dm.getUriForDownloadedFile(downloadId)
                                if (uri != null) {
                                    DeckBridgeLog.state("update: download complete uri=$uri")
                                    _state.value = UpdateState.ReadyToInstall(info, uri)
                                } else {
                                    _state.value = UpdateState.Available(info)
                                }
                                true
                            }
                            DownloadManager.STATUS_FAILED -> {
                                DeckBridgeLog.state("update: download FAILED — reverting to Available")
                                _state.value = UpdateState.Available(info)
                                true
                            }
                            else -> {
                                val progress = if (total > 0) dlBytes.toFloat() / total else -1f
                                _state.value = UpdateState.Downloading(info, progress)
                                false
                            }
                        }
                    } ?: false
                if (finished) break
            }
        }
    }

    private fun getDismissedVersion(): String =
        prefs.getString(PREF_DISMISSED_VERSION, "") ?: ""

    private fun setDismissedVersion(v: String) {
        prefs.edit().putString(PREF_DISMISSED_VERSION, v).apply()
    }

    companion object {
        private const val PREF_DISMISSED_VERSION = "dismissed_version"

        fun isNewerVersion(latest: String, current: String): Boolean {
            val l = parseVer(latest)
            val c = parseVer(current)
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv != cv) return lv > cv
            }
            return false
        }

        private fun parseVer(v: String): List<Int> =
            v.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
    }
}
