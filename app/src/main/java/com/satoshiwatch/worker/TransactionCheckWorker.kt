package com.satoshiwatch.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.satoshiwatch.data.repository.WatchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Úsporné periodické skenování adres přes WorkManager (výchozí interval 15 min –
 * systémové minimum pro PeriodicWorkRequest). Doplňuje volitelnou WebSocket
 * službu; deduplikaci notifikací zajišťuje sdílená Room databáze.
 */
@HiltWorker
class TransactionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WatchRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = try {
            repository.syncAll(notify = true)
        } catch (e: CancellationException) {
            throw e // zastavení workeru musí zůstat kooperativní
        } catch (_: Exception) {
            return retryOrFail()
        }
        return if (outcome.isFullSuccess || outcome.totalAddresses == 0) {
            Result.success()
        } else {
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    companion object {
        private const val WORK_NAME = "satoshiwatch_periodic_check"
        private const val ONE_TIME_WORK_NAME = "satoshiwatch_manual_refresh"
        private const val MAX_RETRIES = 3

        /**
         * Jednorázová synchronizace – ruční obnovení z domovského widgetu.
         * Záměrně BEZ síťové constraint: offline běh selže per-adresa, ale
         * závěrečné překreslení widgetu smaže stav „Aktualizuji…“ (jinak by
         * widget zůstal viset na tomto textu, dokud se zařízení nepřipojí).
         */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<TransactionCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<TransactionCheckWorker>(
                intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
