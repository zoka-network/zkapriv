package com.zoka.zkapriv.network.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.zoka.zkapriv.core.model.Token
import com.zoka.zkapriv.core.model.Transaction
import com.zoka.zkapriv.core.model.TransactionStatus
import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.network.api.ZokaNodeApi
import com.zoka.zkapriv.network.crypto.SeedEnvelopeEncryption
import com.zoka.zkapriv.network.model.ZokaSendFromSeedRequest
import com.zoka.zkapriv.network.model.ZokaStatsResponse
import com.zoka.zkapriv.network.model.ZokaTransactionRequest
import com.zoka.zkapriv.network.nativebridge.LocalProverClient
import com.zoka.zkapriv.security.storage.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.zoka.zkapriv.core.util.Ristretto255
import com.zoka.zkapriv.network.model.ZokaPrivateOutputModel
import com.zoka.zkapriv.network.model.ZokaPrivateTransactionResponse
import com.zoka.zkapriv.network.model.requireCompleteRange
import com.zoka.zkapriv.security.storage.PrivateTxOutboxRecord
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

internal fun privateSendRequestKey(
    from: String,
    to: String,
    amountAtoms: Long,
    feeAtoms: Long
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("zoka-private-send-request-v1".toByteArray(Charsets.UTF_8))
    listOf(from, to, amountAtoms.toString(), feeAtoms.toString()).forEach { field ->
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun matchingPrivateTxOutbox(
    records: List<PrivateTxOutboxRecord>,
    requestKey: String
): PrivateTxOutboxRecord? {
    val matches = records.filter { it.requestKey == requestKey }
    check(matches.size <= 1) {
        "Hay varias transacciones pendientes para la misma solicitud privada"
    }
    return matches.singleOrNull()
}

internal fun ZokaRepository.BalanceInfo.isVerifiedAtTip(
    chainHeight: Long,
    chainTipHash: String
): Boolean =
    isVerified &&
        scannedHeight == chainHeight &&
        verifiedTipHash.isNotBlank() &&
        verifiedTipHash == chainTipHash

@Singleton
class ZokaRepository @Inject constructor(
    private val zokaNodeApi: ZokaNodeApi,
    private val sharedPreferences: android.content.SharedPreferences,
    private val rpcSettings: com.zoka.zkapriv.network.config.RpcSettings,
    private val secureStorage: SecureStorage,
    private val localProverClient: LocalProverClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val gson = Gson()
    private val MAX_CACHED_TRANSACTIONS = 50

    // A genesis reset keeps the same wallet address but starts a new chain
    // (new chain_id) with no history. Local caches (Recent Activity, balance
    // snapshots) are keyed by address only, so they'd otherwise keep showing
    // the previous chain's activity forever. Detect the chain_id change once
    // per app start and wipe exactly those caches — never the wallet/keys.
    init {
        val lastSeenChainId = sharedPreferences.getString(PREF_KEY_NETWORK_EPOCH, null)
        if (lastSeenChainId != Constants.ZOKA_CHAIN_ID) {
            val editor = sharedPreferences.edit()
            sharedPreferences.all.keys
                .filter { it.startsWith(PREF_KEY_PREFIX) }
                .forEach { editor.remove(it) }
            editor.putString(PREF_KEY_NETWORK_EPOCH, Constants.ZOKA_CHAIN_ID)
            editor.apply()
            rpcSettings.clearAllBalanceCaches()
            if (lastSeenChainId != null) {
                Log.i(
                    "ZokaRepository",
                    "Network chain_id changed ($lastSeenChainId -> ${Constants.ZOKA_CHAIN_ID}); cleared local activity/balance caches"
                )
            }
        }
    }

    // On-device private transaction builder (Rust JNI engine). Every build
    // variant uses it, so the recovery seed never leaves the phone.
    private val localTransferBuilder by lazy {
        com.zoka.zkapriv.network.nativebridge.LocalPrivateTransferBuilder(
            zokaNodeApi,
            context,
            secureStorage,
            localProverClient
        )
    }

    // Balance cache: prevents multiple ViewModels from scanning simultaneously
    private val balanceCache = mutableMapOf<String, Pair<BalanceInfo, Long>>()
    private val scanMutex = Mutex()
    // Covers pending replay, local proving, durable outbox/reservation writes and
    // publication. Two taps/coroutines must never build from the same note set.
    private val sendMutex = Mutex()
    // Private tx cache: immutable on-chain data, safe to cache for the app session.
    // ConcurrentHashMap-backed because batched parallel fetches in scanFullBalance
    // write to these from multiple coroutines at once.
    private val privateTxCache = java.util.concurrent.ConcurrentHashMap<String, ZokaPrivateTransactionResponse>()
    /**
     * En que anda el escaneo, para que la interfaz no vaya a ciegas.
     *
     * El avance existia solo en el registro: la aplicacion sabia perfectamente
     * en que bloque iba y el usuario no. Mostraba el resultado -- "saldo sin
     * verificar" -- pero nunca el proceso, asi que una espera larga y una
     * pantalla colgada se veian igual.
     */
    data class SyncProgress(
        val scanning: Boolean,
        val scannedHeight: Long = 0,
        val chainHeight: Long = 0,
        /**
         * En que fase va, porque son dos y duran cosas muy distintas.
         *
         * Medir solo los bloques hacia que la franja se clavara en 99% durante
         * toda la busqueda de cuerpos -- que es justo donde se va el tiempo.
         * Un indicador que se congela en el tramo lento es peor que no tenerlo:
         * parece que la app se colgo.
         */
        val phase: Phase = Phase.BLOCKS,
        val bodiesDone: Int = 0,
        val bodiesTotal: Int = 0
    ) {
        enum class Phase { BLOCKS, BODIES }

        /** Fraccion escaneada, o `null` si todavia no se sabe hasta donde hay que ir. */
        val fraction: Float?
            get() = when {
                phase == Phase.BODIES && bodiesTotal > 0 ->
                    (bodiesDone.toFloat() / bodiesTotal.toFloat()).coerceIn(0f, 1f)
                phase == Phase.BODIES -> null
                chainHeight > 0 && scannedHeight in 0..chainHeight ->
                    (scannedHeight.toFloat() / chainHeight.toFloat()).coerceIn(0f, 1f)
                else -> null
            }
    }

    /**
     * Cuantos pares hay, con su historial y su frescura.
     *
     * Copiado del criterio de ZSilent: un numero de pares sin fecha miente.
     * El conteo se actualiza cuando el nodo bombea eventos, asi que si el nodo
     * esta ocupado el dato envejece -- y presentarlo como actual haria creer
     * que hay red cuando puede no haberla.
     *
     * Por eso viaja con `observedAtMillis` y la pantalla distingue tres casos:
     * en vivo, ultimo conocido, y nunca leido.
     */
    data class PeerTelemetry(
        val peers: Int = 0,
        val observedAtMillis: Long = 0L,
        val consecutiveFailures: Int = 0,
        /** Las ultimas lecturas, para la linea de tendencia. */
        val samples: List<Int> = emptyList()
    ) {
        val everObserved: Boolean get() = observedAtMillis > 0L

        fun isFresh(nowMillis: Long): Boolean =
            everObserved &&
                consecutiveFailures == 0 &&
                (nowMillis - observedAtMillis) <= VENTANA_FRESCA_MS
    }

    private val _peerTelemetry = kotlinx.coroutines.flow.MutableStateFlow(PeerTelemetry())
    val peerTelemetry: kotlinx.coroutines.flow.StateFlow<PeerTelemetry> = _peerTelemetry

    /**
     * Toma una muestra del conteo de pares.
     *
     * Una lectura fallida no borra la anterior: incrementa el contador de
     * fallos y deja el ultimo valor conocido, que la pantalla etiqueta como
     * tal. Tirar el dato ante el primer error hacia parpadear el indicador.
     */
    suspend fun muestrearPares() {
        val leido = runCatching { zokaNodeApi.peerCount() }.getOrNull()
        val ahora = System.currentTimeMillis()
        _peerTelemetry.value = _peerTelemetry.value.let { previo ->
            if (leido == null) {
                previo.copy(consecutiveFailures = previo.consecutiveFailures + 1)
            } else {
                previo.copy(
                    peers = leido,
                    observedAtMillis = ahora,
                    consecutiveFailures = 0,
                    samples = (previo.samples + leido).takeLast(MUESTRAS_DE_PARES)
                )
            }
        }
    }

    private val _syncProgress = kotlinx.coroutines.flow.MutableStateFlow(SyncProgress(false))
    val syncProgress: kotlinx.coroutines.flow.StateFlow<SyncProgress> = _syncProgress

    private val missingPrivateTxCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * La lista de cuerpos irrecuperables sobrevive al cierre de la app.
     *
     * Vivia solo en memoria: cada arranque volvia a pedir los mismos doce
     * cuerpos y a recibir el mismo "not found". Medido en el telefono, el
     * escaneo de cadena tardaba dos segundos y esos reintentos sesenta.
     */
    private var cuerposPerdidosCargados = false

    private fun cargarCuerposPerdidos() {
        if (cuerposPerdidosCargados) return
        cuerposPerdidosCargados = true
        val guardado = runCatching { secureStorage.getUnavailablePrivateTxBodies() }.getOrNull()
            ?: return
        runCatching {
            org.json.JSONArray(guardado)
        }.onSuccess { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { missingPrivateTxCache.add(it) }
            }
            Log.d("ZokaRepository", "cuerpos irrecuperables recordados: ${missingPrivateTxCache.size}")
        }
    }

    private fun guardarCuerposPerdidos() {
        runCatching {
            secureStorage.storeUnavailablePrivateTxBodies(
                org.json.JSONArray(missingPrivateTxCache.toList()).toString()
            )
        }
    }

    /**
     * Cuantos escaneos pasan antes de volver a probar los cuerpos que faltan.
     *
     * Ni cero ni infinito. Cero era lo que habia y hacia que cada refresco
     * gastara minutos en cuerpos perdidos; infinito perderia para siempre uno
     * que reaparezca porque se conecto un par que si lo tiene.
     */
    private var escaneosDesdeElUltimoReintento = 0

    companion object {
        private const val PREF_KEY_PREFIX = "transactions_"
        private const val PREF_KEY_NETWORK_EPOCH = "network_chain_id_epoch"
        private const val ATOMS_PER_ZKA = 1_000_000L
        /**
         * Cuántos bloques se piden por vez.
         *
         * Eran 250, heredado de cuando cada bloque viajaba entero y un tramo
         * grande era medio mega. Con resúmenes comprimidos un bloque son ~51
         * bytes, así que mil entran en cincuenta kilobytes y el cuello deja de
         * ser el ancho de banda: pasa a ser el viaje de ida y vuelta, que sobre
         * Tor cuesta uno o dos segundos. Cuatro veces menos viajes.
         *
         * Mil es el techo que sirve el protocolo de resúmenes; pedir más haría
         * que el par recorte la respuesta y perderíamos el viaje igual.
         */
        private const val RAW_BLOCK_SCAN_BATCH_SIZE = 1_000L
        const val COINBASE_SENDER = "zoka:coinbase:v1"
        private const val COINBASE_MATURITY_BLOCKS = 60L
        private const val BALANCE_CACHE_TTL_MS = 30_000L
        /**
         * Cuántas veces se intenta alcanzar la punta antes de rendirse.
         *
         * Cada intento escanea solo los bloques nuevos, que con resúmenes son
         * segundos. Tres alcanza de sobra para una red que produce un bloque
         * cada dos minutos; si hicieran falta más, el problema no es la
         * carrera sino que no damos abasto, y eso hay que decirlo en vez de
         * seguir intentando.
         */
        private const val MAX_TIP_CATCHUP_ATTEMPTS = 3
        /**
         * Cada cuantos escaneos se reintentan los cuerpos privados que no
         * aparecieron. Diez: con refrescos de medio minuto, una oportunidad
         * cada pocos minutos alcanza para recuperar uno que reaparezca sin
         * pagar el costo en cada pasada.
         */
        private const val ESCANEOS_ENTRE_REINTENTOS_DE_CUERPOS = 10

        /** Cuanto vale una lectura de pares antes de pasar a "ultimo conocido". */
        private const val VENTANA_FRESCA_MS = 30_000L

        /** Cuantas lecturas guarda la linea de tendencia. */
        private const val MUESTRAS_DE_PARES = 40
        private const val BALANCE_SCAN_SCHEMA_VERSION = 2
        private const val MAX_BALANCE_SCAN_SNAPSHOT_CHARS = 2 * 1024 * 1024
        private const val SAVED_BALANCE_VERIFICATION_ISSUE =
            "Showing a saved balance. Sending stays disabled until the P2P scan verifies the current chain tip."
        // Only flag a tx FAILED once NO node has its body and it is in no
        // block for this long — generous so a valid send waiting for a miner
        // (or only on the peer node) is never falsely marked failed.
        private const val TX_FAILED_AFTER_MIN = 6 * 60L
        private const val PERSISTENT_CACHE_TTL_MS = 30 * 60 * 1_000L
        private const val BATCH_DELAY_MS = 80L
        /** Cada cuantos bloques se anota el avance del escaneo. */
        private const val BLOQUES_ENTRE_PUNTOS_DE_CONTROL = 1_000L
        // Estas esperas son herencia de HTTP y aca ya no compran nada.
        //
        // Venian del explorador web pegandole al mismo nodo por REST, donde el
        // limitador de peticiones era real. Esta app habla P2P directo contra
        // un par, y ademas los pedidos pasan por un mutex que los serializa
        // uno por uno: no hay avalancha posible que haya que frenar.
        //
        // Con 266 transacciones, 350 ms por tanda eran mas de veinte segundos
        // de espera pura contra un limitador que no existe en este camino. El
        // reintento por 429 sigue en su lugar por si algun par si limita.
        private const val PRIVATE_TX_PHASE_DELAY_MS = 250L
        private const val PRIVATE_TX_DELAY_MS = 0L
        private val PRIVATE_TX_RETRY_DELAYS_MS = longArrayOf(8_000L, 20_000L)
        // La tanda no gana paralelismo -- el mutex serializa los pedidos igual
        // --, pero si amortiza la pausa entre tandas y hace que el avance en
        // pantalla no salte de a seis. Subirla no carga mas al par: la cola es
        // la misma, solo se pide con menos pausas en el medio.
        private const val PRIVATE_TX_FETCH_BATCH_SIZE = 24
        private const val PRIVATE_TX_BATCH_DELAY_MS = 0L

        /** Cada cuantas tandas de cuerpos se anota lo descubierto. */
        private const val TANDAS_ENTRE_PUNTOS_DE_CONTROL = 2
    }

    data class BalanceInfo(
        val matureAtoms: Long,
        val immatureAtoms: Long,
        val missingTxCount: Int = 0,
        val scannedHeight: Long = 0,
        /**
         * True only when this process has just completed the full private scan
         * that produced this value. Persistent/cache/coinbase fallbacks stay
         * fail-closed because a zero missing count alone is not verification.
         */
        val isVerified: Boolean = false,
        /** Hash of [scannedHeight] used to reject a same-height reorganization. */
        val verifiedTipHash: String = "",
        /** User-facing reason why this value is not safe to spend yet. */
        val verificationIssue: String? = null,
        /**
         * Atoms locally reserved by in-flight outbound transfers (sends
         * the user has broadcast but that the chain has not yet confirmed).
         * Subtracted from [matureAtoms] to compute [availableAtoms] so the
         * user can't accidentally try to double-spend them.
         */
        val pendingOutAtoms: Long = 0
    ) {
        /** Mature funds minus what's locked in pending outgoing transfers. */
        val availableAtoms: Long get() = (matureAtoms - pendingOutAtoms).coerceAtLeast(0L)
        /** Everything the wallet currently holds, mature + immature. */
        val totalAtoms: Long get() = matureAtoms + immatureAtoms
        val hasMining: Boolean get() = immatureAtoms > 0
        val isIncomplete: Boolean get() = missingTxCount > 0
        val hasPendingOut: Boolean get() = pendingOutAtoms > 0
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    suspend fun getAddressBalance(
        address: String,
        scanKey: ByteArray? = null,
        restoreHeight: Long = 0
    ): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        getAddressBalanceOnIo(address, scanKey, restoreHeight)
    }

    private suspend fun getAddressBalanceOnIo(
        address: String,
        scanKey: ByteArray?,
        restoreHeight: Long
    ): Result<BalanceInfo> {
        return try {
            restorePendingReservationsFromOutbox(address)
            val cacheKey = if (scanKey != null) "${address}_full" else address
            // Hydrate in-memory cache from persistent storage on first access of this session
            if (scanKey != null && !balanceCache.containsKey(cacheKey)) {
                rpcSettings.readBalanceCache(address)?.let { saved ->
                    balanceCache[cacheKey] = Pair(
                        BalanceInfo(
                            matureAtoms = saved.matureAtoms,
                            immatureAtoms = saved.immatureAtoms,
                            missingTxCount = saved.missingTxCount,
                            scannedHeight = saved.scannedHeight,
                            verificationIssue = SAVED_BALANCE_VERIFICATION_ISSUE
                        ),
                        saved.savedAtMillis
                    )
                }
            }
            val cached = balanceCache[cacheKey]
            // Coinbase-only reads are informational and may use their short
            // cache. A full private balance is never called current merely
            // because five minutes have not elapsed: first compare it with the
            // live P2P tip inside the scan mutex below.
            if (
                scanKey == null && cached != null &&
                System.currentTimeMillis() - cached.second < BALANCE_CACHE_TTL_MS
            ) {
                return Result.Success(cached.first.withReconciledPendings(address))
            }
            scanMutex.withLock {
                val height = zokaNodeApi.getChainHeight().height
                val cachedNow = balanceCache[cacheKey]
                if (scanKey != null && cachedNow?.first?.isVerified == true) {
                    val currentTipHash = fetchBlockDigestsRange(height, height)
                        .requireCompleteRange(height, height)
                        .single()
                        .header
                        ?.hash
                        .orEmpty()
                    if (cachedNow.first.isVerifiedAtTip(height, currentTipHash)) {
                        reconcilePendingSendsWithNode(address)
                        return@withLock Result.Success(
                            cachedNow.first.withReconciledPendings(address)
                        )
                    }
                }
                if (
                    scanKey == null && cachedNow != null &&
                    System.currentTimeMillis() - cachedNow.second < BALANCE_CACHE_TTL_MS
                ) {
                    return@withLock Result.Success(cachedNow.first.withReconciledPendings(address))
                }
                val rawInfo = if (scanKey != null) {
                    var scanned = scanFullBalance(address, scanKey, height, restoreHeight)
                    var revalidated = revalidateFreshFullScan(scanned.balance)
                    // Alcanzar la cadena en vez de rendirse ante ella.
                    //
                    // Que crezca mientras escaneamos no es un fallo: es lo que
                    // hace una cadena viva. Los bloques nuevos son pocos y con
                    // resúmenes cuestan segundos, así que se escanean y se
                    // vuelve a verificar. Acotado, porque si la red produjera
                    // más rápido de lo que podemos seguir, insistir sería un
                    // bucle: ahí sí conviene rendirse y decirlo.
                    var attempts = 0
                    while (
                        !revalidated.tipIsCurrent &&
                        revalidated.reachable &&
                        attempts < MAX_TIP_CATCHUP_ATTEMPTS
                    ) {
                        attempts++
                        val newHeight = zokaNodeApi.getChainHeight().height
                        scanned = scanFullBalance(address, scanKey, newHeight, restoreHeight)
                        revalidated = revalidateFreshFullScan(scanned.balance)
                    }
                    if (!revalidated.tipIsCurrent) {
                        // The confirmations belong to a tip that is no longer
                        // current (or could not be proven current). Never let
                        // them delete a reservation/outbox. Also avoid caching
                        // this branch so the next refresh retries the scan.
                        confirmedPrivateTxHashes.clear()
                        return@withLock Result.Success(
                            revalidated.balance.withPendingReservationsOnly(address)
                        )
                    }
                    confirmedPrivateTxHashes.clear()
                    confirmedPrivateTxHashes.addAll(scanned.confirmedHashes)
                    revalidated.balance
                } else {
                    scanCoinbaseBalance(address, height)
                }
                balanceCache[cacheKey] = Pair(rawInfo, System.currentTimeMillis())
                if (scanKey != null) {
                    rpcSettings.saveBalanceCache(
                        address = address,
                        matureAtoms = rawInfo.matureAtoms,
                        immatureAtoms = rawInfo.immatureAtoms,
                        missingTxCount = rawInfo.missingTxCount,
                        scannedHeight = rawInfo.scannedHeight
                    )
                }
                // Tie pending-send reservations to the tx's real lifecycle
                // (runs after the scan, so the confirmed set is fresh) before
                // summing them into pendingOut.
                reconcilePendingSendsWithNode(address)
                Result.Success(rawInfo.withReconciledPendings(address))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ZokaRepository", "getAddressBalance: ${e.message}", e)
            // On failure, surface persistent cache so user sees a stale value instead of "0"
            if (scanKey != null) {
                rpcSettings.readBalanceCache(address)?.let { saved ->
                    return Result.Success(
                        BalanceInfo(
                            matureAtoms = saved.matureAtoms,
                            immatureAtoms = saved.immatureAtoms,
                            missingTxCount = saved.missingTxCount,
                            scannedHeight = saved.scannedHeight,
                            verificationIssue = SAVED_BALANCE_VERIFICATION_ISSUE
                        ).withReconciledPendings(address)
                    )
                }
            }
            Result.Error(e)
        }
    }

    /**
     * Overlay locally-tracked pending outbound sends on top of a freshly
     * scanned BalanceInfo:
     *
     *   - Recreate a missing reservation from its durable encrypted outbox.
     *   - Remove a reservation only when the latest chain scan contains the
     *     transaction hash. Wall-clock age is never confirmation evidence.
     *   - Sum the remaining reservations into `pendingOutAtoms` so the
     *     UI can show Available = Mature - PendingOut.
     */
    private fun BalanceInfo.withReconciledPendings(address: String): BalanceInfo {
        restorePendingReservationsFromOutbox(address)
        val survivors = rpcSettings.expirePendingSends(address)
        if (survivors.isEmpty()) return this
        var pendingOut = 0L
        for (p in survivors) {
            if (confirmedPrivateTxHashes.contains(p.hash)) {
                // Remove the replay body first. A crash between these commits
                // can only leave an overly conservative reservation; the
                // opposite order could resurrect a confirmed outbox.
                secureStorage.removePrivateTxOutbox(p.hash)
                rpcSettings.removePendingSend(address, p.hash)
            } else {
                pendingOut = pendingOut.saturatingAdd(p.reservedAtoms)
            }
        }
        return copy(pendingOutAtoms = pendingOut)
    }

    /**
     * Conservative overlay used when a fresh scan could not revalidate its
     * tip. It reflects every reservation but deliberately ignores the scan's
     * confirmation set, so no pending record or replay body can be deleted.
     */
    private fun BalanceInfo.withPendingReservationsOnly(address: String): BalanceInfo {
        val pendingOut = rpcSettings.expirePendingSends(address)
            .fold(0L) { total, pending -> total.saturatingAdd(pending.reservedAtoms) }
        return copy(pendingOutAtoms = pendingOut)
    }

    /**
     * Keep "pending out" reservations in step with each tx's real lifecycle.
     * Run after a scan so the confirmed set is fresh. For every reserved send:
     *   - if the scan already saw it confirmed in a block, drop the
     *     reservation (the spend is now reflected in `mature`);
     *   - if the node does not currently know it, replay the exact encrypted
     *     outbox body. Failure leaves both body and reservation untouched.
     */
    private suspend fun reconcilePendingSendsWithNode(address: String) {
        val outboxByHash = restorePendingReservationsFromOutbox(address)
            .associateBy { it.hash }
        for (p in rpcSettings.readPendingSends(address)) {
            if (confirmedPrivateTxHashes.contains(p.hash)) {
                secureStorage.removePrivateTxOutbox(p.hash)
                rpcSettings.removePendingSend(address, p.hash)
                continue
            }
            val nodeKnowsTx = try {
                zokaNodeApi.getPrivateTransaction(p.hash)
                true
            } catch (_: Exception) {
                false
            }
            if (!nodeKnowsTx) {
                outboxByHash[p.hash]?.let { record ->
                    runCatching { publishOutboxRecord(record) }
                        .onFailure { error ->
                            Log.w(
                                "ZokaRepository",
                                "Could not replay private outbox ${record.hash}: ${error.message}"
                            )
                        }
                }
            }
        }
    }

    /**
     * The encrypted outbox is authoritative because it is committed before the
     * plain RPC preference. Rebuild any missing/mismatched reservation after a
     * process death, then return validated records for replay/build input.
     */
    private fun restorePendingReservationsFromOutbox(
        address: String
    ): List<PrivateTxOutboxRecord> {
        val records = secureStorage.readPrivateTxOutbox(address)
        if (records.isEmpty()) return emptyList()
        val pendingByHash = rpcSettings.readPendingSends(address).associateBy { it.hash }
        for (record in records) {
            parseOutboxBody(record)
            val pending = pendingByHash[record.hash]
            if (pending == null || pending.reservedAtoms != record.reservedAtoms) {
                rpcSettings.recordPendingSend(address, record.hash, record.reservedAtoms)
            }
        }
        return records
    }

    private fun parseOutboxBody(record: PrivateTxOutboxRecord): JsonObject {
        val body = gson.fromJson(record.bodyJson, JsonObject::class.java)
            ?: throw IllegalStateException("Encrypted outbox ${record.hash} has no body")
        check(body.get("tx_hash")?.asString == record.hash) {
            "Encrypted outbox body ${record.hash} identifies a different transaction"
        }
        return body
    }

    private suspend fun publishOutboxRecord(record: PrivateTxOutboxRecord): String {
        val submitted = zokaNodeApi.submitPrivateTx(parseOutboxBody(record))
        val submittedHash = submitted.get("hash")?.asString.orEmpty()
        check(submittedHash == record.hash) {
            "Node returned a different hash for encrypted-outbox private transaction"
        }
        return record.hash
    }

    /**
     * Hashes of private transactions the most recent scan observed in a
     * block (i.e. confirmed). Drives the pending-send reconciliation in
     * [withReconciledPendings]. Rebuilt at the start of every full scan so
     * orphaned hashes from a reorganized branch cannot survive in memory.
     */
    private val confirmedPrivateTxHashes =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Cheap chain-height check (no block/tx fetching) — used to decide
     * whether a periodic balance refresh actually needs to re-scan, instead
     * of blindly invalidating the cache on every tick.
     */
    suspend fun getChainHeightCheap(): Long? = try {
        zokaNodeApi.getChainHeight().height
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun cachedBalanceFor(address: String): BalanceInfo? = withContext(Dispatchers.IO) {
        val saved = rpcSettings.readBalanceCache(address) ?: return@withContext null
        BalanceInfo(
            matureAtoms = saved.matureAtoms,
            immatureAtoms = saved.immatureAtoms,
            missingTxCount = saved.missingTxCount,
            scannedHeight = saved.scannedHeight,
            verificationIssue = SAVED_BALANCE_VERIFICATION_ISSUE
        ).withReconciledPendings(address)
    }

    fun invalidateBalanceCacheFor(address: String) {
        balanceCache.remove(address)
        balanceCache.remove("${address}_full")
    }

    fun invalidateAllInMemoryCaches() {
        balanceCache.clear()
        privateTxCache.clear()
        missingPrivateTxCache.clear()
    }

    /**
     * Trae el tramo en forma de resúmenes.
     *
     * Todo lo que el escaneo mira —el coinbase, los hashes de transacciones
     * privadas, los nulificadores— viaja en un resumen. Lo que no viaja es la
     * prueba de rango del coinbase, que pesa más que todo lo demás junto y solo
     * hace falta para gastar la nota, no para contarla. Quien vaya a gastar
     * (LocalPrivateTransferBuilder) pide el bloque entero solo si reconoce un
     * coinbase propio.
     */
    private suspend fun fetchBlockDigestsRange(from: Long, to: Long) =
        zokaNodeApi.getBlockDigestsRange(from, to)

    private suspend fun scanCoinbaseBalance(address: String, chainHeight: Long): BalanceInfo {
        var matureAtoms = 0L
        var immatureAtoms = 0L
        var from = 1L
        var previousHash = ""
        while (from <= chainHeight) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, chainHeight)
            val response = fetchBlockDigestsRange(from, to)
            val blocks = response.requireCompleteRange(
                from,
                to,
                previousHash.takeIf(String::isNotBlank)
            )
            delay(BATCH_DELAY_MS)
            blocks.forEach { block ->
                // Coinbase tx is always at index 0 with sender == COINBASE_SENDER
                val coinbaseTx = block.transactions.firstOrNull()
                if (coinbaseTx != null && coinbaseTx.receiver == address && coinbaseTx.amount > 0) {
                    // Maturity from private_coinbase if present, else calculate from block height
                    val blockHeight = block.header?.blockHeight ?: 0L
                    val unlockHeight = block.privateCoinbase?.unlockHeight
                        ?: (blockHeight + COINBASE_MATURITY_BLOCKS)
                    if (unlockHeight <= chainHeight) {
                        matureAtoms = matureAtoms.saturatingAdd(coinbaseTx.amount)
                    } else {
                        immatureAtoms = immatureAtoms.saturatingAdd(coinbaseTx.amount)
                    }
                }
            }
            previousHash = blocks.last().header?.hash.orEmpty()
            from = to + 1
        }
        return BalanceInfo(
            matureAtoms = matureAtoms,
            immatureAtoms = immatureAtoms,
            scannedHeight = chainHeight,
            verificationIssue =
                "The private scan key is unavailable, so this is a coinbase-only balance. Sending is disabled."
        )
    }

    // ── Full UTXO scan (requires scan key) ───────────────────────────────────

    private data class CoinbaseEntry(
        val amountAtoms: Long,
        val unlockHeight: Long,
        val outputId: String,
        val commitmentHex: String
    )

    /**
     * Lo que el escaneo de bloques ya averiguó, para no volver a averiguarlo.
     *
     * Recorrer la cadena entera en cada refresco era razonable contra una API
     * REST cercana: un rango de 250 bloques volvía en un suspiro. Pidiéndoselos
     * a la red bloque por bloque son decenas de minutos, y con el saldo
     * caducando cada 30 segundos el escaneo nunca alcanzaba a terminar antes de
     * que lo reiniciaran. La wallet quedaba en 0 para siempre, haciendo trabajo
     * sin llegar nunca a nada.
     *
     * `tipHash` es el ancla contra reorganizaciones: si el bloque que estaba en
     * `scannedHeight` ya no es el mismo, la cadena se movió bajo nuestros pies
     * y hay que rehacer el escaneo.
     */
    /** Una salida nuestra ya descubierta. Se guarda para no volver a descifrarla. */
    private data class OwnedOutput(val amountAtoms: Long = 0L, val nullifierHex: String = "")

    private data class BalanceScanSnapshot(
        val schemaVersion: Int = BALANCE_SCAN_SCHEMA_VERSION,
        val address: String = "",
        val scannedHeight: Long = 0,
        val tipHash: String = "",
        val coinbases: List<CoinbaseEntry> = emptyList(),
        val privateTxHashes: List<String> = emptyList(),
        val nullifiers: List<String> = emptyList(),
        val txTimestamps: Map<String, Long> = emptyMap(),
        /**
         * El resultado de la fase de cuerpos, para no repetirla.
         *
         * Antes la instantanea guardaba solo el avance por bloques, asi que
         * cada arranque volvia a bajar los 266 cuerpos de la cadena entera para
         * redescubrir exactamente las mismas salidas. Medido en el telefono: el
         * recorrido de bloques tardaba dos segundos y esto varios minutos.
         *
         * Guardando lo descubierto, un arranque solo procesa las transacciones
         * nuevas -- normalmente ninguna.
         */
        val ownedOutputs: List<OwnedOutput> = emptyList(),
        val processedTxHashes: List<String> = emptyList(),
        val ourOutputsByTx: Map<String, Long> = emptyMap(),
        val txInputNullifiers: Map<String, List<String>> = emptyMap()
    )

    private data class FullBalanceScanResult(
        val balance: BalanceInfo,
        val confirmedHashes: Set<String>
    )

    private data class FreshTipRevalidation(
        val balance: BalanceInfo,
        val tipIsCurrent: Boolean,
        /**
         * La punta que escaneamos sigue siendo canónica, pero la cadena creció
         * mientras tanto.
         *
         * Es un caso muy distinto de una reorganización, aunque los dos dejen
         * el saldo sin verificar: acá no hay nada mal, solo llegamos tarde. Se
         * arregla escaneando los bloques nuevos, que son pocos y baratos. Una
         * reorganización, en cambio, invalida lo acumulado y obliga a rehacer.
         */
        val reachable: Boolean = false
    )

    /**
     * Vuelve a dar una oportunidad a los cuerpos faltantes, cada tantas
     * pasadas.
     *
     * Un cuerpo puede reaparecer: basta con conectarse a un par que si lo
     * tenga. Pero probarlos todos en cada escaneo es lo que trababa la wallet.
     */
    private fun reintentarAlgunosCuerposFaltantes() {
        escaneosDesdeElUltimoReintento++
        if (escaneosDesdeElUltimoReintento >= ESCANEOS_ENTRE_REINTENTOS_DE_CUERPOS) {
            missingPrivateTxCache.clear()
            guardarCuerposPerdidos()
            escaneosDesdeElUltimoReintento = 0
        }
    }

    private fun readBalanceScanSnapshot(address: String): BalanceScanSnapshot? =
        runCatching {
            val raw = secureStorage.getBalanceScanSnapshot(address) ?: return null
            val snap = gson.fromJson(raw, BalanceScanSnapshot::class.java)
            snap.takeIf {
                it.schemaVersion == BALANCE_SCAN_SCHEMA_VERSION &&
                    it.address == address &&
                    it.scannedHeight > 0 &&
                    it.tipHash.isNotBlank()
            }
        }.getOrNull()

    private fun writeBalanceScanSnapshot(snapshot: BalanceScanSnapshot) {
        val json = gson.toJson(snapshot)
        if (json.length > MAX_BALANCE_SCAN_SNAPSHOT_CHARS) {
            secureStorage.clearBalanceScanSnapshot(snapshot.address)
            throw IllegalStateException(
                "El avance del escaneo de saldo supera el límite seguro de persistencia"
            )
        }
        secureStorage.storeBalanceScanSnapshot(snapshot.address, json)
    }

    /**
     * A full scan can take minutes over Tor. Re-read both height and digest
     * after it finishes so a block or same-height reorganization that landed
     * during the scan cannot turn an obsolete branch into a spendable balance.
     */
    private suspend fun revalidateFreshFullScan(
        balance: BalanceInfo
    ): FreshTipRevalidation {
        return try {
            val finalHeight = zokaNodeApi.getChainHeight().height
            val finalTipHash = fetchBlockDigestsRange(finalHeight, finalHeight)
                .requireCompleteRange(finalHeight, finalHeight)
                .single()
                .header
                ?.hash
                .orEmpty()
            val tipIsCurrent =
                balance.scannedHeight == finalHeight &&
                    balance.verifiedTipHash.isNotBlank() &&
                    balance.verifiedTipHash == finalTipHash
            if (tipIsCurrent) {
                FreshTipRevalidation(
                    balance = balance.copy(
                        isVerified = balance.isVerifiedAtTip(finalHeight, finalTipHash)
                    ),
                    tipIsCurrent = true
                )
            } else {
                // No estamos en la punta. Hay dos motivos posibles y no dan lo
                // mismo: o la cadena avanzó mientras escaneábamos -- normal,
                // pasa siempre, se alcanza -- o el bloque donde terminamos ya
                // no es el que está en esa altura, que es una reorganización y
                // obliga a rehacer el escaneo.
                //
                // Distinguirlos importa: exigir terminar exactamente en la
                // punta es correr contra un blanco móvil. Esta red produce un
                // bloque cada dos minutos y un escaneo sobre Tor tarda; el
                // saldo quedaba sin verificar siempre y Enviar nunca se
                // habilitaba, aunque no hubiera nada mal.
                val anchorHash = runCatching {
                    fetchBlockDigestsRange(balance.scannedHeight, balance.scannedHeight)
                        .requireCompleteRange(balance.scannedHeight, balance.scannedHeight)
                        .single()
                        .header
                        ?.hash
                        .orEmpty()
                }.getOrDefault("")
                val stillCanonical = anchorHash.isNotBlank() &&
                    balance.verifiedTipHash.isNotBlank() &&
                    anchorHash == balance.verifiedTipHash
                val behind = stillCanonical && balance.scannedHeight < finalHeight
                FreshTipRevalidation(
                    balance = balance.copy(
                        isVerified = false,
                        verificationIssue = if (behind) {
                            "The chain grew while the private balance was being scanned. " +
                                "Catching up before sending is enabled."
                        } else {
                            "The P2P chain tip changed while the private balance was being scanned. " +
                                "Sending is disabled until a new scan verifies the current tip."
                        }
                    ),
                    tipIsCurrent = false,
                    reachable = behind
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            FreshTipRevalidation(
                balance = balance.copy(
                    isVerified = false,
                    verificationIssue =
                        "The P2P chain tip could not be revalidated after the private balance scan. " +
                            "Sending is disabled until verification succeeds."
                ),
                tipIsCurrent = false
            )
        }
    }


    private suspend fun scanFullBalance(
        address: String,
        scanKeyBytes: ByteArray,
        chainHeight: Long,
        restoreHeight: Long = 0
    ): FullBalanceScanResult {
        val scanStartedAtMs = System.currentTimeMillis()
        cargarCuerposPerdidos()
        Log.d("ZokaRepository", "scanFullBalance: START chainHeight=$chainHeight restoreHeight=$restoreHeight")
        _syncProgress.value = SyncProgress(
            scanning = true,
            scannedHeight = restoreHeight.coerceAtLeast(0L),
            chainHeight = chainHeight
        )
        // This set describes the current scan only. Keeping entries from a
        // previous branch would let a reorg release a pending reservation and
        // delete the only replayable transaction body.
        confirmedPrivateTxHashes.clear()
        // Derive scan scalar: SHA256(scanKey) as LE mod L
        val scanScalar = Ristretto255.scalarFromSecretBytes(scanKeyBytes)
        // Derive spend key: SHA256("zoka-private-wallet-spend-v1" || 0x1f || scanKey)
        val spendInput = "zoka-private-wallet-spend-v1".toByteArray(Charsets.UTF_8) + byteArrayOf(0x1f) + scanKeyBytes
        val spendKeyRaw = MessageDigest.getInstance("SHA-256").digest(spendInput)
        val spendScalar = Ristretto255.scalarFromSecretBytes(spendKeyRaw)

        val myCoinbases = mutableListOf<CoinbaseEntry>()
        val allPrivateTxHashes = linkedSetOf<String>()
        // Build confirmations off to the side. If any later scan phase fails,
        // fallback balance handling must not delete an outbox based on a
        // partially traversed (or concurrently reorganized) branch.
        val confirmedInCurrentScan = linkedSetOf<String>()
        // Real on-chain time (ms) of each private tx, taken from its block header,
        // so Recent Activity shows when the tx happened — not when we scanned.
        val txTimestampByHash = mutableMapOf<String, Long>()
        // Spent nullifiers, taken from each block's committed `private_nullifiers`
        // field during the block scan. This is the authoritative, fetch-independent
        // source of spends: a note can never show as spendable just because a
        // per-tx body fetch was rate-limited or missed.
        val allInputNullifiers = mutableSetOf<String>()
        // Un cuerpo que no aparece NO siempre es falta de replica.
        //
        // El codigo anterior asumia que un 404 era transitorio y reintentaba
        // todos los faltantes en cada escaneo. Medido contra la red real: de
        // 266 transacciones privadas de la cadena, **12 no las tiene ni el nodo
        // que tiene la cadena entera**. Los bloques guardan el hash y los
        // nulificadores, no el cuerpo; si ningun nodo vivo lo recibio, ese
        // cuerpo no existe mas.
        //
        // Reintentarlos en cada pasada costaba 45 segundos cada uno -- nueve
        // minutos por escaneo -- y el escaneo no alcanzaba a terminar antes de
        // que lo reiniciaran. La wallet quedaba en un bucle: nunca DONE, nunca
        // saldo verificado, nunca Enviar habilitado.
        //
        // Ahora se recuerdan entre escaneos y se reintentan de a poco. Un
        // cuerpo que reaparezca (un par nuevo que si lo tenga) se recupera en
        // el siguiente reintento; uno que se perdio de verdad deja de costar
        // tiempo en cada refresco.
        reintentarAlgunosCuerposFaltantes()

        // Phase 1: Scan blocks for our coinbase outputs and private tx hashes.
        // A freshly-created wallet records its creation height as restoreHeight,
        // so we skip every block before it existed — there is provably nothing
        // of ours there. Imported wallets pass 0 and scan from genesis. This is
        // the safe optimization: it never drops history a wallet could own.
        // Retomar donde quedó el escaneo anterior, si sigue siendo válido.
        //
        // Se comprueba el ancla antes de confiar: se pide el bloque que estaba
        // en `scannedHeight` y se mira si sigue teniendo el mismo hash. Si la
        // cadena se reorganizó, ese bloque ya no es el mismo y todo lo
        // acumulado podría hablar de una rama que dejó de existir, así que se
        // rehace desde el principio. Es la única forma de que retomar sea
        // seguro y no una fuente silenciosa de saldos equivocados.
        var desde = restoreHeight.coerceAtLeast(1L)
        var anclaHash = ""
        // Solo la instantanea que paso la validacion de ancla sirve para saltear
        // trabajo. Si el ancla no coincide (reorganizacion), queda en null y se
        // reprocesa todo.
        var instantaneaPrevia: BalanceScanSnapshot? = null
        val previa = readBalanceScanSnapshot(address)
        if (previa != null && previa.scannedHeight in desde..chainHeight) {
            val ancla = runCatching {
                fetchBlockDigestsRange(previa.scannedHeight, previa.scannedHeight)
                    .requireCompleteRange(previa.scannedHeight, previa.scannedHeight)
                    .single().header?.hash.orEmpty()
            }.getOrDefault("")
            if (ancla.isNotBlank() && ancla == previa.tipHash) {
                desde = previa.scannedHeight + 1L
                anclaHash = previa.tipHash
                previa.coinbases.forEach { myCoinbases.add(it) }
                allPrivateTxHashes.addAll(previa.privateTxHashes)
                allInputNullifiers.addAll(previa.nullifiers)
                txTimestampByHash.putAll(previa.txTimestamps)
                confirmedInCurrentScan.addAll(previa.privateTxHashes)
                instantaneaPrevia = previa
                // Sin esto el avance quedaba en cero hasta el primer punto de
                // control, y cuando faltan pocos bloques ese punto no llega
                // nunca: la franja decia "0 de 19279" con el escaneo casi listo.
                _syncProgress.value = SyncProgress(true, previa.scannedHeight, chainHeight)
                Log.d(
                    "ZokaRepository",
                    "scanFullBalance: retomando desde $desde (instantánea en ${previa.scannedHeight})"
                )
            } else {
                Log.d(
                    "ZokaRepository",
                    "scanFullBalance: la instantánea ya no ancla en la cadena, se rehace"
                )
                secureStorage.clearBalanceScanSnapshot(address)
            }
        }

        var ultimoGuardado = desde - 1
        var from = desde
        while (from <= chainHeight) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, chainHeight)
            val response = fetchBlockDigestsRange(from, to)
            val blocks = response.requireCompleteRange(
                from,
                to,
                anclaHash.takeIf { from > 1 && it.isNotBlank() }
            )
            delay(BATCH_DELAY_MS)
            blocks.forEach { block ->
                val blockHeight = block.header?.blockHeight ?: 0L
                val coinbaseTx = block.transactions.firstOrNull()
                if (coinbaseTx != null && coinbaseTx.receiver == address && coinbaseTx.amount > 0) {
                    val unlockHeight = block.privateCoinbase?.unlockHeight ?: (blockHeight + COINBASE_MATURITY_BLOCKS)
                    myCoinbases.add(CoinbaseEntry(
                        amountAtoms = coinbaseTx.amount,
                        unlockHeight = unlockHeight,
                        outputId = block.privateCoinbase?.outputId.orEmpty(),
                        commitmentHex = block.privateCoinbase?.commitment.orEmpty()
                    ))
                }
                allPrivateTxHashes.addAll(block.privateTxHashes)
                // Authoritative spent set: the nullifiers committed in this block.
                allInputNullifiers.addAll(block.privateNullifiers)
                // Record the block's real timestamp (header is seconds → ms) for
                // each private tx it commits, for Recent Activity.
                val blockTsMs = (block.header?.timestamp ?: 0L) * 1000L
                if (blockTsMs > 0L) {
                    block.privateTxHashes.forEach { h -> txTimestampByHash[h] = blockTsMs }
                }
                // Whatever hash we saw committed in a block is "confirmed"
                // from the reconciliation point of view: it's no longer
                // in flight. Used by withReconciledPendings() to clear
                // matching pending-out reservations.
                confirmedInCurrentScan.addAll(block.privateTxHashes)
            }

            // El ancla es el hash del bloque que cierra el tramo, buscado por
            // altura y no por orden de llegada: nada garantiza que la respuesta
            // venga ordenada, y anclar en el bloque equivocado haria que la
            // proxima corrida retome desde un punto que no es.
            blocks
                .firstOrNull { it.header?.blockHeight == to }
                ?.header?.hash
                ?.takeIf { it.isNotBlank() }
                ?.let { anclaHash = it }

            // Anotar el avance cada tanto, sin esperar al final.
            //
            // La primera corrida son decenas de minutos. Guardar solo al
            // terminar significa que si la aplicacion se cierra, la pantalla se
            // apaga o se corta la red a los treinta y nueve minutos, se pierde
            // todo y hay que empezar de cero -- que es exactamente lo que le
            // paso a este escaner en la practica. Cada punto de control cuesta
            // una escritura y salva media hora de trabajo.
            if (anclaHash.isNotBlank() && (to - ultimoGuardado) >= BLOQUES_ENTRE_PUNTOS_DE_CONTROL) {
                writeBalanceScanSnapshot(
                    BalanceScanSnapshot(
                        address = address,
                        scannedHeight = to,
                        tipHash = anclaHash,
                        coinbases = myCoinbases.toList(),
                        privateTxHashes = allPrivateTxHashes.toList(),
                        nullifiers = allInputNullifiers.toList(),
                        txTimestamps = txTimestampByHash.toMap()
                    )
                )
                ultimoGuardado = to
                _syncProgress.value = SyncProgress(true, to, chainHeight)
                Log.d(
                    "ZokaRepository",
                    "scanFullBalance: avance $to/$chainHeight " +
                        "(${(to - desde + 1) * 100 / (chainHeight - desde + 1).coerceAtLeast(1)}%)"
                )
            }
            from = to + 1
        }

        // Guardar lo aprendido antes de seguir con las fases que salen a pedir
        // cuerpos de transacciones: si eso falla, el recorrido de bloques -- que
        // es lo caro -- no se pierde.
        if (anclaHash.isNotBlank() && chainHeight > ultimoGuardado) {
            writeBalanceScanSnapshot(
                BalanceScanSnapshot(
                    address = address,
                    scannedHeight = chainHeight,
                    tipHash = anclaHash,
                    coinbases = myCoinbases.toList(),
                    privateTxHashes = allPrivateTxHashes.toList(),
                    nullifiers = allInputNullifiers.toList(),
                    txTimestamps = txTimestampByHash.toMap()
                )
            )
        }

        // Phase 2: Fetch all private txs — collect owned outputs (and, as a
        // supplement to the block-committed set above, any spend nullifiers from
        // the tx bodies we do fetch).
        val ownedOutputs = mutableListOf<OwnedOutput>()
        // Per-tx data to classify each tx as outgoing (we spent a note of ours)
        // vs incoming (we only received), so Recent Activity can show our sends
        // after a restore — when there is no local sent history to rely on.
        val ownedByNullifier = mutableMapOf<String, Long>()
        // Deduped per-tx: a private tx carries its spend nullifier both in the
        // `nullifier` field and in `input_nullifiers`, so a Set avoids counting
        // the same spent note twice.
        val txInputNullifiers = mutableMapOf<String, Set<String>>()
        val ourOutputsByTx = mutableMapOf<String, Long>()
        // Outputs of OURS, tagged with the tx that paid them. Used after the
        // scan to surface incoming payments in Recent Activity. A candidate is
        // only a real "received" if the paying tx wasn't sent by us (otherwise
        // it's our own change output coming back).
        val receivedByTx = mutableListOf<Pair<String, Long>>()

        // Lo que ya se descubrio en escaneos anteriores no se vuelve a bajar.
        //
        // La instantanea ya se valido contra el ancla de cadena antes de llegar
        // aca, asi que estos datos pertenecen a la misma rama que estamos
        // mirando. Si hubo reorganizacion, la instantanea se descarto entera y
        // este conjunto viene vacio: se reprocesa todo, que es lo correcto.
        val yaProcesados = mutableSetOf<String>()
        instantaneaPrevia?.let { previa ->
            ownedOutputs.addAll(previa.ownedOutputs)
            for (o in previa.ownedOutputs) ownedByNullifier[o.nullifierHex] = o.amountAtoms
            ourOutputsByTx.putAll(previa.ourOutputsByTx)
            for ((h, ns) in previa.txInputNullifiers) txInputNullifiers[h] = ns.toSet()
            yaProcesados.addAll(previa.processedTxHashes)
        }

        val hashesPendientes = allPrivateTxHashes.filter { it !in yaProcesados }
        if (yaProcesados.isNotEmpty()) {
            Log.d(
                "ZokaRepository",
                "scanFullBalance: ${yaProcesados.size} cuerpos ya conocidos, " +
                    "faltan ${hashesPendientes.size}"
            )
        }

        if (hashesPendientes.isNotEmpty()) {
            delay(PRIVATE_TX_PHASE_DELAY_MS)
        }

        // Fetch tx bodies in small concurrent batches instead of one at a
        // time. The sequential version paid a fixed per-tx delay (avoiding
        // the node's rate limiter) on every single hash — on a chain with
        // hundreds of private txs that alone added minutes, independent of
        // device speed. A small batch keeps the same rate-limit courtesy
        // (and the same 429 retry path) while cutting wall-clock time
        // roughly batch-size-fold. Decryption/bookkeeping below stays
        // single-threaded — only the network fetch runs in parallel.
        var missingTxCount = 0
        // Guardar el resultado a mitad de camino, no solo al terminar.
        //
        // Estaba solo al final y NINGUN escaneo llegaba a terminar: la fase de
        // cuerpos tarda minutos y cualquier refresco, cierre o cambio de
        // pantalla la corta antes. El resultado no se escribia nunca, asi que
        // el trabajo se rehacia entero una y otra vez -- justo lo que el
        // usuario describia como "empezar todo desde cero".
        //
        // Se guarda solo si hay ancla: sin ancla no sabemos a que rama
        // pertenece lo descubierto, y persistirlo seria peor que perderlo.
        fun guardarResultadoDeCuerpos() {
            if (anclaHash.isBlank()) return
            runCatching {
                writeBalanceScanSnapshot(
                    BalanceScanSnapshot(
                        address = address,
                        scannedHeight = chainHeight,
                        tipHash = anclaHash,
                        coinbases = myCoinbases.toList(),
                        privateTxHashes = allPrivateTxHashes.toList(),
                        nullifiers = allInputNullifiers.toList(),
                        txTimestamps = txTimestampByHash.toMap(),
                        ownedOutputs = ownedOutputs.toList(),
                        // Un cuerpo que no se pudo bajar NO se marca procesado:
                        // se reintenta la proxima vez, que es justo lo que
                        // queremos para los que fallaron por red.
                        processedTxHashes = allPrivateTxHashes
                            .filter { it in yaProcesados || privateTxCache.containsKey(it) }
                            .toList(),
                        ourOutputsByTx = ourOutputsByTx.toMap(),
                        txInputNullifiers = txInputNullifiers.mapValues { it.value.toList() }
                    )
                )
            }.onFailure {
                Log.w("ZokaRepository", "no se pudo guardar el resultado del escaneo: ${it.message}")
            }
        }

        var tandasDesdeElUltimoGuardado = 0
        val hashList = hashesPendientes
        _syncProgress.value = SyncProgress(
            scanning = true,
            scannedHeight = chainHeight,
            chainHeight = chainHeight,
            phase = SyncProgress.Phase.BODIES,
            bodiesDone = 0,
            bodiesTotal = hashList.size
        )
        for (batchStart in hashList.indices step PRIVATE_TX_FETCH_BATCH_SIZE) {
            val batch = hashList.subList(
                batchStart,
                minOf(batchStart + PRIVATE_TX_FETCH_BATCH_SIZE, hashList.size)
            )
            val fetched = coroutineScope {
                batch.map { txHash -> async { txHash to getPrivateTransactionCached(txHash) } }
                    .awaitAll()
            }
            for ((txHash, privateTx) in fetched) {
                if (privateTx == null) {
                    if (txHash !in missingPrivateTxCache) missingTxCount++
                    continue
                }
                val txNullifierDomain = privateTx.nullifier.ifBlank {
                    privateTx.inputNullifiers.firstOrNull().orEmpty()
                }

                allInputNullifiers.addAll(privateTx.inputNullifiers)
                if (privateTx.nullifier.isNotBlank()) {
                    allInputNullifiers.add(privateTx.nullifier)
                }
                txInputNullifiers[txHash] = buildSet {
                    addAll(privateTx.inputNullifiers)
                    if (privateTx.nullifier.isNotBlank()) add(privateTx.nullifier)
                }
                for (output in privateTx.outputs) {
                    val amount = checkAndDecryptOutput(
                        output = output,
                        scanScalar = scanScalar,
                        spendScalar = spendScalar,
                        txNullifierDomain = txNullifierDomain
                    ) ?: continue
                    if (output.outputId.isBlank() || output.commitment.isBlank()) continue
                    val nullifierHex = deriveNullifierHex(spendKeyRaw, output.outputId, output.commitment)
                    ownedOutputs.add(OwnedOutput(amount, nullifierHex))
                    ownedByNullifier[nullifierHex] = amount
                    ourOutputsByTx[txHash] = (ourOutputsByTx[txHash] ?: 0L) + amount
                    receivedByTx.add(txHash to amount)
                }
            }
            _syncProgress.value = SyncProgress(
                scanning = true,
                scannedHeight = chainHeight,
                chainHeight = chainHeight,
                phase = SyncProgress.Phase.BODIES,
                bodiesDone = minOf(batchStart + PRIVATE_TX_FETCH_BATCH_SIZE, hashList.size),
                bodiesTotal = hashList.size
            )
            // Un punto de control cada pocas tandas: si esto se corta a la
            // mitad, lo bajado hasta aca se conserva.
            tandasDesdeElUltimoGuardado++
            if (tandasDesdeElUltimoGuardado >= TANDAS_ENTRE_PUNTOS_DE_CONTROL) {
                guardarResultadoDeCuerpos()
                tandasDesdeElUltimoGuardado = 0
            }
            if (batchStart + PRIVATE_TX_FETCH_BATCH_SIZE < hashList.size) {
                if (PRIVATE_TX_BATCH_DELAY_MS > 0L) delay(PRIVATE_TX_BATCH_DELAY_MS)
            }
        }

        guardarResultadoDeCuerpos()

        // Phase 3: Sum unspent outputs
        var matureAtoms = 0L
        var immatureAtoms = 0L

        for (coinbase in myCoinbases) {
            val isSpent = if (coinbase.outputId.isNotBlank() && coinbase.commitmentHex.isNotBlank()) {
                val nullHex = deriveNullifierHex(spendKeyRaw, coinbase.outputId, coinbase.commitmentHex)
                allInputNullifiers.contains(nullHex)
            } else false

            if (!isSpent) {
                if (coinbase.unlockHeight <= chainHeight) {
                    matureAtoms = matureAtoms.saturatingAdd(coinbase.amountAtoms)
                } else {
                    immatureAtoms = immatureAtoms.saturatingAdd(coinbase.amountAtoms)
                }
            }
        }

        for (owned in ownedOutputs) {
            if (!allInputNullifiers.contains(owned.nullifierHex)) {
                matureAtoms = matureAtoms.saturatingAdd(owned.amountAtoms)
            }
        }

        // Classify each private tx: a tx that spent one of our notes is an
        // outgoing send (amount = spent inputs − our change); a tx where we only
        // have outputs is an incoming receipt. This lets Recent Activity show
        // BOTH sends and receipts after a restore, with no local history.
        val sentByTx = mutableMapOf<String, Long>()
        val trueReceivedByTx = mutableListOf<Pair<String, Long>>()
        for (txHash in allPrivateTxHashes) {
            val spentByUs = (txInputNullifiers[txHash] ?: emptySet())
                .sumOf { ownedByNullifier[it] ?: 0L }
            val ourOut = ourOutputsByTx[txHash] ?: 0L
            if (spentByUs > 0L) {
                val sent = (spentByUs - ourOut).coerceAtLeast(0L)
                if (sent > 0L) sentByTx[txHash] = sent
            } else if (ourOut > 0L) {
                trueReceivedByTx.add(txHash to ourOut)
            }
        }
        // Surface received payments (skips our own change) AND our own sends.
        recordIncomingTransactions(address, trueReceivedByTx, txTimestampByHash)
        recordOutgoingTransactions(address, sentByTx, txTimestampByHash)

        val elapsedMs = System.currentTimeMillis() - scanStartedAtMs
        _syncProgress.value = SyncProgress(false, chainHeight, chainHeight)
        Log.d("ZokaRepository", "scanFullBalance: DONE in ${elapsedMs}ms coinbases=${myCoinbases.size} privateTxs=${allPrivateTxHashes.size} ownedOutputs=${ownedOutputs.size} missingTxs=$missingTxCount mature=$matureAtoms immature=$immatureAtoms")
        return FullBalanceScanResult(
            balance = BalanceInfo(
                matureAtoms = matureAtoms,
                immatureAtoms = immatureAtoms,
                missingTxCount = missingTxCount,
                scannedHeight = chainHeight,
                // Un cuerpo que falta NO puede inflar este saldo.
                //
                // La guarda anterior exigia `missingTxCount == 0` y bloqueaba el
                // envio para siempre, porque 12 de esas 266 transacciones ya no
                // existen en ningun nodo vivo. Estaba protegiendo contra el
                // riesgo equivocado.
                //
                // Los gastos se detectan por nulificador, y los nulificadores
                // vienen commiteados en el bloque -- llegan en los resumenes,
                // sin pedir un solo cuerpo. Una nota nuestra no puede figurar
                // como gastable porque un cuerpo no se haya podido bajar.
                //
                // Un cuerpo sirve para lo otro: descubrir salidas nuestras, o
                // sea dinero ENTRANTE. Perderse uno solo puede dejar el saldo
                // por DEBAJO del real, nunca por encima. Y gastar de menos es
                // seguro: la transaccion que armamos con lo que si conocemos es
                // valida igual.
                //
                // Asi que los cuerpos faltantes se avisan -- el saldo puede ser
                // incompleto y el usuario merece saberlo -- pero no bloquean el
                // envio. Lo que si lo bloquea es no poder anclar el saldo a una
                // punta de cadena, porque ahi no sabemos de cuando es nada.
                isVerified = anclaHash.isNotBlank(),
                verifiedTipHash = anclaHash,
                verificationIssue = if (anclaHash.isBlank()) {
                    "The P2P scan could not anchor this balance to a chain tip."
                } else if (missingTxCount > 0) {
                    "$missingTxCount private transaction " +
                        if (missingTxCount == 1) {
                            "body is unavailable from the P2P network, so any " +
                                "funds it may have sent you are not counted here. " +
                                "You can still spend the balance shown."
                        } else {
                            "bodies are unavailable from the P2P network, so any " +
                                "funds they may have sent you are not counted here. " +
                                "You can still spend the balance shown."
                        }
                } else {
                    null
                }
            ),
            confirmedHashes = confirmedInCurrentScan.toSet()
        )
    }

    private suspend fun getPrivateTransactionCached(txHash: String): ZokaPrivateTransactionResponse? {
        privateTxCache[txHash]?.let { return it }
        if (txHash in missingPrivateTxCache) return null

        if (PRIVATE_TX_DELAY_MS > 0L) delay(PRIVATE_TX_DELAY_MS)
        return try {
            zokaNodeApi.getPrivateTransaction(txHash)
                .also { privateTxCache[txHash] = it }
        } catch (e: HttpException) {
            if (e.code() != 429) {
                if (e.code() == 404) {
                    missingPrivateTxCache.add(txHash)
                    guardarCuerposPerdidos()
                }
                Log.w("ZokaRepository", "Skipping $txHash: ${e.message}")
                return null
            }
            retryPrivateTransactionAfterRateLimit(txHash)
        } catch (e: TimeoutCancellationException) {
            // The JNI call may consume its complete reconnect + P2P budget.
            // A timeout remains transient: only a real 404 is cached as missing.
            Log.w("ZokaRepository", "Timed out fetching $txHash, skipping")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ZokaRepository", "Skipping $txHash: ${e.message}")
            null
        }
    }

    private suspend fun retryPrivateTransactionAfterRateLimit(txHash: String): ZokaPrivateTransactionResponse? {
        for (delayMs in PRIVATE_TX_RETRY_DELAYS_MS) {
            Log.d("ZokaRepository", "429 on $txHash, retrying after ${delayMs}ms")
            delay(delayMs)
            try {
                return zokaNodeApi.getPrivateTransaction(txHash)
                    .also { privateTxCache[txHash] = it }
            } catch (e: HttpException) {
                if (e.code() != 429) {
                    if (e.code() == 404) {
                    missingPrivateTxCache.add(txHash)
                    guardarCuerposPerdidos()
                }
                    Log.w("ZokaRepository", "Skipping $txHash after retry: ${e.message}")
                    return null
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("ZokaRepository", "Timed out retrying $txHash, skipping")
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ZokaRepository", "Skipping $txHash after retry: ${e.message}")
                return null
            }
        }
        Log.w("ZokaRepository", "Skipping $txHash after rate limit retries")
        return null
    }

    private fun checkAndDecryptOutput(
        output: ZokaPrivateOutputModel,
        scanScalar: BigInteger,
        spendScalar: BigInteger,
        txNullifierDomain: String
    ): Long? {
        if (output.ephemeralPubkey.size != 32 || output.encryptedAmount.size != 40) return null
        if (txNullifierDomain.isBlank()) return null
        val ephemeralBytes = ByteArray(32) { i -> (output.ephemeralPubkey[i] and 0xFF).toByte() }
        val sharedPointBytes = Ristretto255.scalarMulPointBytes(scanScalar, ephemeralBytes) ?: return null
        val commitmentBytes = output.commitment.toByteArray(Charsets.UTF_8)

        val derivedScalar = Ristretto255.hashToScalar(
            "zoka-output-key".toByteArray(Charsets.UTF_8),
            sharedPointBytes,
            commitmentBytes
        )
        val outputScalar = Ristretto255.scalarAdd(spendScalar, derivedScalar)
        val expectedPubHex = Ristretto255.encodeFromScalar(outputScalar).joinToString("") { "%02x".format(it) }
        if (expectedPubHex != output.oneTimePublicKey.lowercase()) return null

        return try {
            val amountBlind = Ristretto255.hashToScalar(
                "zoka-amount-blind".toByteArray(Charsets.UTF_8),
                sharedPointBytes,
                commitmentBytes
            )
            val key = scalarToLe32(amountBlind) +
                output.commitment.toByteArray(Charsets.UTF_8) +
                txNullifierDomain.toByteArray(Charsets.UTF_8)
            val encrypted = ByteArray(40) { i -> (output.encryptedAmount[i] and 0xFF).toByte() }
            val plaintext = xorWithBlake3Stream(encrypted, key, "zoka-private-note-payload-v2")
            var amount = 0L
            for (i in 0..7) amount = amount or ((plaintext[i].toLong() and 0xFF) shl (i * 8))
            if (amount > 0L) amount else null
        } catch (e: Exception) {
            Log.e("ZokaRepository", "Amount decryption error: ${e.message}")
            null
        }
    }

    private fun deriveNullifierHex(spendKeyRaw: ByteArray, outputId: String, commitment: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("zoka-note-nullifier".toByteArray(Charsets.UTF_8))
        sha.update(spendKeyRaw)
        sha.update(0x1f.toByte())
        sha.update(outputId.toByteArray(Charsets.UTF_8))
        sha.update(0x1f.toByte())
        sha.update(commitment.toByteArray(Charsets.UTF_8))
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun xorWithBlake3Stream(ciphertext: ByteArray, key: ByteArray, domain: String): ByteArray {
        val domainBytes = domain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(ciphertext.size)
        var offset = 0
        var counter = 0L
        while (offset < ciphertext.size) {
            val counterBytes = ByteArray(8) { i -> ((counter shr (i * 8)) and 0xFF).toByte() }
            val input = domainBytes + key + counterBytes
            val block = blake3Hash(input)
            val len = minOf(32, ciphertext.size - offset)
            for (i in 0 until len) out[offset + i] = (ciphertext[offset + i].toInt() xor block[i].toInt()).toByte()
            offset += len
            counter++
        }
        return out
    }

    private fun blake3Hash(data: ByteArray): ByteArray {
        val digest = Blake3Digest(256)
        digest.update(data, 0, data.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        return out
    }

    private fun scalarToLe32(scalar: BigInteger): ByteArray {
        val out = ByteArray(32)
        var be = scalar.toByteArray()
        if (be.isNotEmpty() && be[0] == 0.toByte()) be = be.copyOfRange(1, be.size)
        for (i in be.indices) out[i] = be[be.size - 1 - i]
        return out
    }

    private fun tryHexToBytes(hex: String): ByteArray? {
        if (hex.isBlank()) return null
        val clean = hex.removePrefix("0x").lowercase()
        if (clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: Exception) { null }
    }

    private fun Long.saturatingAdd(other: Long): Long {
        if (other <= 0L) return this
        val sum = this + other
        return if (sum < this) Long.MAX_VALUE else sum
    }

    fun formatAtoms(atoms: Long): String {
        if (atoms <= 0L) return "0"
        return BigDecimal(atoms)
            .divide(BigDecimal(ATOMS_PER_ZKA))
            .stripTrailingZeros()
            .toPlainString()
    }

    // ── Network status ────────────────────────────────────────────────────────

    suspend fun getNetworkStatus(): Result<String> {
        return try {
            val height = zokaNodeApi.getChainHeight()
            Result.Success(height.height.toString())
        } catch (e: Exception) {
            Log.e("ZokaRepository", "getNetworkStatus: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Current chain tip height, or null if the node is unreachable. Used when
     * creating a brand-new wallet to record its restore height — a wallet with
     * no prior history only needs to scan from "now" onward, which avoids
     * re-walking the entire chain on every balance refresh.
     */
    suspend fun getCurrentChainHeight(): Long? {
        return try {
            zokaNodeApi.getChainHeight().height
        } catch (e: Exception) {
            Log.w("ZokaRepository", "getCurrentChainHeight failed: ${e.message}")
            null
        }
    }

    // ── Send transaction ──────────────────────────────────────────────────────
    //
    // Protocol note: the Zoka node derives sender as bech32("zka", sha256(pubkey)[0:20])
    // and verifies the signature over sha256("sender:receiver:amount:nonce")
    // where timestamp is assigned server-side.  Since the client cannot know the
    // exact timestamp before submission, the signature below is computed over
    // sha256("sender:receiver:amount:nonce") without timestamp.
    // Tracked in HOJA_RUTA_TECNICA Fase 3 — advertencia critica.

    suspend fun getNodeStats(): Result<ZokaStatsResponse> {
        return try {
            Result.Success(zokaNodeApi.getStats())
        } catch (e: Exception) {
            Log.e("ZokaRepository", "getNodeStats: ${e.message}", e)
            Result.Error(e)
        }
    }

    fun getRpcEndpoint(): String = Constants.ZOKA_MAINNET_RPC.trimEnd('/')

    /**
     * Send a Zoka private transfer.
     *
     * Every current build variant constructs the private transaction locally
     * and submits its canonical JSON body, so the recovery seed never leaves
     * the phone. The remote branch remains only as a compatibility guard for
     * an explicitly configured legacy build.
     *
     * [publicKey] and [from] are still accepted so existing call-sites
     * compile; only [scanKeyHex], [to], [amount] and [fee] are actually
     * used by the new pipeline.
     */
    suspend fun sendTransaction(
        from: String,
        to: String,
        amount: String,
        fee: String,
        scanKeyHex: String,
        publicKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        sendMutex.withLock {
            try {
            val amountAtoms = zkaToAtoms(amount)
            val feeAtoms = zkaToAtoms(fee).coerceAtLeast(0L)
            val seedHex = scanKeyHex.removePrefix("0x")

            if (amountAtoms <= 0L) {
                return@withLock Result.Error(
                    IllegalArgumentException("Amount must be greater than zero")
                )
            }
            if (!to.startsWith("zka1") || to.length < 32) {
                return@withLock Result.Error(
                    IllegalArgumentException("Invalid recipient address")
                )
            }

            val seedBytesForValidation = tryHexToBytes(seedHex)
                ?: return@withLock Result.Error(
                    IllegalArgumentException("Invalid recovery seed")
                )
            seedBytesForValidation.fill(0)

            val sendMode = com.zoka.zkapriv.network.BuildConfig.ZOKA_PRIVATE_SEND_MODE
            val hash = when (sendMode) {
                "local-self-custody" -> submitLocalSelfCustodyTransfer(
                    from = from,
                    to = to,
                    amountAtoms = amountAtoms,
                    feeAtoms = feeAtoms,
                    seedHex = seedHex
                )
                "remote-seed" -> submitRemoteSeedTransfer(
                    to = to,
                    amountAtoms = amountAtoms,
                    feeAtoms = feeAtoms,
                    seedHex = seedHex
                )
                else -> throw IllegalStateException(
                    "Unsupported private send mode: $sendMode"
                )
            }

            // The local path has already committed its complete encrypted
            // outbox before the reservation and before publication. Keep this
            // legacy bookkeeping only for the transitional remote builder.
            if (sendMode == "remote-seed") {
                addTransactionToCache(
                    from, Transaction(
                        hash = hash,
                        from = from,
                        to = to,
                        amount = amount,
                        token = Token(address = "", symbol = "ZKA", decimals = 9, name = "Zoka"),
                        timestamp = System.currentTimeMillis(),
                        status = TransactionStatus.PENDING,
                        fee = fee
                    )
                )
                rpcSettings.recordPendingSend(
                    address = from,
                    hash = hash,
                    reservedAtoms = Math.addExact(amountAtoms, feeAtoms)
                )
            }

            // Deliberately do NOT invalidate the balance cache here. The
            // pending-send reservation above is enough: the next read applies
            // it as an overlay (withReconciledPendings) on top of the
            // existing cached mature balance, so "Available" drops instantly.
            // Wiping the cache instead forced an immediate full re-scan
            // (1-2 min, walks every private tx on chain) right when the user
            // most wants to see the deduction reflected fast.

            Result.Success(hash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
            // Retrofit's HttpException.message() only carries the HTTP
            // status line ("HTTP 500"); the actual server explanation is
            // in errorBody(). Drain it so the failure dialog and logcat
            // surface the real cause (e.g. "not enough spendable ZKA",
            // "self-transfer not allowed", "invalid recipient").
                val serverMsg = try {
                    e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                } catch (_: Exception) { null }
                val msg = serverMsg ?: "HTTP ${e.code()} ${e.message()}"
                Log.e("ZokaRepository", "sendTransaction HTTP ${e.code()} body=$serverMsg", e)
                Result.Error(RuntimeException(msg))
            } catch (e: Exception) {
                Log.e("ZokaRepository", "sendTransaction error: ${e.message}", e)
                Result.Error(e)
            }
        }
    }

    // ── Private send backends ─────────────────────────────────────────────────

    private suspend fun submitLocalSelfCustodyTransfer(
        from: String,
        to: String,
        amountAtoms: Long,
        feeAtoms: Long,
        seedHex: String
    ): String {
        val reservedAtoms = Math.addExact(amountAtoms, feeAtoms)
        val requestKey = privateSendRequestKey(from, to, amountAtoms, feeAtoms)
        val outboxRecords = restorePendingReservationsFromOutbox(from)
        val outboxByHash = outboxRecords.associateBy { it.hash }
        val retry = matchingPrivateTxOutbox(outboxRecords, requestKey)
        if (retry != null) {
            check(retry.address == from && retry.reservedAtoms == reservedAtoms) {
                "Encrypted outbox metadata does not match the retried private send"
            }
            addPendingTransactionToCache(
                from = from,
                to = to,
                amountAtoms = amountAtoms,
                feeAtoms = feeAtoms,
                hash = retry.hash
            )
            publishOutboxRecord(retry)
            return retry.hash
        }

        Log.d(
            "ZokaRepository",
            "sendTransaction (local self-custody build) initiated"
        )
        val pendingBodies = rpcSettings.expirePendingSends(from).map { pending ->
            val localRecord = outboxByHash[pending.hash]
            if (localRecord != null) {
                parseOutboxBody(localRecord)
                localRecord.bodyJson
            } else try {
                gson.toJson(zokaNodeApi.getPrivateTransactionRaw(pending.hash))
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Cannot build local transfer while pending body ${pending.hash} is unavailable",
                    error
                )
            }
        }
        val built = localTransferBuilder.build(
            from = from,
            to = to,
            amountAtoms = amountAtoms,
            feeAtoms = feeAtoms,
            seedHex = seedHex,
            pendingBodies = pendingBodies,
            privateProofVersion = com.zoka.zkapriv.network.BuildConfig.ZOKA_PRIVATE_PROOF_VERSION,
            privateProofProfile = com.zoka.zkapriv.network.BuildConfig.ZOKA_PRIVATE_PROOF_PROFILE
        )
        // Phase-by-phase timing breakdown from the native prover. This tells us
        // where the wall-clock time actually goes (proof vs in-memory wallet
        // replay vs nullifier rebuild) instead of guessing. Logged at INFO so it
        // shows up in a normal logcat capture.
        built.metrics?.let { m ->
            Log.i("ZokaProofTiming", "metrics=$m")
        }
        check(built.body.get("tx_hash")?.asString == built.hash) {
            "Local Zoka engine returned a body with a different transaction hash"
        }
        val bodyJson = gson.toJson(built.body)
        val outboxRecord = PrivateTxOutboxRecord(
            hash = built.hash,
            address = from,
            reservedAtoms = reservedAtoms,
            requestKey = requestKey,
            bodyJson = bodyJson
        )
        // Crash-safe order: complete encrypted replay record first, then the
        // cheap balance reservation. A missing second commit is reconstructed
        // from the first on the next balance/send/reconciliation pass.
        secureStorage.storePrivateTxOutbox(outboxRecord)
        rpcSettings.recordPendingSend(
            address = from,
            hash = built.hash,
            reservedAtoms = reservedAtoms
        )
        addPendingTransactionToCache(
            from = from,
            to = to,
            amountAtoms = amountAtoms,
            feeAtoms = feeAtoms,
            hash = built.hash
        )
        publishOutboxRecord(outboxRecord)
        Log.i(
            "ZokaRepository",
            "TX submitted hash=${built.hash} via local self-custody proving"
        )
        return built.hash
    }

    private suspend fun submitRemoteSeedTransfer(
        to: String,
        amountAtoms: Long,
        feeAtoms: Long,
        seedHex: String
    ): String {
        Log.d("ZokaRepository", "sendTransaction (encrypted server-side build) initiated")
        val seedBytes = tryHexToBytes(seedHex)
            ?: throw IllegalArgumentException("Invalid recovery seed")
        val envelope = try {
            val serverKey = zokaNodeApi.getServerPubkey()
            check(serverKey.algorithm == "x25519-aes256gcm-v1") {
                "Unsupported server encryption algorithm"
            }
            SeedEnvelopeEncryption.encrypt(seedBytes, serverKey.publicKeyHex)
        } finally {
            seedBytes.fill(0)
        }
        val response = zokaNodeApi.sendFromSeed(
            ZokaSendFromSeedRequest(
                ephemeralPubkeyHex = envelope.ephemeralPubkeyHex,
                nonceHex = envelope.nonceHex,
                encryptedSeedHex = envelope.encryptedSeedHex,
                to = to,
                amountAtoms = amountAtoms,
                feeAtoms = feeAtoms
            )
        )
        Log.i(
            "ZokaRepository",
            "TX submitted hash=${response.hash} via encrypted /private/wallet/send-from-seed"
        )
        return response.hash
    }

    // ── Transaction history ───────────────────────────────────────────────────

    suspend fun getTransactionHistory(address: String): Result<List<Transaction>> {
        return try {
            val cached = loadTransactionsFromPrefs(address).toMutableList()
            val updated = cached.map { tx ->
                // Re-resolve PENDING (might have confirmed) AND FAILED (might
                // have been a false negative that actually landed in a block —
                // e.g. a valid send that sat unmined past the old timeout).
                // CONFIRMED is terminal and never re-queried.
                if (tx.status == TransactionStatus.PENDING || tx.status == TransactionStatus.FAILED) {
                    try { tx.copy(status = resolveTransactionStatus(tx.hash, tx.timestamp)) }
                    catch (e: Exception) { tx }
                } else tx
            }
            if (updated != cached) saveTransactionsToPrefs(address, updated)
            Result.Success(updated)
        } catch (e: Exception) {
            Log.e("ZokaRepository", "getTransactionHistory: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Decide whether a locally-cached transaction should render as
     * PENDING / CONFIRMED / FAILED based on what the node says about its
     * hash. ZKAPriv now sends private transactions exclusively, so check
     * the private endpoint first; only fall through to the public one to
     * stay compatible with any legacy cached public hashes.
     *
     * The UI status may eventually render FAILED when every lookup misses for
     * the long legacy display window below, but that label never releases the
     * spend reservation or deletes its outbox. Only an on-chain confirmation
     * can do that.
     */
    private suspend fun resolveTransactionStatus(hash: String, timestamp: Long): TransactionStatus {
        if (hash.startsWith("incoming_")) return TransactionStatus.CONFIRMED
        if (confirmedPrivateTxHashes.contains(hash)) return TransactionStatus.CONFIRMED
        val ageMin = (System.currentTimeMillis() - timestamp) / 60_000

        // Private tx path (every send lands here).
        try {
            val privateTx = zokaNodeApi.getPrivateTransaction(hash)
            // The node HAS the body, so the tx is ALIVE — either waiting in a
            // mempool or already mined. A stored body is never a failure, so we
            // only ever return FAILED when the node says so explicitly. The
            // status is usually "Verified" while pending; anything that isn't
            // an explicit terminal state falls through to the on-chain check.
            when (privateTx.status.lowercase()) {
                "confirmed", "finalized" -> return TransactionStatus.CONFIRMED
                "failed", "rejected" -> return TransactionStatus.FAILED
            }
            // Mined once its hash is committed in a block; otherwise it stays
            // PENDING for as long as it takes a miner to include it. A node
            // retaining the body is positive evidence that it remains alive.
            return if (isPrivateTxConfirmedOnChain(hash))
                TransactionStatus.CONFIRMED
            else
                TransactionStatus.PENDING
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 404) {
                // 5xx / rate-limit / etc. → not the tx's fault, keep
                // pending so the next refresh can retry.
                return TransactionStatus.PENDING
            }
            // 404 on the private endpoint: this node has no body for the hash.
            // It may be mined-and-pruned, never-propagated-here, or dropped.
            // If it's committed in a block it's confirmed; otherwise fall
            // through to the public endpoint / age heuristic.
            if (isPrivateTxConfirmedOnChain(hash)) return TransactionStatus.CONFIRMED
        } catch (e: Exception) {
            return TransactionStatus.PENDING
        }

        // Public tx fallback (legacy / cross-chain compat).
        return try {
            val tx = zokaNodeApi.getTransaction(hash)
            when (tx.status?.lowercase()) {
                "confirmed", "finalized" -> TransactionStatus.CONFIRMED
                "failed", "rejected"     -> TransactionStatus.FAILED
                else                     -> TransactionStatus.PENDING
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                // No node has a body for this hash AND it isn't in any block.
                // A genuinely dropped tx looks like this, but so does one that
                // only propagated to the other node, so keep it PENDING for a
                // generous window before flagging it as failed.
                if (ageMin > TX_FAILED_AFTER_MIN) TransactionStatus.FAILED
                else TransactionStatus.PENDING
            } else {
                TransactionStatus.PENDING
            }
        } catch (e: Exception) {
            TransactionStatus.PENDING
        }
    }

    // ── Fee estimation ────────────────────────────────────────────────────────

    private suspend fun isPrivateTxConfirmedOnChain(hash: String): Boolean {
        if (confirmedPrivateTxHashes.contains(hash)) return true
        val height = zokaNodeApi.getChainHeight().height
        var from = 1L
        var previousHash = ""
        while (from <= height) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, height)
            val response = fetchBlockDigestsRange(from, to)
            val blocks = response.requireCompleteRange(
                from,
                to,
                previousHash.takeIf(String::isNotBlank)
            )
            for (block in blocks) {
                confirmedPrivateTxHashes.addAll(block.privateTxHashes)
                if (hash in block.privateTxHashes) return true
            }
            previousHash = blocks.last().header?.hash.orEmpty()
            from = to + 1
        }
        return false
    }

    suspend fun estimateTransactionFee(@Suppress("UNUSED_PARAMETER") amount: String): Result<String> {
        // Flat fee: 0.001 ZKA = 1,000 atoms (fee_market_step = 100 atoms = 0.0001 ZKA)
        return Result.Success("1000")
    }

    // ── Address validation ────────────────────────────────────────────────────
    // zka1 = "zka1" + Base58(scan_pub32 + spend_pub32 + checksum4) → 68 decoded bytes

    fun validateZokaAddress(address: String): Boolean {
        if (!address.startsWith("zka1")) return false
        return try {
            val decoded = decodeBase58(address.removePrefix("zka1"))
            if (decoded.size != 68) return false
            val payload  = decoded.copyOfRange(0, 64)
            val checksum = decoded.copyOfRange(64, 68)
            val expected = zokaAddressChecksum(payload)
            checksum.contentEquals(expected)
        } catch (_: Exception) { false }
    }

    private fun zokaAddressChecksum(payload: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("zoka-private-address-v1".toByteArray(Charsets.UTF_8))
        sha.update(payload)
        return sha.digest().copyOfRange(0, 4)
    }

    // ── Export / Import ───────────────────────────────────────────────────────

    suspend fun exportTransactionHistory(address: String): Result<String> {
        return try {
            val transactions = loadTransactionsFromPrefs(address)
            if (transactions.isEmpty()) return Result.Error(Exception("No transaction history to export"))
            val json = gson.toJson(
                mapOf(
                    "address" to address,
                    "exportedAt" to System.currentTimeMillis(),
                    "transactionCount" to transactions.size,
                    "transactions" to transactions
                )
            )
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = java.io.File(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "zkapriv_backup_${address.take(8)}_$ts.json"
            )
            file.writeText(json)
            Log.i("ZokaRepository", "Exported ${transactions.size} txs to ${file.absolutePath}")
            Result.Success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("ZokaRepository", "exportTransactionHistory: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun importTransactionHistory(currentAddress: String, jsonFilePath: String): Result<Int> {
        return try {
            val file = java.io.File(jsonFilePath)
            if (!file.exists()) return Result.Error(Exception("Backup file not found"))
            val importData = gson.fromJson(file.readText(), Map::class.java)
            val exportedAddress = importData["address"] as? String
            if (exportedAddress != currentAddress) {
                return Result.Error(
                    Exception("Address mismatch: expected $currentAddress, found $exportedAddress")
                )
            }
            val type = object : TypeToken<List<Transaction>>() {}.type
            val imported = gson.fromJson<List<Transaction>>(
                gson.toJson(importData["transactions"]), type
            ) ?: return Result.Error(Exception("Invalid transaction data"))

            val existing = loadTransactionsFromPrefs(currentAddress)
            val existingHashes = existing.map { it.hash }.toSet()
            val newTxs = imported.filter { it.hash !in existingHashes }
            val merged = (existing + newTxs).sortedByDescending { it.timestamp }.take(MAX_CACHED_TRANSACTIONS)
            saveTransactionsToPrefs(currentAddress, merged)
            Log.i("ZokaRepository", "Imported ${newTxs.size} new transactions")
            Result.Success(newTxs.size)
        } catch (e: Exception) {
            Log.e("ZokaRepository", "importTransactionHistory: ${e.message}", e)
            Result.Error(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun decodeBase58(base58: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(58)
        for (c in base58) {
            val d = alphabet.indexOf(c)
            require(d >= 0) { "Invalid Base58 char: $c" }
            num = num.multiply(base).add(java.math.BigInteger.valueOf(d.toLong()))
        }
        var bytes = num.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
        val leading = base58.takeWhile { it == '1' }.length
        return if (leading > 0) ByteArray(leading) + bytes else bytes
    }

    /** ZKA decimal string → atoms (u64) */
    private fun zkaToAtoms(zka: String): Long {
        val bd = BigDecimal(zka).multiply(BigDecimal(ATOMS_PER_ZKA)).stripTrailingZeros()
        require(bd.scale() <= 0) { "Amount precision exceeds atoms" }
        val bi = bd.toBigIntegerExact()
        require(bi.bitLength() <= 63) { "Amount too large for u64" }
        return bi.longValueExact()
    }

    private fun loadTransactionsFromPrefs(address: String): List<Transaction> {
        return try {
            val json = sharedPreferences.getString(PREF_KEY_PREFIX + address, null)
                ?: return emptyList()
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson<List<Transaction>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ZokaRepository", "Error loading transactions from prefs", e)
            emptyList()
        }
    }

    private fun saveTransactionsToPrefs(address: String, transactions: List<Transaction>) {
        try {
            sharedPreferences.edit()
                .putString(PREF_KEY_PREFIX + address, gson.toJson(transactions))
                .apply()
        } catch (e: Exception) {
            Log.e("ZokaRepository", "Error saving transactions to prefs", e)
        }
    }

    private fun addTransactionToCache(address: String, transaction: Transaction) {
        val list = loadTransactionsFromPrefs(address)
            .filterNot { it.hash == transaction.hash }
            .toMutableList()
        list.add(0, transaction)
        saveTransactionsToPrefs(address, list.take(MAX_CACHED_TRANSACTIONS))
    }

    private fun addPendingTransactionToCache(
        from: String,
        to: String,
        amountAtoms: Long,
        feeAtoms: Long,
        hash: String
    ) {
        addTransactionToCache(
            from,
            Transaction(
                hash = hash,
                from = from,
                to = to,
                amount = formatAtoms(amountAtoms),
                token = Token(address = "", symbol = "ZKA", decimals = 9, name = "Zoka"),
                timestamp = System.currentTimeMillis(),
                status = TransactionStatus.PENDING,
                fee = formatAtoms(feeAtoms)
            )
        )
    }

    /**
     * Persist newly-seen incoming payments so they show up in Recent Activity.
     * `received` is every output of ours found in the scan, tagged with the tx
     * that paid it. We drop:
     *   - outputs from txs WE sent (those are our own change coming back), and
     *   - txs already in the history (dedup by hash).
     * The sender of a private payment is intentionally unknown, so `from` is
     * left blank and the UI renders it as "Received". Entries are CONFIRMED
     * because we only see them once their tx is committed in a scanned block.
     */
    private fun recordIncomingTransactions(
        address: String,
        received: List<Pair<String, Long>>,
        timestamps: Map<String, Long>
    ) {
        if (received.isEmpty()) return
        val existing = loadTransactionsFromPrefs(address)
        val knownHashes = existing.map { it.hash }.toHashSet()
        val mySentHashes = existing
            .filter { it.from.equals(address, ignoreCase = true) }
            .map { it.hash }
            .toHashSet()

        // One incoming entry per paying tx (sum its owned outputs).
        val byTx = received.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val newIncoming = byTx.mapNotNull { (txHash, atoms) ->
            if (txHash in mySentHashes || txHash in knownHashes) return@mapNotNull null
            Transaction(
                hash = txHash,
                from = "",
                to = address,
                amount = formatAtoms(atoms),
                token = Token(address = "", symbol = "ZKA", decimals = 9, name = "Zoka"),
                timestamp = timestamps[txHash] ?: System.currentTimeMillis(),
                status = TransactionStatus.CONFIRMED,
                fee = "0"
            )
        }
        if (newIncoming.isEmpty()) return
        val merged = (newIncoming + existing)
            .sortedByDescending { it.timestamp }
            .take(MAX_CACHED_TRANSACTIONS)
        saveTransactionsToPrefs(address, merged)
    }

    /**
     * Persist our own sends so they show in Recent Activity after a restore.
     * A fresh restore has no local sent history, so sends are reconstructed from
     * the chain: a tx is ours/outgoing when it spent one of our notes. The sent
     * amount is (spent inputs − our change). The recipient is unknown by design,
     * so `to` is blank; `from = address` marks the entry as Sent in the UI.
     */
    private fun recordOutgoingTransactions(
        address: String,
        sentByTx: Map<String, Long>,
        timestamps: Map<String, Long>
    ) {
        if (sentByTx.isEmpty()) return
        val existing = loadTransactionsFromPrefs(address)
        val knownHashes = existing.map { it.hash }.toHashSet()
        val newOutgoing = sentByTx.mapNotNull { (txHash, atoms) ->
            if (txHash in knownHashes) return@mapNotNull null
            Transaction(
                hash = txHash,
                from = address,
                to = "",
                amount = formatAtoms(atoms),
                token = Token(address = "", symbol = "ZKA", decimals = 9, name = "Zoka"),
                timestamp = timestamps[txHash] ?: System.currentTimeMillis(),
                status = TransactionStatus.CONFIRMED,
                fee = "0"
            )
        }
        if (newOutgoing.isEmpty()) return
        val merged = (newOutgoing + existing)
            .sortedByDescending { it.timestamp }
            .take(MAX_CACHED_TRANSACTIONS)
        saveTransactionsToPrefs(address, merged)
    }
}
