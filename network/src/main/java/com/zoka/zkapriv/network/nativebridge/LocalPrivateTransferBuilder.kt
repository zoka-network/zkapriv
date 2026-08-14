package com.zoka.zkapriv.network.nativebridge

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zoka.zkapriv.network.api.ZokaNodeApi
import com.zoka.zkapriv.security.storage.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalPrivateTransferBuilder @Inject constructor(
    private val api: ZokaNodeApi,
    @ApplicationContext private val context: Context,
    secureStorage: SecureStorage,
    private val proverClient: LocalProverClient
) {
    private val gson = Gson()
    private val provingMutex = Mutex()
    private val chainScanMutex = Mutex()
    private val chainScanner = IncrementalPrivateChainScanner(
        blockDigestsRange = api::getBlockDigestsRange,
        rawBlocksRange = api::getRawBlocksRange,
        privateTransactionRaw = api::getPrivateTransactionRaw,
        snapshotStore = EncryptedPrivateChainSnapshotStore(secureStorage)
    )

    data class BuiltTransfer(
        val hash: String,
        val body: JsonObject,
        val metrics: JsonObject?
    )

    suspend fun preload(): JsonObject = withContext(Dispatchers.Default) {
        provingMutex.withLock {
            val response = gson.fromJson(
                proverClient.preload(trustedSetupDirectory().absolutePath),
                JsonObject::class.java
            )
            response.get("error")?.asString?.let { throw IllegalStateException(it) }
            response
        }
    }

    suspend fun build(
        from: String,
        to: String,
        amountAtoms: Long,
        feeAtoms: Long,
        seedHex: String,
        pendingBodies: List<String>,
        privateProofVersion: Int = 1,
        privateProofProfile: String = "private_tx"
    ): BuiltTransfer {
        val chainState = withContext(Dispatchers.IO) {
            val height = api.getChainHeight().height
            chainScanMutex.withLock {
                chainScanner.refresh(from, height)
            }
        }
        val pending = pendingBodies.mapNotNull { raw ->
            runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
        }
        val request = JsonObject().apply {
            addProperty("seed_hex", seedHex.removePrefix("0x"))
            addProperty("to", to)
            addProperty("amount_atoms", amountAtoms)
            addProperty("fee_atoms", feeAtoms)
            addProperty("private_proof_version", privateProofVersion)
            addProperty("private_proof_profile", privateProofProfile)
            addProperty("current_height", chainState.height)
            addProperty("trusted_setup_dir", trustedSetupDirectory().absolutePath)
            add("coinbase_outputs", gson.toJsonTree(chainState.coinbases))
            add("confirmed_private_txs", gson.toJsonTree(chainState.confirmedBodies))
            add("pending_private_txs", gson.toJsonTree(pending))
        }

        val requestJson = gson.toJson(request)
        val responseJson = withContext(Dispatchers.Default) {
            provingMutex.withLock {
                proverClient.build(requestJson)
            }
        }
        val response = gson.fromJson(responseJson, JsonObject::class.java)
        response.get("error")?.asString?.let { throw IllegalStateException(it) }
        val hash = response.get("hash")?.asString.orEmpty()
        val body = response.getAsJsonObject("tx")
            ?: throw IllegalStateException("Local Zoka engine returned no transaction body")
        val metrics = response.getAsJsonObject("metrics")
        check(hash.isNotBlank()) { "Local Zoka engine returned no transaction hash" }
        return BuiltTransfer(hash, body, metrics)
    }

    private fun trustedSetupDirectory(): File {
        val out = File(context.filesDir, "trusted-setup")
        val optionalNames = arrayOf("private_tx_v2_i1.params")
            .filter { assetExists("trusted-setup/$it") }
        val names = arrayOf(
            "balance.params",
            "merkle.params",
            "nullifier.params",
            "private_tx.params",
            "DIGEST"
        ) + optionalNames
        check(out.mkdirs() || out.isDirectory) {
            "Could not create trusted setup directory"
        }
        val assetDigest = context.assets.open("trusted-setup/DIGEST").bufferedReader().use {
            it.readText().trim()
        }
        val localDigest = File(out, "DIGEST").takeIf { it.isFile }?.readText()?.trim()
        val staleOptionalNames = arrayOf("private_tx_v2_i1.params")
            .filterNot { it in optionalNames }
        if (
            localDigest == assetDigest &&
            names.all { File(out, it).isFile } &&
            staleOptionalNames.none { File(out, it).exists() }
        ) return out

        for (name in names) {
            val destination = File(out, name)
            copyAssetAtomically("trusted-setup/$name", destination)
        }
        staleOptionalNames.forEach { File(out, it).delete() }
        return out
    }

    private fun assetExists(assetName: String): Boolean =
        runCatching { context.assets.open(assetName).use { } }.isSuccess

    private fun copyAssetAtomically(assetName: String, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        context.assets.open(assetName).use { source ->
            temporary.outputStream().use { target ->
                source.copyTo(target)
                target.flush()
            }
        }
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}
