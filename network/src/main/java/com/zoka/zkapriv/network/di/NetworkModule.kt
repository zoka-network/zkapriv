package com.zoka.zkapriv.network.di

import android.content.Context
import android.content.SharedPreferences
import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.network.api.P2pZokaNodeApi
import com.zoka.zkapriv.network.api.ZokaNodeApi
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

    /**
     * Cliente HTTP compartido, ya sin el interceptor de RPC.
     *
     * Ese interceptor reescribía el host de **toda** petición que pasara por
     * este cliente para mandarla al nodo. Como el mismo cliente lo usan las
     * APIs de precios, terminaba pidiéndole las cotizaciones al nodo: los
     * precios nunca funcionaron, y el síntoma parecía de CoinGecko.
     *
     * Ahora los datos de la cadena salen de la red P2P y nadie habla por HTTP
     * con el nodo, así que el interceptor no tiene nada que reescribir. Se saca
     * en vez de acotarlo a ciertas rutas: no hay ruta que acotar.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Generous read timeout: /private/wallet/send-from-seed does a
            // full chain scan + Groth16 proof generation on the node side,
            // which can take 20-30 seconds before a tx hash is returned.
            // Balance scans + other GETs finish much faster, so paying the
            // longer ceiling is safe across the board.
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
        // Retrofit exige una URL base al construirse aunque nadie la use para la
        // cadena: los precios traen la suya y los datos de cadena salen de la
        // red P2P.
        return Retrofit.Builder()
            .baseUrl(Constants.ZOKA_MAINNET_RPC)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * De dónde salen los datos de la cadena.
     *
     * Antes salían de dos nodos por HTTP. Esos nodos ya no existen, así que la
     * wallet le pregunta a la red: pide bloques a los pares y publica sus
     * transacciones por gossip, igual que cualquier otro participante.
     *
     * La interfaz no cambió — quien la usaba la sigue usando igual —, solo
     * cambió quién contesta. Retrofit se sigue armando porque la
     * infraestructura HTTP la usan otras partes de la app; simplemente ya no es
     * quien habla con la cadena.
     */
    @Provides
    @Singleton
    fun provideZokaNodeApi(@ApplicationContext context: Context): ZokaNodeApi =
        P2pZokaNodeApi(context)
}
