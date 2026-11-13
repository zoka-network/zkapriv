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
 * - Varias billeteras (3/11/2026): cada una con su frase, su nombre, su altura
 *   de nacimiento y su carpeta de estado; una sola está activa y todo lo que
 *   lee y firma la app es de esa. La primera (`main`) conserva las claves y la
 *   carpeta de antes, así una billetera existente no se migra ni se vuelve a
 *   sincronizar. El PIN, la biometría y el Modo Tor son de la app.
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

    // ── Billeteras ───────────────────────────────────────────────────────────

    /** Una billetera guardada en este teléfono. */
    data class WalletEntry(val id: String, val name: String)

    /** Los ids en el orden en que se agregaron; la de antes es `main`. */
    private fun ids(): List<String> {
        val stored = prefs.getString(KEY_WALLETS, null)
        if (stored != null) return stored.split(',').filter { it.isNotBlank() }
        return if (prefs.getString(KEY_PHRASE, null) != null) listOf(MAIN) else emptyList()
    }

    /** `main` usa las claves de siempre; las demás, la clave con su id. */
    private fun key(base: String, id: String): String = if (id == MAIN) base else "$base.$id"

    private fun stateDirOf(id: String): String =
        context.filesDir.resolve(if (id == MAIN) "stark" else "stark-$id").absolutePath

    fun wallets(): List<WalletEntry> = ids().map { WalletEntry(it, walletName(it)) }

    fun walletName(id: String): String =
        prefs.getString("$KEY_NAME.$id", null) ?: if (id == MAIN) "Main wallet" else "Wallet"

    /** La billetera que se lee y se firma ahora. */
    fun activeId(): String {
        val all = ids()
        return prefs.getString(KEY_ACTIVE, null)?.takeIf { it in all } ?: all.firstOrNull() ?: MAIN
    }

    fun selectWallet(id: String) {
        require(id in ids()) { "There is no such wallet on this phone" }
        prefs.edit().putString(KEY_ACTIVE, id).commit()
    }

    /**
     * Agrega una billetera (nueva o restaurada) sin tocar el PIN ni las demás.
     * Devuelve su id; no la activa.
     */
    fun addWallet(name: String, phrase: String, birth: Int): String {
        val normalized = normalize(phrase)
        val all = ids()
        require(all.none { prefs.getString(key(KEY_PHRASE, it), null) == normalized }) {
            "This wallet is already on this phone"
        }
        var id: String
        do {
            id = ByteArray(4).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        } while (id in all || id == MAIN)
        prefs.edit()
            .putString(key(KEY_PHRASE, id), normalized)
            .putInt(key(KEY_BIRTH, id), birth)
            .putBoolean(key(KEY_PUBLISHED, id), false)
            .putString("$KEY_NAME.$id", name.trim().ifEmpty { "Wallet ${all.size + 1}" })
            .putString(KEY_WALLETS, (all + id).joinToString(","))
            .commit()
        return id
    }

    fun renameWallet(id: String, name: String) {
        require(id in ids()) { "There is no such wallet on this phone" }
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= MAX_NAME) { "A name has 1 to $MAX_NAME characters" }
        prefs.edit().putString("$KEY_NAME.$id", clean).commit()
    }

    /**
     * Saca una billetera del teléfono: su frase, su nombre y su estado. Quedan
     * las demás; si era la activa, pasa a serlo la primera que queda. La última
     * no se saca así: para eso está [wipe].
     */
    fun removeWallet(id: String) {
        val all = ids()
        require(id in all) { "There is no such wallet on this phone" }
        require(all.size > 1) { "This is the only wallet on this phone" }
        val rest = all - id
        prefs.edit()
            .remove(key(KEY_PHRASE, id))
            .remove(key(KEY_BIRTH, id))
            .remove(key(KEY_PUBLISHED, id))
            .apply { (1..MAX_EXTRA_ADDRESSES).forEach { remove(extraKey(id, it)) } }
            .remove("$KEY_NAME.$id")
            .putString(KEY_WALLETS, rest.joinToString(","))
            .apply { if (activeId() == id) putString(KEY_ACTIVE, rest.first()) }
            .commit()
        java.io.File(stateDirOf(id)).deleteRecursively()
    }

    /** Dónde vive el estado cifrado de la billetera activa (lo escribe Rust). */
    val stateDir: String get() = stateDirOf(activeId())

    fun hasWallet(): Boolean = phrase() != null && prefs.getBoolean(KEY_READY, false)

    /** La frase de la billetera activa. */
    fun phrase(): String? = prefs.getString(key(KEY_PHRASE, activeId()), null)

    /**
     * Guarda la frase de la primera billetera, nueva o restaurada en el alta
     * (todavía sin PIN). Las siguientes entran con [addWallet].
     */
    fun storePhrase(phrase: String, birth: Int) {
        prefs.edit()
            .putString(KEY_PHRASE, normalize(phrase))
            .putInt(KEY_BIRTH, birth)
            .putBoolean(KEY_READY, false)
            .putBoolean(KEY_PUBLISHED, false)
            .apply { (1..MAX_EXTRA_ADDRESSES).forEach { remove(extraKey(MAIN, it)) } }
            .putString(KEY_WALLETS, MAIN)
            .putString(KEY_ACTIVE, MAIN)
            .apply()
    }

    /** Altura desde la que se abren salidas: la punta al crearla, 1 al restaurar. */
    fun birth(): Int = prefs.getInt(key(KEY_BIRTH, activeId()), 1)

    private fun normalize(phrase: String) = phrase.trim().split(Regex("\\s+")).joinToString(" ")

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
        get() = isPublished(activeId())
        set(value) = setPublished(activeId(), value)

    fun isPublished(id: String): Boolean = prefs.getBoolean(key(KEY_PUBLISHED, id), false)

    /** Con el id explícito: una publicación que termina tras cambiar de billetera marca la suya. */
    fun setPublished(id: String, value: Boolean) {
        prefs.edit().putBoolean(key(KEY_PUBLISHED, id), value).apply()
    }

    /**
     * Lo mismo para cada dirección de la billetera: `index` 0 es la principal
     * y 1 a [MAX_EXTRA_ADDRESSES] las otras. Sin esta marca la app volvía a
     * ofrecer «Publish» cada vez que se abría Receive.
     */
    fun isPublished(id: String, index: Int): Boolean =
        if (index == 0) isPublished(id) else prefs.getBoolean(extraKey(id, index), false)

    fun setPublished(id: String, index: Int, value: Boolean) {
        if (index == 0) setPublished(id, value)
        else prefs.edit().putBoolean(extraKey(id, index), value).apply()
    }

    private fun extraKey(id: String, index: Int) = "${key(KEY_PUBLISHED, id)}#$index"

    /** Borra todas las billeteras v3 de este teléfono: frases, PIN y estado. */
    fun wipe() {
        val dirs = ids().map(::stateDirOf)
        prefs.edit().clear().commit()
        context.filesDir.resolve("stark").deleteRecursively()
        dirs.forEach { java.io.File(it).deleteRecursively() }
        context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("stark-") }
            ?.forEach { it.deleteRecursively() }
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
        private const val KEY_WALLETS = "wallets"
        private const val KEY_ACTIVE = "active_wallet"
        private const val KEY_NAME = "name"
        const val MAIN = "main"
        const val MAX_NAME = 24
        /** Además de la principal: el motor admite ocho direcciones por billetera. */
        const val MAX_EXTRA_ADDRESSES = 7
    }
}
