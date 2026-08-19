package com.satoshiwatch.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.satoshiwatch.MainActivity
import com.satoshiwatch.R
import com.satoshiwatch.core.locale.AppLocale
import com.satoshiwatch.core.util.Formatting
import com.satoshiwatch.domain.model.ParsedTransaction
import com.satoshiwatch.domain.model.TxDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notifikační kanály a stavba všech Android notifikací aplikace. */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    /** Kontext obalený zvoleným jazykem – notifikace respektují volbu v Nastavení. */
    private val context: Context get() = AppLocale.wrap(appContext)

    companion object {
        const val CHANNEL_OUTGOING = "channel_outgoing_tx"
        const val CHANNEL_INCOMING = "channel_incoming_tx"
        const val CHANNEL_SERVICE = "channel_watch_service"
        const val SERVICE_NOTIFICATION_ID = 1
    }

    /** Idempotentní – volá se při startu aplikace. */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val outgoing = NotificationChannel(
            CHANNEL_OUTGOING,
            context.getString(R.string.channel_outgoing_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_outgoing_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val incoming = NotificationChannel(
            CHANNEL_INCOMING,
            context.getString(R.string.channel_incoming_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_incoming_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(outgoing, incoming, service))
    }

    fun buildServiceNotification(stateText: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_satoshi)
            .setContentTitle(context.getString(R.string.service_notif_title))
            .setContentText(stateText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainActivityIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    fun updateServiceNotification(stateText: String) {
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context)
            .notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(stateText))
    }

    /**
     * Notifikace o transakci: typ (ODCHOZÍ/PŘÍCHOZÍ), částka v BTC i sat,
     * štítek adresy, stav (mempool/blok) a zkrácený TXID.
     */
    fun notifyTransaction(tx: ParsedTransaction, addressLabel: String, confirmationUpdate: Boolean) {
        if (!canPostNotifications()) return

        val outgoing = tx.direction == TxDirection.OUTGOING
        val channel = if (outgoing) CHANNEL_OUTGOING else CHANNEL_INCOMING

        val title = when {
            outgoing && confirmationUpdate -> context.getString(R.string.notif_outgoing_confirmed_title)
            outgoing -> context.getString(R.string.notif_outgoing_title)
            confirmationUpdate -> context.getString(R.string.notif_incoming_confirmed_title)
            else -> context.getString(R.string.notif_incoming_title)
        }

        val sign = if (outgoing) "−" else "+"
        val amountLine = context.getString(
            R.string.notif_amount_line,
            addressLabel,
            sign + Formatting.satsToBtc(tx.amountSat),
            sign + Formatting.formatSats(tx.amountSat)
        )
        val statusText = if (tx.confirmed) {
            context.getString(R.string.status_confirmed_height, tx.blockHeight ?: 0L)
        } else {
            context.getString(R.string.status_in_mempool)
        }
        val bigText = buildString {
            append(amountLine)
            append('\n').append(context.getString(R.string.notif_status_line, statusText))
            append('\n').append(context.getString(R.string.notif_txid_line, Formatting.shortTxid(tx.txid)))
            if (outgoing && tx.feeSat > 0) {
                append('\n').append(
                    context.getString(R.string.notif_fee_line, Formatting.formatSats(tx.feeSat))
                )
            }
        }

        // Na zamčené obrazovce se nezobrazují částky ani adresy
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_satoshi)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_public_fallback))
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_satoshi)
            .setContentTitle(title)
            .setContentText(amountLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (outgoing) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (outgoing) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(mainActivityIntent())
            .build()

        // Stabilní ID: mempool fáze a potvrzení mají vlastní notifikaci,
        // opakované zpracování téže fáze notifikaci jen tiše nahradí.
        val phase = if (confirmationUpdate) "confirmed" else "seen"
        val id = (tx.txid + tx.address + phase).hashCode()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun mainActivityIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
