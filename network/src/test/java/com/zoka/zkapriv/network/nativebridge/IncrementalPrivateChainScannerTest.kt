package com.zoka.zkapriv.network.nativebridge

import com.google.gson.JsonObject
import com.zoka.zkapriv.network.model.ZokaRawBlockHeader
import com.zoka.zkapriv.network.model.ZokaRawBlockResponse
import com.zoka.zkapriv.network.model.ZokaRawBlocksRangeResponse
import com.zoka.zkapriv.network.model.ZokaRawPrivateCoinbase
import com.zoka.zkapriv.network.model.ZokaRawTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IncrementalPrivateChainScannerTest {
    @Test
    fun refreshDownloadsOnlyNewBlocksAndBodies() = runBlocking {
        val fixture = ScannerFixture(
            blocks = mutableListOf(
                block(1, "block-1", privateTxHashes = listOf("private-1")),
                block(2, "block-2"),
                block(3, "block-3", privateTxHashes = listOf("private-2"))
            )
        )

        val first = fixture.scanner.refresh("alice", 2)
        val second = fixture.scanner.refresh("alice", 3)

        assertEquals(listOf(1L to 2L, 2L to 2L, 3L to 3L), fixture.requestedRanges)
        assertEquals(listOf("private-1", "private-2"), fixture.requestedBodies)
        assertEquals(listOf("private-1"), first.confirmedBodies.map(::bodyId))
        assertEquals(listOf("private-1", "private-2"), second.confirmedBodies.map(::bodyId))
    }

    @Test
    fun refreshClearsCachedCoinbasesWhenWalletChanges() = runBlocking {
        val fixture = ScannerFixture(
            blocks = mutableListOf(
                block(1, "block-1", coinbaseRecipient = "alice"),
                block(2, "block-2", coinbaseRecipient = "bob")
            )
        )

        val alice = fixture.scanner.refresh("alice", 2)
        val bob = fixture.scanner.refresh("bob", 2)

        assertEquals(listOf("alice"), alice.coinbases.map(::coinbaseRecipient))
        assertEquals(listOf("bob"), bob.coinbases.map(::coinbaseRecipient))
        assertEquals(listOf(1L to 2L, 1L to 2L), fixture.requestedRanges)
        assertEquals(listOf(1L to 1L, 2L to 2L), fixture.requestedFullRanges)
    }

    @Test
    fun refreshUsesDigestsAndDownloadsOnlyOwnedCoinbaseBlocksInFull() = runBlocking {
        val fixture = ScannerFixture(
            blocks = mutableListOf(
                block(1, "block-1", coinbaseRecipient = "someone-else"),
                block(2, "block-2", coinbaseRecipient = "alice"),
                block(3, "block-3", privateTxHashes = listOf("private-1"))
            )
        )

        val state = fixture.scanner.refresh("alice", 3)

        assertEquals(listOf(1L to 3L), fixture.requestedRanges)
        assertEquals(listOf(2L to 2L), fixture.requestedFullRanges)
        assertEquals(listOf("alice"), state.coinbases.map(::coinbaseRecipient))
    }

    @Test
    fun refreshRescansFromStartWhenTipAnchorChanges() = runBlocking {
        val blocks = mutableListOf(
            block(1, "block-1"),
            block(2, "block-2", privateTxHashes = listOf("private-old"))
        )
        val fixture = ScannerFixture(blocks)
        fixture.scanner.refresh("alice", 2)
        blocks[1] = block(2, "block-2-reorg", privateTxHashes = listOf("private-new"))

        val rescanned = fixture.scanner.refresh("alice", 2)

        assertEquals(listOf(1L to 2L, 2L to 2L, 1L to 2L), fixture.requestedRanges)
        assertEquals(listOf("private-new"), rescanned.confirmedBodies.map(::bodyId))
    }

    @Test
    fun refreshFailsExplicitlyWhenConfirmedBodyIsUnavailable() {
        val fixture = ScannerFixture(
            blocks = mutableListOf(block(1, "block-1", privateTxHashes = listOf("missing"))),
            unavailableBodies = setOf("missing")
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.scanner.refresh("alice", 1) }
        }

        assertEquals("Missing private transaction body missing during local scan", error.message)
    }

    @Test
    fun refreshRestoresEncryptedCheckpointWithoutPersistingBodies() = runBlocking {
        val store = RecordingSnapshotStore()
        val blocks = mutableListOf(
            block(1, "block-1", coinbaseRecipient = "alice"),
            block(2, "block-2", privateTxHashes = listOf("private-1"))
        )
        ScannerFixture(blocks = blocks, snapshotStore = store).scanner.refresh("alice", 2)
        val restored = ScannerFixture(blocks = blocks, snapshotStore = store)

        val state = restored.scanner.refresh("alice", 2)

        assertEquals(listOf(2L to 2L), restored.requestedRanges)
        assertEquals(listOf("private-1"), restored.requestedBodies)
        assertEquals(listOf("alice"), state.coinbases.map(::coinbaseRecipient))
        assertEquals(listOf("private-1"), state.confirmedBodies.map(::bodyId))
        assertEquals(false, store.snapshot.orEmpty().contains("\"id\""))
    }

    @Test
    fun refreshClearsCorruptCheckpointAndRescans() = runBlocking {
        val store = RecordingSnapshotStore(snapshot = """{"schemaVersion":999}""")
        val fixture = ScannerFixture(
            blocks = mutableListOf(block(1, "block-1")),
            snapshotStore = store
        )

        fixture.scanner.refresh("alice", 1)

        assertEquals(listOf(1L to 1L), fixture.requestedRanges)
        assertEquals(1, store.clearCount)
        assertEquals(1, store.writeCount)
    }

    private class ScannerFixture(
        val blocks: MutableList<ZokaRawBlockResponse>,
        private val unavailableBodies: Set<String> = emptySet(),
        snapshotStore: PrivateChainSnapshotStore = NoOpPrivateChainSnapshotStore
    ) {
        val requestedRanges = mutableListOf<Pair<Long, Long>>()
        val requestedFullRanges = mutableListOf<Pair<Long, Long>>()
        val requestedBodies = mutableListOf<String>()
        val scanner = IncrementalPrivateChainScanner(
            blockDigestsRange = { from, to ->
                requestedRanges += from to to
                ZokaRawBlocksRangeResponse(
                    fromHeight = from,
                    toHeight = to,
                    blocks = blocks.filter { block ->
                        block.header?.blockHeight in from..to
                    }
                )
            },
            rawBlocksRange = { from, to ->
                requestedFullRanges += from to to
                ZokaRawBlocksRangeResponse(
                    fromHeight = from,
                    toHeight = to,
                    blocks = blocks.filter { block ->
                        block.header?.blockHeight in from..to
                    }
                )
            },
            privateTransactionRaw = { hash ->
                requestedBodies += hash
                check(hash !in unavailableBodies)
                JsonObject().apply {
                    addProperty("id", hash)
                    addProperty("tx_hash", hash)
                }
            },
            snapshotStore = snapshotStore
        )
    }

    private class RecordingSnapshotStore(
        var snapshot: String? = null
    ) : PrivateChainSnapshotStore {
        var clearCount = 0
        var writeCount = 0

        override fun read(): String? = snapshot

        override fun write(snapshotJson: String) {
            snapshot = snapshotJson
            writeCount++
        }

        override fun clear() {
            snapshot = null
            clearCount++
        }
    }

    companion object {
        private fun block(
            height: Long,
            hash: String,
            coinbaseRecipient: String? = null,
            privateTxHashes: List<String> = emptyList()
        ): ZokaRawBlockResponse = ZokaRawBlockResponse(
            header = ZokaRawBlockHeader(
                blockHeight = height,
                hash = hash,
                prevHash = if (height > 1) "block-${height - 1}" else "genesis"
            ),
            privateCoinbase = coinbaseRecipient?.let { recipient ->
                ZokaRawPrivateCoinbase(
                    blockHeight = height,
                    recipientAddress = recipient,
                    outputId = "coinbase-$hash",
                    assetTag = "ZOKA",
                    assetTagCommitment = "asset-tag-commitment",
                    recipientCommitment = "recipient-commitment",
                    stealthAddress = "stealth-address",
                    ephemeralPubkey = listOf(1, 2, 3),
                    commitment = "commitment",
                    amountCommitment = "amount-commitment",
                    rangeProof = listOf(4, 5, 6),
                    oneTimePublicKey = "one-time-public-key",
                    encryptedAmount = listOf(7, 8),
                    commitmentBlind = listOf(9, 10),
                    unlockHeight = height + 60
                )
            },
            transactions = coinbaseRecipient?.let { recipient ->
                listOf(ZokaRawTransaction(receiver = recipient, amount = height * 100))
            }.orEmpty(),
            privateTxHashes = privateTxHashes
        )

        private fun bodyId(body: JsonObject): String = body.get("id").asString

        private fun coinbaseRecipient(coinbase: JsonObject): String =
            coinbase.getAsJsonObject("output").get("recipient_address").asString
    }
}
