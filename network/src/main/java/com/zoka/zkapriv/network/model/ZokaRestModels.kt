package com.zoka.zkapriv.network.model

import com.google.gson.annotations.SerializedName

data class ZokaHealthResponse(
    @SerializedName("status")
    val status: String
)

data class ZokaChainHeightResponse(
    @SerializedName("height")
    val height: Long
)

data class ZokaAccountResponse(
    @SerializedName("address")
    val address: String,
    @SerializedName("balance")
    val balance: Long,
    @SerializedName("nonce")
    val nonce: Long
)

data class ZokaTransactionRequest(
    @SerializedName("sender")
    val sender: String,
    @SerializedName("receiver")
    val receiver: String,
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("nonce")
    val nonce: Long,
    @SerializedName("priority_fee")
    val priorityFee: Long? = null,
    @SerializedName("signature_hex")
    val signatureHex: String? = null,
    @SerializedName("public_key_hex")
    val publicKeyHex: String? = null
)

data class ZokaServerPubkeyResponse(
    @SerializedName("algorithm")
    val algorithm: String,
    @SerializedName("public_key_hex")
    val publicKeyHex: String
)

/// Body for POST /private/wallet/send-from-seed. The seed is sealed with
/// the server's X25519 public key and authenticated with AES-256-GCM.
data class ZokaSendFromSeedRequest(
    @SerializedName("ephemeral_pubkey_hex")
    val ephemeralPubkeyHex: String,
    @SerializedName("nonce_hex")
    val nonceHex: String,
    @SerializedName("encrypted_seed_hex")
    val encryptedSeedHex: String,
    @SerializedName("to")
    val to: String,
    @SerializedName("amount_atoms")
    val amountAtoms: Long,
    @SerializedName("fee_atoms")
    val feeAtoms: Long? = null
)

data class ZokaSendFromSeedResponse(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("status")
    val status: String
)

data class ZokaTransactionResponse(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("sender")
    val sender: String? = null,
    @SerializedName("receiver")
    val receiver: String? = null,
    @SerializedName("amount")
    val amount: Long? = null,
    @SerializedName("nonce")
    val nonce: Long? = null,
    @SerializedName("timestamp")
    val timestamp: Long? = null
)

data class ZokaBlockResponse(
    @SerializedName("height")
    val height: Long,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("prev_hash")
    val prevHash: String? = null,
    @SerializedName("timestamp")
    val timestamp: Long? = null,
    @SerializedName("transactions_count")
    val transactionsCount: Int? = null
)

data class ZokaRawBlocksRangeResponse(
    @SerializedName("from_height")
    val fromHeight: Long,
    @SerializedName("to_height")
    val toHeight: Long,
    @SerializedName("blocks")
    val blocks: List<ZokaRawBlockResponse> = emptyList()
)

data class ZokaRawBlockResponse(
    @SerializedName("header")
    val header: ZokaRawBlockHeader? = null,
    @SerializedName("private_coinbase")
    val privateCoinbase: ZokaRawPrivateCoinbase? = null,
    @SerializedName("transactions")
    val transactions: List<ZokaRawTransaction> = emptyList(),
    @SerializedName("private_tx_hashes")
    val privateTxHashes: List<String> = emptyList(),
    @SerializedName("private_nullifiers")
    val privateNullifiers: List<String> = emptyList()
)

data class ZokaRawBlockHeader(
    @SerializedName("block_height")
    val blockHeight: Long = 0,
    @SerializedName("hash")
    val hash: String = "",
    @SerializedName("prev_hash")
    val prevHash: String = "",
    @SerializedName("timestamp")
    val timestamp: Long? = null
)

data class ZokaRawPrivateCoinbase(
    @SerializedName("block_height")
    val blockHeight: Long = 0,
    @SerializedName("recipient_address")
    val recipientAddress: String = "",
    @SerializedName("unlock_height")
    val unlockHeight: Long = 0,
    @SerializedName("output_id")
    val outputId: String = "",
    @SerializedName("asset_tag")
    val assetTag: String = "",
    @SerializedName("asset_tag_commitment")
    val assetTagCommitment: String = "",
    @SerializedName("recipient_commitment")
    val recipientCommitment: String = "",
    @SerializedName("stealth_address")
    val stealthAddress: String = "",
    @SerializedName("ephemeral_pubkey")
    val ephemeralPubkey: List<Int> = emptyList(),
    @SerializedName("commitment")
    val commitment: String = "",
    @SerializedName("amount_commitment")
    val amountCommitment: String = "",
    @SerializedName("range_proof")
    val rangeProof: List<Int> = emptyList(),
    @SerializedName("one_time_public_key")
    val oneTimePublicKey: String = "",
    @SerializedName("encrypted_amount")
    val encryptedAmount: List<Int> = emptyList(),
    @SerializedName("commitment_blind")
    val commitmentBlind: List<Int> = emptyList()
)

data class ZokaRawTransaction(
    @SerializedName("sender")
    val sender: String = "",
    @SerializedName("amount")
    val amount: Long = 0,
    @SerializedName("receiver")
    val receiver: String = ""
)

data class ZokaStatsResponse(
    @SerializedName("height")
    val height: Long? = null,
    @SerializedName("difficulty")
    val difficulty: Long? = null,
    @SerializedName("hashrate_hs")
    val hashrateHs: Double? = null,
    @SerializedName("emission_zka")
    val emissionZka: Double? = null,
    @SerializedName("last_block_time")
    val lastBlockTime: Long? = null,
    @SerializedName("network_version")
    val networkVersion: String? = null,
    @SerializedName("connected_peers")
    val connectedPeers: Long? = null,
    @SerializedName("miners_online")
    val minersOnline: Long? = null,
    @SerializedName("remote_miners_online")
    val remoteMinersOnline: Long? = null,
    @SerializedName("local_mining")
    val localMining: Boolean? = null,
    @SerializedName("reported_hashrate_hps")
    val reportedHashrateHps: Double? = null
)

data class ZokaMempoolPendingResponse(
    @SerializedName("count")
    val count: Int = 0,
    @SerializedName("pending")
    val pending: List<ZokaPendingTransactionRef> = emptyList()
)

data class ZokaPendingTransactionRef(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("timestamp")
    val timestamp: Long? = null
)

data class ZokaMempoolMetricsResponse(
    @SerializedName("current_size")
    val currentSize: Long? = null,
    @SerializedName("max_size")
    val maxSize: Long? = null,
    @SerializedName("transactions_added")
    val transactionsAdded: Long? = null,
    @SerializedName("transactions_removed")
    val transactionsRemoved: Long? = null,
    @SerializedName("transactions_evicted")
    val transactionsEvicted: Long? = null,
    @SerializedName("total_fees_collected")
    val totalFeesCollected: Long? = null,
    @SerializedName("average_fee")
    val averageFee: Double? = null,
    @SerializedName("rejections")
    val rejections: ZokaMempoolRejections? = null
)

data class ZokaMempoolRejections(
    @SerializedName("insufficient_fee")
    val insufficientFee: Long? = null,
    @SerializedName("too_many_from_sender")
    val tooManyFromSender: Long? = null,
    @SerializedName("invalid_state")
    val invalidState: Long? = null
)

data class ZokaPrivateTransactionResponse(
    @SerializedName("tx_hash")
    val txHash: String = "",
    @SerializedName("status")
    val status: String = "",
    @SerializedName("nullifier")
    val nullifier: String = "",
    @SerializedName("input_nullifiers")
    val inputNullifiers: List<String> = emptyList(),
    @SerializedName("outputs")
    val outputs: List<ZokaPrivateOutputModel> = emptyList()
)

data class ZokaPrivateOutputModel(
    @SerializedName("ephemeral_pubkey")
    val ephemeralPubkey: List<Int> = emptyList(),
    @SerializedName("one_time_public_key")
    val oneTimePublicKey: String = "",
    @SerializedName("commitment")
    val commitment: String = "",
    @SerializedName("encrypted_amount")
    val encryptedAmount: List<Int> = emptyList(),
    @SerializedName("output_id")
    val outputId: String = "",
    @SerializedName("is_change")
    val isChange: Boolean = false
)


/**
 * Lo que volvió de un pedido de cuerpos en lote.
 *
 * `missing` no es lo mismo que "no vino": son los cuerpos que los pares dijeron
 * explícitamente que no tienen. Esa diferencia es la que permite dejar de
 * pedirlos sin confundir una pérdida real con un problema de red.
 */
data class PrivateTxBatchResult(
    val bodies: Map<String, ZokaPrivateTransactionResponse>,
    val missing: List<String>
)
