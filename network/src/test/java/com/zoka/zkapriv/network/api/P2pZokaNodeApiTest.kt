package com.zoka.zkapriv.network.api

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class P2pZokaNodeApiTest {
    @Test
    fun publicacionP2pDevuelveElHashConstruidoParaQueLaWalletLaRegistre() {
        val body = JsonObject().apply { addProperty("tx_hash", "tx-local") }
        val respuesta = JsonObject().apply { addProperty("published", true) }

        val normalizada = P2pZokaNodeApi.normalizarPublicacionPrivada(body, respuesta)

        assertEquals("tx-local", normalizada.get("hash").asString)
        assertEquals(true, normalizada.get("published").asBoolean)
    }

    @Test
    fun publicacionP2pRechazaUnaConfirmacionConOtroHash() {
        val body = JsonObject().apply { addProperty("tx_hash", "tx-local") }
        val respuesta = JsonObject().apply {
            addProperty("published", true)
            addProperty("hash", "tx-ajena")
        }

        assertThrows(IllegalStateException::class.java) {
            P2pZokaNodeApi.normalizarPublicacionPrivada(body, respuesta)
        }
    }

    @Test
    fun publicacionP2pNoConvierteUnaDifusionFallidaEnExito() {
        val body = JsonObject().apply { addProperty("tx_hash", "tx-local") }
        val respuesta = JsonObject().apply { addProperty("published", false) }

        assertThrows(IllegalStateException::class.java) {
            P2pZokaNodeApi.normalizarPublicacionPrivada(body, respuesta)
        }
    }
}
