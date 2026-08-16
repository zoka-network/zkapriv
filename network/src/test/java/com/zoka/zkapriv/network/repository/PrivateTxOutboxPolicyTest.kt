package com.zoka.zkapriv.network.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.network.api.ZokaNodeApi
import com.zoka.zkapriv.network.config.RpcSettings
import com.zoka.zkapriv.network.nativebridge.LocalProverClient
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.storage.PrivateTxOutboxRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class PrivateTxOutboxPolicyTest {
    @Test
    fun identicalRequestSelectsTheExistingBodyInsteadOfBuildingAgain() {
        val requestKey = privateSendRequestKey("alice", "bob", 1_000L, 10L)
        val existing = record(hash = "same-hash", requestKey = requestKey)

        assertEquals(existing, matchingPrivateTxOutbox(listOf(existing), requestKey))
        assertEquals(requestKey, privateSendRequestKey("alice", "bob", 1_000L, 10L))
    }

    @Test
    fun requestKeySeparatesEverySpendIntentField() {
        val original = privateSendRequestKey("alice", "bob", 1_000L, 10L)

        assertNotEquals(original, privateSendRequestKey("carol", "bob", 1_000L, 10L))
        assertNotEquals(original, privateSendRequestKey("alice", "carol", 1_000L, 10L))
        assertNotEquals(original, privateSendRequestKey("alice", "bob", 2_000L, 10L))
        assertNotEquals(original, privateSendRequestKey("alice", "bob", 1_000L, 20L))
        assertNull(matchingPrivateTxOutbox(listOf(record("old", "other-key")), original))
    }

    @Test
    fun duplicateBodiesForOneRequestFailClosed() {
        val requestKey = privateSendRequestKey("alice", "bob", 1_000L, 10L)

        assertThrows(IllegalStateException::class.java) {
            matchingPrivateTxOutbox(
                listOf(record("first", requestKey), record("second", requestKey)),
                requestKey
            )
        }
    }

    @Test
    fun balanceVerificationDefaultsFailClosedAndSurvivesPendingOverlayCopy() {
        val fallback = ZokaRepository.BalanceInfo(1_000L, 0L)
        val verified = fallback.copy(
            scannedHeight = 50L,
            isVerified = true,
            verifiedTipHash = "tip-50"
        )

        assertFalse(fallback.isVerified)
        assertTrue(verified.copy(pendingOutAtoms = 100L).isVerified)
        assertTrue(verified.isVerifiedAtTip(50L, "tip-50"))
        assertFalse(verified.isVerifiedAtTip(51L, "tip-50"))
        assertFalse(verified.isVerifiedAtTip(50L, "other-branch"))
    }

    /**
     * Un cuerpo que falta no puede inflar el saldo, asi que no debe bloquear el
     * envio.
     *
     * Los gastos se detectan por nulificador y los nulificadores llegan
     * commiteados en el bloque, sin pedir cuerpos. Un cuerpo perdido solo puede
     * ocultar dinero ENTRANTE: deja el saldo por debajo del real, nunca por
     * encima. Gastar de menos es seguro.
     *
     * La version anterior exigia cero cuerpos faltantes y dejaba la wallet sin
     * poder enviar para siempre, porque doce de esas transacciones ya no existen
     * en ningun nodo vivo. Esta prueba existe para que ese endurecimiento no
     * vuelva "por las dudas".
     */
    @Test
    fun missingBodiesWarnButDoNotBlockSpendingBecauseTheyCanOnlyUndercountFunds() {
        val conCuerposPerdidos = ZokaRepository.BalanceInfo(
            1_000L,
            0L,
            scannedHeight = 50L,
            isVerified = true,
            verifiedTipHash = "tip-50",
            missingTxCount = 12
        )

        assertTrue(
            "doce cuerpos irrecuperables no deben impedir gastar lo que si conocemos",
            conCuerposPerdidos.isVerifiedAtTip(50L, "tip-50")
        )
    }

    /**
     * Lo que si tiene que bloquear: un saldo que no se pudo anclar a una punta
     * de cadena. Ahi no sabemos de cuando es, y podria estar contando notas ya
     * gastadas.
     */
    @Test
    fun aBalanceWithNoChainAnchorStillFailsClosed() {
        val sinAncla = ZokaRepository.BalanceInfo(
            1_000L,
            0L,
            scannedHeight = 50L,
            isVerified = false,
            verifiedTipHash = ""
        )

        assertFalse(sinAncla.isVerifiedAtTip(50L, "tip-50"))
    }

    @Test
    fun repositoryRetryPublishesStoredBodyAndNeverInvokesTheProver() {
        mockStatic(Log::class.java).use {
            runBlocking {
        val from = "zka1" + "a".repeat(40)
        val to = "zka1" + "b".repeat(40)
        val amountAtoms = 1_000L
        val feeAtoms = 10L
        val hash = "same-hash"
        val body = JsonObject().apply { addProperty("tx_hash", hash) }
        val stored = PrivateTxOutboxRecord(
            hash = hash,
            address = from,
            reservedAtoms = amountAtoms + feeAtoms,
            requestKey = privateSendRequestKey(from, to, amountAtoms, feeAtoms),
            bodyJson = body.toString()
        )
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
        `when`(secureStorage.readPrivateTxOutbox(from)).thenReturn(listOf(stored))
        `when`(rpcSettings.readPendingSends(from)).thenReturn(
            listOf(RpcSettings.PendingSend(hash, amountAtoms + feeAtoms, 1L))
        )
        `when`(api.submitPrivateTx(body)).thenReturn(
            JsonObject().apply { addProperty("hash", hash) }
        )
        val repository = ZokaRepository(
            zokaNodeApi = api,
            sharedPreferences = preferences,
            rpcSettings = rpcSettings,
            secureStorage = secureStorage,
            localProverClient = prover,
            context = context
        )

        val result = repository.sendTransaction(
            from = from,
            to = to,
            amount = "0.001",
            fee = "0.00001",
            scanKeyHex = "00".repeat(16),
            publicKey = ""
        )

        assertEquals(Result.Success(hash), result)
        verify(api).submitPrivateTx(body)
        verify(secureStorage).readPrivateTxOutbox(from)
        verifyNoMoreInteractions(secureStorage)
        verifyNoInteractions(prover)
            }
        }
    }

    private fun record(hash: String, requestKey: String) = PrivateTxOutboxRecord(
        hash = hash,
        address = "alice",
        reservedAtoms = 1_010L,
        requestKey = requestKey,
        bodyJson = """{"tx_hash":"$hash"}"""
    )
}
