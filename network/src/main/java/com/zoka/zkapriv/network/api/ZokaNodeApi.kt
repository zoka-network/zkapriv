package com.zoka.zkapriv.network.api

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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ZokaNodeApi {
    @GET("health")
    suspend fun health(): ZokaHealthResponse

    /**
     * Cuantos pares hay ahora mismo, no solo si hay alguno.
     *
     * La app ya leia este estado pero se quedaba con el booleano. Con un solo
     * par la wallet funciona y se cuelga cada vez que ese par tarda; con tres
     * puede rotar. Es una diferencia que el usuario merece ver.
     *
     * Devuelve null si no se pudo leer: un conteo viejo presentado como actual
     * es peor que admitir que no sabemos.
     */
    suspend fun peerCount(): Int?

    @GET("stats")
    suspend fun getStats(): ZokaStatsResponse

    @GET("chain/height")
    suspend fun getChainHeight(): ZokaChainHeightResponse

    @GET("crypto/server-pubkey")
    suspend fun getServerPubkey(): ZokaServerPubkeyResponse

    @GET("accounts/{address}")
    suspend fun getAccount(@Path("address") address: String): ZokaAccountResponse

    @GET("blocks/latest")
    suspend fun getLatestBlock(): ZokaBlockResponse

    @GET("blocks/{height}")
    suspend fun getBlockByHeight(@Path("height") height: Long): ZokaBlockResponse

    @GET("blocks/range/{from}/{to}/raw")
    suspend fun getRawBlocksRange(
        @Path("from") from: Long,
        @Path("to") to: Long
    ): ZokaRawBlocksRangeResponse

    /// Resúmenes de bloque: lo justo para escanear un saldo.
    ///
    /// Un bloque entero pesa unos 4.500 bytes y más de la mitad es la prueba de
    /// rango del coinbase, que solo hace falta para gastar la nota. Recorrer la
    /// cadena en bloques enteros son 77 MB; en resúmenes, unos 8.
    ///
    /// Por defecto cae a los bloques enteros, así que una implementación que no
    /// sepa de resúmenes sigue funcionando sin cambios.
    suspend fun getBlockDigestsRange(from: Long, to: Long): ZokaRawBlocksRangeResponse =
        getRawBlocksRange(from, to)

    @GET("transactions/{hash}")
    suspend fun getTransaction(@Path("hash") hash: String): ZokaTransactionResponse

    @POST("transactions")
    suspend fun submitTransaction(@Body request: ZokaTransactionRequest): ZokaTransactionResponse

    @GET("mempool/pending")
    suspend fun getMempoolPending(): ZokaMempoolPendingResponse

    @GET("mempool/metrics")
    suspend fun getMempoolMetrics(): ZokaMempoolMetricsResponse

    @GET("private/tx/{hash}")
    suspend fun getPrivateTransaction(@Path("hash") hash: String): ZokaPrivateTransactionResponse

    /// Raw private-tx body as the node stored it. The local transfer engine
    /// needs every field of the original transaction (not just the parsed
    /// subset in [ZokaPrivateTransactionResponse]) to use it as a spendable
    /// input, so this returns the untouched JSON object.
    @GET("private/tx/{hash}")
    suspend fun getPrivateTransactionRaw(@Path("hash") hash: String): com.google.gson.JsonObject

    /// Build, sign and broadcast a Zoka private transaction on behalf of
    /// the client. The body carries the encrypted BIP-39 entropy; the node
    /// decrypts and uses it ephemerally inside a sandboxed
    /// temp dir (CLI shell-out under the hood) to construct the Groth16
    /// proof + CLSAG ring signature, then wipes the wallet file.
    @POST("private/wallet/send-from-seed")
    suspend fun sendFromSeed(@Body request: ZokaSendFromSeedRequest): ZokaSendFromSeedResponse

    /// Publish a locally-built private transaction. The body is the canonical
    /// PrivateTransaction JSON produced by the on-device engine
    /// (LocalPrivateTransferBuilder). The node validates it (proofs, nullifier
    /// conflicts), stores it, and gossips it to miners. This is the canonical
    /// optional self-custody path for devices that can afford local proof
    /// generation.
    @POST("private/tx")
    suspend fun submitPrivateTx(@Body body: com.google.gson.JsonObject): com.google.gson.JsonObject
}
