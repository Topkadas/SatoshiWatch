package com.satoshiwatch.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kompletní uživatelská konfigurace aplikace. */
data class AppSettings(
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val wsUrl: String = DEFAULT_WS_URL,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "127.0.0.1",
    /** Výchozí port Orbot SOCKS5. */
    val proxyPort: Int = 9050,
    /** Foreground služba s trvalým WebSocket spojením. */
    val realtimeEnabled: Boolean = false,
    /** Periodické skenování přes WorkManager. */
    val periodicEnabled: Boolean = true,
    val pollIntervalMinutes: Long = 15L
) {
    companion object {
        const val DEFAULT_API_BASE_URL = "https://mempool.space/api/"
        const val DEFAULT_WS_URL = "wss://mempool.space/api/v1/ws"
    }
}

/**
 * Nastavení v EncryptedSharedPreferences (AES-256-GCM hodnoty, AES-256-SIV klíče,
 * master key v Android KeyStore). Změny publikuje jako [StateFlow].
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private companion object {
        const val PREFS_FILE = "satoshiwatch_settings"
        const val KEY_API_URL = "api_base_url"
        const val KEY_WS_URL = "ws_url"
        const val KEY_PROXY_ENABLED = "proxy_enabled"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_REALTIME = "realtime_enabled"
        const val KEY_PERIODIC = "periodic_enabled"
        const val KEY_POLL_INTERVAL = "poll_interval_minutes"
    }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            apiBaseUrl = prefs.getString(KEY_API_URL, null) ?: defaults.apiBaseUrl,
            wsUrl = prefs.getString(KEY_WS_URL, null) ?: defaults.wsUrl,
            proxyEnabled = prefs.getBoolean(KEY_PROXY_ENABLED, defaults.proxyEnabled),
            proxyHost = prefs.getString(KEY_PROXY_HOST, null) ?: defaults.proxyHost,
            proxyPort = prefs.getInt(KEY_PROXY_PORT, defaults.proxyPort),
            realtimeEnabled = prefs.getBoolean(KEY_REALTIME, defaults.realtimeEnabled),
            periodicEnabled = prefs.getBoolean(KEY_PERIODIC, defaults.periodicEnabled),
            pollIntervalMinutes = prefs.getLong(KEY_POLL_INTERVAL, defaults.pollIntervalMinutes)
        ).normalized()
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value).normalized()
        prefs.edit()
            .putString(KEY_API_URL, updated.apiBaseUrl)
            .putString(KEY_WS_URL, updated.wsUrl)
            .putBoolean(KEY_PROXY_ENABLED, updated.proxyEnabled)
            .putString(KEY_PROXY_HOST, updated.proxyHost)
            .putInt(KEY_PROXY_PORT, updated.proxyPort)
            .putBoolean(KEY_REALTIME, updated.realtimeEnabled)
            .putBoolean(KEY_PERIODIC, updated.periodicEnabled)
            .putLong(KEY_POLL_INTERVAL, updated.pollIntervalMinutes)
            .apply()
        _settings.value = updated
    }

    private fun AppSettings.normalized(): AppSettings = copy(
        // Retrofit vyžaduje base URL končící lomítkem
        apiBaseUrl = apiBaseUrl.trim().let { if (it.endsWith("/")) it else "$it/" },
        wsUrl = wsUrl.trim(),
        proxyHost = proxyHost.trim(),
        pollIntervalMinutes = pollIntervalMinutes.coerceAtLeast(15L)
    )
}
