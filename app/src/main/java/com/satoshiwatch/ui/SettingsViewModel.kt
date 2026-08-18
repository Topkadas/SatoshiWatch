package com.satoshiwatch.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.satoshiwatch.data.settings.AppSettings
import com.satoshiwatch.data.settings.SettingsRepository
import com.satoshiwatch.service.TransactionWatchService
import com.satoshiwatch.worker.TransactionCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nastavení: vlastní uzel (REST/WS URL), SOCKS5 proxy (Orbot/Tor)
 * a režimy monitorování. Validační metody vrací text chyby, nebo null.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun saveNetworkSettings(apiUrl: String, wsUrl: String): String? {
        val trimmedApi = apiUrl.trim()
        val parsed = trimmedApi.toHttpUrlOrNull()
        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https")) {
            return "Neplatná URL adresa REST API (musí být http:// nebo https://)"
        }
        val trimmedWs = wsUrl.trim()
        if (!trimmedWs.startsWith("ws://") && !trimmedWs.startsWith("wss://")) {
            return "WebSocket URL musí začínat ws:// nebo wss://"
        }
        settingsRepository.update { it.copy(apiBaseUrl = trimmedApi, wsUrl = trimmedWs) }
        restartRealtimeIfRunning()
        return null
    }

    fun setProxyEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(proxyEnabled = enabled) }
        restartRealtimeIfRunning()
    }

    fun saveProxy(host: String, portText: String): String? {
        val port = portText.trim().toIntOrNull()
        if (port == null || port !in 1..65535) return "Neplatný port (1–65535)"
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return "Zadejte adresu proxy"
        settingsRepository.update { it.copy(proxyHost = trimmedHost, proxyPort = port) }
        restartRealtimeIfRunning()
        return null
    }

    fun setRealtimeEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(realtimeEnabled = enabled) }
        if (enabled) {
            TransactionWatchService.start(appContext)
        } else {
            TransactionWatchService.stop(appContext)
        }
    }

    fun setPeriodicEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(periodicEnabled = enabled) }
        if (enabled) {
            TransactionCheckWorker.schedule(appContext, settingsRepository.current.pollIntervalMinutes)
        } else {
            TransactionCheckWorker.cancel(appContext)
        }
    }

    fun setPollInterval(minutes: Long) {
        settingsRepository.update { it.copy(pollIntervalMinutes = minutes) }
        if (settingsRepository.current.periodicEnabled) {
            TransactionCheckWorker.schedule(appContext, minutes)
        }
    }

    /** Změna sítě/proxy se do běžící WS služby promítne restartem spojení. */
    private fun restartRealtimeIfRunning() {
        if (settingsRepository.current.realtimeEnabled) {
            TransactionWatchService.stop(appContext)
            TransactionWatchService.start(appContext)
        }
    }
}
