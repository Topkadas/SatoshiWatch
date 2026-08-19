package com.satoshiwatch.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.satoshiwatch.worker.TransactionCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Domovský widget SatoshiWatch.
 *
 * Aktualizace přichází ze tří zdrojů:
 *  1. po každé synchronizaci (WorkManager / WebSocket služba) přes [WidgetRenderer],
 *  2. systémový interval widgetu (updatePeriodMillis, 30 min),
 *  3. ruční obnovení tlačítkem ve widgetu ([ACTION_REFRESH] → jednorázový worker).
 */
@AndroidEntryPoint
class SatoshiWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetRenderer: WidgetRenderer

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // DB čtení mimo hlavní vlákno; goAsync drží receiver naživu (limit 10 s)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { widgetRenderer.updateAll() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // injektuje závislosti a rozešle onUpdate
        if (intent.action == ACTION_REFRESH) {
            // Okamžitá zpětná vazba („Aktualizuji…“), pak plná synchronizace
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    runCatching { widgetRenderer.updateAll(refreshing = true) }
                } finally {
                    pendingResult.finish()
                }
            }
            TransactionCheckWorker.enqueueOneTime(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.satoshiwatch.action.WIDGET_REFRESH"
    }
}
