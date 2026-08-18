package com.satoshiwatch.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satoshiwatch.R
import com.satoshiwatch.ui.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Nastavení: vlastní uzel (REST + WS), SOCKS5 proxy (Orbot/Tor),
 * režimy monitorování a odkaz na výjimku z optimalizace baterie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var apiUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
    var wsUrl by remember(settings.wsUrl) { mutableStateOf(settings.wsUrl) }
    var proxyHost by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPort by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }

    val savedMessage = stringResource(R.string.msg_settings_saved)
    fun report(error: String?) {
        scope.launch { snackbarHostState.showSnackbar(error ?: savedMessage) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------------------------------------------------- Síť
            SectionTitle(stringResource(R.string.settings_section_network))
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text(stringResource(R.string.settings_api_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = wsUrl,
                onValueChange = { wsUrl = it },
                label = { Text(stringResource(R.string.settings_ws_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { report(viewModel.saveNetworkSettings(apiUrl, wsUrl)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_save_network)) }

            HorizontalDivider()

            // --------------------------------------------------------- Proxy
            SectionTitle(stringResource(R.string.settings_section_proxy))
            SwitchRow(
                title = stringResource(R.string.settings_proxy_enable),
                checked = settings.proxyEnabled,
                onCheckedChange = { viewModel.setProxyEnabled(it) }
            )
            OutlinedTextField(
                value = proxyHost,
                onValueChange = { proxyHost = it },
                label = { Text(stringResource(R.string.settings_proxy_host)) },
                singleLine = true,
                enabled = settings.proxyEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proxyPort,
                onValueChange = { proxyPort = it },
                label = { Text(stringResource(R.string.settings_proxy_port)) },
                singleLine = true,
                enabled = settings.proxyEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { report(viewModel.saveProxy(proxyHost, proxyPort)) },
                enabled = settings.proxyEnabled,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_save_proxy)) }

            HorizontalDivider()

            // -------------------------------------------------- Monitorování
            SectionTitle(stringResource(R.string.settings_section_monitoring))
            SwitchRow(
                title = stringResource(R.string.settings_realtime),
                subtitle = stringResource(R.string.settings_realtime_desc),
                checked = settings.realtimeEnabled,
                onCheckedChange = { viewModel.setRealtimeEnabled(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_periodic),
                subtitle = stringResource(R.string.settings_periodic_desc),
                checked = settings.periodicEnabled,
                onCheckedChange = { viewModel.setPeriodicEnabled(it) }
            )
            Text(
                stringResource(R.string.settings_interval),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L).forEach { minutes ->
                    FilterChip(
                        selected = settings.pollIntervalMinutes == minutes,
                        onClick = { viewModel.setPollInterval(minutes) },
                        label = {
                            Text(stringResource(R.string.settings_interval_minutes, minutes))
                        },
                        enabled = settings.periodicEnabled
                    )
                }
            }

            HorizontalDivider()

            // ------------------------------------------------------- Baterie
            SectionTitle(stringResource(R.string.settings_section_battery))
            Text(
                stringResource(R.string.settings_battery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    // Otevře systémový seznam – změnu provádí sám uživatel
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_battery_button)) }

            HorizontalDivider()

            // ------------------------------------------------------ Soukromí
            SectionTitle(stringResource(R.string.settings_section_privacy))
            Text(
                stringResource(R.string.settings_privacy_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
