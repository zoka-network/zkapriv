package com.zoka.zkapriv.network.repository

import android.util.Log
import com.google.gson.Gson
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.zoka.zkapriv.core.util.Ristretto255
import com.zoka.zkapriv.network.model.ZokaPrivateOutputModel
import com.zoka.zkapriv.network.model.ZokaPrivateTransactionResponse
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

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

    // On-device private transaction builder (Rust JNI engine). Staging builds
    // select it explicitly; release stays remote until coordinated rollout.
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
    // Private tx cache: immutable on-chain data, safe to cache for the app session.
    // ConcurrentHashMap-backed because batched parallel fetches in scanFullBalance
    // write to these from multiple coroutines at once.
    private val privateTxCache = java.util.concurrent.ConcurrentHashMap<String, ZokaPrivateTransactionResponse>()
    private val missingPrivateTxCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val PREF_KEY_PREFIX = "transactions_"
        private const val PREF_KEY_NETWORK_EPOCH = "network_chain_id_epoch"
        private const val ATOMS_PER_ZKA = 1_000_000L
        private const val RAW_BLOCK_SCAN_BATCH_SIZE = 250L
        const val COINBASE_SENDER = "zoka:coinbase:v1"
        private const val COINBASE_MATURITY_BLOCKS = 60L
        private const val BALANCE_CACHE_TTL_MS = 30_000L
        private const val FULL_SCAN_CACHE_TTL_MS = 5 * 60 * 1_000L
        private const val PENDING_FULL_SCAN_CACHE_TTL_MS = 10_000L
        // Only flag a tx FAILED once NO node has its body and it is in no
        // block for this long — generous so a valid send waiting for a miner
        // (or only on the peer node) is never falsely marked failed.
        private const val TX_FAILED_AFTER_MIN = 6 * 60L
        private const val PERSISTENT_CACHE_TTL_MS = 30 * 60 * 1_000L
        private const val BATCH_DELAY_MS = 80L
        private const val PRIVATE_TX_PHASE_DELAY_MS = 2_000L
        private const val PRIVATE_TX_DELAY_MS = 350L
        private val PRIVATE_TX_RETRY_DELAYS_MS = longArrayOf(8_000L, 20_000L)
        // Concurrent tx-body fetches per batch during a full balance scan.
        // Mirrors the batch size the web explorer already uses against the
        // same node (src/lib/api.ts, scanPrivateKey) without tripping its
        // rate limiter.
        private const val PRIVATE_TX_FETCH_BATCH_SIZE = 6
        private const val PRIVATE_TX_BATCH_DELAY_MS = 150L
        // Cuanto se espera el cuerpo de una transaccion privada.
        //
        // Casi siempre sale del cache que dejaron los bloques ya bajados y
        // vuelve al instante. Los 45 segundos son para el caso raro en que hay
        // que pedirselo a un par por Tor; con los 10 originales, pensados para
        // un servidor HTTP cercano, ese caso fallaba siempre.
        private const val PRIVATE_TX_FETCH_TIMEOUT_MS = 45_000L
        // Cuanto se le da a un rango de bloques, por bloque pedido.
        //
        // Antes eran 20 segundos fijos, medidos contra un servidor HTTP que
        // devolvia el rango entero en una respuesta. Ahora cada bloque es un
        // pedido punto a punto que viaja por Tor, asi que el costo crece con el
        // tamaño del rango y un techo fijo lo unico que garantiza es que el
        // escaneo falle siempre.
        private const val RAW_BLOCK_TIMEOUT_PER_BLOCK_MS = 2_000L
        // Piso para rangos chicos: armar el circuito ya cuesta esto.
        private const val RAW_BLOCK_RANGE_TIMEOUT_FLOOR_MS = 30_000L
    }

    data class BalanceInfo(
        val matureAtoms: Long,
        val immatureAtoms: Long,
        val missingTxCount: Int = 0,
        val scannedHeight: Long = 0,
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
    ): Result<BalanceInfo> {
        return try {
            val cacheKey = if (scanKey != null) "${address}_full" else address
            val hasPendingOutbound = rpcSettings.readPendingSends(address).isNotEmpty()
            val cacheTtlMs = when {
                scanKey != null && hasPendingOutbound -> PENDING_FULL_SCAN_CACHE_TTL_MS
                scanKey != null -> FULL_SCAN_CACHE_TTL_MS
                else -> BALANCE_CACHE_TTL_MS
            }
            // Hydrate in-memory cache from persistent storage on first access of this session
            if (scanKey != null && !balanceCache.containsKey(cacheKey)) {
                rpcSettings.readBalanceCache(address)?.let { saved ->
                    balanceCache[cacheKey] = Pair(
                        BalanceInfo(
                            matureAtoms = saved.matureAtoms,
                            immatureAtoms = saved.immatureAtoms,
                            missingTxCount = saved.missingTxCount,
                            scannedHeight = saved.scannedHeight
                        ),
                        saved.savedAtMillis
                    )
                }
            }
            val cached = balanceCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.second < cacheTtlMs) {
                return Result.Success(cached.first.withReconciledPendings(address))
            }
            scanMutex.withLock {
                val cachedNow = balanceCache[cacheKey]
                if (cachedNow != null && System.currentTimeMillis() - cachedNow.second < cacheTtlMs) {
                    return@withLock Result.Success(cachedNow.first.withReconciledPendings(address))
                }
                val height = zokaNodeApi.getChainHeight().height
                val rawInfo = if (scanKey != null) {
                    scanFullBalance(address, scanKey, height, restoreHeight)
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
                            scannedHeight = saved.scannedHeight
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
     *   - Drop pending entries older than 30 min (assumed dropped by every
     *     mempool by now → reserved atoms revert to available).
     *   - For each surviving pending, if the next balance scan saw any of
     *     the tx's input nullifiers as spent on chain, the tx confirmed
     *     and the reservation is no longer needed (chain already reflects
     *     the deduction). We approximate that by checking whether the tx
     *     body lives in the node's `priv_tx` storage AND its hash is in
     *     `confirmedPrivateTxHashes` (txs we found in a block during the
     *     scan). Cheap heuristic: if the chain scanned-height advanced
     *     past the send's creation time and the tx hash is in the
     *     confirmed set, it's done.
     *   - Sum the remaining reservations into `pendingOutAtoms` so the
     *     UI can show Available = Mature - PendingOut.
     */
    private fun BalanceInfo.withReconciledPendings(address: String): BalanceInfo {
        val survivors = rpcSettings.expirePendingSends(address)
        if (survivors.isEmpty()) return this
        // A pending entry is considered "confirmed" once the matching tx
        // body is no longer being chased by the next scan. We use the
        // confirmed-hashes set produced by the most recent full scan
        // (populated below in scanFullBalance). For now, drop any pending
        // whose hash appears in the in-session `confirmedPrivateTxHashes`
        // cache; otherwise keep it as still-pending.
        var pendingOut = 0L
        for (p in survivors) {
            if (confirmedPrivateTxHashes.contains(p.hash)) {
                rpcSettings.removePendingSend(address, p.hash)
            } else {
                pendingOut += p.reservedAtoms
            }
        }
        return copy(pendingOutAtoms = pendingOut)
    }

    /**
     * Keep "pending out" reservations in step with each tx's real lifecycle
     * instead of a blind 30-min timer. Run after a scan so the confirmed set
     * is fresh. For every reserved send:
     *   - if the scan already saw it confirmed in a block, drop the
     *     reservation (the spend is now reflected in `mature`);
     *   - else, if the node still knows the tx (waiting in a mempool / stored),
     *     refresh its timestamp so the TTL can't drop a tx that is merely
     *     waiting for a miner — this was the cause of the balance "snapping
     *     back to full" while a verified tx sat unmined.
     * A genuinely dropped tx (node 404 + never confirmed) is left to the TTL
     * fallback in [withReconciledPendings], so a single-node propagation gap
     * never releases funds early.
     */
    private suspend fun reconcilePendingSendsWithNode(address: String) {
        for (p in rpcSettings.readPendingSends(address)) {
            if (confirmedPrivateTxHashes.contains(p.hash)) {
                rpcSettings.removePendingSend(address, p.hash)
                continue
            }
            val nodeKnowsTx = try {
                zokaNodeApi.getPrivateTransaction(p.hash)
                true
            } catch (_: Exception) {
                false
            }
            if (nodeKnowsTx) {
                // Refresh the reservation's timestamp so the TTL window
                // restarts while the tx is still alive on the node.
                rpcSettings.recordPendingSend(address, p.hash, p.reservedAtoms)
            }
        }
    }

    /**
     * Hashes of private transactions the most recent scan observed in a
     * block (i.e. confirmed). Drives the pending-send reconciliation in
     * [withReconciledPendings]. Cleared at session start; safe to grow.
     */
    private val confirmedPrivateTxHashes = mutableSetOf<String>()

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

    fun cachedBalanceFor(address: String): BalanceInfo? {
        val saved = rpcSettings.readBalanceCache(address) ?: return null
        return BalanceInfo(
            matureAtoms = saved.matureAtoms,
            immatureAtoms = saved.immatureAtoms,
            missingTxCount = saved.missingTxCount,
            scannedHeight = saved.scannedHeight
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

    private suspend fun fetchRawBlocksRange(from: Long, to: Long) =
        withTimeout(rawBlockRangeTimeoutMs(from, to)) {
            zokaNodeApi.getRawBlocksRange(from, to)
        }

    /** Tiempo que se le da a un rango, proporcional a cuantos bloques trae. */
    private fun rawBlockRangeTimeoutMs(from: Long, to: Long): Long {
        val bloques = (to - from + 1L).coerceAtLeast(1L)
        return (bloques * RAW_BLOCK_TIMEOUT_PER_BLOCK_MS)
            .coerceAtLeast(RAW_BLOCK_RANGE_TIMEOUT_FLOOR_MS)
    }

    private suspend fun scanCoinbaseBalance(address: String, chainHeight: Long): BalanceInfo {
        var matureAtoms = 0L
        var immatureAtoms = 0L
        var from = 1L
        while (from <= chainHeight) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, chainHeight)
            val response = fetchRawBlocksRange(from, to)
            delay(BATCH_DELAY_MS)
            response.blocks.forEach { block ->
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
            from = to + 1
        }
        return BalanceInfo(matureAtoms, immatureAtoms)
    }

    // ── Full UTXO scan (requires scan key) ───────────────────────────────────

    private suspend fun scanFullBalance(
        address: String,
        scanKeyBytes: ByteArray,
        chainHeight: Long,
        restoreHeight: Long = 0
    ): BalanceInfo {
        val scanStartedAtMs = System.currentTimeMillis()
        Log.d("ZokaRepository", "scanFullBalance: START chainHeight=$chainHeight restoreHeight=$restoreHeight")
        // Derive scan scalar: SHA256(scanKey) as LE mod L
        val scanScalar = Ristretto255.scalarFromSecretBytes(scanKeyBytes)
        // Derive spend key: SHA256("zoka-private-wallet-spend-v1" || 0x1f || scanKey)
        val spendInput = "zoka-private-wallet-spend-v1".toByteArray(Charsets.UTF_8) + byteArrayOf(0x1f) + scanKeyBytes
        val spendKeyRaw = MessageDigest.getInstance("SHA-256").digest(spendInput)
        val spendScalar = Ristretto255.scalarFromSecretBytes(spendKeyRaw)

        data class CoinbaseEntry(
            val amountAtoms: Long, val unlockHeight: Long,
            val outputId: String, val commitmentHex: String
        )
        val myCoinbases = mutableListOf<CoinbaseEntry>()
        val allPrivateTxHashes = linkedSetOf<String>()
        // Real on-chain time (ms) of each private tx, taken from its block header,
        // so Recent Activity shows when the tx happened — not when we scanned.
        val txTimestampByHash = mutableMapOf<String, Long>()
        // Spent nullifiers, taken from each block's committed `private_nullifiers`
        // field during the block scan. This is the authoritative, fetch-independent
        // source of spends: a note can never show as spendable just because a
        // per-tx body fetch was rate-limited or missed.
        val allInputNullifiers = mutableSetOf<String>()
        // Every full scan re-attempts any body a prior scan couldn't fetch: these
        // hashes are committed on-chain, so a 404 is transient (a node's missing
        // body / replication lag), never permanent.
        missingPrivateTxCache.clear()

        // Phase 1: Scan blocks for our coinbase outputs and private tx hashes.
        // A freshly-created wallet records its creation height as restoreHeight,
        // so we skip every block before it existed — there is provably nothing
        // of ours there. Imported wallets pass 0 and scan from genesis. This is
        // the safe optimization: it never drops history a wallet could own.
        var from = restoreHeight.coerceAtLeast(1L)
        while (from <= chainHeight) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, chainHeight)
            val response = fetchRawBlocksRange(from, to)
            delay(BATCH_DELAY_MS)
            response.blocks.forEach { block ->
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
                confirmedPrivateTxHashes.addAll(block.privateTxHashes)
            }
            from = to + 1
        }

        // Phase 2: Fetch all private txs — collect owned outputs (and, as a
        // supplement to the block-committed set above, any spend nullifiers from
        // the tx bodies we do fetch).
        data class OwnedOutput(val amountAtoms: Long, val nullifierHex: String)
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

        if (allPrivateTxHashes.isNotEmpty()) {
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
        val hashList = allPrivateTxHashes.toList()
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
            if (batchStart + PRIVATE_TX_FETCH_BATCH_SIZE < hashList.size) {
                delay(PRIVATE_TX_BATCH_DELAY_MS)
            }
        }

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
        Log.d("ZokaRepository", "scanFullBalance: DONE in ${elapsedMs}ms coinbases=${myCoinbases.size} privateTxs=${allPrivateTxHashes.size} ownedOutputs=${ownedOutputs.size} missingTxs=$missingTxCount mature=$matureAtoms immature=$immatureAtoms")
        return BalanceInfo(
            matureAtoms = matureAtoms,
            immatureAtoms = immatureAtoms,
            missingTxCount = missingTxCount,
            scannedHeight = chainHeight
        )
    }

    private suspend fun getPrivateTransactionCached(txHash: String): ZokaPrivateTransactionResponse? {
        privateTxCache[txHash]?.let { return it }
        if (txHash in missingPrivateTxCache) return null

        delay(PRIVATE_TX_DELAY_MS)
        return try {
            withTimeout(PRIVATE_TX_FETCH_TIMEOUT_MS) {
                zokaNodeApi.getPrivateTransaction(txHash)
            }.also { privateTxCache[txHash] = it }
        } catch (e: HttpException) {
            if (e.code() != 429) {
                if (e.code() == 404) missingPrivateTxCache.add(txHash)
                Log.w("ZokaRepository", "Skipping $txHash: ${e.message}")
                return null
            }
            retryPrivateTransactionAfterRateLimit(txHash)
        } catch (e: TimeoutCancellationException) {
            // The shared OkHttp client carries a 120s read timeout sized for
            // proof generation elsewhere; a single stalled fetch here would
            // otherwise block this entire concurrent batch for that long.
            // Treat it like any other transient failure: skip for this scan,
            // don't permanently mark as missing (only a real 404 does that).
            Log.w("ZokaRepository", "Timed out fetching $txHash after ${PRIVATE_TX_FETCH_TIMEOUT_MS}ms, skipping")
            null
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
                return withTimeout(PRIVATE_TX_FETCH_TIMEOUT_MS) {
                    zokaNodeApi.getPrivateTransaction(txHash)
                }.also { privateTxCache[txHash] = it }
            } catch (e: HttpException) {
                if (e.code() != 429) {
                    if (e.code() == 404) missingPrivateTxCache.add(txHash)
                    Log.w("ZokaRepository", "Skipping $txHash after retry: ${e.message}")
                    return null
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("ZokaRepository", "Timed out retrying $txHash, skipping")
                return null
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
     * The selected build variant decides the proving path:
     * - release keeps the transitional encrypted remote builder so the
     *   currently published APK keeps working during the dual-stack window.
     * - staging builds the private transaction locally and submits the
     *   canonical JSON body to /private/tx, so the recovery seed never leaves
     *   the phone.
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
    ): Result<String> {
        return try {
            val amountAtoms = zkaToAtoms(amount)
            val feeAtoms = zkaToAtoms(fee).coerceAtLeast(0L)
            val seedHex = scanKeyHex.removePrefix("0x")

            if (amountAtoms <= 0L) {
                return Result.Error(IllegalArgumentException("Amount must be greater than zero"))
            }
            if (!to.startsWith("zka1") || to.length < 32) {
                return Result.Error(IllegalArgumentException("Invalid recipient address"))
            }

            val seedBytesForValidation = tryHexToBytes(seedHex)
                ?: return Result.Error(IllegalArgumentException("Invalid recovery seed"))
            seedBytesForValidation.fill(0)

            val hash = when (com.zoka.zkapriv.network.BuildConfig.ZOKA_PRIVATE_SEND_MODE) {
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
                    "Unsupported private send mode: ${com.zoka.zkapriv.network.BuildConfig.ZOKA_PRIVATE_SEND_MODE}"
                )
            }

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

            // Reserve `amount + fee` atoms locally so the dashboard's
            // "Available" line drops immediately and a follow-up send
            // can't accidentally try to spend the same UTXO twice. The
            // reservation is cleared once the next balance scan sees
            // the tx hash committed in a block (or 30 min elapses).
            rpcSettings.recordPendingSend(
                address = from,
                hash = hash,
                reservedAtoms = amountAtoms + feeAtoms
            )

            // Deliberately do NOT invalidate the balance cache here. The
            // pending-send reservation above is enough: the next read applies
            // it as an overlay (withReconciledPendings) on top of the
            // existing cached mature balance, so "Available" drops instantly.
            // Wiping the cache instead forced an immediate full re-scan
            // (1-2 min, walks every private tx on chain) right when the user
            // most wants to see the deduction reflected fast.

            Result.Success(hash)
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

    // ── Private send backends ─────────────────────────────────────────────────

    private suspend fun submitLocalSelfCustodyTransfer(
        from: String,
        to: String,
        amountAtoms: Long,
        feeAtoms: Long,
        seedHex: String
    ): String {
        Log.d(
            "ZokaRepository",
            "sendTransaction (local self-custody build) initiated"
        )
        val pendingBodies = rpcSettings.expirePendingSends(from).map { pending ->
            try {
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
        val submitted = zokaNodeApi.submitPrivateTx(built.body)
        val submittedHash = submitted.get("hash")?.asString.orEmpty()
        check(submittedHash == built.hash) {
            "Node returned a different hash for locally-built private transaction"
        }
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
     * A pending tx (already broadcast, awaiting a miner) MUST stay
     * PENDING until either:
     *   - the receiver/sender UTXO scan confirms it landed in a block, or
     *   - enough wall-clock time has elapsed that the tx was almost
     *     certainly dropped from every mempool (currently 30 minutes).
     *
     * The previous logic flipped pending txs to FAILED in under 2 minutes
     * and (worse) treated a 404 as CONFIRMED past that, which is exactly
     * backwards: 404 means "node has no record", which for a fresh tx is
     * the *expected* state while it's still propagating.
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
            // PENDING for as long as it takes a miner to include it. We do NOT
            // time it out to FAILED here — that was the bug that flipped a
            // valid, still-pending send to "failed" after 30 minutes when no
            // miner was running.
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
        while (from <= height) {
            val to = minOf(from + RAW_BLOCK_SCAN_BATCH_SIZE - 1, height)
            val response = fetchRawBlocksRange(from, to)
            for (block in response.blocks) {
                confirmedPrivateTxHashes.addAll(block.privateTxHashes)
                if (hash in block.privateTxHashes) return true
            }
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
        val list = loadTransactionsFromPrefs(address).toMutableList()
        list.add(0, transaction)
        saveTransactionsToPrefs(address, list.take(MAX_CACHED_TRANSACTIONS))
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
