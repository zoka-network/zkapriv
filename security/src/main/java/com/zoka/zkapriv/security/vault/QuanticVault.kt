package com.zoka.zkapriv.security.vault

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que ZKAPriv 3 guarda de la billetera de Zoka Quantic Testnet v2, aparte
 * de la 2.x (que era otra red y otro formato).
 *
 * - La frase de 24 palabras, en EncryptedSharedPreferences (AES-256-GCM con
 *   la clave maestra del Keystore). Es lo único que hace falta para gastar.
 * - El PIN nunca se guarda: se guarda su PBKDF2-HMAC-SHA256 con sal propia, y
 *   se compara en tiempo constante. Después de [MAX_PIN_ATTEMPTS] fallos
 *   seguidos, cada intento espera más.
 * - Preferencias: biometría, Modo Tor, altura de nacimiento.
 */
@Singleton
class QuanticVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Dónde vive el estado cifrado de la billetera liviana (lo escribe Rust). */
    val stateDir: String get() = context.filesDir.resolve("stark").absolutePath

    fun hasWallet(): Boolean = prefs.getString(KEY_PHRASE, null) != null &&
        prefs.getBoolean(KEY_READY, false)

    fun phrase(): String? = prefs.getString(KEY_PHRASE, null)

    /** Guarda la frase de una billetera nueva o restaurada (todavía sin PIN). */
    fun storePhrase(phrase: String, birth: Int) {
        prefs.edit()
            .putString(KEY_PHRASE, phrase.trim().split(Regex("\\s+")).joinToString(" "))
            .putInt(KEY_BIRTH, birth)
            .putBoolean(KEY_READY, false)
            .putBoolean(KEY_PUBLISHED, false)
            .apply()
    }

    /** Altura desde la que se abren salidas: la punta al crearla, 1 al restaurar. */
    fun birth(): Int = prefs.getInt(KEY_BIRTH, 1)

    /** El onboarding terminó: hay frase respaldada y PIN. */
    fun markReady() {
        prefs.edit().putBoolean(KEY_READY, true).apply()
    }

    // ── PIN ──────────────────────────────────────────────────────────────────

    fun setPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit)) { "The PIN has $PIN_LENGTH digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.b64())
            .putString(KEY_PIN_HASH, derive(pin, salt).b64())
            .putInt(KEY_PIN_FAILS, 0)
            .putLong(KEY_PIN_LOCKED_UNTIL, 0)
            .apply()
    }

    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    /** Milisegundos que faltan para poder probar otro PIN (0 si ya se puede). */
    fun pinWaitMillis(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0) - now).coerceAtLeast(0)

    fun failedPinAttempts(): Int = prefs.getInt(KEY_PIN_FAILS, 0)

    /** Comprueba el PIN y lleva la cuenta de fallos. */
    fun checkPin(pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        if (pinWaitMillis(now) > 0) return PinCheck.Wait(pinWaitMillis(now))
        val salt = prefs.getString(KEY_PIN_SALT, null)?.unb64() ?: return PinCheck.Wrong(0)
        val stored = prefs.getString(KEY_PIN_HASH, null)?.unb64() ?: return PinCheck.Wrong(0)
        if (MessageDigest.isEqual(stored, derive(pin, salt))) {
            prefs.edit().putInt(KEY_PIN_FAILS, 0).putLong(KEY_PIN_LOCKED_UNTIL, 0).apply()
            return PinCheck.Ok
        }
        val fails = failedPinAttempts() + 1
        val wait = if (fails >= MAX_PIN_ATTEMPTS) {
            // 30 s, 1 min, 2 min… hasta una hora.
            (30_000L shl (fails - MAX_PIN_ATTEMPTS).coerceAtMost(7)).coerceAtMost(3_600_000L)
        } else 0L
        prefs.edit()
            .putInt(KEY_PIN_FAILS, fails)
            .putLong(KEY_PIN_LOCKED_UNTIL, if (wait > 0) now + wait else 0)
            .apply()
        return if (wait > 0) PinCheck.Wait(wait) else PinCheck.Wrong(MAX_PIN_ATTEMPTS - fails)
    }

    // ── Preferencias ─────────────────────────────────────────────────────────

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** Modo Tor: apagado por defecto (decisión del usuario, 30/10/2026). */
    var torMode: Boolean
        get() = prefs.getBoolean(KEY_TOR, false)
        set(value) = prefs.edit().putBoolean(KEY_TOR, value).apply()

    /**
     * La dirección corta ya fue aceptada por una semilla. Sin esto ZSilent
     * responde 404: ni el nodo ni sus pares conocen la corta.
     */
    var addressPublished: Boolean
        get() = prefs.getBoolean(KEY_PUBLISHED, false)
        set(value) = prefs.edit().putBoolean(KEY_PUBLISHED, value).apply()

    /** Borra la billetera v3 de este teléfono: frase, PIN y estado. */
    fun wipe() {
        prefs.edit().clear().commit()
        context.filesDir.resolve("stark").deleteRecursively()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    sealed interface PinCheck {
        object Ok : PinCheck
        /** Intentos que quedan antes de empezar a esperar. */
        data class Wrong(val attemptsLeft: Int) : PinCheck
        data class Wait(val millis: Long) : PinCheck
    }

    companion object {
        const val PIN_LENGTH = 6
        const val MAX_PIN_ATTEMPTS = 5
        private const val PIN_ITERATIONS = 120_000
        private const val FILE = "zkapriv_quantic_v2"
        private const val KEY_PHRASE = "phrase"
        private const val KEY_BIRTH = "birth"
        private const val KEY_READY = "ready"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FAILS = "pin_fails"
        private const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_TOR = "tor_mode"
        private const val KEY_PUBLISHED = "address_published"
    }
}
