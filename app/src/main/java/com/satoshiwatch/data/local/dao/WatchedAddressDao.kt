package com.satoshiwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satoshiwatch.data.local.entity.WatchedAddressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedAddressDao {

    @Query("SELECT * FROM watched_addresses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WatchedAddressEntity>>

    @Query("SELECT * FROM watched_addresses ORDER BY createdAt DESC")
    suspend fun getAll(): List<WatchedAddressEntity>

    @Query("SELECT * FROM watched_addresses WHERE address = :address")
    suspend fun get(address: String): WatchedAddressEntity?

    /** Nejstarší adresy (první přidané = hlavní trezory) – pro domovský widget. */
    @Query("SELECT * FROM watched_addresses ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOldest(limit: Int): List<WatchedAddressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WatchedAddressEntity)

    @Query("DELETE FROM watched_addresses WHERE address = :address")
    suspend fun delete(address: String)

    @Query(
        "UPDATE watched_addresses SET balanceSat = :balanceSat, txCount = :txCount, " +
            "lastCheckedAt = :checkedAt WHERE address = :address"
    )
    suspend fun updateStats(address: String, balanceSat: Long, txCount: Int, checkedAt: Long)

    @Query("UPDATE watched_addresses SET lastCheckedAt = :checkedAt WHERE address = :address")
    suspend fun touch(address: String, checkedAt: Long)
}
