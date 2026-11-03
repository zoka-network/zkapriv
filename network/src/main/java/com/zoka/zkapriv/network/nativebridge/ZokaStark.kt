package com.zoka.zkapriv.network.nativebridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Motor de Zoka Quantic Testnet v2 (STARK) dentro de `zoka_mobile`
 * (`native/src/stark.rs`, feature `quantic-pq`).
 *
 * Entra un JSON y sale un JSON; un error vuelve como `{"error": "..."}` y acá
 * se convierte en [StarkException]. Todas las funciones que tocan la red o
 * prueban (sync, send, resolve, publish) bloquean: llamarlas en un hilo de
 * fondo. `torProxy` es el `127.0.0.1:PUERTO` del SOCKS de Arti
 * ([ZokaNativeNetwork.startTor]): si viene, todo va solo por Tor.
 */
object ZokaStark {
    private val loadError: Throwable? = try {
        System.loadLibrary("zoka_mobile")
        null
    } catch (error: Throwable) {
        error
    }

    /** La red de ZKAPriv 3. */
    const val NETWORK = "testnet"

    class StarkException(message: String) : Exception(message)

    private fun call(request: JSONObject, native: (String) -> String): JSONObject {
        val error = loadError
        check(error == null) { "The Zoka engine is not packaged for this device: ${error?.message}" }
        val reply = JSONObject(native(request.toString()))
        if (reply.has("error")) throw StarkException(reply.getString("error"))
        return reply
    }

    private fun base(phrase: String?, stateDir: String? = null, torProxy: String? = null) =
        JSONObject().apply {
            put("network", NETWORK)
            phrase?.let { put("phrase", it) }
            stateDir?.let { put("state_dir", it) }
            torProxy?.let { put("tor_proxy", it) }
        }

    /** 24 palabras nuevas: las mismas que entiende ZSilent. */
    fun newPhrase(): String {
        val error = loadError
        check(error == null) { "The Zoka engine is not packaged for this device: ${error?.message}" }
        val reply = JSONObject(newPhrase0())
        if (reply.has("error")) throw StarkException(reply.getString("error"))
        return reply.getString("phrase")
    }

    /** `address` (completa, ~1760 caracteres) y `short` (57). */
    fun walletInfo(phrase: String): JSONObject = call(base(phrase), ::walletInfo0)

    /** La clave de vista `zqtestview2…`: muestra el saldo sin poder gastar. */
    fun viewKey(phrase: String): String = call(base(phrase), ::viewKey0).getString("view_key")

    /** `valid` y `kind` (`full` o `short`). */
    fun checkAddress(address: String): JSONObject =
        call(base(null).put("address", address), ::checkAddress0)

    /** Lo guardado, sin red: saldo, historial y `next`. */
    fun status(phrase: String, stateDir: String, birth: Int? = null): JSONObject =
        call(base(phrase, stateDir).apply { birth?.let { put("birth", it) } }, ::status0)

    /** Páginas `/scan2` traídas por HTTPS, en orden desde `next`. */
    fun addPages(phrase: String, stateDir: String, pages: List<ByteArray>, birth: Int? = null): JSONObject {
        val hex = JSONArray()
        pages.forEach { page -> hex.put(page.joinToString("") { "%02x".format(it) }) }
        return call(
            base(phrase, stateDir).put("pages_hex", hex).apply { birth?.let { put("birth", it) } },
            ::addPages0,
        )
    }

    /** Hasta 20 páginas pedidas a una semilla por P2P; repetir mientras `in_sync` sea falso. */
    fun sync(phrase: String, stateDir: String, torProxy: String?, birth: Int? = null): JSONObject =
        call(base(phrase, stateDir, torProxy).apply { birth?.let { put("birth", it) } }, ::sync0)

    /**
     * Confirma lo leído contra un checkpoint traído por HTTPS (el JSON de
     * `/checkpoint`): Rust verifica la firma híbrida (Ed25519 + ML-DSA-65) del
     * equipo. Devuelve el estado, con `verified`; si no coincide, la billetera
     * vuelve a empezar y es una [StarkException].
     */
    fun confirmCheckpoint(phrase: String, stateDir: String, checkpointJson: String): JSONObject =
        call(base(phrase, stateDir).put("checkpoint", JSONObject(checkpointJson)), ::confirmCheckpoint0)

    /** `enough`, `fee_atoms`, `inputs`, `max_sendable_atoms` (y `reason` si no alcanza). */
    fun quote(phrase: String, stateDir: String, amountAtoms: Long): JSONObject =
        call(base(phrase, stateDir).put("amount_atoms", amountAtoms), ::quote0)

    /** Prueba y manda un pago: `txid`, `fee_atoms`, `expiry`, `proving_ms`. Tarda. */
    fun send(
        phrase: String,
        stateDir: String,
        to: String,
        amountAtoms: Long,
        memo: String,
        torProxy: String?,
    ): JSONObject = call(
        base(phrase, stateDir, torProxy)
            .put("to", to)
            .put("amount_atoms", amountAtoms)
            .put("memo", memo),
        ::send0,
    )

    /** La dirección completa de una corta, o null si la red no la conoce. */
    fun resolve(short: String, torProxy: String?): String? {
        val reply = call(base(null, null, torProxy).put("address", short), ::resolve0)
        return if (reply.isNull("address")) null else reply.getString("address")
    }

    /** Publica la dirección propia para que su forma corta sirva. */
    fun publish(phrase: String, torProxy: String?): Boolean =
        call(base(phrase, null, torProxy), ::publish0).getBoolean("published")

    @JvmStatic private external fun newPhrase0(): String
    @JvmStatic private external fun walletInfo0(request: String): String
    @JvmStatic private external fun viewKey0(request: String): String
    @JvmStatic private external fun checkAddress0(request: String): String
    @JvmStatic private external fun status0(request: String): String
    @JvmStatic private external fun addPages0(request: String): String
    @JvmStatic private external fun sync0(request: String): String
    @JvmStatic private external fun quote0(request: String): String
    @JvmStatic private external fun confirmCheckpoint0(request: String): String
    @JvmStatic private external fun send0(request: String): String
    @JvmStatic private external fun resolve0(request: String): String
    @JvmStatic private external fun publish0(request: String): String
}
