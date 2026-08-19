package com.satoshiwatch.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.satoshiwatch.data.settings.AppSettings
import com.satoshiwatch.data.settings.SettingsRepository
import com.satoshiwatch.data.update.UpdateManager
import com.satoshiwatch.data.update.UpdateState
import com.satoshiwatch.service.TransactionWatchService
import com.satoshiwatch.worker.TransactionCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nastavení: vlastní uzel (REST/WS URL), SOCKS5 proxy (Orbot/Tor),
 * režimy monitorování a aktualizace aplikace. Validační metody vrací
 * text chyby, nebo null.
 *
 * Stav aktualizace jen zrcadlí ze singleton [UpdateManager] – stahování
 * tak přežije opuštění obrazovky (ViewModel je vázaný na back-stack entry).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val updateState: StateFlow<UpdateState> = updateManager.state
    val currentVersionName: String get() = updateManager.currentVersionName

    // ------------------------------------------------------------------ Síť

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

    // --------------------------------------------------------- Monitorování

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

    // ----------------------------------------------- Aktualizace z GitHubu

    fun checkForUpdate() = updateManager.checkForUpdate()

    fun downloadUpdate() = updateManager.downloadUpdate()

    fun cancelDownload() = updateManager.cancelDownload()

    fun installUpdate() = updateManager.installUpdate()

    /** „Zkontrolovat znovu“ – reset a nová kontrola. */
    fun recheckUpdate() {
        updateManager.resetState()
        updateManager.checkForUpdate()
    }
}
