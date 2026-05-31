package com.zoka.zkapriv.price.repository

import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.price.api.CoinGeckoApi
import com.zoka.zkapriv.price.api.CoinPaprikaApi
import com.zoka.zkapriv.price.db.PriceCacheDao
import com.zoka.zkapriv.price.model.PriceCache
import com.zoka.zkapriv.price.model.ZokaStats
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi,
    private val coinPaprikaApi: CoinPaprikaApi,
    private val priceCacheDao: PriceCacheDao
) {
    data class PriceData(
        val price: Double,
        val priceChange24h: Double
    )

    suspend fun getPrice(coinId: String): Flow<Result<Double>> = flow {
        // First, emit cached price if available
        val cachedPrice = priceCacheDao.getPriceForCoin(coinId)
            .firstOrNull()

        if (cachedPrice != null && isCacheValid(cachedPrice.timestamp)) {
            emit(Result.Success(cachedPrice.usdPrice))
        }

        // Then try to fetch fresh price
        try {
            val response = coinGeckoApi.getPrice(coinId)
            val price = response[coinId]?.get("usd")
            if (price != null) {
                // Cache the new price
                priceCacheDao.insertPrice(
                    PriceCache(
                        coinId = coinId,
                        usdPrice = price,
                        timestamp = System.currentTimeMillis()
                    )
                )
                emit(Result.Success(price))
            } else {
                emit(Result.Error(Exception("Price not found")))
            }
        } catch (e: Exception) {
            // If we have a cached price, keep using it even if expired
            if (cachedPrice != null) {
                emit(Result.Success(cachedPrice.usdPrice))
            } else {
                emit(Result.Error(e))
            }
        }

        // Clean up old cache entries
        cleanOldCache()
    }

    suspend fun getPriceWithChange(coinId: String): Flow<Result<PriceData>> = flow {
        try {
            val response = coinGeckoApi.getPrice(coinId)
            val coinData = response[coinId]
            val price = coinData?.get("usd")
            val change = coinData?.get("usd_24h_change")
            
            if (price != null) {
                emit(Result.Success(PriceData(price, change ?: 0.0)))
                
                // Cache the price
                priceCacheDao.insertPrice(
                    PriceCache(
                        coinId = coinId,
                        usdPrice = price,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                emit(Result.Error(Exception("Price not found")))
            }
        } catch (e: Exception) {
            // Try to use cached price with 0% change
            val cachedPrice = priceCacheDao.getPriceForCoin(coinId).firstOrNull()
            if (cachedPrice != null) {
                emit(Result.Success(PriceData(cachedPrice.usdPrice, 0.0)))
            } else {
                emit(Result.Error(e))
            }
        }
    }

    private fun isCacheValid(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < Constants.PRICE_CACHE_DURATION
    }

    private suspend fun cleanOldCache() {
        val threshold = System.currentTimeMillis() - Constants.PRICE_CACHE_DURATION
        priceCacheDao.deleteOldPrices(threshold)
    }

    // Get complete Zoka statistics from CoinPaprika
    suspend fun getZokaStats(): Flow<Result<ZokaStats>> = flow {
        android.util.Log.d("PriceRepository", "Fetching Zoka stats from CoinPaprika...")
        val response = coinPaprikaApi.getTicker()
        android.util.Log.d("PriceRepository", "Response received: rank=${response.rank}, price=${response.quotes.usd.price}")
        
        val usdQuote = response.quotes.usd
        
        val stats = ZokaStats(
            price = usdQuote.price,
            percentChange24h = usdQuote.percentChange24h,
            percentChange7d = usdQuote.percentChange7d,
            percentChange30d = usdQuote.percentChange30d,
            volume24h = usdQuote.volume24h,
            marketCap = usdQuote.marketCap,
            athPrice = usdQuote.athPrice,
            percentFromAth = usdQuote.percentFromAth,
            rank = response.rank,
            totalSupply = response.totalSupply,
            lastUpdated = response.lastUpdated
        )
        
        android.util.Log.d("PriceRepository", "Stats created successfully")
        emit(Result.Success(stats))
        
        // Cache the current price
        priceCacheDao.insertPrice(
            PriceCache(
                coinId = "zka",
                usdPrice = usdQuote.price,
                timestamp = System.currentTimeMillis()
            )
        )
    }.catch { e ->
        android.util.Log.e("PriceRepository", "Error fetching Zoka stats: ${e.message}")
        // Don't emit error in catch to avoid type mismatch - error is logged and flow completes empty
    }
}