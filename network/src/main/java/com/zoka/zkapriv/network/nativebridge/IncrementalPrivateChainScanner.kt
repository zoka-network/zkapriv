package com.zoka.zkapriv.network.nativebridge

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zoka.zkapriv.network.model.ZokaRawBlocksRangeResponse
import com.zoka.zkapriv.network.model.requireCompleteRange
import kotlin.coroutines.cancellation.CancellationException

internal class IncrementalPrivateChainScanner(
    private val blockDigestsRange: suspend (Long, Long) -> ZokaRawBlocksRangeResponse,
    private val rawBlocksRange: suspend (Long, Long) -> ZokaRawBlocksRangeResponse,
    private val privateTransactionRaw: suspend (String) -> JsonObject,
    private val snapshotStore: PrivateChainSnapshotStore = NoOpPrivateChainSnapshotStore
) {
    private val gson = Gson()
    private val snapshot = restoreSnapshot()

    internal data class PreparedChainState(
        val height: Long,
        val coinbases: List<JsonObject>,
        val confirmedBodies: List<JsonObject>,
        /** Cuerpos confirmados que ningún par tiene y quedaron afuera. */
        val lostBodies: Int = 0
    )

    private data class ChainSnapshot(
        var ownerAddress: String = "",
        var scannedHeight: Long = 0,
        var tipHash: String = "",
        val coinbasesById: LinkedHashMap<String, JsonObject> = linkedMapOf(),
        val confirmedHashes: LinkedHashSet<String> = linkedSetOf(),
        val confirmedBodiesByHash: LinkedHashMap<String, JsonObject> = linkedMapOf(),
        // Solo en memoria: al reabrir la app se vuelven a preguntar, por si
        // algún par los consiguió.
        val lostHashes: LinkedHashSet<String> = linkedSetOf()
    ) {
        fun clear() {
            ownerAddress = ""
            scannedHeight = 0
            tipHash = ""
            coinbasesById.clear()
            confirmedHashes.clear()
            confirmedBodiesByHash.clear()
            lostHashes.clear()
        }
    }

    private data class PersistedChainSnapshot(
        val schemaVersion: Int,
        val ownerAddress: String,
        val scannedHeight: Long,
        val tipHash: String,
        val coinbasesById: LinkedHashMap<String, JsonObject>,
        val confirmedHashes: LinkedHashSet<String>
    )

    suspend fun refresh(from: String, height: Long): PreparedChainState {
        if (snapshot.ownerAddress.isNotEmpty() && snapshot.ownerAddress != from) {
            clearSnapshot()
        }
        snapshot.ownerAddress = from

        if (snapshot.scannedHeight > height) {
            clearSnapshot()
            snapshot.ownerAddress = from
        }
        if (snapshot.scannedHeight > 0) {
            val anchor = blockDigestsRange(snapshot.scannedHeight, snapshot.scannedHeight)
                .requireCompleteRange(snapshot.scannedHeight, snapshot.scannedHeight)
                .single()
            val anchorHash = anchor?.header?.hash.orEmpty()
            if (anchorHash.isBlank() || anchorHash != snapshot.tipHash) {
                clearSnapshot()
                snapshot.ownerAddress = from
            }
        }

        var rangeFrom = snapshot.scannedHeight + 1L
        while (rangeFrom <= height) {
            val rangeTo = minOf(rangeFrom + 249L, height)
            val blocks = blockDigestsRange(rangeFrom, rangeTo).requireCompleteRange(
                from = rangeFrom,
                to = rangeTo,
                expectedPrevHash = snapshot.tipHash.takeIf { snapshot.scannedHeight > 0 }
            )
            for (block in blocks) {
                val blockHeight = block.header?.blockHeight
                    ?: throw IllegalStateException("Missing block height during local scan")
                val blockHash = block.header?.hash.orEmpty()
                check(blockHash.isNotBlank()) {
                    "Raw block $blockHeight has no hash during local scan"
                }
                snapshot.confirmedHashes.addAll(block.privateTxHashes)
                val coinbase = block.privateCoinbase
                val coinbaseTx = block.transactions.firstOrNull()
                if (
                    coinbase != null &&
                    coinbaseTx != null &&
                    coinbase.recipientAddress == from &&
                    coinbaseTx.receiver == from
                ) {
                    // El resumen omite la prueba de rango y otros secretos del
                    // coinbase. Solo para un coinbase propio se pide el bloque
                    // entero, porque Rust necesita esa salida completa al
                    // importarla como nota gastable.
                    val fullBlock = rawBlocksRange(blockHeight, blockHeight)
                        .requireCompleteRange(blockHeight, blockHeight)
                        .single()
                    check(fullBlock.header?.hash == blockHash) {
                        "Full block $blockHeight does not match its digest"
                    }
                    val fullCoinbase = fullBlock.privateCoinbase
                        ?: throw IllegalStateException(
                            "Full block $blockHeight has no private coinbase"
                        )
                    val fullCoinbaseTx = fullBlock.transactions.firstOrNull()
                        ?: throw IllegalStateException(
                            "Full block $blockHeight has no coinbase transaction"
                        )
                    check(
                            fullCoinbase.recipientAddress == from &&
                            fullCoinbaseTx.receiver == from &&
                            fullCoinbase.outputId == coinbase.outputId &&
                            fullCoinbase.commitment == coinbase.commitment &&
                            fullCoinbase.unlockHeight == coinbase.unlockHeight &&
                            fullCoinbaseTx.amount == coinbaseTx.amount
                    ) {
                        "Full block $blockHeight does not match its owned coinbase digest"
                    }
                    val coinbaseId = fullCoinbase.outputId.ifBlank { "block:$blockHash" }
                    snapshot.coinbasesById[coinbaseId] = JsonObject().apply {
                        addProperty("source_block_hash", blockHash)
                        addProperty("expected_amount_atoms", fullCoinbaseTx.amount)
                        add("output", gson.toJsonTree(fullCoinbase))
                    }
                }
                snapshot.scannedHeight = blockHeight
                snapshot.tipHash = blockHash
            }
            // El recorrido puede durar minutos sobre Tor. Guardar al cerrar
            // cada lote evita volver a genesis si luego falta un cuerpo de tx
            // o Android mata el proceso.
            persistSnapshot()
            rangeFrom = rangeTo + 1L
        }

        for (hash in snapshot.confirmedHashes) {
            if (snapshot.confirmedBodiesByHash.containsKey(hash) || hash in snapshot.lostHashes) {
                continue
            }
            val fetched = runCatching { privateTransactionRaw(hash) }
            val error = fetched.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null) {
                // Un cuerpo que todos los pares dicen no tener se saltea; uno que
                // no se pudo preguntar sigue frenando el envio.
                //
                // En Zenith, antes de los bundles canonicos (25.570), los cuerpos
                // privados viajaban fuera de los bloques y 12 de esa epoca no los
                // tiene ningun nodo. Exigirlos dejaba sin poder enviar a toda
                // wallet movil para siempre. El motor solo usa los cuerpos para
                // encontrar pagos propios y marcar notas gastadas: sin uno, a lo
                // sumo no ve un pago viejo o intenta gastar una nota ya gastada,
                // y eso ultimo lo rechaza el nodo.
                if (!isLostEverywhere(error)) {
                    throw IllegalStateException(
                        "Missing private transaction body $hash during local scan",
                        error
                    )
                }
                snapshot.lostHashes += hash
                continue
            }
            val body = fetched.getOrThrow()
            check(body.get("tx_hash")?.asString == hash) {
                "Private transaction body $hash identifies a different transaction"
            }
            snapshot.confirmedBodiesByHash[hash] = body
        }

        return PreparedChainState(
            height = height,
            coinbases = snapshot.coinbasesById.values.toList(),
            confirmedBodies = snapshot.confirmedHashes.mapNotNull { hash ->
                snapshot.confirmedBodiesByHash[hash]
            },
            lostBodies = snapshot.confirmedHashes.count { it in snapshot.lostHashes }
        )
    }

    /**
     * Todos los pares consultados contestaron que no tienen el cuerpo.
     *
     * El motor arma ese texto cuando la ultima respuesta fue un NotFound
     * explicito (`p2p.rs`, "did not have private tx body"). Una demora, un
     * rechazo o un par que no contesto no prueban que el cuerpo no exista, asi
     * que no cuentan.
     */
    private fun isLostEverywhere(error: Throwable): Boolean {
        val detail = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
        return detail.contains("did not have private tx body") &&
            !INCONCLUSIVE.containsMatchIn(detail)
    }

    private fun clearSnapshot() {
        snapshot.clear()
        snapshotStore.clear()
    }

    private fun persistSnapshot() {
        val persisted = PersistedChainSnapshot(
            schemaVersion = SNAPSHOT_SCHEMA_VERSION,
            ownerAddress = snapshot.ownerAddress,
            scannedHeight = snapshot.scannedHeight,
            tipHash = snapshot.tipHash,
            coinbasesById = snapshot.coinbasesById,
            confirmedHashes = snapshot.confirmedHashes
        )
        val json = gson.toJson(persisted)
        if (json.length > MAX_PERSISTED_SNAPSHOT_CHARS) {
            snapshotStore.clear()
            throw IllegalStateException(
                "El avance del escaneo privado supera el límite seguro de persistencia"
            )
        }
        snapshotStore.write(json)
    }

    private fun restoreSnapshot(): ChainSnapshot {
        val stored = runCatching { snapshotStore.read() }.getOrNull() ?: return ChainSnapshot()
        if (stored.length > MAX_PERSISTED_SNAPSHOT_CHARS) {
            runCatching { snapshotStore.clear() }
            return ChainSnapshot()
        }
        return runCatching {
            val persisted = gson.fromJson(stored, PersistedChainSnapshot::class.java)
            require(persisted.schemaVersion == SNAPSHOT_SCHEMA_VERSION)
            require(persisted.scannedHeight >= 0)
            require(persisted.ownerAddress.isNotBlank() || persisted.scannedHeight == 0L)
            require(persisted.tipHash.isNotBlank() || persisted.scannedHeight == 0L)
            require(persisted.coinbasesById.keys.none(String::isBlank))
            require(persisted.confirmedHashes.none(String::isBlank))
            ChainSnapshot(
                ownerAddress = persisted.ownerAddress,
                scannedHeight = persisted.scannedHeight,
                tipHash = persisted.tipHash,
                coinbasesById = persisted.coinbasesById,
                confirmedHashes = persisted.confirmedHashes
            )
        }.getOrElse {
            runCatching { snapshotStore.clear() }
            ChainSnapshot()
        }
    }

    private companion object {
        const val SNAPSHOT_SCHEMA_VERSION = 2
        const val MAX_PERSISTED_SNAPSHOT_CHARS = 2 * 1024 * 1024
        val INCONCLUSIVE = Regex("(?i)timed out|timeout|rate.?limit|rejected|invalid|no connected peers")
    }
}
