package com.satoshiwatch

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.satoshiwatch.data.settings.SettingsRepository
import com.satoshiwatch.notifications.NotificationHelper
import com.satoshiwatch.worker.TransactionCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Aplikace bez jakékoli telemetrie: žádný Firebase, žádná analytika,
 * žádné crash reportery. WorkManager je inicializován ručně přes Hilt.
 */
@HiltAndroidApp
class SatoshiWatchApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        // Zajistí naplánování periodické kontroly podle uložených nastavení
        val settings = settingsRepository.current
        if (settings.periodicEnabled) {
            TransactionCheckWorker.schedule(this, settings.pollIntervalMinutes)
        }
    }
}
