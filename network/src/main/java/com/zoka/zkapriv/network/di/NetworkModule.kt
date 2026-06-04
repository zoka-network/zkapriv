package com.zoka.zkapriv.network.di

import android.content.Context
import android.content.SharedPreferences
import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.network.api.ZokaNodeApi
import com.zoka.zkapriv.network.config.RpcEndpointInterceptor
import com.zoka.zkapriv.network.config.RpcSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("zoka_transactions", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(rpcEndpointInterceptor: RpcEndpointInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Generous read timeout: /private/wallet/send-from-seed does a
            // full chain scan + Groth16 proof generation on the node side,
            // which can take 20-30 seconds before a tx hash is returned.
            // Balance scans + other GETs finish much faster, so paying the
            // longer ceiling is safe across the board.
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(rpcEndpointInterceptor)
        // Only log in debug builds. BODY level exposes signature_hex and public_key_hex.
        if (com.zoka.zkapriv.network.BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // Base URL is BOOT (primary RPC). RpcEndpointInterceptor rewrites
        // per-request to the freshest node and falls back on connection failure.
        return Retrofit.Builder()
            .baseUrl(Constants.ZOKA_MAINNET_RPC)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideZokaNodeApi(retrofit: Retrofit): ZokaNodeApi {
        return retrofit.create(ZokaNodeApi::class.java)
    }
}
