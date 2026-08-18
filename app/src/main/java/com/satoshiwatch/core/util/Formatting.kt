package com.satoshiwatch.core.util

import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Formátování částek, adres a časů pro UI a notifikace. */
object Formatting {

    private val czechLocale = Locale("cs", "CZ")

    /** 150000000 -> "1.5"; 1500 -> "0.000015" (bez ztráty přesnosti, bez vědecké notace). */
    fun satsToBtc(sats: Long): String {
        val btc = BigDecimal(sats).movePointLeft(8).stripTrailingZeros()
        return btc.toPlainString()
    }

    /** 1500000 -> "1 500 000" (skupinové oddělovače dle českého locale). */
    fun formatSats(sats: Long): String =
        NumberFormat.getIntegerInstance(czechLocale).format(sats)

    fun shortAddress(address: String): String =
        if (address.length <= 16) address else address.take(8) + "…" + address.takeLast(6)

    fun shortTxid(txid: String): String =
        if (txid.length <= 18) txid else txid.take(10) + "…" + txid.takeLast(6)

    fun formatTime(epochMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, czechLocale)
            .format(Date(epochMillis))
}
