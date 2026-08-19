package com.satoshiwatch.core.validation

import androidx.annotation.StringRes
import com.satoshiwatch.R
import java.security.MessageDigest
import java.util.Locale

/** Typ bitcoinové adresy rozpoznaný validací. */
enum class AddressType(val label: String) {
    P2PKH("Legacy (P2PKH)"),
    P2SH("Nested SegWit (P2SH)"),
    P2WPKH("Native SegWit (P2WPKH)"),
    P2WSH("Native SegWit (P2WSH)"),
    P2TR("Taproot (P2TR)")
}

sealed class ValidationResult {
    /** [normalized] je kanonický tvar adresy (bech32 vždy malými písmeny). */
    data class Valid(val normalized: String, val type: AddressType) : ValidationResult()

    /** Důvod jako string resource – UI ho lokalizuje dle zvoleného jazyka. */
    data class Invalid(@StringRes val reasonRes: Int) : ValidationResult()
}

/**
 * Offline validace bitcoinových adres (mainnet) včetně kontrolních součtů:
 *  - Base58Check (P2PKH „1…“, P2SH „3…“) – dvojitý SHA-256 checksum
 *  - Bech32 (BIP-173, SegWit v0 „bc1q…“) a Bech32m (BIP-350, Taproot „bc1p…“)
 * Žádná data neopouštějí zařízení.
 */
object BitcoinAddressValidator {

    private const val BASE58_ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR =
        intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3
    private const val MAINNET_HRP = "bc"

    /** Odstraní BIP-21 obal („bitcoin:adresa?amount=…“) a bílé znaky. */
    fun sanitize(raw: String): String {
        var s = raw.trim()
        if (s.lowercase(Locale.ROOT).startsWith("bitcoin:")) {
            s = s.substring("bitcoin:".length)
        }
        val query = s.indexOf('?')
        if (query >= 0) s = s.substring(0, query)
        return s.trim()
    }

    fun validate(input: String): ValidationResult {
        val address = sanitize(input)
        if (address.isEmpty()) return ValidationResult.Invalid(R.string.val_err_empty)
        return when {
            address.startsWith("1") || address.startsWith("3") -> validateBase58(address)
            address.lowercase(Locale.ROOT).startsWith("bc1") -> validateBech32(address)
            else -> ValidationResult.Invalid(R.string.val_err_unknown_format)
        }
    }

    // ---------------------------------------------------------------- Base58

    private fun validateBase58(address: String): ValidationResult {
        if (address.length !in 26..35) {
            return ValidationResult.Invalid(R.string.val_err_legacy_length)
        }
        val decoded = base58Decode(address)
            ?: return ValidationResult.Invalid(R.string.val_err_base58_char)
        if (decoded.size != 25) {
            return ValidationResult.Invalid(R.string.val_err_decoded_length)
        }
        val payload = decoded.copyOfRange(0, 21)
        val checksum = decoded.copyOfRange(21, 25)
        val expected = sha256(sha256(payload)).copyOfRange(0, 4)
        if (!expected.contentEquals(checksum)) {
            return ValidationResult.Invalid(R.string.val_err_checksum)
        }
        return when (decoded[0].toInt() and 0xFF) {
            0x00 -> ValidationResult.Valid(address, AddressType.P2PKH)
            0x05 -> ValidationResult.Valid(address, AddressType.P2SH)
            else -> ValidationResult.Invalid(R.string.val_err_version)
        }
    }

    private fun base58Decode(input: String): ByteArray? {
        val digits = IntArray(input.length)
        for (i in input.indices) {
            val idx = BASE58_ALPHABET.indexOf(input[i])
            if (idx < 0) return null
            digits[i] = idx
        }
        var zeros = 0
        while (zeros < input.length && input[zeros] == '1') zeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < digits.size) {
            decoded[--outputStart] = divmod(digits, inputStart, 58, 256)
            if (digits[inputStart] == 0) inputStart++
        }
        // přeskočí nadbytečné nulové bajty vzniklé převodem
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return ByteArray(zeros) + decoded.copyOfRange(outputStart, decoded.size)
    }

    /** Jeden krok dělení velkého čísla v bázi [base] číslem [divisor]; vrací zbytek. */
    private fun divmod(number: IntArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val temp = remainder * base + number[i]
            number[i] = temp / divisor
            remainder = temp % divisor
        }
        return remainder.toByte()
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    // ---------------------------------------------------------------- Bech32

    private fun validateBech32(address: String): ValidationResult {
        // Rozsah znaků se ověřuje PŘED lowercase: Unicode mapování (např. U+212A
        // KELVIN SIGN → „k“) by jinak propašovalo znak mimo BIP-173 rozsah 33–126.
        if (address.any { it.code < 33 || it.code > 126 }) {
            return ValidationResult.Invalid(R.string.val_err_invalid_char)
        }
        val hasUpper = address.any { it in 'A'..'Z' }
        val hasLower = address.any { it in 'a'..'z' }
        if (hasUpper && hasLower) {
            return ValidationResult.Invalid(R.string.val_err_mixed_case)
        }
        val addr = address.lowercase(Locale.ROOT)
        if (addr.length > 90) return ValidationResult.Invalid(R.string.val_err_too_long)

        val sep = addr.lastIndexOf('1')
        if (sep < 1 || sep + 7 > addr.length) {
            return ValidationResult.Invalid(R.string.val_err_bech32_format)
        }
        val hrp = addr.substring(0, sep)
        if (hrp != MAINNET_HRP) {
            return ValidationResult.Invalid(R.string.val_err_not_mainnet)
        }
        val dataPart = addr.substring(sep + 1)
        val data = IntArray(dataPart.length)
        for (i in dataPart.indices) {
            val idx = BECH32_CHARSET.indexOf(dataPart[i])
            if (idx < 0) return ValidationResult.Invalid(R.string.val_err_bech32_char)
            data[i] = idx
        }
        val encoding = verifyChecksum(hrp, data)
            ?: return ValidationResult.Invalid(R.string.val_err_checksum)
        if (data.size < 7) return ValidationResult.Invalid(R.string.val_err_too_short)

        val witnessVersion = data[0]
        if (witnessVersion > 16) {
            return ValidationResult.Invalid(R.string.val_err_witness_version)
        }
        val program = convertBits(data.copyOfRange(1, data.size - 6))
            ?: return ValidationResult.Invalid(R.string.val_err_program_alignment)
        if (program.size < 2 || program.size > 40) {
            return ValidationResult.Invalid(R.string.val_err_program_length)
        }

        if (witnessVersion == 0) {
            if (encoding != BECH32_CONST) {
                return ValidationResult.Invalid(R.string.val_err_v0_encoding)
            }
            return when (program.size) {
                20 -> ValidationResult.Valid(addr, AddressType.P2WPKH)
                32 -> ValidationResult.Valid(addr, AddressType.P2WSH)
                else -> ValidationResult.Invalid(R.string.val_err_v0_program_length)
            }
        }
        if (encoding != BECH32M_CONST) {
            return ValidationResult.Invalid(R.string.val_err_v1_encoding)
        }
        return if (witnessVersion == 1 && program.size == 32) {
            ValidationResult.Valid(addr, AddressType.P2TR)
        } else {
            ValidationResult.Invalid(R.string.val_err_future_version)
        }
    }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) {
                if ((top ushr i) and 1 == 1) chk = chk xor GENERATOR[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val result = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) result[i] = hrp[i].code ushr 5
        result[hrp.length] = 0
        for (i in hrp.indices) result[hrp.length + 1 + i] = hrp[i].code and 31
        return result
    }

    /** Vrací konstantu kódování (1 = bech32, 0x2bc830a3 = bech32m), jinak null. */
    private fun verifyChecksum(hrp: String, data: IntArray): Int? =
        when (polymod(hrpExpand(hrp) + data)) {
            BECH32_CONST -> BECH32_CONST
            BECH32M_CONST -> BECH32M_CONST
            else -> null
        }

    /** Převod 5bitových skupin na bajty bez paddingu (BIP-173, strict). */
    private fun convertBits(data: IntArray): ByteArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>(data.size * 5 / 8 + 1)
        for (value in data) {
            if (value < 0 || value ushr 5 != 0) return null
            acc = (acc shl 5) or value
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.add(((acc ushr bits) and 0xFF).toByte())
            }
        }
        if (bits >= 5 || ((acc shl (8 - bits)) and 0xFF) != 0) return null
        return out.toByteArray()
    }
}
