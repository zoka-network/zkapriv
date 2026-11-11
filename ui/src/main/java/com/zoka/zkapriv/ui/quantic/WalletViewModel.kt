package com.zoka.zkapriv.ui.quantic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.core.preferences.ThemeManager
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.network.quantic.QuanticWallet
import com.zoka.zkapriv.network.quantic.ViewKeyScope
import com.zoka.zkapriv.security.vault.QuanticVault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class SendState(
    val running: Boolean = false,
    val phase: Int = 0,
    val startedAt: Long = 0,
    val amountAtoms: Long = 0,
    val result: JSONObject? = null,
    val error: String? = null,
)

/**
 * Estado y acciones de la billetera para todas las pantallas de ZKAPriv 3.
 * Vive lo que vive la actividad; lo persistente está en [QuanticVault] y en
 * el estado cifrado que maneja Rust.
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val wallet: QuanticWallet,
    private val vault: QuanticVault,
    private val themeManager: ThemeManager,
) : ViewModel() {

    val state: StateFlow<QuanticState> = wallet.state

    val themeMode: StateFlow<ThemeMode> =
        themeManager.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    val animations: StateFlow<Boolean> =
        themeManager.animations.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { themeManager.setThemeMode(mode) }
    fun setAnimations(enabled: Boolean) = viewModelScope.launch { themeManager.setAnimations(enabled) }

    fun hasWallet(): Boolean = vault.hasWallet()

    // ── Onboarding ───────────────────────────────────────────────────────────

    /**
     * La frase en curso de onboarding: solo en memoria hasta que el usuario
     * la verificó (billetera nueva) o la escribió (restaurada).
     */
    var draftPhrase: String? = null
        private set

    fun startNewWallet(): List<String> {
        val phrase = wallet.newPhrase()
        draftPhrase = phrase
        return phrase.split(" ")
    }

    fun validPhrase(phrase: String): Boolean = wallet.validPhrase(normalize(phrase))

    fun useRestoredPhrase(phrase: String) {
        draftPhrase = normalize(phrase)
    }

    /** Guarda la frase en el almacén: desde acá existe la billetera (falta el PIN). */
    fun commitPhrase() {
        val phrase = draftPhrase ?: return
        wallet.store(phrase)
        draftPhrase = null
    }

    fun setPin(pin: String) = vault.setPin(pin)

    fun finishOnboarding(biometric: Boolean) {
        vault.biometricEnabled = biometric
        vault.markReady()
    }

    private fun normalize(phrase: String) =
        phrase.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    /** Descarta una frase en curso (se salió del alta de una billetera más). */
    fun clearDraft() { draftPhrase = null }

    // ── Billeteras ───────────────────────────────────────────────────────────

    private val _wallets = MutableStateFlow(wallet.wallets())
    val wallets: StateFlow<List<QuanticVault.WalletEntry>> = _wallets.asStateFlow()
    private val _activeWallet = MutableStateFlow(wallet.activeWalletId())
    val activeWallet: StateFlow<String> = _activeWallet.asStateFlow()

    private fun refreshWallets() {
        _wallets.value = wallet.wallets()
        _activeWallet.value = wallet.activeWalletId()
    }

    fun nextWalletName(): String = "Wallet ${_wallets.value.size + 1}"

    /** Pasa a otra billetera. Mientras un pago se arma, no. */
    fun switchWallet(id: String, onDone: () -> Unit = {}) {
        if (_sendState.value.running || id == wallet.activeWalletId()) {
            onDone()
            return
        }
        viewModelScope.launch {
            stopSync()
            try {
                wallet.switchTo(id)
            } finally {
                _sendState.value = SendState()
                refreshWallets()
                startSync()
                onDone()
            }
        }
    }

    /** Agrega la billetera en curso (nueva y verificada, o restaurada) y pasa a ella. */
    fun addDraftWallet(name: String, onDone: (Result<Unit>) -> Unit) {
        val phrase = draftPhrase ?: return onDone(Result.failure(IllegalStateException("There is no phrase to add")))
        if (_sendState.value.running) {
            return onDone(Result.failure(IllegalStateException("Wait until your payment finishes")))
        }
        viewModelScope.launch {
            stopSync()
            val result = runCatching { wallet.addWallet(name, phrase) }
            if (result.isSuccess) {
                draftPhrase = null
                _sendState.value = SendState()
            }
            refreshWallets()
            startSync()
            onDone(result.map { })
        }
    }

    fun renameWallet(id: String, name: String): Result<Unit> =
        runCatching { wallet.renameWallet(id, name) }.also { refreshWallets() }

    /** Saca una billetera del teléfono. Solo sus 24 palabras la devuelven. */
    fun removeWallet(id: String, onDone: (Result<Unit>) -> Unit) {
        if (_sendState.value.running) {
            return onDone(Result.failure(IllegalStateException("Wait until your payment finishes")))
        }
        viewModelScope.launch {
            stopSync()
            val result = runCatching { wallet.removeWallet(id) }
            refreshWallets()
            startSync()
            onDone(result)
        }
    }

    // ── Bloqueo ──────────────────────────────────────────────────────────────

    fun biometricEnabled(): Boolean = vault.biometricEnabled
    fun setBiometric(enabled: Boolean) {
        vault.biometricEnabled = enabled
    }

    fun checkPin(pin: String): QuanticVault.PinCheck = vault.checkPin(pin)
    fun pinWaitMillis(): Long = vault.pinWaitMillis()

    // ── Cadena ───────────────────────────────────────────────────────────────

    private val syncLoop = WalletSyncLoop(viewModelScope, { wallet.sync() }) {
        when {
            state.value.error != null -> 15_000L
            !state.value.inSync -> 1_000L
            state.value.pendingTxid != null -> 4_000L
            else -> 10_000L
        }
    }
    private var publishJob: Job? = null
    private var loadJob: Job? = null

    /** Actualiza mientras está abierta y al volver, sin acumular pedidos. */
    fun startSync() {
        if (!vault.hasWallet()) return
        if (!state.value.loaded && loadJob?.isActive != true) {
            loadJob = viewModelScope.launch { wallet.load() }
        }
        syncLoop.start()
        syncLoop.request()
        if (publishJob?.isActive != true) publishJob = viewModelScope.launch { publishUntilKnown() }
    }

    /**
     * Publica la dirección corta en una semilla. ZSilent paga a una corta solo
     * si su nodo la tiene en el directorio; si no, la API responde 404.
     */
    private suspend fun publishUntilKnown() {
        while (currentCoroutineContext().isActive && !vault.addressPublished) {
            val accepted = publishAddress().getOrNull() == true
            if (!accepted) delay(15_000)
        }
    }

    fun stopSync() {
        syncLoop.stop()
        publishJob?.cancel()
        publishJob = null
    }

    fun syncNow() { syncLoop.request() }

    // ── Direcciones ──────────────────────────────────────────────────────────

    fun shortAddress(): String = wallet.shortAddress()
    fun fullAddress(): String = wallet.fullAddress()
    suspend fun viewKey(expectedWalletId: String, scope: ViewKeyScope = ViewKeyScope.INCOMING): String =
        wallet.viewKey(expectedWalletId, scope)
    fun phrase(): String? = vault.phrase()

    suspend fun publishAddress(): Result<Boolean> = try {
        Result.success(wallet.publishAddress())
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (e: Exception) { Result.failure(e) }

    // ── Enviar ───────────────────────────────────────────────────────────────

    fun checkAddress(text: String): JSONObject = wallet.checkAddress(text)
    suspend fun quote(amountAtoms: Long): JSONObject = wallet.quote(amountAtoms)
    /**
     * El envío corre en el alcance del ViewModel: si la pantalla se va (se
     * bloquea el teléfono mientras prueba), el pago sigue y su resultado no
     * se pierde.
     */
    private val _sendState = MutableStateFlow(SendState())
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    fun startSend(to: String, amountAtoms: Long, memo: String) {
        if (_sendState.value.running) return
        _sendState.value = SendState(running = true, startedAt = System.currentTimeMillis(), amountAtoms = amountAtoms)
        viewModelScope.launch {
            val phaseJob = launch {
                while (isActive) {
                    val phase = wallet.sendPhase()
                    _sendState.value = _sendState.value.copy(phase = phase)
                    delay(250)
                }
            }
            try {
                val result = wallet.send(to, amountAtoms, memo)
                _sendState.value = _sendState.value.copy(running = false, result = result)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                _sendState.value = _sendState.value.copy(running = false,
                    error = when {
                        state.value.pendingTxid != null -> "The network response was interrupted. Your payment may have been accepted; funds stay reserved until confirmation or expiry."
                        e.message.orEmpty().contains("direccion", ignoreCase = true) -> "Could not resolve the recipient. Check the address or ask for their full address."
                        else -> "Could not complete the payment. Check your connection and available balance, then try again."
                    })
            } finally {
                phaseJob.cancel()
                syncNow()
            }
        }
    }

    fun clearSendResult() { if (!_sendState.value.running) _sendState.value = SendState() }

    // ── Ajustes ──────────────────────────────────────────────────────────────

    fun torMode(): Boolean = wallet.torMode()
    fun setTorMode(enabled: Boolean) {
        wallet.setTorMode(enabled)
        syncNow()
    }

    /** Borra la billetera v3 del teléfono. La frase es lo único que la recupera. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        check(!_sendState.value.running) { "Wait until your payment finishes before removing the wallet" }
        stopSync()
        vault.wipe()
        wallet.forget()
        refreshWallets()
    }

    override fun onCleared() {
        stopSync()
    }

}
