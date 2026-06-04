package com.zoka.zkapriv.network.crypto

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SeedEnvelopeEncryptionTest {
    @Test
    fun encryptProducesAuthenticatedEnvelopeWithoutPlaintextSeed() {
        val serverPublicKey = X25519PrivateKeyParameters(SecureRandom())
            .generatePublicKey()
            .encoded
            .toHex()
        val seed = ByteArray(32) { index -> index.toByte() }

        val envelope = SeedEnvelopeEncryption.encrypt(seed, serverPublicKey)

        assertEquals(64, envelope.ephemeralPubkeyHex.length)
        assertEquals(24, envelope.nonceHex.length)
        assertEquals((seed.size + 16) * 2, envelope.encryptedSeedHex.length)
        assertTrue(envelope.ephemeralPubkeyHex.matches(Regex("[0-9a-f]+")))
        assertNotEquals(seed.toHex(), envelope.encryptedSeedHex)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
