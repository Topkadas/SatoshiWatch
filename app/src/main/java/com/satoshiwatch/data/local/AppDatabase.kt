package com.satoshiwatch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.satoshiwatch.data.local.dao.TransactionDao
import com.satoshiwatch.data.local.dao.WatchedAddressDao
import com.satoshiwatch.data.local.entity.TransactionEntity
import com.satoshiwatch.data.local.entity.WatchedAddressEntity

@Database(
    entities = [WatchedAddressEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchedAddressDao(): WatchedAddressDao
    abstract fun transactionDao(): TransactionDao
}
