package com.satoshiwatch.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Transakce ve formátu mempool.space / esplora REST i WebSocket API. */
@Serializable
data class TransactionDto(
    val txid: String,
    val version: Int = 0,
    val locktime: Long = 0L,
    val vin: List<VinDto> = emptyList(),
    val vout: List<VoutDto> = emptyList(),
    val size: Int = 0,
    val weight: Int = 0,
    val fee: Long = 0L,
    val status: TxStatusDto = TxStatusDto()
)

@Serializable
data class VinDto(
    val txid: String? = null,
    val vout: Int? = null,
    /** U coinbase vstupů chybí. */
    val prevout: VoutDto? = null,
    @SerialName("is_coinbase") val isCoinbase: Boolean = false,
    val sequence: Long = 0L
)

@Serializable
data class VoutDto(
    @SerialName("scriptpubkey") val scriptPubKey: String? = null,
    @SerialName("scriptpubkey_type") val scriptPubKeyType: String? = null,
    @SerialName("scriptpubkey_address") val scriptPubKeyAddress: String? = null,
    /** Hodnota výstupu v satoshi. */
    val value: Long = 0L
)

@Serializable
data class TxStatusDto(
    val confirmed: Boolean = false,
    @SerialName("block_height") val blockHeight: Long? = null,
    @SerialName("block_hash") val blockHash: String? = null,
    @SerialName("block_time") val blockTime: Long? = null
)

/** Souhrn adresy z GET /address/{address} – zdroj pro zůstatek. */
@Serializable
data class AddressInfoDto(
    val address: String = "",
    @SerialName("chain_stats") val chainStats: AddressStatsDto = AddressStatsDto(),
    @SerialName("mempool_stats") val mempoolStats: AddressStatsDto = AddressStatsDto()
)

@Serializable
data class AddressStatsDto(
    @SerialName("funded_txo_count") val fundedTxoCount: Int = 0,
    @SerialName("funded_txo_sum") val fundedTxoSum: Long = 0L,
    @SerialName("spent_txo_count") val spentTxoCount: Int = 0,
    @SerialName("spent_txo_sum") val spentTxoSum: Long = 0L,
    @SerialName("tx_count") val txCount: Int = 0
)
