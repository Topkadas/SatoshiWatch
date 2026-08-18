package com.satoshiwatch.data.remote

import com.satoshiwatch.data.settings.AppSettings
import com.satoshiwatch.data.settings.SettingsRepository
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Vyrábí OkHttp/Retrofit klienty podle aktuálních uživatelských nastavení
 * (vlastní URL uzlu, SOCKS5 proxy). Při změně konfigurace se klienti
 * transparentně přestaví; instance jsou cachované.
 *
 * Poznámka k soukromí: při SOCKS proxy vytváří OkHttp cílovou adresu jako
 * „unresolved“, takže DNS dotaz na cílový host provádí až proxy (Tor/Orbot)
 * a nedochází k úniku DNS na lokální resolver.
 */
@Singleton
class NetworkClientProvider @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private data class NetworkConfig(
        val apiBaseUrl: String,
        val wsUrl: String,
        val proxyEnabled: Boolean,
        val proxyHost: String,
        val proxyPort: Int
    )

    private var cachedConfig: NetworkConfig? = null
    private var cachedClient: OkHttpClient? = null
    private var cachedApi: MempoolApiService? = null

    private fun configOf(s: AppSettings) = NetworkConfig(
        apiBaseUrl = s.apiBaseUrl,
        wsUrl = s.wsUrl,
        proxyEnabled = s.proxyEnabled,
        proxyHost = s.proxyHost,
        proxyPort = s.proxyPort
    )

    @Synchronized
    fun okHttpClient(): OkHttpClient {
        ensureFresh()
        return checkNotNull(cachedClient)
    }

    @Synchronized
    fun api(): MempoolApiService {
        ensureFresh()
        return checkNotNull(cachedApi)
    }

    fun wsUrl(): String = settingsRepository.current.wsUrl

    private fun ensureFresh() {
        val cfg = configOf(settingsRepository.current)
        if (cfg == cachedConfig && cachedClient != null && cachedApi != null) return

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // WS ping/pong drží spojení naživu i přes NAT/idle timeouty
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (cfg.proxyEnabled && cfg.proxyHost.isNotBlank()) {
            builder.proxy(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(cfg.proxyHost, cfg.proxyPort))
            )
        }

        val client = builder.build()
        cachedClient = client
        cachedApi = Retrofit.Builder()
            .baseUrl(cfg.apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MempoolApiService::class.java)
        cachedConfig = cfg
    }
}
