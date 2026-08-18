package com.satoshiwatch.di

import android.content.Context
import androidx.room.Room
import com.satoshiwatch.core.crypto.DatabaseKeyManager
import com.satoshiwatch.data.local.AppDatabase
import com.satoshiwatch.data.local.dao.TransactionDao
import com.satoshiwatch.data.local.dao.WatchedAddressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room + SQLCipher: databáze je šifrovaná AES-256, passphrase je náhodná,
 * uložená pouze zabalená klíčem z Android KeyStore (viz [DatabaseKeyManager]).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = keyManager.getOrCreateDatabasePassphrase()
        return Room.databaseBuilder(context, AppDatabase::class.java, "satoshiwatch.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides
    fun provideWatchedAddressDao(db: AppDatabase): WatchedAddressDao = db.watchedAddressDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
}
