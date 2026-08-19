package com.satoshiwatch.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satoshiwatch.R
import com.satoshiwatch.data.settings.AppSettings
import com.satoshiwatch.data.settings.SettingsRepository
import com.satoshiwatch.data.update.UpdateManager
import com.satoshiwatch.data.update.UpdateState
import com.satoshiwatch.service.TransactionWatchService
import com.satoshiwatch.widget.WidgetRenderer
import com.satoshiwatch.worker.TransactionCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nastavení: vlastní uzel (REST/WS URL), SOCKS5 proxy (Orbot/Tor),
 * režimy monitorování, jazyk a aktualizace aplikace. Validační metody
 * vrací string resource chyby, nebo null.
 *
 * Stav aktualizace jen zrcadlí ze singleton [UpdateManager] – stahování
 * tak přežije opuštění obrazovky (ViewModel je vázaný na back-stack entry).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
    private val widgetRenderer: WidgetRenderer,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val updateState: StateFlow<UpdateState> = updateManager.state
    val currentVersionName: String get() = updateManager.currentVersionName

    // ------------------------------------------------------------------ Síť

    @StringRes
    fun saveNetworkSettings(apiUrl: String, wsUrl: String): Int? {
        val trimmedApi = apiUrl.trim()
        val parsed = trimmedApi.toHttpUrlOrNull()
        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https")) {
            return R.string.error_invalid_api_url
        }
        val trimmedWs = wsUrl.trim()
        if (!trimmedWs.startsWith("ws://") && !trimmedWs.startsWith("wss://")) {
            return R.string.error_invalid_ws_url
        }
        settingsRepository.update { it.copy(apiBaseUrl = trimmedApi, wsUrl = trimmedWs) }
        restartRealtimeIfRunning()
        return null
    }

    fun setProxyEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(proxyEnabled = enabled) }
        restartRealtimeIfRunning()
    }

    @StringRes
    fun saveProxy(host: String, portText: String): Int? {
        val port = portText.trim().toIntOrNull()
        if (port == null || port !in 1..65535) return R.string.error_invalid_port
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return R.string.error_proxy_host
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

    // ----------------------------------------------------------------- Jazyk

    /** Po změně jazyka překreslí widget (jeho texty žijí mimo aktivitu). */
    fun onLanguageChanged() {
        viewModelScope.launch {
            runCatching { widgetRenderer.updateAll() }
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
