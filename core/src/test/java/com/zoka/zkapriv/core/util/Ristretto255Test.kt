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

    @Test
    fun zkaprivReceiveWalletKeysMatchRustDalek() {
        assertEquals(
            "fcfff74b906cdbba07f007d899b472091c382341f96410dd9810cb703df93a09",
            Ristretto255.publicKey(
                hex("44766c48173eadb18634b509dd532d7bce58c5c1e1818ea8bda2a0232e3598c7")
            ).toHex()
        )
        assertEquals(
            "0cd0d9e60dae35cc394cdb20cda5358dc2f11c9b762b80157e1624161afdde75",
            Ristretto255.publicKey(
                hex("b70909e42239777ef28e8fd20fae1be9cdf2850274b3db6e0b0e845280b5c735")
            ).toHex()
        )
    }

    @Test
    fun scalarMultiplesMatchRustDalek() {
        assertEquals(
            "6a493210f7499cd17fecb510ae0cea23a110e8d5b901f8acadd3095c73a3b919",
            Ristretto255.encodeFromScalar(BigInteger.TWO).toHex()
        )
        assertEquals(
            "94741f5d5d52755ece4f23f044ee27d5d1ea1e2bd196b462166b16152a9d0259",
            Ristretto255.encodeFromScalar(BigInteger.valueOf(3)).toHex()
        )
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
