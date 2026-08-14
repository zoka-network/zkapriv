package com.zoka.zkapriv.network.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.network.api.ZokaNodeApi
import com.zoka.zkapriv.network.config.RpcSettings
import com.zoka.zkapriv.network.model.ZokaChainHeightResponse
import com.zoka.zkapriv.network.model.ZokaRawBlockHeader
import com.zoka.zkapriv.network.model.ZokaRawBlockResponse
import com.zoka.zkapriv.network.model.ZokaRawBlocksRangeResponse
import com.zoka.zkapriv.network.nativebridge.LocalProverClient
import com.zoka.zkapriv.security.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class FreshBalanceRevalidationTest {
    @Test
    fun freshScanIsVerifiedOnlyAfterTheSameTipIsObservedAgain() = runOnRepositoryIo {
        mockStatic(Log::class.java).use {
                val fixture = fixture()
                `when`(fixture.api.getChainHeight()).thenReturn(
                    ZokaChainHeightResponse(1L),
                    ZokaChainHeightResponse(1L)
                )
                `when`(fixture.api.getBlockDigestsRange(1L, 1L)).thenReturn(
                    range(1L, "tip-1"),
                    range(1L, "tip-1")
                )

                val result = fixture.repository.getAddressBalance(
                    ADDRESS,
                    SCAN_KEY,
                    restoreHeight = 0L
                )
                val balance = (result as Result.Success).data

                assertTrue(balance.isVerified)
                assertEquals(1L, balance.scannedHeight)
                assertEquals("tip-1", balance.verifiedTipHash)
                assertNull(balance.verificationIssue)
                verify(fixture.api, times(2)).getChainHeight()
                verify(fixture.api, times(2)).getBlockDigestsRange(1L, 1L)
                assertTrue(wasInvoked(fixture.rpcSettings, "saveBalanceCache"))
        }
    }

    @Test
    fun changedTipReturnsFailClosedWithoutCachingOrPendingReconciliation() = runOnRepositoryIo {
        mockStatic(Log::class.java).use {
                val fixture = fixture(withPendingReservation = true)
                `when`(fixture.api.getChainHeight()).thenReturn(
                    ZokaChainHeightResponse(1L),
                    ZokaChainHeightResponse(2L)
                )
                `when`(fixture.api.getBlockDigestsRange(1L, 1L))
                    .thenReturn(range(1L, "old-tip"))
                `when`(fixture.api.getBlockDigestsRange(2L, 2L))
                    .thenReturn(range(2L, "new-tip", prevHash = "old-tip"))

                val result = fixture.repository.getAddressBalance(
                    ADDRESS,
                    SCAN_KEY,
                    restoreHeight = 0L
                )
                val balance = (result as Result.Success).data

                assertFalse(balance.isVerified)
                assertTrue(balance.verificationIssue.orEmpty().contains("tip changed"))
                assertEquals(RESERVED_ATOMS, balance.pendingOutAtoms)
                assertFalse(wasInvoked(fixture.rpcSettings, "saveBalanceCache"))
                assertFalse(wasInvoked(fixture.secureStorage, "removePrivateTxOutbox"))
                verify(fixture.api, times(2)).getChainHeight()
                verify(fixture.api).getBlockDigestsRange(1L, 1L)
                verify(fixture.api).getBlockDigestsRange(2L, 2L)
                verifyNoMoreInteractions(fixture.api)
        }
    }

    @Test
    fun unavailableFinalTipReturnsFailClosedWithoutCachingOrPendingReconciliation() = runOnRepositoryIo {
        mockStatic(Log::class.java).use {
                val fixture = fixture(withPendingReservation = true)
                `when`(fixture.api.getChainHeight())
                    .thenReturn(ZokaChainHeightResponse(1L))
                    .thenThrow(IllegalStateException("network unavailable"))
                `when`(fixture.api.getBlockDigestsRange(1L, 1L))
                    .thenReturn(range(1L, "tip-1"))

                val result = fixture.repository.getAddressBalance(
                    ADDRESS,
                    SCAN_KEY,
                    restoreHeight = 0L
                )
                val balance = (result as Result.Success).data

                assertFalse(balance.isVerified)
                assertTrue(balance.verificationIssue.orEmpty().contains("could not be revalidated"))
                assertEquals(RESERVED_ATOMS, balance.pendingOutAtoms)
                assertFalse(wasInvoked(fixture.rpcSettings, "saveBalanceCache"))
                assertFalse(wasInvoked(fixture.secureStorage, "removePrivateTxOutbox"))
                verify(fixture.api, times(2)).getChainHeight()
                verify(fixture.api).getBlockDigestsRange(1L, 1L)
                verifyNoMoreInteractions(fixture.api)
        }
    }

    private fun runOnRepositoryIo(block: suspend () -> Unit) = runBlocking {
        // getAddressBalance deliberately switches to Dispatchers.IO. Create the
        // thread-local static Android Log mock on that dispatcher as well.
        withContext(Dispatchers.IO) { block() }
    }

    private fun fixture(withPendingReservation: Boolean = false): Fixture {
        val api = mock(ZokaNodeApi::class.java)
        val preferences = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val rpcSettings = mock(RpcSettings::class.java)
        val secureStorage = mock(SecureStorage::class.java)
        val prover = mock(LocalProverClient::class.java)
        val context = mock(Context::class.java)

        `when`(preferences.all).thenReturn(emptyMap())
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(secureStorage.readPrivateTxOutbox(ADDRESS)).thenReturn(emptyList())
        val pendingSends = if (withPendingReservation) {
            listOf(RpcSettings.PendingSend("pending-hash", RESERVED_ATOMS, 1L))
        } else {
            emptyList()
        }
        `when`(rpcSettings.readPendingSends(ADDRESS)).thenReturn(pendingSends)
        `when`(rpcSettings.expirePendingSends(ADDRESS)).thenReturn(pendingSends)

        return Fixture(
            api = api,
            rpcSettings = rpcSettings,
            secureStorage = secureStorage,
            repository = ZokaRepository(
                zokaNodeApi = api,
                sharedPreferences = preferences,
                rpcSettings = rpcSettings,
                secureStorage = secureStorage,
                localProverClient = prover,
                context = context
            )
        )
    }

    private fun range(
        height: Long,
        hash: String,
        prevHash: String = ""
    ) = ZokaRawBlocksRangeResponse(
        fromHeight = height,
        toHeight = height,
        blocks = listOf(
            ZokaRawBlockResponse(
                header = ZokaRawBlockHeader(
                    blockHeight = height,
                    hash = hash,
                    prevHash = prevHash,
                    timestamp = 1L
                )
            )
        )
    )

    private fun wasInvoked(mock: Any, methodName: String): Boolean =
        mockingDetails(mock).invocations.any { invocation ->
            invocation.method.name == methodName
        }

    private data class Fixture(
        val api: ZokaNodeApi,
        val rpcSettings: RpcSettings,
        val secureStorage: SecureStorage,
        val repository: ZokaRepository
    )

    private companion object {
        const val ADDRESS = "zka1freshbalance0000000000000000000000000000"
        const val RESERVED_ATOMS = 777L
        val SCAN_KEY = ByteArray(32) { index -> index.toByte() }
    }
}
