package com.zoka.zkapriv.network.config

import android.util.Log
import com.zoka.zkapriv.core.util.Constants
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpcEndpointInterceptor @Inject constructor(
    private val rpcSettings: RpcSettings
) : Interceptor {
    private val heightCheckLock = Any()

    @Volatile
    private var preferredEndpoint: HttpUrl? = null

    @Volatile
    private var preferredEndpointForPrimary: String? = null

    @Volatile
    private var nextHeightCheckAtMillis: Long = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val primary = rpcSettings.getEndpoint().toHttpUrlOrNull()
            ?: return chain.proceed(original)
        val fallback = Constants.ZOKA_MAINNET_RPC_FALLBACK.toHttpUrlOrNull()
        val preferred = selectFreshestEndpoint(primary, fallback)

        return try {
            chain.proceed(rewriteRequest(original, preferred))
        } catch (e: IOException) {
            val alternate = alternateEndpoint(primary, fallback, preferred) ?: throw e
            Log.w(
                "RpcInterceptor",
                "RPC unreachable (${preferred.host}), switching to alternate (${alternate.host})"
            )
            chain.proceed(rewriteRequest(original, alternate))
        }
    }

    private fun selectFreshestEndpoint(primary: HttpUrl, fallback: HttpUrl?): HttpUrl {
        if (fallback == null || sameEndpoint(primary, fallback)) return primary

        val now = System.currentTimeMillis()
        val primaryKey = primary.toString()
        preferredEndpoint
            ?.takeIf {
                preferredEndpointForPrimary == primaryKey &&
                    now < nextHeightCheckAtMillis
            }
            ?.let { return it }

        synchronized(heightCheckLock) {
            val lockedNow = System.currentTimeMillis()
            preferredEndpoint
                ?.takeIf {
                    preferredEndpointForPrimary == primaryKey &&
                        lockedNow < nextHeightCheckAtMillis
                }
                ?.let { return it }

            val primaryHeight = fetchHeight(primary)
            val fallbackHeight = fetchHeight(fallback)
            val selected = when {
                primaryHeight == null && fallbackHeight != null -> fallback
                primaryHeight != null && fallbackHeight != null && fallbackHeight > primaryHeight -> fallback
                else -> primary
            }

            preferredEndpoint = selected
            preferredEndpointForPrimary = primaryKey
            nextHeightCheckAtMillis = lockedNow + HEIGHT_CHECK_INTERVAL_MILLIS
            Log.d(
                "RpcInterceptor",
                "RPC heights primary=${primaryHeight ?: "unreachable"} " +
                    "fallback=${fallbackHeight ?: "unreachable"} selected=${selected.host}"
            )
            return selected
        }
    }

    private fun fetchHeight(endpoint: HttpUrl): Long? {
        val connection = endpoint.newBuilder()
            .addPathSegments("chain/height")
            .build()
            .toUrl()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = HEIGHT_CHECK_TIMEOUT_MILLIS
            connection.readTimeout = HEIGHT_CHECK_TIMEOUT_MILLIS
            connection.inputStream.bufferedReader().use { reader ->
                JSONObject(reader.readText()).optLong("height", -1L).takeIf { it >= 0 }
            }
        } catch (e: Exception) {
            Log.w("RpcInterceptor", "Height check failed for ${endpoint.host}: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun alternateEndpoint(primary: HttpUrl, fallback: HttpUrl?, preferred: HttpUrl): HttpUrl? {
        if (fallback == null || sameEndpoint(primary, fallback)) return null
        return if (sameEndpoint(preferred, primary)) fallback else primary
    }

    private fun sameEndpoint(left: HttpUrl, right: HttpUrl): Boolean =
        left.scheme == right.scheme && left.host == right.host && left.port == right.port

    private fun rewriteRequest(original: okhttp3.Request, target: HttpUrl): okhttp3.Request {
        val rewritten = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        if (rewritten == original.url) return original
        val hostHeader = target.host + if (
            (target.scheme == "https" && target.port != 443) ||
            (target.scheme == "http" && target.port != 80)
        ) ":${target.port}" else ""
        return original.newBuilder()
            .url(rewritten)
            .header("Host", hostHeader)
            .build()
    }

    private companion object {
        const val HEIGHT_CHECK_INTERVAL_MILLIS = 30_000L
        const val HEIGHT_CHECK_TIMEOUT_MILLIS = 2_000
    }
}
