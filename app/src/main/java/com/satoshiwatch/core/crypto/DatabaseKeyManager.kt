package com.satoshiwatch.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Správa šifrovacího klíče databáze (SQLCipher).
 *
 * Schéma: náhodná 32bajtová passphrase je vygenerována přes [SecureRandom]
 * a uložena POUZE v zašifrované podobě (AES-256-GCM) v SharedPreferences.
 * Obalovací (wrapping) klíč nikdy neopustí Android KeyStore – hardware-backed
 * tam, kde zařízení má StrongBox/TEE.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "satoshiwatch_db_wrapping_key"
        private const val PREFS_FILE = "satoshiwatch_keys"
        private const val PREF_WRAPPED_KEY = "wrapped_db_passphrase"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_LENGTH_BYTES = 32
    }

    /**
     * Vrátí (a při prvním spuštění vytvoří) passphrase pro SQLCipher.
     * Volat před prvním otevřením Room databáze.
     */
    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val wrappingKey = getOrCreateWrappingKey()

        prefs.getString(PREF_WRAPPED_KEY, null)?.let { stored ->
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = blob.copyOfRange(GCM_IV_LENGTH_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return cipher.doFinal(ciphertext)
        }

        val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val blob = cipher.iv + cipher.doFinal(passphrase)
        prefs.edit()
            .putString(PREF_WRAPPED_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
