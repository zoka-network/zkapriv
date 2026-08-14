package com.zoka.zkapriv.security.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zoka.zkapriv.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class PrivateTxOutboxRecord(
    val hash: String,
    val address: String,
    val reservedAtoms: Long,
    val requestKey: String,
    val bodyJson: String
)

/**
 * Codec deliberately independent from Android so the durable outbox format can
 * be covered by ordinary JVM tests. Every field that can contain arbitrary
 * text is base64url encoded; the whole record is then encrypted by
 * [EncryptedSharedPreferences].
 */
internal object PrivateTxOutboxCodec {
    private const val VERSION = "zoka-private-outbox-v2"
    private const val FIELD_SEPARATOR = "|"

    fun encode(record: PrivateTxOutboxRecord): String {
        require(record.hash.isNotBlank()) { "El hash de la outbox no puede estar vacío" }
        require(record.address.isNotBlank()) { "La dirección de la outbox no puede estar vacía" }
        require(record.reservedAtoms > 0L) { "La reserva de la outbox debe ser positiva" }
        require(record.requestKey.isNotBlank()) { "La clave de solicitud no puede estar vacía" }
        require(record.bodyJson.isNotBlank()) { "El cuerpo de la outbox no puede estar vacío" }
        return listOf(
            VERSION,
            encodeField(record.hash),
            encodeField(record.address),
            record.reservedAtoms.toString(),
            encodeField(record.requestKey),
            encodeField(record.bodyJson)
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decode(encoded: String): PrivateTxOutboxRecord {
        val fields = encoded.split(FIELD_SEPARATOR, limit = 6)
        require(fields.size == 6 && fields[0] == VERSION) {
            "Formato de outbox privada desconocido"
        }
        return PrivateTxOutboxRecord(
            hash = decodeField(fields[1]),
            address = decodeField(fields[2]),
            reservedAtoms = fields[3].toLongOrNull()
                ?: throw IllegalArgumentException("Reserva de outbox inválida"),
            requestKey = decodeField(fields[4]),
            bodyJson = decodeField(fields[5])
        ).also { record -> encode(record) }
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8
    )
}

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        Constants.PREF_ENCRYPTED_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun storeMnemonic(alias: String, encryptedMnemonic: String) {
        prefs.edit().putString("mnemonic_$alias", encryptedMnemonic).apply()
    }
    
    fun getMnemonic(alias: String): String? {
        return prefs.getString("mnemonic_$alias", null)
    }
    
    fun storeScanKey(alias: String, encryptedKey: String) {
        prefs.edit().putString("scan_key_$alias", encryptedKey).apply()
    }
    
    fun getScanKey(alias: String): String? {
        return prefs.getString("scan_key_$alias", null)
    }
    
    fun storePublicKey(alias: String, publicKey: String) {
        prefs.edit().putString("public_key_$alias", publicKey).apply()
    }
    
    fun getPublicKey(alias: String): String? {
        return prefs.getString("public_key_$alias", null)
    }

    fun storeLocalPrivateChainSnapshot(snapshotJson: String) {
        check(prefs.edit().putString(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT, snapshotJson).commit()) {
            "No se pudo guardar el avance del escaneo privado"
        }
    }

    fun getLocalPrivateChainSnapshot(): String? {
        return prefs.getString(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT, null)
    }

    fun clearLocalPrivateChainSnapshot() {
        check(prefs.edit().remove(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT).commit()) {
            "No se pudo borrar el avance del escaneo privado"
        }
    }

    /**
     * Instantánea del escaneo de saldo, por dirección.
     *
     * Guarda hasta dónde se escaneó la cadena y qué se encontró, para no tener
     * que volver a bajarla entera en cada refresco. Sobre una API REST cercana
     * rebajar todo costaba segundos; pidiendo bloque por bloque a la red, son
     * decenas de minutos, y el escaneo nunca alcanzaba a terminar antes de que
     * lo reiniciaran.
     *
     * Va por dirección porque cada una ve cosas distintas de la misma cadena.
     */
    fun storeBalanceScanSnapshot(address: String, snapshotJson: String) {
        check(prefs.edit().putString(balanceScanKey(address), snapshotJson).commit()) {
            "No se pudo guardar el avance del escaneo de saldo"
        }
    }

    fun getBalanceScanSnapshot(address: String): String? {
        return prefs.getString(balanceScanKey(address), null)
    }

    fun clearBalanceScanSnapshot(address: String) {
        check(prefs.edit().remove(balanceScanKey(address)).commit()) {
            "No se pudo borrar el avance del escaneo de saldo"
        }
    }

    /**
     * Persiste en un solo valor cifrado todo lo necesario para recuperar una
     * publicación ambigua. Este commit debe ocurrir antes de crear la reserva
     * RPC: si el proceso muere entre ambos pasos, la reserva se reconstruye a
     * partir de este registro y nunca se pierde el cuerpo exacto.
     */
    fun storePrivateTxOutbox(record: PrivateTxOutboxRecord) {
        val encoded = PrivateTxOutboxCodec.encode(record)
        check(prefs.edit().putString(privateTxOutboxKey(record.hash), encoded).commit()) {
            "No se pudo guardar la transacción en la outbox cifrada"
        }
    }

    fun getPrivateTxOutbox(hash: String): PrivateTxOutboxRecord? {
        val encoded = prefs.getString(privateTxOutboxKey(hash), null) ?: return null
        return PrivateTxOutboxCodec.decode(encoded).also { record ->
            check(record.hash == hash) { "La clave y el hash de la outbox no coinciden" }
        }
    }

    fun readPrivateTxOutbox(address: String): List<PrivateTxOutboxRecord> =
        prefs.all.entries.asSequence()
            .filter { it.key.startsWith(PRIVATE_TX_OUTBOX_PREFIX) }
            .map { entry ->
                val hash = entry.key.removePrefix(PRIVATE_TX_OUTBOX_PREFIX)
                val encoded = entry.value as? String
                    ?: throw IllegalStateException("La outbox $hash no contiene texto")
                PrivateTxOutboxCodec.decode(encoded).also { record ->
                    check(record.hash == hash) { "La clave y el hash de la outbox no coinciden" }
                }
            }
            .filter { it.address == address }
            .sortedBy { it.hash }
            .toList()

    fun removePrivateTxOutbox(hash: String) {
        check(prefs.edit().remove(privateTxOutboxKey(hash)).commit()) {
            "No se pudo retirar la transacciÃ³n confirmada de la outbox"
        }
    }

    private fun balanceScanKey(address: String): String = "balance_scan_$address"

    private fun privateTxOutboxKey(hash: String): String = "$PRIVATE_TX_OUTBOX_PREFIX$hash"
    
    fun setActiveWallet(address: String) {
        prefs.edit().putString(Constants.PREF_KEY_ACTIVE_WALLET, address).apply()
    }
    
    fun getActiveWallet(): String? {
        return prefs.getString(Constants.PREF_KEY_ACTIVE_WALLET, null)
    }
    
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }
    
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }
    
    fun storePin(pin: String) {
        prefs.edit().putString("user_pin", pin).apply()
    }
    
    fun getPin(): String? {
        return prefs.getString("user_pin", null)
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }
    
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("biometric_enabled", false)
    }
    
    fun hasWallet(): Boolean {
        val active = getActiveWallet()
        
        if (active == null) {
            return false
        }
        
        // Consider both mnemonic-based wallets and scan-key imports.
        val hasDefaultMnemonic = getMnemonic("default_wallet") != null
        val hasScanKeyImport = getMnemonic("scan_key_hex") != null || getMnemonic("imported_scan_key_hex") != null
        // Also consider if a scan key was stored explicitly
        val hasStoredScanKey = getScanKey("default_wallet") != null
        
        return hasDefaultMnemonic || hasScanKeyImport || hasStoredScanKey
    }
    
    fun clear() {
        prefs.edit().clear().commit()
        // Delete the underlying XML file so EncryptedSharedPreferences can start fresh
        // on next launch. Simply calling clear() leaves the file on disk; the next
        // EncryptedSharedPreferences.create() would fail to decode the corrupt header.
        File(context.filesDir.parent, "shared_prefs/${Constants.PREF_ENCRYPTED_FILE}.xml").delete()
    }

    private companion object {
        const val KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT = "local_private_chain_snapshot_v1"
        const val PRIVATE_TX_OUTBOX_PREFIX = "private_tx_outbox_"
    }
}
