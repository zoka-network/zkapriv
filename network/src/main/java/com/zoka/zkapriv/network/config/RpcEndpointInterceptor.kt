package com.zoka.zkapriv.network.config

import android.util.Log
import com.zoka.zkapriv.core.util.Constants
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpcEndpointInterceptor @Inject constructor(
    private val rpcSettings: RpcSettings
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val primary = rpcSettings.getEndpoint().toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val primaryRequest = rewriteRequest(original, primary)
        return try {
            chain.proceed(primaryRequest)
        } catch (e: IOException) {
            // Primary unreachable — try fallback node transparently
            val fallback = Constants.ZOKA_MAINNET_RPC_FALLBACK.toHttpUrlOrNull()
            if (fallback == null || fallback.host == primary.host) throw e
            Log.w("RpcInterceptor", "Primary RPC unreachable (${primary.host}), switching to fallback (${fallback.host})")
            chain.proceed(rewriteRequest(original, fallback))
        }
    }

    private fun rewriteRequest(original: okhttp3.Request, target: HttpUrl): okhttp3.Request {
        val rewritten = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        if (rewritten == original.url) return original
        val hostHeader = target.host + if (
            (target.scheme == "https" && target.port != 443) ||
            (target.scheme == "http"  && target.port != 80)
        ) ":${target.port}" else ""
        return original.newBuilder()
            .url(rewritten)
            .header("Host", hostHeader)
            .build()
    }
}
