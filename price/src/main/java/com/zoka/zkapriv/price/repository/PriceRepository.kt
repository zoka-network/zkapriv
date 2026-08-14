package com.zoka.zkapriv.price.repository

import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.price.db.PriceCacheDao
import com.zoka.zkapriv.price.model.ZokaStats
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val priceCacheDao: PriceCacheDao
) {
    data class PriceData(
        val price: Double,
        val priceChange24h: Double
    )

    suspend fun getPrice(coinId: String): Flow<Result<Double>> = flow {
        // No clearnet fallback: consulting a public price API when the wallet
        // opens creates a periodic IP-level usage signal. Until this client can
        // route HTTP and remote DNS through its embedded Tor instance, only a
        // previously cached value may be exposed.
        val cachedPrice = priceCacheDao.getPriceForCoin(coinId)
            .firstOrNull()
        if (cachedPrice != null) {
            emit(Result.Success(cachedPrice.usdPrice))
        } else {
            emit(Result.Error(IllegalStateException(PRICE_PRIVACY_MESSAGE)))
        }
    }

    suspend fun getPriceWithChange(coinId: String): Flow<Result<PriceData>> = flow {
        val cachedPrice = priceCacheDao.getPriceForCoin(coinId).firstOrNull()
        if (cachedPrice != null) {
            emit(Result.Success(PriceData(cachedPrice.usdPrice, 0.0)))
        } else {
            emit(Result.Error(IllegalStateException(PRICE_PRIVACY_MESSAGE)))
        }
    }

    // Statistics stay disabled for the same reason as spot prices.
    suspend fun getZokaStats(): Flow<Result<ZokaStats>> = flow {
        emit(Result.Error(IllegalStateException(PRICE_PRIVACY_MESSAGE)))
    }

    private companion object {
        const val PRICE_PRIVACY_MESSAGE =
            "Public price feeds are disabled until they can be routed through embedded Tor"
    }
}
