package com.satoshiwatch.domain.model

/** Směr transakce z pohledu sledované adresy. */
enum class TxDirection {
    /** Sledovaná adresa figuruje ve vstupech (vin) – prostředky se POHNULY. */
    OUTGOING,

    /** Sledovaná adresa figuruje pouze ve výstupech (vout). */
    INCOMING
}

/** Výsledek analýzy jedné transakce vůči jedné sledované adrese. */
data class ParsedTransaction(
    val txid: String,
    val address: String,
    val direction: TxDirection,
    /** Přesunutá částka v satoshi (odchozí: odesláno mimo adresu vč. změny; příchozí: přijato). */
    val amountSat: Long,
    /** Podepsaná změna zůstatku adresy (příjem − výdej), satoshi. */
    val deltaSat: Long,
    val feeSat: Long,
    val confirmed: Boolean,
    val blockHeight: Long?,
    /** Unixový čas bloku v sekundách (jen u potvrzených). */
    val blockTime: Long?
)

/** Výsledek hromadné synchronizace. */
data class SyncResult(val totalAddresses: Int, val failedAddresses: Int) {
    val isFullSuccess: Boolean get() = failedAddresses == 0
}
