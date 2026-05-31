package com.zoka.zkapriv.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.security.biometric.BiometricManager
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.ui.reset.WalletResetCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val biometricManager: BiometricManager,
    private val walletResetCoordinator: WalletResetCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockState())
    val uiState: StateFlow<LockState> = _uiState

    init {
        _uiState.update { 
            it.copy(biometricAvailable = secureStorage.isBiometricEnabled())
        }
    }

    fun verifyPin(pin: String) {
        val storedPin = secureStorage.getPin()
        if (storedPin == pin) {
            _uiState.update { it.copy(isUnlocked = true, error = null) }
        } else {
            _uiState.update { it.copy(error = "Incorrect PIN", isUnlocked = false) }
        }
    }

    fun authenticateWithBiometric() {
        _uiState.update { it.copy(showBiometricPrompt = true, isLoading = true) }
    }

    fun resetBiometricPrompt() {
        _uiState.update { it.copy(showBiometricPrompt = false) }
    }

    fun onBiometricSuccess() {
        _uiState.update { it.copy(error = null, showBiometricPrompt = false, isLoading = false, isUnlocked = true) }
    }

    fun onBiometricError(error: String) {
        _uiState.update { it.copy(error = error, isUnlocked = false, showBiometricPrompt = false, isLoading = false) }
    }

    fun resetWallet() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                walletResetCoordinator.resetLocalWalletState()
                _uiState.update {
                    it.copy(
                        walletReset = true,
                        showForgotPasswordDialog = false,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Reset failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun showForgotPasswordDialog(show: Boolean) {
        _uiState.update { it.copy(showForgotPasswordDialog = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class LockState(
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val error: String? = null,
    val showForgotPasswordDialog: Boolean = false,
    val walletReset: Boolean = false,
    val isLoading: Boolean = false
)
