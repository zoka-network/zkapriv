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

    @GET("stats")
    suspend fun getStats(): ZokaStatsResponse

    @GET("chain/height")
    suspend fun getChainHeight(): ZokaChainHeightResponse

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

    /// Build, sign and broadcast a Zoka private transaction on behalf of
    /// the client. The body carries the BIP-39 entropy of the user's
    /// recovery phrase; the node uses it ephemerally inside a sandboxed
    /// temp dir (CLI shell-out under the hood) to construct the Groth16
    /// proof + CLSAG ring signature, then wipes the wallet file.
    @POST("private/wallet/send-from-seed")
    suspend fun sendFromSeed(@Body request: ZokaSendFromSeedRequest): ZokaSendFromSeedResponse
}
