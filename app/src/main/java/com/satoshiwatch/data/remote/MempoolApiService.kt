package com.satoshiwatch.data.remote

import com.satoshiwatch.data.remote.dto.AddressInfoDto
import com.satoshiwatch.data.remote.dto.TransactionDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * REST rozhraní mempool.space / esplora kompatibilního uzlu.
 * Base URL je konfigurovatelná v nastavení (výchozí https://mempool.space/api/),
 * funguje i proti vlastnímu Umbrel/RaspiBlitz uzlu nebo onion službě přes Tor.
 */
interface MempoolApiService {

    /** Posledních až 50 transakcí adresy (mempool + potvrzené). */
    @GET("address/{address}/txs")
    suspend fun getAddressTransactions(@Path("address") address: String): List<TransactionDto>

    /** Souhrnné statistiky adresy – zdroj pro výpočet zůstatku. */
    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): AddressInfoDto

    /** Aktuální výška řetězce – použitelné jako levný health-check uzlu. */
    @GET("blocks/tip/height")
    suspend fun getTipHeight(): Long
}
