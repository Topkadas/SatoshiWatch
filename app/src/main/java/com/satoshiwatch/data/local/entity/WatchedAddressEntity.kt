package com.satoshiwatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Sledovaná adresa se soukromým štítkem; uloženo v šifrované DB (SQLCipher). */
@Entity(tableName = "watched_addresses")
data class WatchedAddressEntity(
    @PrimaryKey val address: String,
    val label: String,
    /** Název hodnoty [com.satoshiwatch.core.validation.AddressType]. */
    val type: String,
    val createdAt: Long,
    /** Aktuální zůstatek v satoshi (chain + mempool), aktualizuje se při synchronizaci. */
    val balanceSat: Long = 0L,
    val txCount: Int = 0,
    val lastCheckedAt: Long = 0L
)
