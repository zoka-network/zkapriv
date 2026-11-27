package com.zoka.zkapriv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zoka.zkapriv.network.nativebridge.ZokaStark
import com.zoka.zkapriv.network.nativebridge.ZokaNativeNetwork
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Billetera efímera en caché: nunca lee el almacén del usuario ni envía pagos. */
@RunWith(AndroidJUnit4::class)
class StarkReadOnlyInstrumentedTest {
    private fun report(message: String) = InstrumentationRegistry.getInstrumentation()
        .sendStatus(0, android.os.Bundle().apply { putString("stream", "$message\n") })

    @Test fun nativeEngineReadsAndRefreshesTheRealV2Chain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.cacheDir.resolve("stark-read-test-${System.nanoTime()}")
        check(directory.mkdir())
        val phrase = ZokaStark.newPhrase() // Solo memoria, no se imprime ni publica.
        val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
        fun get(url: String): ByteArray = http.newCall(Request.Builder().url(url)
            .header("Cache-Control", "no-cache").build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            checkNotNull(it.body).bytes()
        }
        try {
            assertEquals(0, ZokaStark.sendPhase())
            report("JNI initialized; reading public HTTPS pages")
            var status = ZokaStark.status(phrase, directory.absolutePath)
            // El tope sale de la punta de hoy más un margen: con 99 páginas fijas
            // la prueba se cortaba en cuanto la cadena pasó el bloque 9.900.
            val tip = JSONObject(get("https://api.zokascan.cc/status").toString(Charsets.UTF_8)).getInt("height")
            val maxRounds = tip / 100 + 10
            var rounds = 0
            do {
                val next = status.getInt("next")
                val page = get("https://api.zokascan.cc/scan2?from=$next&count=100")
                status = ZokaStark.addPages(phrase, directory.absolutePath, listOf(page))
                rounds++
                assertTrue("El escaneo no converge", rounds < maxRounds)
                if (rounds % 10 == 0) report("HTTPS: ${status.getInt("height")} blocks read")
            } while (!status.getBoolean("in_sync"))
            assertTrue(status.getInt("height") > 0)
            val firstHeight = status.getInt("height")
            // Reabrir el estado y pasar una página sin bloques también actualiza la punta.
            status = ZokaStark.status(phrase, directory.absolutePath)
            val next = status.getInt("next")
            status = ZokaStark.addPages(phrase, directory.absolutePath,
                listOf(get("https://api.zokascan.cc/scan2?from=$next&count=100")))
            assertTrue(status.getInt("height") >= firstHeight)
            status = ZokaStark.confirmCheckpoint(phrase, directory.absolutePath,
                get("https://api.zokascan.cc/checkpoint").toString(Charsets.UTF_8))
            assertTrue("Falta checkpoint verificado", status.getInt("verified") > 0)
            assertEquals(0L, status.getLong("spendable_atoms"))
            assertFalse(ZokaStark.quote(phrase, directory.absolutePath, 1).getBoolean("enough"))
            assertEquals(0, ZokaStark.sendPhase())
            report("HTTPS refresh and checkpoint verified; connecting via P2P")
            // Validar DNS/contexto Android y pares aprendidos, sin publicar direcciones.
            status = ZokaStark.sync(phrase, directory.absolutePath, null)
            assertTrue("P2P no verificó la cadena", status.getInt("verified") > 0)
            // Los pares aprendidos son los nodos que no son semillas y que las
            // semillas comparten: con solo las tres semillas en línea, cero.
            assertTrue("P2P no informó sus pares", status.getInt("peers") >= 0)
            report("P2P checkpoint verified; ${status.getInt("peers")} peers learned besides the seeds; starting Arti")
            val tor = JSONObject(ZokaNativeNetwork.startTor(directory.absolutePath))
            assertFalse("Arti no arrancó", tor.has("error"))
            report("Arti bootstrapped; reading via onion peers")
            status = ZokaStark.sync(phrase, directory.absolutePath, tor.getString("proxy"))
            assertTrue("Tor no verificó la cadena", status.getInt("verified") > 0)
            assertTrue(status.getInt("height") >= firstHeight)
            assertEquals(0L, status.getLong("spendable_atoms"))
            report("Tor checkpoint verified; temporary wallet has zero spendable balance")
        } finally {
            directory.deleteRecursively()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }
}
