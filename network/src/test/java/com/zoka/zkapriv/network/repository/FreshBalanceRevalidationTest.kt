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
    fun reorganizedTipReturnsFailClosedWithoutCachingOrPendingReconciliation() = runOnRepositoryIo {
        // Una reorganizacion de verdad: el bloque donde termino el escaneo ya
        // no es el que esta en esa altura. Lo acumulado puede hablar de una
        // rama que dejo de existir, asi que no se gasta sobre eso.
        //
        // Se distingue de que la cadena crezca por el ancla: aca la altura
        // escaneada devuelve OTRO hash, no el que vimos.
        mockStatic(Log::class.java).use {
                val fixture = fixture(withPendingReservation = true)
                `when`(fixture.api.getChainHeight()).thenReturn(
                    ZokaChainHeightResponse(1L),
                    ZokaChainHeightResponse(2L)
                )
                `when`(fixture.api.getBlockDigestsRange(1L, 1L))
                    .thenReturn(range(1L, "old-tip"), range(1L, "reorganized-tip"))
                `when`(fixture.api.getBlockDigestsRange(2L, 2L))
                    .thenReturn(range(2L, "new-tip", prevHash = "reorganized-tip"))

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
        }
    }

    @Test
    fun aChainThatGrewDuringTheScanIsCaughtUpInsteadOfBlockingSend() = runOnRepositoryIo {
        // Que la cadena crezca mientras se escanea no es un fallo: es lo que
        // hace una cadena viva. Esta red produce un bloque cada dos minutos y
        // un escaneo sobre Tor tarda, asi que exigir terminar exactamente en la
        // punta dejaba el saldo sin verificar SIEMPRE y Enviar nunca se
        // habilitaba, aunque no hubiera nada mal.
        //
        // El ancla distingue los dos casos: aca la altura escaneada sigue
        // devolviendo el mismo hash, o sea que estamos en la rama buena, solo
        // atras. Eso se alcanza escaneando los bloques nuevos.
        mockStatic(Log::class.java).use {
                val fixture = fixture()
                `when`(fixture.api.getChainHeight()).thenReturn(
                    ZokaChainHeightResponse(1L),
                    ZokaChainHeightResponse(2L),
                    ZokaChainHeightResponse(2L),
                    ZokaChainHeightResponse(2L)
                )
                `when`(fixture.api.getBlockDigestsRange(1L, 1L))
                    .thenReturn(range(1L, "tip-1"))
                `when`(fixture.api.getBlockDigestsRange(2L, 2L))
                    .thenReturn(range(2L, "tip-2", prevHash = "tip-1"))
                `when`(fixture.api.getBlockDigestsRange(1L, 2L))
                    .thenReturn(
                        ZokaRawBlocksRangeResponse(
                            fromHeight = 1L,
                            toHeight = 2L,
                            blocks = listOf(
                                block(1L, "tip-1"),
                                block(2L, "tip-2", prevHash = "tip-1")
                            )
                        )
                    )

                val result = fixture.repository.getAddressBalance(
                    ADDRESS,
                    SCAN_KEY,
                    restoreHeight = 0L
                )
                val balance = (result as Result.Success).data

                assertTrue(
                    "un crecimiento normal de la cadena no puede bloquear el envio",
                    balance.isVerified
                )
                assertEquals(2L, balance.scannedHeight)
                assertEquals("tip-2", balance.verifiedTipHash)
                assertNull(balance.verificationIssue)
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
            ).also {
                // Sin ambito propio: el escaneo corre en el hilo del test.
                //
                // En produccion el escaneo salta a un ambito del repositorio para
                // sobrevivir a que la pantalla se cierre. Aca eso rompe los mocks
                // estaticos de `android.util.Log`, que son por hilo, y el fallo
                // no tiene nada que ver con lo que se esta probando.
                it.ambitoDeEscaneo = null
            }
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

    private fun block(
        height: Long,
        hash: String,
        prevHash: String = ""
    ) = ZokaRawBlockResponse(
        header = ZokaRawBlockHeader(
            blockHeight = height,
            hash = hash,
            prevHash = prevHash,
            timestamp = 1L
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
