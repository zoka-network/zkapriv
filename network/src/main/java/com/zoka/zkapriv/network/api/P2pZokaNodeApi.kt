package com.zoka.zkapriv.network.api

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.network.model.ZokaAccountResponse
import com.zoka.zkapriv.network.model.ZokaBlockResponse
import com.zoka.zkapriv.network.model.ZokaChainHeightResponse
import com.zoka.zkapriv.network.model.ZokaHealthResponse
import com.zoka.zkapriv.network.model.ZokaMempoolMetricsResponse
import com.zoka.zkapriv.network.model.ZokaMempoolPendingResponse
import com.zoka.zkapriv.network.model.ZokaPrivateTransactionResponse
import com.zoka.zkapriv.network.model.ZokaRawBlocksRangeResponse
import com.zoka.zkapriv.network.model.ZokaSendFromSeedRequest
import com.zoka.zkapriv.network.model.ZokaSendFromSeedResponse
import com.zoka.zkapriv.network.model.ZokaServerPubkeyResponse
import com.zoka.zkapriv.network.model.ZokaStatsResponse
import com.zoka.zkapriv.network.model.ZokaTransactionRequest
import com.zoka.zkapriv.network.model.ZokaTransactionResponse
import com.zoka.zkapriv.network.nativebridge.ZokaNativeNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Serializa la entrada al hilo nativo sin cobrarle al comando el tiempo que
 * paso esperando su turno. Rust procesa estos comandos en un unico loop; si
 * se encolan varios a la vez, cada `recv_timeout` nativo corre mientras los
 * anteriores siguen trabajando y puede abandonar al caller antes de ejecutar.
 */
internal class SerializedNativeCommandGate(
    private val mutex: Mutex = Mutex()
) {
    suspend fun <T> run(timeoutMs: Long, command: suspend () -> T): T {
        require(timeoutMs > 0L) { "Native command timeout must be positive" }
        return mutex.withLock {
            if (timeoutMs == Long.MAX_VALUE) {
                command()
            } else {
                withTimeout(timeoutMs) { command() }
            }
        }
    }
}

/** Presupuestos exteriores de los `recv_timeout` definidos en mobile_net.rs. */
internal object MobileNativeTimeoutPolicy {
    private const val MOBILE_PEER_WAIT_MS = 90_000L
    private const val MOBILE_CHUNK_WAIT_MS = 60_000L
    private const val MOBILE_CALL_SLACK_MS = 15_000L
    private const val MOBILE_DIGESTS_BATCH_SIZE = 1_000L
    private const val MOBILE_BLOCKS_BATCH_SIZE = 32L
    private const val KOTLIN_JNI_DELIVERY_SLACK_MS = 15_000L

    fun privateTxBodyMs(): Long = outerTimeout(
        saturatingAdd(MOBILE_PEER_WAIT_MS, MOBILE_CHUNK_WAIT_MS)
    )

    fun rawBlocksMs(from: Long, to: Long): Long {
        val blocks = rangeSize(from, to)
        val nativeWork = saturatingAdd(
            MOBILE_PEER_WAIT_MS,
            saturatingMultiply(ceilingDivision(blocks, MOBILE_BLOCKS_BATCH_SIZE), MOBILE_CHUNK_WAIT_MS)
        )
        return outerTimeout(nativeWork)
    }

    fun blockDigestsMs(from: Long, to: Long): Long {
        val blocks = rangeSize(from, to)
        val nativeWork = saturatingAdd(
            saturatingAdd(
                MOBILE_PEER_WAIT_MS,
                saturatingMultiply(ceilingDivision(blocks, MOBILE_DIGESTS_BATCH_SIZE), MOBILE_CHUNK_WAIT_MS)
            ),
            saturatingMultiply(ceilingDivision(blocks, MOBILE_BLOCKS_BATCH_SIZE), MOBILE_CHUNK_WAIT_MS)
        )
        return outerTimeout(nativeWork)
    }

    // Rust: PEER_WAIT + HEIGHT_DISCOVERY_WAIT + MOBILE_CALL_SLACK.
    fun chainHeightMs(): Long = saturatingAdd(225_000L, KOTLIN_JNI_DELIVERY_SLACK_MS)

    // Rust: PEER_WAIT + PUBLISH_RETRY_WORK_WAIT + MOBILE_CALL_SLACK.
    fun publishPrivateTxMs(): Long = saturatingAdd(110_000L, KOTLIN_JNI_DELIVERY_SLACK_MS)

    private fun outerTimeout(nativeWorkMs: Long): Long =
        saturatingAdd(
            saturatingAdd(nativeWorkMs, MOBILE_CALL_SLACK_MS),
            KOTLIN_JNI_DELIVERY_SLACK_MS
        )

    private fun rangeSize(from: Long, to: Long): Long {
        require(from >= 0L && to >= from) { "Invalid block range $from..$to" }
        val distance = to - from
        return if (distance == Long.MAX_VALUE) Long.MAX_VALUE else distance + 1L
    }

    private fun ceilingDivision(value: Long, divisor: Long): Long =
        1L + (value - 1L) / divisor

    private fun saturatingMultiply(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

// El nodo y su cola Rust son globales al proceso, por eso el gate tambien lo
// es: dos instancias accidentales del adaptador no deben volver a encolar en
// paralelo sobre el mismo hilo nativo.
private val processNativeCommandGate = SerializedNativeCommandGate()

/**
 * La misma interfaz que hablaba con los nodos centrales, servida por la red.
 *
 * ZKAPriv le pedía todo a dos servidores por HTTP. Esos servidores se dieron de
 * baja y la wallet quedó muerta apuntando a IPs que ya no existen. Esto lo
 * reemplaza sin tocar ninguna pantalla: se implementa la misma interfaz, así
 * que quien la llamaba sigue llamándola igual, pero ahora los datos salen de
 * los pares de la red.
 *
 * **Lo que se puede servir y lo que no.** Un cliente liviano pide bloques y
 * publica transacciones; no guarda la cadena ni recalcula el estado global. Lo
 * que necesita la wallet privada —altura, bloques, cuerpos de transacciones y
 * publicar— sale de la red. Lo que era propio de un servidor con la cadena
 * entera abierta —saldos públicos, mempool, estadísticas— no tiene de dónde
 * salir, y estos métodos fallan diciendo exactamente eso en vez de devolver
 * ceros que parecerían un saldo real.
 *
 * **`sendFromSeed` no vuelve.** Ese camino mandaba la frase semilla a un
 * servidor para que armara la transacción por vos. En una wallet de
 * autocustodia no corresponde, y ahora ni siquiera hay servidor: la
 * transacción se arma y se prueba en el teléfono.
 */
class P2pZokaNodeApi(
    private val context: Context,
    private val red: ZokaNativeNetwork = ZokaNativeNetwork
) : ZokaNodeApi {

    private val gson = Gson()
    private val arranque = Mutex()

    /**
     * Deja la red lista antes de usarla, levantando Tor y marcando a las
     * semillas la primera vez.
     *
     * Va acá y no en una pantalla de arranque a propósito: así ninguna parte de
     * la app necesita acordarse de conectar antes de pedir algo. El candado
     * evita que dos pantallas que refrescan a la vez arranquen dos veces.
     *
     * La primera llamada tarda: armar los primeros circuitos de Tor son
     * decenas de segundos en una red móvil. Las siguientes no cuestan nada,
     * porque tanto Tor como el nodo devuelven lo que ya está andando.
     */
    private suspend fun asegurarRed() {
        if (conectada()) return
        arranque.withLock {
            if (conectada()) return
            val respuesta = red.startTorAndConnect(
                context.filesDir.absolutePath,
                gson.toJson(Constants.ZOKA_P2P_SEEDS)
            )
            leerJson(respuesta)
        }
    }

    private fun conectada(): Boolean =
        runCatching { leerJson(red.status()).get("connected")?.asBoolean == true }
            .getOrDefault(false)

    override suspend fun health(): ZokaHealthResponse = withContext(Dispatchers.IO) {
        val conectado = runCatching { asegurarRed() }.isSuccess && conectada()
        ZokaHealthResponse(status = if (conectado) "ok" else "disconnected")
    }

    override suspend fun getChainHeight(): ZokaChainHeightResponse = withContext(Dispatchers.IO) {
        asegurarRed()
        val json = processNativeCommandGate.run(MobileNativeTimeoutPolicy.chainHeightMs()) {
            leerJson(red.chainHeight())
        }
        ZokaChainHeightResponse(height = json.get("height")?.asLong ?: 0L)
    }

    override suspend fun getRawBlocksRange(
        from: Long,
        to: Long
    ): ZokaRawBlocksRangeResponse = withContext(Dispatchers.IO) {
        asegurarRed()
        val json = processNativeCommandGate.run(MobileNativeTimeoutPolicy.rawBlocksMs(from, to)) {
            leerJson(red.blocksRange(from, to))
        }
        gson.fromJson(json, ZokaRawBlocksRangeResponse::class.java)
    }

    override suspend fun getBlockDigestsRange(
        from: Long,
        to: Long
    ): ZokaRawBlocksRangeResponse = withContext(Dispatchers.IO) {
        asegurarRed()
        val json = processNativeCommandGate.run(MobileNativeTimeoutPolicy.blockDigestsMs(from, to)) {
            leerJson(red.blocksDigestRange(from, to))
        }
        // Señal local de aceptación: el camino de resúmenes fabrica un header
        // mínimo, mientras el fallback serializa BlockHeader completo e incluye
        // merkle_root. Solo se registra en APKs depurables y nunca se exponen
        // alturas, peers, onions, direcciones, hashes, transacciones ni saldos.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val blocks = json.get("blocks")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
            val firstHeader = blocks
                ?.takeIf { it.size() > 0 }
                ?.get(0)
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("header")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            val path = when {
                firstHeader == null -> "unknown"
                firstHeader.has("merkle_root") -> "full_blocks_fallback"
                else -> "digests"
            }
            Log.d("ZokaMobileNet", "wallet_batch path=$path blocks=${blocks?.size() ?: 0}")
        }
        gson.fromJson(json, ZokaRawBlocksRangeResponse::class.java)
    }

    override suspend fun getPrivateTransactionRaw(hash: String): JsonObject =
        withContext(Dispatchers.IO) {
            asegurarRed()
            processNativeCommandGate.run(MobileNativeTimeoutPolicy.privateTxBodyMs()) {
                leerJson(red.privateTxBody(hash))
            }
        }

    override suspend fun getPrivateTransaction(hash: String): ZokaPrivateTransactionResponse =
        withContext(Dispatchers.IO) {
            asegurarRed()
            val cuerpo = processNativeCommandGate.run(MobileNativeTimeoutPolicy.privateTxBodyMs()) {
                leerJson(red.privateTxBody(hash))
            }
            gson.fromJson(cuerpo, ZokaPrivateTransactionResponse::class.java)
        }

    override suspend fun submitPrivateTx(body: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            asegurarRed()
            processNativeCommandGate.run(MobileNativeTimeoutPolicy.publishPrivateTxMs()) {
                normalizarPublicacionPrivada(
                    body = body,
                    respuesta = leerJson(red.publishPrivateTx(gson.toJson(body)))
                )
            }
        }

    override suspend fun getBlockByHeight(height: Long): ZokaBlockResponse =
        withContext(Dispatchers.IO) {
            asegurarRed()
            bloque(height)
        }

    override suspend fun getLatestBlock(): ZokaBlockResponse = withContext(Dispatchers.IO) {
        asegurarRed()
        val punta = processNativeCommandGate.run(MobileNativeTimeoutPolicy.chainHeightMs()) {
            leerJson(red.chainHeight()).get("height")?.asLong ?: 0L
        }
        require(punta > 0) { "Todavía no sé a qué altura está la red" }
        bloque(punta)
    }

    private suspend fun bloque(height: Long): ZokaBlockResponse =
        processNativeCommandGate.run(MobileNativeTimeoutPolicy.rawBlocksMs(height, height)) {
            bloqueSinGate(height)
        }

    private fun bloqueSinGate(height: Long): ZokaBlockResponse {
        val rango = gson.fromJson(
            leerJson(red.blocksRange(height, height)),
            ZokaRawBlocksRangeResponse::class.java
        )
        val crudo = rango.blocks.firstOrNull()
            ?: throw IllegalStateException("Ningún par tenía el bloque $height")
        return ZokaBlockResponse(
            height = crudo.header?.blockHeight ?: height,
            hash = crudo.header?.hash.orEmpty(),
            timestamp = crudo.header?.timestamp,
            transactionsCount = crudo.transactions.size
        )
    }

    override suspend fun getAccount(address: String): ZokaAccountResponse =
        sinNodoCompleto("el saldo público de una cuenta")

    override suspend fun getStats(): ZokaStatsResponse =
        sinNodoCompleto("las estadísticas de la cadena")

    override suspend fun getTransaction(hash: String): ZokaTransactionResponse =
        sinNodoCompleto("una transacción pública por hash")

    override suspend fun submitTransaction(
        request: ZokaTransactionRequest
    ): ZokaTransactionResponse = sinNodoCompleto("enviar una transacción pública")

    override suspend fun getMempoolPending(): ZokaMempoolPendingResponse =
        sinNodoCompleto("la lista de transacciones en espera")

    override suspend fun getMempoolMetrics(): ZokaMempoolMetricsResponse =
        sinNodoCompleto("las métricas del mempool")

    override suspend fun getServerPubkey(): ZokaServerPubkeyResponse =
        throw UnsupportedOperationException(
            "Ya no hay servidor al que cifrarle nada: la wallet arma y firma sus " +
                "transacciones en el teléfono."
        )

    override suspend fun sendFromSeed(
        request: ZokaSendFromSeedRequest
    ): ZokaSendFromSeedResponse = throw UnsupportedOperationException(
        "Enviar la frase semilla a un servidor ya no es una opción. La " +
            "transacción se arma y se prueba acá mismo."
    )

    /**
     * Lee la respuesta del puente nativo, que siempre devuelve JSON y reporta
     * los errores como `{"error": "..."}` en vez de lanzar excepciones.
     */
    private fun leerJson(texto: String): JsonObject {
        val json = runCatching { gson.fromJson(texto, JsonObject::class.java) }.getOrNull()
            ?: throw IllegalStateException("El motor de red devolvió algo que no es JSON: $texto")
        json.get("error")?.takeIf { !it.isJsonNull }?.let {
            throw IllegalStateException(it.asString)
        }
        return json
    }

    internal companion object {
        /**
         * Mantiene el contrato de [ZokaNodeApi.submitPrivateTx]. El transporte
         * P2P confirma la difusiÃ³n con `published=true`, mientras el flujo de
         * autocustodia necesita el hash que ya venÃ­a en el cuerpo local.
         */
        fun normalizarPublicacionPrivada(
            body: JsonObject,
            respuesta: JsonObject
        ): JsonObject {
            check(respuesta.get("published")?.asBoolean == true) {
                "La red no confirmÃ³ la publicaciÃ³n de la transacciÃ³n privada"
            }
            val hashConstruido = body.get("tx_hash")?.asString.orEmpty()
            check(hashConstruido.isNotBlank()) {
                "La transacciÃ³n privada construida no tiene tx_hash"
            }
            val hashNativo = respuesta.get("hash")?.asString.orEmpty()
            check(hashNativo.isBlank() || hashNativo == hashConstruido) {
                "La red devolviÃ³ un hash distinto para la transacciÃ³n privada"
            }
            return respuesta.deepCopy().apply {
                addProperty("hash", hashConstruido)
            }
        }
    }

    private fun sinNodoCompleto(que: String): Nothing = throw UnsupportedOperationException(
        "Este cliente pide bloques y publica transacciones, pero no guarda la " +
            "cadena entera, así que no puede decir $que. Hace falta un nodo completo."
    )
}
