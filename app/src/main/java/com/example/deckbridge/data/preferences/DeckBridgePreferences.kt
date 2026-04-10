package com.example.deckbridge.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deckBridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "deck_bridge_settings")

private val KEY_HOST_PLATFORM = stringPreferencesKey("host_platform")

fun Context.deckBridgePreferences(): DataStore<Preferences> = deckBridgeDataStore

suspend fun DataStore<Preferences>.readPersistedHostPlatform(): HostPlatform {
    val raw = data.map { it[KEY_HOST_PLATFORM] }.first() ?: return HostPlatform.WINDOWS
    return when (raw) {
        "MAC" -> HostPlatform.MAC
        "WINDOWS" -> HostPlatform.WINDOWS
        else -> HostPlatform.WINDOWS
    }
}

suspend fun DataStore<Preferences>.writePersistedHostPlatform(platform: HostPlatform) {
    edit { prefs ->
        prefs[KEY_HOST_PLATFORM] = when (platform) {
            HostPlatform.MAC -> "MAC"
            HostPlatform.WINDOWS, HostPlatform.UNKNOWN -> "WINDOWS"
        }
    }
}
