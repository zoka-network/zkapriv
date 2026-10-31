package com.zoka.zkapriv.ui.quantic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.core.preferences.ThemeManager
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.network.quantic.QuanticWallet
import com.zoka.zkapriv.security.vault.QuanticVault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

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

    // ── Bloqueo ──────────────────────────────────────────────────────────────

    fun biometricEnabled(): Boolean = vault.biometricEnabled
    fun setBiometric(enabled: Boolean) {
        vault.biometricEnabled = enabled
    }

    fun checkPin(pin: String): QuanticVault.PinCheck = vault.checkPin(pin)
    fun pinWaitMillis(): Long = vault.pinWaitMillis()

    // ── Cadena ───────────────────────────────────────────────────────────────

    private var syncLoop: Job? = null

    /** Lee la cadena mientras la app está a la vista: cada minuto al día, seguido si falta. */
    fun startSync() {
        if (syncLoop?.isActive == true || !vault.hasWallet()) return
        syncLoop = viewModelScope.launch {
            wallet.load()
            while (isActive) {
                wallet.sync()
                val current = state.value
                delay(if (current.inSync && current.error == null) 60_000 else 15_000)
            }
        }
    }

    fun stopSync() {
        syncLoop?.cancel()
        syncLoop = null
    }

    fun syncNow() = viewModelScope.launch { wallet.sync() }

    // ── Direcciones ──────────────────────────────────────────────────────────

    fun shortAddress(): String = wallet.shortAddress()
    fun fullAddress(): String = wallet.fullAddress()
    fun viewKey(): String = wallet.viewKey()
    fun phrase(): String? = vault.phrase()

    suspend fun publishAddress(): Result<Boolean> = runCatching { wallet.publishAddress() }

    // ── Enviar ───────────────────────────────────────────────────────────────

    fun checkAddress(text: String): JSONObject = wallet.checkAddress(text)
    suspend fun quote(amountAtoms: Long): JSONObject = wallet.quote(amountAtoms)
    /**
     * El envío corre en el alcance del ViewModel: si la pantalla se va (se
     * bloquea el teléfono mientras prueba), el pago sigue y su resultado no
     * se pierde.
     */
    fun startSend(to: String, amountAtoms: Long, memo: String): Deferred<JSONObject> =
        viewModelScope.async { wallet.send(to, amountAtoms, memo) }

    // ── Ajustes ──────────────────────────────────────────────────────────────

    fun torMode(): Boolean = wallet.torMode()
    fun setTorMode(enabled: Boolean) {
        wallet.setTorMode(enabled)
        syncNow()
    }

    /** Borra la billetera v3 del teléfono. La frase es lo único que la recupera. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        stopSync()
        vault.wipe()
        wallet.forget()
    }

    override fun onCleared() {
        stopSync()
    }
}
