package com.satoshiwatch.domain

import com.satoshiwatch.data.remote.dto.TransactionDto
import com.satoshiwatch.domain.model.ParsedTransaction
import com.satoshiwatch.domain.model.TxDirection
import javax.inject.Inject

/**
 * Určuje směr transakce a změnu bilance sledované adresy z JSON odpovědi uzlu.
 *
 * Pravidla detekce:
 *  - ODCHOZÍ: adresa se objevuje v poli vin (prevout.scriptpubkey_address).
 *    To platí i pro „self-transfer“ – jakýkoli pohyb ze sledované (trezorové)
 *    adresy je bezpečnostní událost.
 *  - PŘÍCHOZÍ: adresa se objevuje pouze v poli vout.
 */
class TransactionParser @Inject constructor() {

    /** Vrací null, pokud se transakce sledované adresy vůbec netýká. */
    fun parse(tx: TransactionDto, watchedAddress: String): ParsedTransaction? {
        var spentSat = 0L
        var receivedSat = 0L

        for (vin in tx.vin) {
            val prevout = vin.prevout ?: continue // coinbase vstup
            if (prevout.scriptPubKeyAddress == watchedAddress) {
                spentSat += prevout.value
            }
        }
        for (vout in tx.vout) {
            if (vout.scriptPubKeyAddress == watchedAddress) {
                receivedSat += vout.value
            }
        }

        if (spentSat == 0L && receivedSat == 0L) return null

        val direction = if (spentSat > 0L) TxDirection.OUTGOING else TxDirection.INCOMING
        val amountSat = when (direction) {
            // odesláno mimo adresu: utraceno minus vrácená změna (change output)
            TxDirection.OUTGOING -> (spentSat - receivedSat).coerceAtLeast(0L)
            TxDirection.INCOMING -> receivedSat
        }

        return ParsedTransaction(
            txid = tx.txid,
            address = watchedAddress,
            direction = direction,
            amountSat = amountSat,
            deltaSat = receivedSat - spentSat,
            feeSat = tx.fee,
            confirmed = tx.status.confirmed,
            blockHeight = tx.status.blockHeight,
            blockTime = tx.status.blockTime
        )
    }
}
