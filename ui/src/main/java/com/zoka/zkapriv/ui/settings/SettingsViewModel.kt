package com.zoka.zkapriv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.wallet.AccountManager
import com.zoka.zkapriv.core.preferences.ThemeManager
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.ui.reset.WalletResetCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val themeManager: ThemeManager,
    private val walletManager: com.zoka.zkapriv.security.wallet.WalletManager,
    private val zokaRepository: com.zoka.zkapriv.network.repository.ZokaRepository,
    private val accountManager: AccountManager,
    private val rpcSettings: com.zoka.zkapriv.network.config.RpcSettings,
    private val walletResetCoordinator: WalletResetCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState

    init {
        loadSettings()
        observeActiveAccount()
        loadRpcEndpoint()
    }

    private fun loadRpcEndpoint() {
        _uiState.update { it.copy(
            rpcEndpoint = rpcSettings.getEndpoint(),
            isCustomRpc = rpcSettings.isCustomEndpoint()
        ) }
    }

    fun setRpcEndpoint(url: String?) {
        val trimmed = url?.trim()
        if (!trimmed.isNullOrBlank() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            _uiState.update { it.copy(error = "RPC URL must start with http:// or https://") }
            return
        }
        rpcSettings.setEndpoint(trimmed)
        zokaRepository.invalidateAllInMemoryCaches()
        loadRpcEndpoint()
    }

    fun resetRpcEndpoint() {
        rpcSettings.resetToDefault()
        zokaRepository.invalidateAllInMemoryCaches()
        loadRpcEndpoint()
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountManager.activeAccount.collect { account ->
                account?.let {
                    _uiState.update { state ->
                        state.copy(
                            activeWallet = account.address,
                            activeAccountName = account.name
                        )
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            themeManager.themeMode.collect { themeMode ->
                val activeAccount = accountManager.activeAccount.value
                val activeWallet = activeAccount?.address ?: secureStorage.getActiveWallet()
                
                // Check if active account has mnemonic or was imported from a scan key.
                val hasMnemonic = !activeAccount?.mnemonic.isNullOrBlank()
                val isScanKeyImport = !hasMnemonic
                
                _uiState.update {
                    it.copy(
                        biometricEnabled = secureStorage.isBiometricEnabled(),
                        activeWallet = activeWallet,
                        activeAccountName = activeAccount?.name ?: "Main Account",
                        seedPhrase = if (hasMnemonic) activeAccount?.mnemonic else null,
                        scanKeyHex = if (isScanKeyImport) activeAccount?.scanKeyHex else null,
                        isScanKeyImport = isScanKeyImport,
                        themeMode = themeMode
                    )
                }
            }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun enableBiometric(activity: androidx.fragment.app.FragmentActivity, onError: (String) -> Unit) {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        when (canAuthenticate) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                val biometricPrompt = androidx.biometric.BiometricPrompt(
                    activity,
                    executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            toggleBiometric(true)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            onError("Authentication error: $errString")
                        }
                        override fun onAuthenticationFailed() {
                            onError("Authentication failed")
                        }
                    }
                )
                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable Biometric Authentication")
                    .setSubtitle("Authenticate to enable biometric unlock")
                    .setNegativeButtonText("Cancel")
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                onError("No biometric hardware available")
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onError("Biometric hardware unavailable")
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onError("No biometric credentials enrolled. Please set up biometric in device settings.")
            }
            else -> {
                onError("Biometric authentication not available")
            }
        }
    }

    fun disableBiometric() {
        toggleBiometric(false)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeManager.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun changePin(oldPin: String, newPin: String) {
        val storedPin = secureStorage.getPin()
        if (storedPin == oldPin) {
            secureStorage.storePin(newPin)
            _uiState.update { it.copy(error = null, pinChangeSuccess = true) }
        } else {
            _uiState.update { it.copy(error = "Incorrect current PIN", pinChangeSuccess = false) }
        }
    }
    
    fun clearPinChangeStatus() {
        _uiState.update { it.copy(error = null, pinChangeSuccess = false) }
    }

    fun verifyPin(pin: String): Boolean {
        val storedPin = secureStorage.getPin()
        return storedPin == pin
    }
    
    fun loadSeedPhrase() {
        // Get seed phrase from active account
        val activeAccount = accountManager.activeAccount.value
        val mnemonic = activeAccount?.mnemonic
        val hasMnemonic = !mnemonic.isNullOrBlank()
        
        _uiState.update { 
            it.copy(
                seedPhrase = if (hasMnemonic) mnemonic else null,
                scanKeyHex = if (!hasMnemonic) activeAccount?.scanKeyHex else null,
                isScanKeyImport = !hasMnemonic
            ) 
        }
    }

    fun loadScanKey() {
        viewModelScope.launch {
            try {
                // Get keys from active account
                val activeAccount = accountManager.activeAccount.value
                
                if (activeAccount != null) {
                    _uiState.update { it.copy(
                        scanKeyHex = activeAccount.scanKeyHex,
                        zpriv = activeAccount.publicKey,
                        isScanKeyImport = activeAccount.mnemonic.isBlank()
                    ) }
                } else {
                    // Fallback for the first wallet before account migration.
                    val mnemonic = secureStorage.getMnemonic("default_wallet")
                    val scanKey = secureStorage.getMnemonic("scan_key_hex")
                    val isScanKeyImport = scanKey != null && mnemonic == null
                    
                    if (isScanKeyImport && scanKey != null) {
                        val zokaAddress = walletManager.importFromScanKeyHex(scanKey)
                        
                        _uiState.update { it.copy(
                            scanKeyHex = walletManager.normalizeScanKeyHex(scanKey),
                            zpriv = zokaAddress.publicKey,
                            isScanKeyImport = true
                        ) }
                    } else if (mnemonic != null) {
                        val zokaAddress = walletManager.deriveAddress(mnemonic)
                        
                        _uiState.update { it.copy(
                            scanKeyHex = walletManager.getScanKeyHex(mnemonic),
                            zpriv = zokaAddress.publicKey,
                            isScanKeyImport = false
                        ) }
                    } else {
                        _uiState.update { it.copy(error = "No wallet found") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load keys: ${e.message}") }
            }
        }
    }
    
    fun clearAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                walletResetCoordinator.resetLocalWalletState()
                _uiState.update { SettingsState() }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Reset failed: ${e.message ?: "Unknown error"}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Export transaction history to JSON file
     */
    fun exportTransactionHistory(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val address = secureStorage.getActiveWallet() ?: run {
                    _uiState.update { it.copy(error = "No active wallet found") }
                    return@launch
                }
                
                when (val result = zokaRepository.exportTransactionHistory(address)) {
                    is com.zoka.zkapriv.core.util.Result.Success -> {
                        onSuccess(result.data)
                    }
                    is com.zoka.zkapriv.core.util.Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message) }
                    }
                    is com.zoka.zkapriv.core.util.Result.Loading -> {
                        // Loading state not expected for this operation
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            }
        }
    }

    /**
     * Import transaction history from JSON file
     */
    fun importTransactionHistory(filePath: String, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val address = secureStorage.getActiveWallet() ?: run {
                    _uiState.update { it.copy(error = "No active wallet found") }
                    return@launch
                }
                
                when (val result = zokaRepository.importTransactionHistory(address, filePath)) {
                    is com.zoka.zkapriv.core.util.Result.Success -> {
                        onSuccess(result.data) // Number of new transactions imported
                    }
                    is com.zoka.zkapriv.core.util.Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message) }
                    }
                    is com.zoka.zkapriv.core.util.Result.Loading -> {
                        // Loading state not expected for this operation
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Import failed: ${e.message}") }
            }
        }
    }
}

data class SettingsState(
    val biometricEnabled: Boolean = false,
    val activeWallet: String? = null,
    val activeAccountName: String = "Main Account",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val seedPhrase: String? = null,
    val isScanKeyImport: Boolean = false,
    val scanKeyHex: String? = null,
    val zpriv: String? = null,
    val error: String? = null,
    val pinChangeSuccess: Boolean = false,
    val rpcEndpoint: String = "",
    val isCustomRpc: Boolean = false
)
