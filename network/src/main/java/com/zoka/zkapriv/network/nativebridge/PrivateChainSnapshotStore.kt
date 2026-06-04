package com.zoka.zkapriv.network.nativebridge

import com.zoka.zkapriv.security.storage.SecureStorage

internal interface PrivateChainSnapshotStore {
    fun read(): String?
    fun write(snapshotJson: String)
    fun clear()
}

internal object NoOpPrivateChainSnapshotStore : PrivateChainSnapshotStore {
    override fun read(): String? = null

    override fun write(snapshotJson: String) = Unit

    override fun clear() = Unit
}

internal class EncryptedPrivateChainSnapshotStore(
    private val secureStorage: SecureStorage
) : PrivateChainSnapshotStore {
    override fun read(): String? = secureStorage.getLocalPrivateChainSnapshot()

    override fun write(snapshotJson: String) {
        secureStorage.storeLocalPrivateChainSnapshot(snapshotJson)
    }

    override fun clear() {
        secureStorage.clearLocalPrivateChainSnapshot()
    }
}
