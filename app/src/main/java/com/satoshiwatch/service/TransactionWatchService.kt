package com.satoshiwatch.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.satoshiwatch.R
import com.satoshiwatch.data.remote.MempoolWebSocketListener
import com.satoshiwatch.data.remote.NetworkClientProvider
import com.satoshiwatch.data.repository.WatchRepository
import com.satoshiwatch.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.WebSocket

/**
 * Foreground služba s trvalým WebSocket spojením na mempool.space kompatibilní
 * uzel – okamžité notifikace bez zpoždění Doze režimu.
 *
 * Protokol: po otevření pošle {"action":"want","data":["blocks"]} a
 * {"track-addresses":[…]}; server pak push-uje "address-transactions" /
 * "multi-address-transactions" a "block". Při novém bloku se navíc spouští
 * REST synchronizace, která spolehlivě zachytí potvrzení (i RBF/reorg).
 */
@AndroidEntryPoint
class TransactionWatchService : Service() {

    @Inject lateinit var repository: WatchRepository
    @Inject lateinit var network: NetworkClientProvider
    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var trackedAddresses: List<String> = emptyList()
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    @Volatile private var stopped = false

    override fun onCreate() {
        super.onCreate()
        // ForegroundServiceStartNotAllowedException (např. start z pozadí na
        // Androidu 14+) nesmí shodit aplikaci – služba se pak jen tiše ukončí
        // a monitorování zajistí periodický WorkManager.
        try {
            startForeground(
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification(getString(R.string.service_state_connecting))
            )
        } catch (e: Exception) {
            stopped = true
            stopSelf()
            return
        }
        // Reaguje na přidání/odebrání adresy za běhu služby
        serviceScope.launch {
            repository.observeAddresses().collect { list ->
                trackedAddresses = list.map { it.address }
                webSocket?.let { sendTrackMessage(it) }
            }
        }
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        webSocket?.close(NORMAL_CLOSURE, "service stopped")
        webSocket = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connect() {
        if (stopped) return
        val request = Request.Builder().url(network.wsUrl()).build()
        val listener = MempoolWebSocketListener(
            json = network.json,
            onConnected = { ws ->
                reconnectAttempts = 0
                notificationHelper.updateServiceNotification(getString(R.string.service_state_connected))
                ws.send("""{"action":"want","data":["blocks"]}""")
                sendTrackMessage(ws)
            },
            onTransactions = { txs ->
                serviceScope.launch {
                    runCatching { repository.processPushedTransactions(txs) }
                }
            },
            onNewBlock = { height ->
                serviceScope.launch {
                    height?.let {
                        notificationHelper.updateServiceNotification(
                            getString(R.string.service_state_block, it)
                        )
                    }
                    // Záchytná síť: REST sweep potvrdí transakce, které push minul
                    runCatching { repository.syncAll(notify = true) }
                    notificationHelper.updateServiceNotification(
                        getString(R.string.service_state_connected)
                    )
                }
            },
            onDisconnected = { scheduleReconnect() }
        )
        webSocket = network.okHttpClient().newWebSocket(request, listener)
    }

    private fun sendTrackMessage(ws: WebSocket) {
        val addresses = trackedAddresses
        if (addresses.isEmpty()) return
        val payload = buildJsonObject {
            put("track-addresses", buildJsonArray { addresses.forEach { add(it) } })
        }
        ws.send(payload.toString())
    }

    /** Exponenciální backoff 1 s → 60 s; jediný naplánovaný pokus současně. */
    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            val delaySeconds = (1L shl reconnectAttempts.coerceAtMost(6)).coerceAtMost(60L)
            reconnectAttempts++
            notificationHelper.updateServiceNotification(
                getString(R.string.service_state_reconnecting, delaySeconds)
            )
            delay(delaySeconds * 1000L)
            webSocket?.cancel()
            connect()
        }
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, TransactionWatchService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TransactionWatchService::class.java))
        }
    }
}
