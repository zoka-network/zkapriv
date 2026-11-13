package com.zoka.zkapriv.network.quantic

import android.content.Context
import android.util.Log
import com.zoka.zkapriv.network.nativebridge.ZokaNativeNetwork
import com.zoka.zkapriv.network.nativebridge.ZokaStark
import com.zoka.zkapriv.security.vault.QuanticVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/**
 * Una dirección de la billetera: la principal (`index` 0) o una de las otras
 * (1 a 7). `published` dice si una semilla ya aceptó su forma corta.
 */
data class WalletAddress(
    val index: Int,
    val short: String,
    val full: String,
    val published: Boolean,
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
    val verifiedHeight: Int = 0,
    val peers: Int = 0,
    val torEnabled: Boolean = false,
    val addressPublished: Boolean = false,
    val error: String? = null,
    /**
     * Hashrate estimado de la red, de `api.zokascan.cc/status` (la misma API de
     * las páginas). 0 si no se sabe: en el Modo Tor no se le pregunta.
     */
    val hashrateHps: Long = 0,
) {
    val balanceAtoms: Long get() = spendableAtoms + immatureAtoms + reservedAtoms
    /** Avance de la lectura de la cadena, de 0 a 1. */
    val progress: Float get() = if (tip <= 0) 0f else (height.toFloat() / tip).coerceIn(0f, 1f)
}

/**
 * La billetera de Zoka Quantic Testnet v2 del teléfono, sobre [ZokaStark].
 *
 * Lee la cadena por páginas: sin Tor, primero por HTTPS (API del explorador,
 * luego la web) y, si no hay respuesta, por P2P
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
    private val publishing = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
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

    // ── Varias billeteras ────────────────────────────────────────────────────

    fun wallets(): List<QuanticVault.WalletEntry> = vault.wallets()

    fun activeWalletId(): String = vault.activeId()

    /**
     * Pasa a otra billetera. Toma el mismo candado que la sincronización y el
     * envío: nunca se mezcla el estado de dos billeteras.
     */
    suspend fun switchTo(id: String) = withContext(Dispatchers.IO) {
        busy.withLock {
            vault.selectWallet(id)
            info = null
            _state.value = QuanticState()
            reloadStatus()
        }
    }

    /**
     * Agrega una billetera nueva o restaurada y pasa a ella. Lee desde el
     * bloque 1, como la primera.
     */
    suspend fun addWallet(name: String, phrase: String) = withContext(Dispatchers.IO) {
        busy.withLock {
            val id = vault.addWallet(name, phrase, birth = 1)
            vault.selectWallet(id)
            info = null
            _state.value = QuanticState()
            reloadStatus()
        }
    }

    fun renameWallet(id: String, name: String) = vault.renameWallet(id, name)

    /** Saca una billetera del teléfono; si era la activa, carga la que queda. */
    suspend fun removeWallet(id: String) = withContext(Dispatchers.IO) {
        busy.withLock {
            val wasActive = vault.activeId() == id
            vault.removeWallet(id)
            if (wasActive) {
                info = null
                _state.value = QuanticState()
                reloadStatus()
            }
        }
    }

    private fun phrase(): String = vault.phrase() ?: error("There is no wallet on this phone")

    private fun info(): JSONObject = info ?: ZokaStark.walletInfo(phrase()).also { info = it }

    /** La dirección completa (unos 1760 caracteres). */
    fun fullAddress(): String = info().getString("address")

    /** La dirección corta (57 caracteres): la que se comparte. */
    fun shortAddress(): String = info().getString("short")

    /** Pide autenticación antes de llamarla. No cambia el almacén ni el estado de sincronización. */
    suspend fun viewKey(expectedWalletId: String, scope: ViewKeyScope = ViewKeyScope.INCOMING): String =
        withContext(Dispatchers.IO) {
            busy.withLock {
                exportViewKeyForWallet(expectedWalletId, vault::activeId, scope) {
                    ZokaStark.viewKey(phrase(), it)
                }
            }
        }

    /** El estado guardado, sin red. */
    suspend fun load() = withContext(Dispatchers.IO) {
        busy.withLock { reloadStatus() }
    }

    private fun reloadStatus() {
        runCatching { ZokaStark.status(phrase(), vault.stateDir, vault.birth()) }
            .onSuccess { reply -> _state.update { it.from(reply).copy(loaded = true,
                torEnabled = vault.torMode, addressPublished = vault.addressPublished) } }
            .onFailure { _state.update { it.copy(error = "Could not read the wallet state") } }
    }

    /** Lee la cadena hasta la punta. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        if (!vault.hasWallet() && vault.phrase() == null) return@withContext
        // Un toque repetido o un envío no acumulan lecturas en espera.
        if (!busy.tryLock()) return@withContext
        try {
            _state.update { it.copy(syncing = true, error = null) }
            val result = runCatching {
                if (vault.torMode) syncP2p(tor = true) else syncHttpsThenP2p()
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            currentCoroutineContext().ensureActive()
            _state.update { current ->
                result.fold(
                    onSuccess = { (reply, source) ->
                        Log.i(TAG, "sync ok via $source")
                        current.from(reply).copy(
                            loaded = true,
                            syncing = false,
                            source = source,
                            lastSyncMillis = System.currentTimeMillis(),
                            error = null,
                        )
                    },
                    onFailure = { e ->
                        Log.w(TAG, "No se pudo sincronizar (${e.javaClass.simpleName})")
                        current.copy(syncing = false, error = "Could not refresh your balance. We will retry automatically.")
                    },
                )
            }
        } finally {
            _state.update { it.copy(syncing = false) }
            busy.unlock()
        }
    }

    private suspend fun syncHttpsThenP2p(): Pair<JSONObject, String> {
        val phrase = phrase()
        var reply = ZokaStark.status(phrase, vault.stateDir, vault.birth())
        return try {
            var rounds = 0
            while (!reply.getBoolean("in_sync") || rounds == 0) {
                if (rounds++ >= MAX_ROUNDS) break
                currentCoroutineContext().ensureActive()
                if (vault.torMode) return syncP2p(tor = true)
                val page = fetchPage(reply.getInt("next"))
                reply = ZokaStark.addPages(phrase, vault.stateDir, listOf(page), vault.birth())
                _state.update { it.from(reply).copy(loaded = true) }
            }
            currentCoroutineContext().ensureActive()
            if (vault.torMode) return syncP2p(tor = true)
            val request = Request.Builder().url("https://api.zokascan.cc/checkpoint")
                .header("Cache-Control", "no-cache").get().build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Checkpoint HTTP ${response.code}" }
                val checkpoint = response.body?.string() ?: error("Checkpoint vacío")
                reply = ZokaStark.confirmCheckpoint(phrase, vault.stateDir, checkpoint)
            }
            // Solo para mostrar: si falla, el saldo ya quedó leído y verificado.
            networkHashrate()?.let { hps -> _state.update { it.copy(hashrateHps = hps) } }
            reply to "https"
        } catch (https: Exception) {
            if (https is CancellationException) throw https
            // La web no contesta (o no tiene /scan2 todavía): a una semilla, directo.
            try {
                syncP2p(tor = vault.torMode)
            } catch (p2p: Exception) {
                if (p2p is CancellationException) throw p2p
                reloadStatus() // Una verificación fallida puede haber reiniciado la caché nativa.
                throw IllegalStateException("https: ${https.message}; p2p: ${p2p.message}")
            }
        }
    }

    /** `hashrate_estimate_hps` del explorador; null si no contesta. */
    private fun networkHashrate(): Long? = runCatching {
        val request = Request.Builder().url("https://api.zokascan.cc/status").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string() ?: return null).optLong("hashrate_estimate_hps", 0)
                .takeIf { it > 0 }
        }
    }.getOrNull()

    /** También las páginas vacías se verifican en Rust: actualizan la punta. */
    private fun fetchPage(from: Int): ByteArray {
        var last: Exception? = null
        for (base in SCAN_BASES) {
            try {
                val request = Request.Builder().url("$base?from=$from&count=100")
                    .header("Cache-Control", "no-cache").get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from $base")
                    // zokascan.cc/scan2 hoy cae en la página del explorador (HTML 200).
                    // Sin este filtro esa página se toma como cadena y no se prueba la API.
                    val type = response.header("Content-Type").orEmpty().substringBefore(';').trim()
                    if (!type.equals("application/octet-stream", ignoreCase = true)) {
                        throw IllegalStateException("page from $base is $type")
                    }
                    val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty answer from $base")
                    return bytes
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No page source")
    }

    private suspend fun syncP2p(tor: Boolean): Pair<JSONObject, String> {
        val proxy = if (tor) ensureTor() else null
        val phrase = phrase()
        var reply = ZokaStark.sync(phrase, vault.stateDir, proxy, vault.birth())
        var rounds = 0
        while (!reply.getBoolean("in_sync") && rounds++ < MAX_ROUNDS) {
            currentCoroutineContext().ensureActive()
            _state.update { it.from(reply).copy(loaded = true) }
            reply = ZokaStark.sync(phrase, vault.stateDir, proxy, vault.birth())
        }
        return reply to if (tor) "tor" else "p2p"
    }

    /** El SOCKS de Arti; si Tor no arranca, falla (nunca sale sin Tor). */
    @Synchronized private fun ensureTor(): String {
        torProxy?.let { return it }
        val reply = JSONObject(ZokaNativeNetwork.startTor(context.cacheDir.absolutePath))
        if (reply.has("error")) throw IllegalStateException("Tor could not start. Try again in a few seconds.")
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
            try {
                ZokaStark.send(phrase(), vault.stateDir, to, amountAtoms, memo, route())
            } finally {
                // También ante un timeout: las notas pueden quedar reservadas.
                reloadStatus()
            }
        }
    }

    fun sendPhase(): Int = ZokaStark.sendPhase()

    suspend fun resolve(short: String): String? = withContext(Dispatchers.IO) {
        ZokaStark.resolve(short, route(), vault.stateDir)
    }

    /**
     * Todas las direcciones de la billetera, la principal primero (punto 4 de
     * la revisión del 10/11), con la marca de si la red ya conoce su corta.
     */
    suspend fun addresses(): List<WalletAddress> = withContext(Dispatchers.IO) {
        busy.withLock {
            val id = vault.activeId()
            val list = ZokaStark.addresses(phrase(), vault.stateDir, vault.birth()).getJSONArray("addresses")
            (0 until list.length()).map { list.getJSONObject(it).toAddress(id) }
        }
    }

    /**
     * Una dirección más, hasta ocho. Los pagos a ella se encuentran desde el
     * próximo bloque: se crea antes de darla.
     */
    suspend fun addAddress(): WalletAddress = withContext(Dispatchers.IO) {
        busy.withLock {
            val id = vault.activeId()
            ZokaStark.addAddress(phrase(), vault.stateDir, vault.birth()).toAddress(id)
        }
    }

    private fun JSONObject.toAddress(walletId: String): WalletAddress {
        val index = getInt("index")
        return WalletAddress(index, getString("short"), getString("address"), vault.isPublished(walletId, index))
    }

    /**
     * Publica la dirección número `index` (1 en adelante) para que su corta
     * sirva, y lo recuerda: no hay que volver a publicarla.
     */
    suspend fun publishExtra(index: Int): Boolean = withContext(Dispatchers.IO) {
        publishing.withLock {
            // El id al empezar, como en [publishAddress].
            val id = vault.activeId()
            val accepted = ZokaStark.publish(phrase(), route(), vault.stateDir, index)
            if (accepted) vault.setPublished(id, index, true)
            accepted
        }
    }

    suspend fun publishAddress(): Boolean = withContext(Dispatchers.IO) {
        publishing.withLock {
            // El id al empezar: si en el medio se cambia de billetera, se
            // marca como publicada la que se publicó.
            val id = vault.activeId()
            val accepted = ZokaStark.publish(phrase(), route(), vault.stateDir)
            if (accepted) vault.setPublished(id, true)
            _state.update { it.copy(addressPublished = vault.addressPublished) }
            accepted
        }
    }

    /** Al cambiar el Modo Tor la próxima lectura usa la ruta nueva. */
    fun setTorMode(enabled: Boolean) {
        vault.torMode = enabled
        _state.update { it.copy(torEnabled = enabled) }
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
            verifiedHeight = reply.optInt("verified", verifiedHeight),
            peers = reply.optInt("peers", peers),
        )
    }

    companion object {
        /** La punta debe estar fresca; la web es la alternativa de lectura. */
        val SCAN_BASES = listOf("https://api.zokascan.cc/scan2", "https://zokascan.cc/scan2")
        private const val MAX_ROUNDS = 40
        private const val TAG = "QuanticWallet"
        const val ATOMS_PER_ZKA = 1_000_000L

        fun formatZka(atoms: Long): String {
            val whole = atoms / ATOMS_PER_ZKA
            val fraction = (atoms % ATOMS_PER_ZKA).toString().padStart(6, '0')
            return String.format(java.util.Locale.US, "%,d.%s", whole, fraction)
        }

        /** "12.5" → átomos; null si no es un monto válido con hasta 6 decimales. */
        fun parseZka(text: String): Long? {
            val clean = text.trim().replace(',', '.')
            if (!Regex("^\\d{1,9}(\\.\\d{0,6})?$").matches(clean)) return null
            val parts = clean.split(".")
            val whole = parts[0].toLong()
            val fraction = parts.getOrNull(1).orEmpty().padEnd(6, '0').ifEmpty { "0" }.toLong()
            return whole * ATOMS_PER_ZKA + fraction
        }
    }
}
