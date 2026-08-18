package com.satoshiwatch.data.local.entity

import androidx.room.Entity

/**
 * Zaznamenaná transakce dotýkající se sledované adresy.
 * Složený klíč (txid, address): jedna transakce může zasáhnout více sledovaných adres.
 */
@Entity(tableName = "transactions", primaryKeys = ["txid", "address"])
data class TransactionEntity(
    val txid: String,
    val address: String,
    /** Název hodnoty [com.satoshiwatch.domain.model.TxDirection]. */
    val direction: String,
    /** Přesunutá částka v satoshi (u odchozí = odesláno mimo adresu, u příchozí = přijato). */
    val amountSat: Long,
    /** Podepsaná změna zůstatku adresy v satoshi (příjem − výdej). */
    val deltaSat: Long,
    val feeSat: Long,
    val confirmed: Boolean,
    val blockHeight: Long?,
    /** Čas bloku (potvrzená) nebo čas prvního zaznamenání (mempool), epoch ms. */
    val timestamp: Long
)
