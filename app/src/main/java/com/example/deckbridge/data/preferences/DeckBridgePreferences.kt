package com.example.deckbridge.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DEFAULT_LAN_SERVER_PORT: Int = 8765

private val Context.deckBridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "deck_bridge_settings")

private val KEY_HOST_PLATFORM = stringPreferencesKey("host_platform")
private val KEY_HOST_AUTO_DETECT = booleanPreferencesKey("host_auto_detect")
private val KEY_HARDWARE_CALIBRATION_JSON = stringPreferencesKey("hardware_calibration_json")
private val KEY_HOST_DELIVERY_CHANNEL = stringPreferencesKey("host_delivery_channel")
/** Legacy single-host keys (pre–per-platform); migrated into [KEY_LAN_WIN_HOST] on first boot. */
private val KEY_LAN_SERVER_HOST = stringPreferencesKey("lan_server_host")
private val KEY_LAN_SERVER_PORT = intPreferencesKey("lan_server_port")
private val KEY_LAN_PAIR_TOKEN = stringPreferencesKey("lan_pair_token")

/** Per–host-OS LAN endpoint (Windows vs macOS agent); avoids mixing pair tokens when switching platform. */
private val KEY_LAN_PLATFORM_SLOTS_MIGRATED = booleanPreferencesKey("lan_platform_slots_migrated_v1")
private val KEY_LAN_WIN_HOST = stringPreferencesKey("lan_win_host")
private val KEY_LAN_WIN_PORT = intPreferencesKey("lan_win_port")
private val KEY_LAN_WIN_PAIR_TOKEN = stringPreferencesKey("lan_win_pair_token")
private val KEY_LAN_MAC_HOST = stringPreferencesKey("lan_mac_host")
private val KEY_LAN_MAC_PORT = intPreferencesKey("lan_mac_port")
private val KEY_LAN_MAC_PAIR_TOKEN = stringPreferencesKey("lan_mac_pair_token")

private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
private val KEY_SKIP_INITIAL_PC_CONNECT = booleanPreferencesKey("skip_initial_pc_connect")
private val KEY_LAN_MOBILE_DEVICE_ID = stringPreferencesKey("lan_mobile_device_id")
private val KEY_DECK_GRID_LAYOUT_JSON = stringPreferencesKey("deck_grid_layout_json")
private val KEY_ANIMATED_BACKGROUND_MODE  = stringPreferencesKey("animated_background_mode")
private val KEY_ANIMATED_BACKGROUND_THEME = stringPreferencesKey("animated_background_theme")

fun Context.deckBridgePreferences(): DataStore<Preferences> = deckBridgeDataStore

suspend fun DataStore<Preferences>.readPersistedHostPlatform(): HostPlatform {
    val raw = data.map { it[KEY_HOST_PLATFORM] }.first() ?: return HostPlatform.WINDOWS
    return when (raw) {
        "MAC" -> HostPlatform.MAC
        "WINDOWS" -> HostPlatform.WINDOWS
        "UNKNOWN" -> HostPlatform.UNKNOWN
        else -> HostPlatform.WINDOWS
    }
}

suspend fun DataStore<Preferences>.writePersistedHostPlatform(platform: HostPlatform) {
    edit { prefs ->
        prefs[KEY_HOST_PLATFORM] = when (platform) {
            HostPlatform.MAC -> "MAC"
            HostPlatform.WINDOWS -> "WINDOWS"
            HostPlatform.UNKNOWN -> "UNKNOWN"
        }
    }
}

suspend fun DataStore<Preferences>.readHostAutoDetect(): Boolean {
    return data.map { it[KEY_HOST_AUTO_DETECT] }.first() ?: false
}

suspend fun DataStore<Preferences>.writeHostAutoDetect(enabled: Boolean) {
    edit { prefs ->
        prefs[KEY_HOST_AUTO_DETECT] = enabled
    }
}

suspend fun DataStore<Preferences>.readHardwareCalibrationJson(): String? {
    return data.map { it[KEY_HARDWARE_CALIBRATION_JSON] }.first()
}

suspend fun DataStore<Preferences>.writeHardwareCalibrationJson(json: String?) {
    edit { prefs ->
        if (json.isNullOrBlank()) {
            prefs.remove(KEY_HARDWARE_CALIBRATION_JSON)
        } else {
            prefs[KEY_HARDWARE_CALIBRATION_JSON] = json
        }
    }
}

suspend fun DataStore<Preferences>.readHostDeliveryChannel(): HostDeliveryChannel {
    val raw = data.map { it[KEY_HOST_DELIVERY_CHANNEL] }.first()
    return HostDeliveryChannel.fromPersisted(raw)
}

suspend fun DataStore<Preferences>.writeHostDeliveryChannel(channel: HostDeliveryChannel) {
    edit { prefs ->
        prefs[KEY_HOST_DELIVERY_CHANNEL] = channel.persisted()
    }
}

private val KEY_MAC_SLOT_CHANNEL = stringPreferencesKey("mac_slot_channel")

suspend fun DataStore<Preferences>.readMacSlotChannel(): HostDeliveryChannel {
    val raw = data.map { it[KEY_MAC_SLOT_CHANNEL] }.first()
    // Fall back to global channel for migration (users who had MAC_BRIDGE set)
    if (raw == null) {
        val global = data.map { it[KEY_HOST_DELIVERY_CHANNEL] }.first()
        return HostDeliveryChannel.fromPersisted(global)
    }
    return HostDeliveryChannel.fromPersisted(raw)
}

suspend fun DataStore<Preferences>.writeMacSlotChannel(channel: HostDeliveryChannel) {
    edit { prefs ->
        prefs[KEY_MAC_SLOT_CHANNEL] = channel.persisted()
    }
}

/**
 * Copies legacy `lan_server_host` / port / token into the Windows slot once, then sets the migration flag.
 * Safe to call repeatedly.
 */
suspend fun DataStore<Preferences>.migrateLanPlatformSlotsFromLegacyIfNeeded() {
    edit { prefs ->
        if (prefs[KEY_LAN_PLATFORM_SLOTS_MIGRATED] == true) return@edit
        val legacyHost = prefs[KEY_LAN_SERVER_HOST]?.trim().orEmpty()
        val legacyPort = prefs[KEY_LAN_SERVER_PORT] ?: DEFAULT_LAN_SERVER_PORT
        val legacyTok = prefs[KEY_LAN_PAIR_TOKEN]?.trim().orEmpty()
        if (legacyHost.isNotEmpty() && prefs[KEY_LAN_WIN_HOST].isNullOrBlank()) {
            prefs[KEY_LAN_WIN_HOST] = legacyHost
            prefs[KEY_LAN_WIN_PORT] = legacyPort.coerceIn(1, 65535)
            if (legacyTok.isNotEmpty()) {
                prefs[KEY_LAN_WIN_PAIR_TOKEN] = legacyTok
            }
        }
        prefs[KEY_LAN_PLATFORM_SLOTS_MIGRATED] = true
    }
}

suspend fun DataStore<Preferences>.readLanHostForPlatform(platform: HostPlatform): String {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    val snap = data.first()
    val v = when (platform) {
        HostPlatform.MAC -> snap[KEY_LAN_MAC_HOST]?.trim().orEmpty()
        HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> snap[KEY_LAN_WIN_HOST]?.trim().orEmpty()
    }
    if (v.isNotEmpty()) return v
    if (platform != HostPlatform.MAC) {
        return snap[KEY_LAN_SERVER_HOST]?.trim().orEmpty()
    }
    return ""
}

suspend fun DataStore<Preferences>.readLanPortForPlatform(platform: HostPlatform): Int {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    val snap = data.first()
    val p = when (platform) {
        HostPlatform.MAC -> snap[KEY_LAN_MAC_PORT]
        HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> snap[KEY_LAN_WIN_PORT]
    }
    if (p != null) return p.coerceIn(1, 65535)
    if (platform != HostPlatform.MAC) {
        return snap[KEY_LAN_SERVER_PORT] ?: DEFAULT_LAN_SERVER_PORT
    }
    return DEFAULT_LAN_SERVER_PORT
}

suspend fun DataStore<Preferences>.readLanPairTokenForPlatform(platform: HostPlatform): String {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    val snap = data.first()
    val v = when (platform) {
        HostPlatform.MAC -> snap[KEY_LAN_MAC_PAIR_TOKEN]?.trim().orEmpty()
        HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> snap[KEY_LAN_WIN_PAIR_TOKEN]?.trim().orEmpty()
    }
    if (v.isNotEmpty()) return v
    if (platform != HostPlatform.MAC) {
        return snap[KEY_LAN_PAIR_TOKEN]?.trim().orEmpty()
    }
    return ""
}

suspend fun DataStore<Preferences>.writeLanHostForPlatform(platform: HostPlatform, host: String) {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    edit { prefs ->
        when (platform) {
            HostPlatform.MAC -> prefs[KEY_LAN_MAC_HOST] = host.trim()
            HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> prefs[KEY_LAN_WIN_HOST] = host.trim()
        }
    }
}

suspend fun DataStore<Preferences>.writeLanPortForPlatform(platform: HostPlatform, port: Int) {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    val clamped = port.coerceIn(1, 65535)
    edit { prefs ->
        when (platform) {
            HostPlatform.MAC -> prefs[KEY_LAN_MAC_PORT] = clamped
            HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> prefs[KEY_LAN_WIN_PORT] = clamped
        }
    }
}

suspend fun DataStore<Preferences>.writeLanPairTokenForPlatform(platform: HostPlatform, token: String?) {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    edit { prefs ->
        when (platform) {
            HostPlatform.MAC -> {
                if (token.isNullOrBlank()) prefs.remove(KEY_LAN_MAC_PAIR_TOKEN)
                else prefs[KEY_LAN_MAC_PAIR_TOKEN] = token.trim()
            }
            HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> {
                if (token.isNullOrBlank()) prefs.remove(KEY_LAN_WIN_PAIR_TOKEN)
                else prefs[KEY_LAN_WIN_PAIR_TOKEN] = token.trim()
            }
        }
    }
}

suspend fun DataStore<Preferences>.readOnboardingCompleted(): Boolean {
    return data.map { it[KEY_ONBOARDING_COMPLETED] }.first() ?: false
}

suspend fun DataStore<Preferences>.writeOnboardingCompleted(done: Boolean) {
    edit { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] = done
    }
}

/**
 * When false, the app may show the full-screen PC link flow after first-run onboarding until LAN health succeeds
 * or the user skips. Migration: if key absent but onboarding is done and a LAN host was already saved, treat as skipped.
 */
suspend fun DataStore<Preferences>.migrateAndReadSkipInitialPcConnect(): Boolean {
    migrateLanPlatformSlotsFromLegacyIfNeeded()
    val snapshot = data.first()
    val existing = snapshot[KEY_SKIP_INITIAL_PC_CONNECT]
    if (existing != null) return existing
    val onboarded = snapshot[KEY_ONBOARDING_COMPLETED] ?: false
    val winH = snapshot[KEY_LAN_WIN_HOST].orEmpty().trim()
    val macH = snapshot[KEY_LAN_MAC_HOST].orEmpty().trim()
    val legH = snapshot[KEY_LAN_SERVER_HOST].orEmpty().trim()
    val host = winH.isNotBlank() || macH.isNotBlank() || legH.isNotBlank()
    val migratedSkip = onboarded && host
    edit { prefs ->
        prefs[KEY_SKIP_INITIAL_PC_CONNECT] = migratedSkip
    }
    return migratedSkip
}

suspend fun DataStore<Preferences>.writeSkipInitialPcConnect(skip: Boolean) {
    edit { prefs ->
        prefs[KEY_SKIP_INITIAL_PC_CONNECT] = skip
    }
}

suspend fun DataStore<Preferences>.readLanMobileDeviceId(): String {
    return data.map { it[KEY_LAN_MOBILE_DEVICE_ID] }.first().orEmpty()
}

suspend fun DataStore<Preferences>.writeLanMobileDeviceId(id: String) {
    edit { prefs ->
        prefs[KEY_LAN_MOBILE_DEVICE_ID] = id.trim()
    }
}

suspend fun DataStore<Preferences>.readDeckGridLayoutJson(): String? {
    return data.map { it[KEY_DECK_GRID_LAYOUT_JSON] }.first()
}

suspend fun DataStore<Preferences>.writeDeckGridLayoutJson(json: String) {
    edit { prefs ->
        prefs[KEY_DECK_GRID_LAYOUT_JSON] = json
    }
}

suspend fun DataStore<Preferences>.readAnimatedBackgroundMode(): AnimatedBackgroundMode {
    val raw = data.map { it[KEY_ANIMATED_BACKGROUND_MODE] }.first()
    return AnimatedBackgroundMode.fromPersisted(raw)
}

suspend fun DataStore<Preferences>.writeAnimatedBackgroundMode(mode: AnimatedBackgroundMode) {
    edit { prefs ->
        prefs[KEY_ANIMATED_BACKGROUND_MODE] = mode.persisted()
    }
}

suspend fun DataStore<Preferences>.readAnimatedBackgroundTheme(): AnimatedBackgroundTheme {
    val raw = data.map { it[KEY_ANIMATED_BACKGROUND_THEME] }.first()
    return AnimatedBackgroundTheme.fromPersisted(raw)
}

suspend fun DataStore<Preferences>.writeAnimatedBackgroundTheme(theme: AnimatedBackgroundTheme) {
    edit { prefs ->
        prefs[KEY_ANIMATED_BACKGROUND_THEME] = theme.persisted()
    }
}

private val KEY_KEEP_KEYBOARD_AWAKE = booleanPreferencesKey("keep_keyboard_awake")

suspend fun DataStore<Preferences>.readKeepKeyboardAwake(): Boolean {
    return data.map { it[KEY_KEEP_KEYBOARD_AWAKE] }.first() ?: false
}

suspend fun DataStore<Preferences>.writeKeepKeyboardAwake(enabled: Boolean) {
    edit { prefs ->
        prefs[KEY_KEEP_KEYBOARD_AWAKE] = enabled
    }
}
