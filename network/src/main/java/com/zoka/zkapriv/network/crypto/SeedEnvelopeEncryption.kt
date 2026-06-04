package com.zoka.zkapriv.network.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedSeedEnvelope(
    val ephemeralPubkeyHex: String,
    val nonceHex: String,
    val encryptedSeedHex: String
)

object SeedEnvelopeEncryption {
    private val secureRandom = SecureRandom()
    private val aad = "zoka-seed-envelope-v1".toByteArray(Charsets.UTF_8)

    fun encrypt(seed: ByteArray, serverPubkeyHex: String): EncryptedSeedEnvelope {
        require(seed.size == 16 || seed.size == 32) { "Seed must be 16 or 32 bytes" }
        val serverPubkeyBytes = serverPubkeyHex.hexToBytes()
        require(serverPubkeyBytes.size == 32) { "Server X25519 public key must be 32 bytes" }

        val ephemeralSecretBytes = ByteArray(32).also(secureRandom::nextBytes)
        val sharedSecret = ByteArray(32)
        val aesKey = ByteArray(32)
        try {
            val ephemeralSecret = X25519PrivateKeyParameters(ephemeralSecretBytes, 0)
            val ephemeralPubkeyBytes = ephemeralSecret.generatePublicKey().encoded
            val agreement = X25519Agreement()
            agreement.init(ephemeralSecret)
            agreement.calculateAgreement(
                X25519PublicKeyParameters(serverPubkeyBytes, 0),
                sharedSecret,
                0
            )

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(aad)
            digest.update(sharedSecret)
            digest.update(ephemeralPubkeyBytes)
            digest.update(serverPubkeyBytes)
            val derivedKey = digest.digest()
            derivedKey.copyInto(aesKey)
            derivedKey.fill(0)

            val nonce = ByteArray(12).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(seed)
            return EncryptedSeedEnvelope(
                ephemeralPubkeyHex = ephemeralPubkeyBytes.toHex(),
                nonceHex = nonce.toHex(),
                encryptedSeedHex = ciphertext.toHex()
            )
        } finally {
            ephemeralSecretBytes.fill(0)
            sharedSecret.fill(0)
            aesKey.fill(0)
            serverPubkeyBytes.fill(0)
        }
    }

    private fun String.hexToBytes(): ByteArray {
        val clean = removePrefix("0x")
        require(clean.length % 2 == 0) { "Hex value must have an even length" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
