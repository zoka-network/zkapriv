package com.zoka.zkapriv.security.wallet

import android.content.Context
import android.content.SharedPreferences
import com.zoka.zkapriv.core.util.Ristretto255
import com.zoka.zkapriv.security.storage.SecureStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallet manager for Zoka private addresses.
 *
 * Canonical derivation matches the Zoka node:
 * - mnemonic -> raw BIP-39 entropy
 * - scan_key = raw entropy
 * - spend_key = SHA256("zoka-private-wallet-spend-v1" || 0x1f || scan_key)
 * - address = zka1 + Base58(scan_pub || spend_pub || checksum)
 */
@Singleton
class WalletManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val secureStorage: SecureStorageManager,
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "zoka_wallet_prefs"
        const val KEY_ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
        const val KEY_WALLET_CREATED = "wallet_created"
        const val SCAN_KEY_BYTES_12_WORDS = 16
        const val SCAN_KEY_BYTES_24_WORDS = 32
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveMnemonic(mnemonic: String, passphrase: String = ""): Boolean {
        return try {
            require(mnemonicManager.validateMnemonic(mnemonic)) {
                "Invalid mnemonic phrase"
            }

            val encryptedMnemonic = secureStorage.encryptWithIntegrity(mnemonic)
            prefs.edit()
                .putString(KEY_ENCRYPTED_MNEMONIC, encryptedMnemonic)
                .putBoolean(KEY_WALLET_CREATED, true)
                .apply()

            true
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Failed to save mnemonic", e)
            false
        }
    }

    fun loadMnemonic(): String? {
        return try {
            val encryptedMnemonic = prefs.getString(KEY_ENCRYPTED_MNEMONIC, null)
                ?: return null
            val mnemonic = secureStorage.decryptWithIntegrity(encryptedMnemonic)

            if (!mnemonicManager.validateMnemonic(mnemonic)) {
                throw SecurityException("Decrypted mnemonic failed validation")
            }

            mnemonic
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Failed to load mnemonic", e)
            null
        }
    }

    fun hasWallet(): Boolean {
        return prefs.getBoolean(KEY_WALLET_CREATED, false) &&
            prefs.getString(KEY_ENCRYPTED_MNEMONIC, null) != null
    }

    fun deleteWallet() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_MNEMONIC)
            .remove(KEY_WALLET_CREATED)
            .apply()
        android.util.Log.w("WalletManager", "Wallet deleted")
    }

    fun getCurrentAddress(accountIndex: Int = 0, addressIndex: Int = 0): ZokaAddress? {
        val mnemonic = loadMnemonic() ?: return null
        return deriveAddress(mnemonic, accountIndex = accountIndex, addressIndex = addressIndex)
    }

    fun getCurrentScanKey(accountIndex: Int = 0, addressIndex: Int = 0): ByteArray? {
        val mnemonic = loadMnemonic() ?: return null
        return getScanKey(mnemonic, accountIndex = accountIndex, addressIndex = addressIndex)
    }

    /**
     * accountIndex/addressIndex are kept only to avoid touching older call sites.
     * Zoka private wallets do not derive child accounts from one master seed.
     */
    fun deriveAddress(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): ZokaAddress {
        val scanKey = mnemonicManager.mnemonicToEntropy(mnemonic)
        return zokaAddressFromScanKeyDetails(scanKey)
    }

    fun deriveAddresses(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        count: Int = 5
    ): List<ZokaAddress> {
        val address = deriveAddress(mnemonic, passphrase, accountIndex)
        return List(count) { address }
    }

    fun deriveZokaNetworkAddress(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): String {
        return deriveAddress(mnemonic, passphrase, accountIndex, addressIndex).address
    }

    fun deriveZokaNetworkAddressFromScanKey(scanKey: ByteArray): String {
        return zokaAddressFromEntropy(scanKey)
    }

    fun importFromScanKeyHex(scanKeyHex: String): ZokaAddress {
        return zokaAddressFromScanKeyDetails(scanKeyFromHex(scanKeyHex))
    }

    fun getScanKeyFromHex(scanKeyHex: String): ByteArray {
        return scanKeyFromHex(scanKeyHex)
    }

    /**
     * Returns the raw scan key bytes for compatibility with older app layers.
     */
    fun getScanKey(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): ByteArray {
        return mnemonicManager.mnemonicToEntropy(mnemonic)
    }

    fun getScanKeyHex(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): String {
        return getScanKey(mnemonic, passphrase, accountIndex, addressIndex).toHexString()
    }

    fun normalizeScanKeyHex(scanKeyHex: String): String {
        return scanKeyFromHex(scanKeyHex).toHexString()
    }

    fun zokaAddressFromEntropy(entropy: ByteArray): String {
        validateScanKey(entropy)
        val spendKey = deriveSpendKey(entropy)
        val scanPub = Ristretto255.publicKey(entropy)
        val spendPub = Ristretto255.publicKey(spendKey)
        return buildZka1Address(scanPub, spendPub)
    }

    private fun zokaAddressFromScanKeyDetails(scanKey: ByteArray): ZokaAddress {
        validateScanKey(scanKey)
        val spendKey = deriveSpendKey(scanKey)
        val scanPub = Ristretto255.publicKey(scanKey)
        val spendPub = Ristretto255.publicKey(spendKey)
        val zpriv = "zpriv:${scanPub.toHexString()}:${spendPub.toHexString()}"
        return ZokaAddress(
            address = buildZka1Address(scanPub, spendPub),
            publicKey = zpriv,
            derivationPath = "zoka-private-v1/bip39-entropy"
        )
    }

    private fun deriveSpendKey(scanKey: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("zoka-private-wallet-spend-v1".toByteArray(Charsets.UTF_8))
        sha.update(0x1f.toByte())
        sha.update(scanKey)
        return sha.digest()
    }

    private fun buildZka1Address(scanPub: ByteArray, spendPub: ByteArray): String {
        val payload = scanPub + spendPub
        val checksum = zokaAddressChecksum(payload)
        return "zka1" + encodeBase58(payload + checksum)
    }

    private fun zokaAddressChecksum(payload: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("zoka-private-address-v1".toByteArray(Charsets.UTF_8))
        sha.update(payload)
        return sha.digest().copyOfRange(0, 4)
    }

    private fun scanKeyFromHex(scanKeyHex: String): ByteArray {
        val normalized = scanKeyHex
            .trim()
            .removePrefix("0x")
            .lowercase()
        require(normalized.length % 2 == 0) { "Scan key hex must have an even length" }
        require(normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Scan key must be hexadecimal"
        }
        val bytes = normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        validateScanKey(bytes)
        return bytes
    }

    private fun validateScanKey(scanKey: ByteArray) {
        require(scanKey.size == SCAN_KEY_BYTES_12_WORDS || scanKey.size == SCAN_KEY_BYTES_24_WORDS) {
            "Zoka scan key must be 16 bytes (12 words) or 32 bytes (24 words)"
        }
    }

    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, input)
        val base = BigInteger.valueOf(58)
        val result = StringBuilder()

        while (num > BigInteger.ZERO) {
            val remainder = num.mod(base).toInt()
            result.insert(0, alphabet[remainder])
            num = num.divide(base)
        }

        for (b in input) {
            if (b.toInt() == 0) result.insert(0, '1') else break
        }

        return result.toString()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

data class ZokaAddress(
    val address: String,
    val publicKey: String,
    val derivationPath: String
)
