package com.satoshiwatch.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testovací vektory z BIP-173 (Bech32), BIP-350 (Bech32m) a známé
 * mainnetové adresy pro Base58Check.
 */
class BitcoinAddressValidatorTest {

    private fun assertValid(address: String, expectedType: AddressType) {
        val result = BitcoinAddressValidator.validate(address)
        assertTrue("Očekávána platná adresa: $address -> $result", result is ValidationResult.Valid)
        assertEquals(expectedType, (result as ValidationResult.Valid).type)
    }

    private fun assertInvalid(address: String) {
        val result = BitcoinAddressValidator.validate(address)
        assertTrue("Očekávána NEplatná adresa: $address", result is ValidationResult.Invalid)
    }

    // ------------------------------------------------------------- Base58

    @Test
    fun `genesis P2PKH adresa je platna`() =
        assertValid("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", AddressType.P2PKH)

    @Test
    fun `P2SH adresa je platna`() =
        assertValid("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy", AddressType.P2SH)

    @Test
    fun `P2PKH s prehozenym znakem neprojde checksumem`() =
        assertInvalid("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb")

    @Test
    fun `Base58 s neplatnym znakem 0 je odmitnut`() =
        assertInvalid("10Ozp1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    // ------------------------------------------------------------- Bech32 (BIP-173)

    @Test
    fun `P2WPKH mala pismena je platna`() =
        assertValid("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", AddressType.P2WPKH)

    @Test
    fun `P2WPKH velka pismena je platna a normalizuje se`() {
        val result = BitcoinAddressValidator.validate("BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", (result as ValidationResult.Valid).normalized)
        assertEquals(AddressType.P2WPKH, result.type)
    }

    @Test
    fun `P2WSH je platna`() =
        assertValid(
            "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
            AddressType.P2WSH
        )

    @Test
    fun `bech32 se spatnym checksumem je odmitnuta`() =
        assertInvalid("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5")

    @Test
    fun `michana velikost pismen je odmitnuta`() =
        assertInvalid("bc1qW508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")

    @Test
    fun `testnet tb1 adresa je odmitnuta jako ne-mainnet`() =
        assertInvalid("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")

    @Test
    fun `SegWit v0 s bech32m kodovanim je odmitnut`() =
        // Oficiální BIP-350 vektor „Invalid checksum (Bech32m instead of Bech32)“
        assertInvalid("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kemeawh")

    // ------------------------------------------------------------- Bech32m (BIP-350)

    @Test
    fun `Taproot P2TR adresa je platna`() =
        assertValid(
            "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0",
            AddressType.P2TR
        )

    @Test
    fun `SegWit v1 s bech32 kodovanim je odmitnut`() =
        // Oficiální BIP-350 vektor „Invalid checksum (Bech32 instead of Bech32m)“
        assertInvalid("bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqh2y7hd")

    @Test
    fun `neznama budouci verze witness je odmitnuta`() =
        // Platné Bech32m kódování v16, ale aplikace podporuje jen v0/v1
        assertInvalid("BC1SW50QGDZ25J")

    // ------------------------------------------------------------- Sanitizace vstupu

    @Test
    fun `BIP-21 URI z QR kodu je ocisteno a validovano`() {
        val result = BitcoinAddressValidator.validate(
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.01&label=test"
        )
        assertTrue(result is ValidationResult.Valid)
        assertEquals(
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            (result as ValidationResult.Valid).normalized
        )
    }

    @Test
    fun `prazdny vstup je odmitnut`() = assertInvalid("   ")

    @Test
    fun `nesmyslny retezec je odmitnut`() = assertInvalid("hello world")
}
