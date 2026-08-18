package com.satoshiwatch.data.remote

import com.satoshiwatch.data.remote.dto.TransactionDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Posluchač WebSocket kanálu mempool.space (wss://…/api/v1/ws).
 *
 * Zpracovává push zprávy:
 *  - "address-transactions"        – nové transakce jedné trackované adresy
 *  - "block-transactions"          – potvrzené transakce trackované adresy v novém bloku
 *  - "multi-address-transactions"  – { adresa: { mempool: [...], confirmed: [...] } }
 *  - "block"                       – nový blok (spouští kontrolu potvrzení)
 *
 * Všechny callbacky přicházejí z OkHttp WS vlákna – volající je přehazuje do coroutine.
 */
class MempoolWebSocketListener(
    private val json: Json,
    private val onConnected: (WebSocket) -> Unit,
    private val onTransactions: (List<TransactionDto>) -> Unit,
    private val onNewBlock: (Long?) -> Unit,
    private val onDisconnected: (reason: String?) -> Unit
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onConnected(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        // Poškozená/neznámá zpráva nesmí shodit spojení
        runCatching { handleMessage(text) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onDisconnected(reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        onDisconnected(t.message)
    }

    private fun handleMessage(text: String) {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return

        root["block"]?.let { block ->
            val height = (block as? JsonObject)?.get("height")?.jsonPrimitive?.longOrNull
            onNewBlock(height)
        }

        root["address-transactions"]?.let { decodeTransactions(it)?.let(onTransactions) }
        root["block-transactions"]?.let { decodeTransactions(it)?.let(onTransactions) }

        root["multi-address-transactions"]?.let { multi ->
            (multi as? JsonObject)?.values?.forEach { perAddress ->
                val group = perAddress as? JsonObject ?: return@forEach
                group["mempool"]?.let { decodeTransactions(it)?.let(onTransactions) }
                group["confirmed"]?.let { decodeTransactions(it)?.let(onTransactions) }
                // "removed" (RBF/reorg) neřešíme push cestou – srovná periodická synchronizace
            }
        }
    }

    private fun decodeTransactions(element: JsonElement): List<TransactionDto>? =
        runCatching {
            json.decodeFromJsonElement(ListSerializer(TransactionDto.serializer()), element)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
}
