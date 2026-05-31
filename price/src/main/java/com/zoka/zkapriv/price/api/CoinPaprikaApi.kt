package com.zoka.zkapriv.price.api

import com.zoka.zkapriv.price.model.CoinPaprikaResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface CoinPaprikaApi {
    @GET("v1/tickers/{coin_id}")
    suspend fun getTicker(
        @Path("coin_id") coinId: String = "zka-zoka"
    ): CoinPaprikaResponse
}
