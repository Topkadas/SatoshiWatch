package com.satoshiwatch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.satoshiwatch.MainActivity
import com.satoshiwatch.R
import com.satoshiwatch.core.locale.AppLocale
import com.satoshiwatch.core.util.Formatting
import com.satoshiwatch.data.local.dao.TransactionDao
import com.satoshiwatch.data.local.dao.WatchedAddressDao
import com.satoshiwatch.domain.model.TxDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stavba a aktualizace domovského widgetu: až 3 sledované adresy
 * (v pořadí přidání – první přidané bývají hlavní trezory), u každé
 * štítek, zůstatek a poslední pohyb. Volá se po každé synchronizaci
 * (worker, WebSocket služba, ruční obnovení) – widget je tak vždy čerstvý.
 */
@Singleton
class WidgetRenderer @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val addressDao: WatchedAddressDao,
    private val transactionDao: TransactionDao
) {

    /** Kontext obalený zvoleným jazykem – texty widgetu respektují volbu v Nastavení. */
    private val context: Context get() = AppLocale.wrap(appContext)

    private companion object {
        const val MAX_ROWS = 3
        const val COLOR_SECONDARY = 0xFF9AA5B1.toInt()
        const val COLOR_OUTGOING = 0xFFFF5252.toInt()
        const val COLOR_INCOMING = 0xFF4CAF50.toInt()
    }

    /** Překreslí všechny instance widgetu; bez instancí nedělá nic. */
    suspend fun updateAll(refreshing: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SatoshiWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, buildViews(refreshing))
    }

    private suspend fun buildViews(refreshing: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_satoshi)

        val updatedText = if (refreshing) {
            context.getString(R.string.widget_refreshing)
        } else {
            context.getString(
                R.string.widget_updated_at,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            )
        }
        views.setTextViewText(R.id.widget_updated, updatedText)

        // Tlačítko obnovení -> broadcast provideru, tělo widgetu -> otevření aplikace
        val refreshIntent = Intent(context, SatoshiWidgetProvider::class.java)
            .setAction(SatoshiWidgetProvider.ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val addresses = addressDao.getOldest(MAX_ROWS)
        val rowIds = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
        val labelIds = intArrayOf(
            R.id.widget_row1_label, R.id.widget_row2_label, R.id.widget_row3_label
        )
        val movementIds = intArrayOf(
            R.id.widget_row1_movement, R.id.widget_row2_movement, R.id.widget_row3_movement
        )
        val balanceIds = intArrayOf(
            R.id.widget_row1_balance, R.id.widget_row2_balance, R.id.widget_row3_balance
        )

        views.setViewVisibility(
            R.id.widget_empty,
            if (addresses.isEmpty()) View.VISIBLE else View.GONE
        )

        for (i in 0 until MAX_ROWS) {
            if (i >= addresses.size) {
                views.setViewVisibility(rowIds[i], View.GONE)
                continue
            }
            val address = addresses[i]
            views.setViewVisibility(rowIds[i], View.VISIBLE)
            views.setTextViewText(labelIds[i], address.label)
            views.setTextViewText(
                balanceIds[i],
                context.getString(R.string.balance_btc, Formatting.satsToBtc(address.balanceSat))
            )

            val lastTx = transactionDao.getLatestForAddress(address.address)
            if (lastTx == null) {
                views.setTextViewText(movementIds[i], context.getString(R.string.widget_no_movement))
                views.setTextColor(movementIds[i], COLOR_SECONDARY)
            } else {
                val outgoing = lastTx.direction == TxDirection.OUTGOING.name
                val arrow = if (outgoing) "↑" else "↓"
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    lastTx.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
                views.setTextViewText(movementIds[i], "$arrow $relativeTime")
                views.setTextColor(
                    movementIds[i],
                    if (outgoing) COLOR_OUTGOING else COLOR_INCOMING
                )
            }
        }
        return views
    }
}
