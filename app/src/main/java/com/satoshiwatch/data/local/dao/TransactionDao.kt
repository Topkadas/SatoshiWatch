package com.satoshiwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.satoshiwatch.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE txid = :txid AND address = :address")
    suspend fun get(txid: String, address: String): TransactionEntity?

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE address = :address")
    suspend fun deleteForAddress(address: String)
}
