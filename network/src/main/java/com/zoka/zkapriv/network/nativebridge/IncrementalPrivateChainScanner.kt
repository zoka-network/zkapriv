package com.zoka.zkapriv.network.nativebridge

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zoka.zkapriv.network.model.ZokaRawBlocksRangeResponse

internal class IncrementalPrivateChainScanner(
    private val rawBlocksRange: suspend (Long, Long) -> ZokaRawBlocksRangeResponse,
    private val privateTransactionRaw: suspend (String) -> JsonObject,
    private val snapshotStore: PrivateChainSnapshotStore = NoOpPrivateChainSnapshotStore
) {
    private val gson = Gson()
    private val snapshot = restoreSnapshot()

    internal data class PreparedChainState(
        val height: Long,
        val coinbases: List<JsonObject>,
        val confirmedBodies: List<JsonObject>
    )

    private data class ChainSnapshot(
        var ownerAddress: String = "",
        var scannedHeight: Long = 0,
        var tipHash: String = "",
        val coinbasesById: LinkedHashMap<String, JsonObject> = linkedMapOf(),
        val confirmedHashes: LinkedHashSet<String> = linkedSetOf(),
        val confirmedBodiesByHash: LinkedHashMap<String, JsonObject> = linkedMapOf()
    ) {
        fun clear() {
            ownerAddress = ""
            scannedHeight = 0
            tipHash = ""
            coinbasesById.clear()
            confirmedHashes.clear()
            confirmedBodiesByHash.clear()
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
            val anchor = rawBlocksRange(snapshot.scannedHeight, snapshot.scannedHeight)
                .blocks
                .singleOrNull()
            val anchorHash = anchor?.header?.hash.orEmpty()
            if (anchorHash.isBlank() || anchorHash != snapshot.tipHash) {
                clearSnapshot()
                snapshot.ownerAddress = from
            }
        }

        var rangeFrom = snapshot.scannedHeight + 1L
        while (rangeFrom <= height) {
            val rangeTo = minOf(rangeFrom + 249L, height)
            val blocksByHeight = rawBlocksRange(rangeFrom, rangeTo)
                .blocks
                .associateBy { it.header?.blockHeight }
            for (blockHeight in rangeFrom..rangeTo) {
                val block = blocksByHeight[blockHeight]
                    ?: throw IllegalStateException("Missing raw block $blockHeight during local scan")
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
                    val coinbaseId = coinbase.outputId.ifBlank { "block:$blockHash" }
                    snapshot.coinbasesById[coinbaseId] = JsonObject().apply {
                        addProperty("source_block_hash", blockHash)
                        addProperty("expected_amount_atoms", coinbaseTx.amount)
                        add("output", gson.toJsonTree(coinbase))
                    }
                }
                snapshot.scannedHeight = blockHeight
                snapshot.tipHash = blockHash
            }
            rangeFrom = rangeTo + 1L
        }

        for (hash in snapshot.confirmedHashes) {
            if (!snapshot.confirmedBodiesByHash.containsKey(hash)) {
                val body = runCatching { privateTransactionRaw(hash) }
                    .getOrElse { error ->
                        throw IllegalStateException(
                            "Missing private transaction body $hash during local scan",
                            error
                        )
                    }
                snapshot.confirmedBodiesByHash[hash] = body
            }
        }

        persistSnapshot()
        return PreparedChainState(
            height = height,
            coinbases = snapshot.coinbasesById.values.toList(),
            confirmedBodies = snapshot.confirmedHashes.map { hash ->
                snapshot.confirmedBodiesByHash.getValue(hash)
            }
        )
    }

    private fun clearSnapshot() {
        snapshot.clear()
        runCatching { snapshotStore.clear() }
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
            runCatching { snapshotStore.clear() }
            return
        }
        runCatching { snapshotStore.write(json) }
            .onFailure { runCatching { snapshotStore.clear() } }
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
        const val SNAPSHOT_SCHEMA_VERSION = 1
        const val MAX_PERSISTED_SNAPSHOT_CHARS = 2 * 1024 * 1024
    }
}
