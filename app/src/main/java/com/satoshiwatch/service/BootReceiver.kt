package com.satoshiwatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.satoshiwatch.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Po restartu zařízení obnoví foreground WebSocket službu, pokud ji má
 * uživatel zapnutou. Periodický WorkManager přežívá reboot sám.
 *
 * Android 14+ (targetSdk 34) zakazuje start dataSync foreground služby
 * z BOOT_COMPLETED – tam se spoléhá na periodickou kontrolu a služba se
 * obnoví při příštím otevření aplikace (viz MainActivity).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT >= 34) return
        if (settingsRepository.current.realtimeEnabled) {
            runCatching { TransactionWatchService.start(context) }
        }
    }
}
