package com.zoka.zkapriv.core.util

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Ristretto255Test {
    @Test
    fun encodeBasepointMatchesDalekVector() {
        assertEquals(
            "e2f2ae0a6abc4e71a884a961c500515f58e30b6aa582dd8db6a65945e08d2d76",
            Ristretto255.encodeFromScalar(BigInteger.ONE).toHex()
        )
    }

    @Test
    fun scalarMulPointBytesAcceptsKnownValidEncodings() {
        val basepoint = hex("e2f2ae0a6abc4e71a884a961c500515f58e30b6aa582dd8db6a65945e08d2d76")
        val ephemeral = hex("e8b76384f093464d4333fa69a7d5b05ac28c98a3ecd6e6d69638cfbbefadac2d")

        assertArrayEquals(basepoint, Ristretto255.scalarMulPointBytes(BigInteger.ONE, basepoint))
        assertNotNull(Ristretto255.scalarMulPointBytes(BigInteger.ONE, ephemeral))
    }

    @Test
    fun publicKeyMatchesRustDalekVector() {
        assertEquals(
            "cc0746806d850e3c2e947e614fba71269e25209adf672bbf41822b0ae711bd67",
            Ristretto255.publicKey(
                hex("1bf91a148e7002d047f59e94a6266e25ea3b8d3cf0e37bf5e0794980f5deb987")
            ).toHex()
        )
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
