package com.zoka.zkapriv.security.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zoka.zkapriv.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
        prefs.edit().putString(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT, snapshotJson).apply()
    }

    fun getLocalPrivateChainSnapshot(): String? {
        return prefs.getString(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT, null)
    }

    fun clearLocalPrivateChainSnapshot() {
        prefs.edit().remove(KEY_LOCAL_PRIVATE_CHAIN_SNAPSHOT).apply()
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
        prefs.edit().putString(balanceScanKey(address), snapshotJson).apply()
    }

    fun getBalanceScanSnapshot(address: String): String? {
        return prefs.getString(balanceScanKey(address), null)
    }

    fun clearBalanceScanSnapshot(address: String) {
        prefs.edit().remove(balanceScanKey(address)).apply()
    }

    private fun balanceScanKey(address: String): String = "balance_scan_$address"
    
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
    }
}
