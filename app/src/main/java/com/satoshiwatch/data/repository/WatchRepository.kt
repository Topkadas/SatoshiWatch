package com.satoshiwatch.data.repository

import com.satoshiwatch.core.util.Formatting
import com.satoshiwatch.core.validation.AddressType
import com.satoshiwatch.data.local.dao.TransactionDao
import com.satoshiwatch.data.local.dao.WatchedAddressDao
import com.satoshiwatch.data.local.entity.TransactionEntity
import com.satoshiwatch.data.local.entity.WatchedAddressEntity
import com.satoshiwatch.data.remote.NetworkClientProvider
import com.satoshiwatch.data.remote.dto.TransactionDto
import com.satoshiwatch.domain.TransactionParser
import com.satoshiwatch.domain.model.ParsedTransaction
import com.satoshiwatch.domain.model.SyncResult
import com.satoshiwatch.notifications.NotificationHelper
import com.satoshiwatch.widget.WidgetRenderer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Jediný zdroj pravdy pro sledované adresy a jejich transakce.
 * Sdílený WorkManager workerem, foreground službou i UI vrstvou –
 * deduplikace notifikací probíhá nad šifrovanou Room databází.
 */
@Singleton
class WatchRepository @Inject constructor(
    private val addressDao: WatchedAddressDao,
    private val transactionDao: TransactionDao,
    private val network: NetworkClientProvider,
    private val parser: TransactionParser,
    private val notificationHelper: NotificationHelper,
    private val widgetRenderer: WidgetRenderer
) {

    fun observeAddresses(): Flow<List<WatchedAddressEntity>> = addressDao.observeAll()

    fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeRecent(100)

    suspend fun getWatchedAddresses(): List<WatchedAddressEntity> = addressDao.getAll()

    suspend fun isWatched(address: String): Boolean = addressDao.get(address) != null

    suspend fun addAddress(address: String, label: String, type: AddressType) {
        addressDao.insert(
            WatchedAddressEntity(
                address = address,
                label = label.ifBlank { Formatting.shortAddress(address) },
                type = type.name,
                createdAt = System.currentTimeMillis()
            )
        )
        // Prvotní import historie BEZ notifikací – staré transakce nesmí spustit poplach.
        // Případný výpadek sítě nevadí, doplní ho nejbližší synchronizace.
        try {
            syncAddress(address, notify = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // doplní nejbližší synchronizace
        }
        updateWidgetsSafely()
    }

    suspend fun removeAddress(address: String) {
        addressDao.delete(address)
        transactionDao.deleteForAddress(address)
        updateWidgetsSafely()
    }

    /** Synchronizace všech adres přes REST (worker, pull-to-refresh, nový blok). */
    suspend fun syncAll(notify: Boolean): SyncResult {
        val addresses = addressDao.getAll()
        var failed = 0
        for (entity in addresses) {
            try {
                syncAddress(entity.address, notify)
            } catch (e: CancellationException) {
                throw e // zrušení nesmí degradovat na "failed" a pokračovat
            } catch (_: Exception) {
                failed++
            }
        }
        updateWidgetsSafely()
        return SyncResult(totalAddresses = addresses.size, failedAddresses = failed)
    }

    suspend fun syncAddress(address: String, notify: Boolean) {
        val entity = addressDao.get(address) ?: return
        val api = network.api()

        val txs = api.getAddressTransactions(address)
        processTransactions(entity, txs, notify)

        val info = runCatching { api.getAddressInfo(address) }.getOrNull()
        if (info != null) {
            val funded = info.chainStats.fundedTxoSum + info.mempoolStats.fundedTxoSum
            val spent = info.chainStats.spentTxoSum + info.mempoolStats.spentTxoSum
            addressDao.updateStats(
                address = address,
                balanceSat = funded - spent,
                txCount = info.chainStats.txCount + info.mempoolStats.txCount,
                checkedAt = System.currentTimeMillis()
            )
        } else {
            addressDao.touch(address, System.currentTimeMillis())
        }
    }

    /** Zpracování transakcí přijatých push cestou (WebSocket) – prověří všechny adresy. */
    suspend fun processPushedTransactions(txs: List<TransactionDto>) {
        val addresses = addressDao.getAll()
        for (entity in addresses) {
            processTransactions(entity, txs, notify = true)
        }
        updateWidgetsSafely()
    }

    /** Chyba widgetu nesmí shodit synchronizaci; zrušení ale propouští dál. */
    private suspend fun updateWidgetsSafely() {
        try {
            widgetRenderer.updateAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // widget se překreslí při příští synchronizaci
        }
    }

    /**
     * Jádro detekce: nová transakce → notifikace; přechod mempool → blok
     * u již známé transakce → notifikace o potvrzení. Vše ostatní ticho.
     */
    suspend fun processTransactions(
        entity: WatchedAddressEntity,
        txs: List<TransactionDto>,
        notify: Boolean
    ) {
        for (tx in txs) {
            val parsed = parser.parse(tx, entity.address) ?: continue
            val existing = transactionDao.get(parsed.txid, entity.address)
            when {
                existing == null -> {
                    transactionDao.upsert(parsed.toEntity())
                    if (notify) {
                        notificationHelper.notifyTransaction(parsed, entity.label, confirmationUpdate = false)
                    }
                }

                !existing.confirmed && parsed.confirmed -> {
                    transactionDao.upsert(
                        existing.copy(
                            confirmed = true,
                            blockHeight = parsed.blockHeight,
                            timestamp = parsed.blockTime?.times(1000L) ?: existing.timestamp
                        )
                    )
                    if (notify) {
                        notificationHelper.notifyTransaction(parsed, entity.label, confirmationUpdate = true)
                    }
                }
            }
        }
    }

    private fun ParsedTransaction.toEntity() = TransactionEntity(
        txid = txid,
        address = address,
        direction = direction.name,
        amountSat = amountSat,
        deltaSat = deltaSat,
        feeSat = feeSat,
        confirmed = confirmed,
        blockHeight = blockHeight,
        timestamp = blockTime?.times(1000L) ?: System.currentTimeMillis()
    )
}
