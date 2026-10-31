package com.zoka.zkapriv.network.quantic

import android.content.Context
import com.zoka.zkapriv.network.nativebridge.ZokaNativeNetwork
import com.zoka.zkapriv.network.nativebridge.ZokaStark
import com.zoka.zkapriv.security.vault.QuanticVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Un movimiento del historial, como lo muestra la app. */
data class QuanticMovement(
    val txid: String,
    val height: Int,
    val timestamp: Long,
    /** `mined`, `received` o `sent`. */
    val kind: String,
    val amountAtoms: Long,
    val feeAtoms: Long,
    val memo: String,
    val confirmations: Int,
)

data class QuanticState(
    /** Hay billetera guardada y se leyó su estado al menos una vez. */
    val loaded: Boolean = false,
    val height: Int = 0,
    val tip: Int = 0,
    val inSync: Boolean = false,
    val spendableAtoms: Long = 0,
    val immatureAtoms: Long = 0,
    val reservedAtoms: Long = 0,
    val maxSendableAtoms: Long = 0,
    val pendingTxid: String? = null,
    val history: List<QuanticMovement> = emptyList(),
    val syncing: Boolean = false,
    /** Cómo se leyó la última vez: `https`, `p2p` o `tor`. */
    val source: String? = null,
    val lastSyncMillis: Long = 0,
    val error: String? = null,
) {
    val balanceAtoms: Long get() = spendableAtoms + immatureAtoms + reservedAtoms
    /** Avance de la lectura de la cadena, de 0 a 1. */
    val progress: Float get() = if (tip <= 0) 0f else (height.toFloat() / tip).coerceIn(0f, 1f)
}

/**
 * La billetera de Zoka Quantic Testnet v2 del teléfono, sobre [ZokaStark].
 *
 * Lee la cadena por páginas: sin Tor, primero por HTTPS (zokascan.cc, que
 * las cachea; después la API del explorador) y, si no hay respuesta, por P2P
 * a una semilla; con el Modo Tor, solo por P2P a las semillas .onion, a
 * través del Arti del teléfono. Si Tor no arranca, no se conecta de otra
 * forma. La prueba de cada pago se hace en el teléfono.
 */
@Singleton
class QuanticWallet @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: QuanticVault,
) {
    private val _state = MutableStateFlow(QuanticState())
    val state: StateFlow<QuanticState> = _state.asStateFlow()

    /** Sincronizar y enviar no se pisan (Rust también lo cuida). */
    private val busy = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var torProxy: String? = null
    @Volatile private var info: JSONObject? = null

    fun hasWallet(): Boolean = vault.hasWallet()

    /** 24 palabras nuevas; no se guardan hasta [store]. */
    fun newPhrase(): String = ZokaStark.newPhrase()

    /** Valida una frase (24 palabras de ZSilent) sin guardarla. */
    fun validPhrase(phrase: String): Boolean =
        runCatching { ZokaStark.walletInfo(phrase) }.isSuccess

    /** Guarda la frase. Una billetera restaurada lee desde el bloque 1. */
    fun store(phrase: String) {
        vault.storePhrase(phrase, birth = 1)
        info = null
        _state.value = QuanticState()
    }

    private fun phrase(): String = vault.phrase() ?: error("There is no wallet on this phone")

    private fun info(): JSONObject = info ?: ZokaStark.walletInfo(phrase()).also { info = it }

    /** La dirección completa (unos 1760 caracteres). */
    fun fullAddress(): String = info().getString("address")

    /** La dirección corta (57 caracteres): la que se comparte. */
    fun shortAddress(): String = info().getString("short")

    /** Pide autenticación antes de llamarla: muestra el saldo a quien la tenga. */
    fun viewKey(): String = ZokaStark.viewKey(phrase())

    /** El estado guardado, sin red. */
    suspend fun load() = withContext(Dispatchers.IO) {
        runCatching { ZokaStark.status(vault.phrase() ?: return@withContext, vault.stateDir, vault.birth()) }
            .onSuccess { reply -> _state.update { it.from(reply).copy(loaded = true) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    /** Lee la cadena hasta la punta. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        if (!vault.hasWallet() && vault.phrase() == null) return@withContext
        busy.withLock {
            _state.update { it.copy(syncing = true, error = null) }
            val result = runCatching {
                if (vault.torMode) syncP2p(tor = true) else syncHttpsThenP2p()
            }
            _state.update { current ->
                result.fold(
                    onSuccess = { (reply, source) ->
                        current.from(reply).copy(
                            loaded = true,
                            syncing = false,
                            source = source,
                            lastSyncMillis = System.currentTimeMillis(),
                        )
                    },
                    onFailure = { e -> current.copy(syncing = false, error = e.message ?: "Sync failed") },
                )
            }
        }
    }

    private fun syncHttpsThenP2p(): Pair<JSONObject, String> {
        val phrase = phrase()
        var reply = ZokaStark.status(phrase, vault.stateDir, vault.birth())
        return try {
            var rounds = 0
            while (!reply.getBoolean("in_sync") || rounds == 0) {
                if (rounds++ >= MAX_ROUNDS) break
                val page = fetchPage(reply.getInt("next")) ?: break
                reply = ZokaStark.addPages(phrase, vault.stateDir, listOf(page), vault.birth())
                _state.update { it.from(reply) }
            }
            reply to "https"
        } catch (https: Exception) {
            // La web no contesta (o no tiene /scan2 todavía): a una semilla, directo.
            syncP2p(tor = false)
        }
    }

    /** Una página de [SCAN_BASES]; null si la cadena no tiene más. */
    private fun fetchPage(from: Int): ByteArray? {
        var last: Exception? = null
        for (base in SCAN_BASES) {
            try {
                val request = Request.Builder().url("$base?from=$from&count=100").get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from $base")
                    val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty answer from $base")
                    return if (bytes.size < EMPTY_PAGE_BYTES && emptyPage(bytes)) null else bytes
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No page source")
    }

    /** Una página sin bloques: red, punta y origen, nada más (unos pocos bytes). */
    private fun emptyPage(bytes: ByteArray): Boolean = bytes.isNotEmpty() && bytes.last() == 0.toByte()

    private fun syncP2p(tor: Boolean): Pair<JSONObject, String> {
        val proxy = if (tor) ensureTor() else null
        val phrase = phrase()
        var reply = ZokaStark.sync(phrase, vault.stateDir, proxy, vault.birth())
        var rounds = 0
        while (!reply.getBoolean("in_sync") && rounds++ < MAX_ROUNDS) {
            _state.update { it.from(reply) }
            reply = ZokaStark.sync(phrase, vault.stateDir, proxy, vault.birth())
        }
        return reply to if (tor) "tor" else "p2p"
    }

    /** El SOCKS de Arti; si Tor no arranca, falla (nunca sale sin Tor). */
    private fun ensureTor(): String {
        torProxy?.let { return it }
        val reply = JSONObject(ZokaNativeNetwork.startTor(context.cacheDir.absolutePath))
        if (reply.has("error")) throw IllegalStateException("Tor did not start: ${reply.getString("error")}")
        return reply.getString("proxy").also { torProxy = it }
    }

    private fun route(): String? = if (vault.torMode) ensureTor() else null

    /** `enough`, `fee_atoms`, `max_sendable_atoms` y, si no alcanza, `reason`. */
    suspend fun quote(amountAtoms: Long): JSONObject = withContext(Dispatchers.IO) {
        ZokaStark.quote(phrase(), vault.stateDir, amountAtoms)
    }

    fun checkAddress(text: String): JSONObject = ZokaStark.checkAddress(text)

    /**
     * Prueba y manda un pago. Tarda (la prueba es en el teléfono). Devuelve
     * `txid`, `fee_atoms`, `expiry` y `proving_ms`.
     */
    suspend fun send(to: String, amountAtoms: Long, memo: String): JSONObject = withContext(Dispatchers.IO) {
        busy.withLock {
            val reply = ZokaStark.send(phrase(), vault.stateDir, to, amountAtoms, memo, route())
            runCatching { ZokaStark.status(phrase(), vault.stateDir, vault.birth()) }
                .onSuccess { status -> _state.update { it.from(status) } }
            reply
        }
    }

    suspend fun resolve(short: String): String? = withContext(Dispatchers.IO) {
        ZokaStark.resolve(short, route())
    }

    suspend fun publishAddress(): Boolean = withContext(Dispatchers.IO) {
        ZokaStark.publish(phrase(), route())
    }

    /** Al cambiar el Modo Tor la próxima lectura usa la ruta nueva. */
    fun setTorMode(enabled: Boolean) {
        vault.torMode = enabled
    }

    fun torMode(): Boolean = vault.torMode

    /** Olvida lo que tiene en memoria (al borrar la billetera). */
    fun forget() {
        info = null
        _state.value = QuanticState()
    }

    private fun QuanticState.from(reply: JSONObject): QuanticState {
        val history = reply.optJSONArray("history")?.let { list ->
            (0 until list.length()).map { i ->
                val m = list.getJSONObject(i)
                QuanticMovement(
                    txid = m.getString("txid"),
                    height = m.getInt("height"),
                    timestamp = m.getLong("timestamp"),
                    kind = m.getString("kind"),
                    amountAtoms = m.getLong("amount_atoms"),
                    feeAtoms = m.getLong("fee_atoms"),
                    memo = m.optString("memo"),
                    confirmations = m.getInt("confirmations"),
                )
            }
        } ?: history
        return copy(
            height = reply.optInt("height", height),
            tip = reply.optInt("tip", tip),
            inSync = reply.optBoolean("in_sync", inSync),
            spendableAtoms = reply.optLong("spendable_atoms", spendableAtoms),
            immatureAtoms = reply.optLong("immature_atoms", immatureAtoms),
            reservedAtoms = reply.optLong("reserved_atoms", reservedAtoms),
            maxSendableAtoms = reply.optLong("max_sendable_atoms", maxSendableAtoms),
            pendingTxid = reply.optJSONObject("pending")?.optString("txid"),
            history = history,
        )
    }

    companion object {
        /** Primero la copia en caché de Cloudflare, después la API del explorador. */
        val SCAN_BASES = listOf("https://zokascan.cc/scan2", "https://api.zokascan.cc/scan2")
        private const val MAX_ROUNDS = 40
        /** Una página vacía pesa unos pocos bytes; una con un bloque, kilobytes. */
        private const val EMPTY_PAGE_BYTES = 64
        const val ATOMS_PER_ZKA = 1_000_000L

        fun formatZka(atoms: Long): String {
            val whole = atoms / ATOMS_PER_ZKA
            val fraction = (atoms % ATOMS_PER_ZKA).toString().padStart(6, '0')
            return "%,d.%s".format(whole, fraction)
        }

        /** "12.5" → átomos; null si no es un monto válido con hasta 6 decimales. */
        fun parseZka(text: String): Long? {
            val clean = text.trim().replace(",", "")
            if (!Regex("^\\d{1,9}(\\.\\d{0,6})?$").matches(clean)) return null
            val parts = clean.split(".")
            val whole = parts[0].toLong()
            val fraction = parts.getOrNull(1).orEmpty().padEnd(6, '0').ifEmpty { "0" }.toLong()
            return whole * ATOMS_PER_ZKA + fraction
        }
    }
}
