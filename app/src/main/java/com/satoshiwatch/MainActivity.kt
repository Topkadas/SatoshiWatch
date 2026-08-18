package com.satoshiwatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.satoshiwatch.data.settings.SettingsRepository
import com.satoshiwatch.service.TransactionWatchService
import com.satoshiwatch.ui.navigation.AppRoot
import com.satoshiwatch.ui.theme.SatoshiWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Jediná aktivita; celé UI je v Jetpack Compose. Žádné přihlašování, žádné účty. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Na Androidu 14+ nelze dataSync službu spustit z BOOT_COMPLETED;
        // proto se zapnuté real-time sledování obnovuje při otevření aplikace
        // (start je idempotentní – běžící službě jen doručí onStartCommand).
        if (settingsRepository.current.realtimeEnabled) {
            runCatching { TransactionWatchService.start(this) }
        }
        setContent {
            SatoshiWatchTheme {
                NotificationPermissionEffect()
                AppRoot()
            }
        }
    }
}

/** Na Androidu 13+ si při prvním spuštění vyžádá oprávnění k notifikacím. */
@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT >= 33) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* odmítnutí respektujeme – notifikace se pak tiše zahazují */ }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
