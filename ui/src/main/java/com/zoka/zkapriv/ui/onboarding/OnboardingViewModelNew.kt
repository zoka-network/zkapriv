package com.zoka.zkapriv.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.security.wallet.MnemonicManager
import com.zoka.zkapriv.security.wallet.WalletManager
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.crypto.KeystoreManager
import com.zoka.zkapriv.core.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModelNew @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val walletManager: WalletManager,
    private val secureStorage: SecureStorage,
    private val keystoreManager: KeystoreManager,
    private val accountManager: com.zoka.zkapriv.security.wallet.AccountManager,
    private val repository: com.zoka.zkapriv.network.repository.ZokaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingStateNew())
    val uiState: StateFlow<OnboardingStateNew> = _uiState

    fun startCreateWallet() {
        viewModelScope.launch {
            try {
                val mnemonic = mnemonicManager.generateMnemonic()
                _uiState.update {
                    it.copy(
                        seedWords = mnemonic.split(" "),
                        isNewWallet = true,
                        currentStep = OnboardingStepNew.GENERATE_SEED,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to generate seed: ${e.message}")
                }
            }
        }
    }

    fun startImportWallet(seedPhrase: String) {
        viewModelScope.launch {
            try {
                // Check if this is a scan-key import.
                if (seedPhrase.startsWith("SCAN_KEY_IMPORT:")) {
                    val scanKey = seedPhrase.substring("SCAN_KEY_IMPORT:".length).trim()
                    
                    // Store with special marker
                    _uiState.update {
                        it.copy(
                            seedWords = listOf("SCAN_KEY_HEX", scanKey), // Special marker
                            isNewWallet = false,
                            currentStep = OnboardingStepNew.SETUP_PIN,
                            error = null
                        )
                    }
                } else {
                    // Standard mnemonic import
                    if (mnemonicManager.validateMnemonic(seedPhrase.trim())) {
                        _uiState.update {
                            it.copy(
                                seedWords = seedPhrase.trim().split(" "),
                                isNewWallet = false,
                                currentStep = OnboardingStepNew.SETUP_PIN,
                                error = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(error = "Invalid seed phrase")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Import failed: ${e.message}")
                }
            }
        }
    }

    fun moveToVerifySeed() {
        _uiState.update {
            it.copy(currentStep = OnboardingStepNew.VERIFY_SEED)
        }
    }

    fun onSeedVerified() {
        _uiState.update {
            it.copy(currentStep = OnboardingStepNew.SETUP_PIN)
        }
    }

    fun onPinCreated(pin: String, biometricEnabled: Boolean) {
        _uiState.update { it.copy(finishing = true) }
        viewModelScope.launch {
            try {
                // Store PIN
                secureStorage.storePin(pin)
                
                // Store biometric preference
                secureStorage.setBiometricEnabled(biometricEnabled)
                
                // Check if this is a scan-key import.
                val seedWords = _uiState.value.seedWords
                val isScanKeyImport = seedWords.firstOrNull() == "SCAN_KEY_HEX"
                
                val address = if (isScanKeyImport) {
                    val scanKeyHex = walletManager.normalizeScanKeyHex(seedWords[1])
                    secureStorage.storeMnemonic("scan_key_hex", scanKeyHex)
                    val scanKeyBytes = walletManager.getScanKeyFromHex(scanKeyHex)
                    walletManager.deriveZokaNetworkAddressFromScanKey(scanKeyBytes)
                } else {
                    val mnemonicString = seedWords.joinToString(" ")
                    secureStorage.storeMnemonic("default_wallet", mnemonicString)
                    val zokaAddress = walletManager.deriveZokaNetworkAddress(
                        mnemonic = mnemonicString,
                        passphrase = "",
                        accountIndex = 0,
                        addressIndex = 0
                    )
                    android.util.Log.d("Onboarding", "Wallet address derived")
                    zokaAddress
                }
                
                secureStorage.setActiveWallet(address)
                
                // Mark onboarding as completed
                secureStorage.setOnboardingCompleted(true)
                
                // CRITICAL: Clear old accounts before initializing new ones
                // This fixes the bug where old addresses were shown after importing a new seed
                try {
                    android.util.Log.d("Onboarding", "Clearing old accounts before init...")
                    accountManager.clearAllAccounts()
                    // For a freshly created wallet (no prior history), anchor the
                    // scan at the current chain tip so it doesn't walk the whole
                    // chain. Imported wallets pass 0 to scan from genesis.
                    val restoreHeight = if (_uiState.value.isNewWallet) {
                        newWalletRestoreHeight()
                    } else {
                        0L
                    }
                    android.util.Log.d("Onboarding", "Initializing account with address: $address (restoreHeight=$restoreHeight)")
                    accountManager.initializeFromExistingWallet(restoreHeight)
                    android.util.Log.d("Onboarding", "Account initialized successfully")
                } catch (e: Exception) {
                    android.util.Log.e("Onboarding", "Failed to init default account", e)
                }
                
                _uiState.update {
                    it.copy(
                        currentStep = OnboardingStepNew.COMPLETED,
                        finishing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        finishing = false,
                        error = "Setup failed: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Height a brand-new wallet starts scanning from. Its keys were just
     * generated, so nothing can exist for it before now: the chain tip is
     * enough, and so is any height that already existed when this build was
     * made ([Constants.WALLET_BIRTHDAY_FLOOR]).
     *
     * The tip comes from the P2P network over Tor, which on a first run (cold
     * Arti, no directory cache) can take minutes. This used to block the PIN
     * screen for up to ~2 minutes with no feedback and then fall back to 0,
     * i.e. scanning the whole chain from genesis. Now the lookup runs on its
     * own job and is awaited at most [NEW_WALLET_TIP_WAIT_MS]; the native call
     * cannot be interrupted, so it keeps running in the background and warms
     * up the network for the first sync.
     */
    private suspend fun newWalletRestoreHeight(): Long {
        val tip = viewModelScope.async(Dispatchers.IO) { repository.getCurrentChainHeight() }
        val height = withTimeoutOrNull(NEW_WALLET_TIP_WAIT_MS) { tip.await() }
        android.util.Log.d(
            "Onboarding",
            "New wallet tip=${height ?: "unavailable"} floor=${Constants.WALLET_BIRTHDAY_FLOOR}"
        )
        return maxOf(height ?: 0L, Constants.WALLET_BIRTHDAY_FLOOR)
    }

    private companion object {
        const val NEW_WALLET_TIP_WAIT_MS = 15_000L
    }

    fun goBack() {
        _uiState.update { state ->
            val previousStep = when (state.currentStep) {
                OnboardingStepNew.WELCOME -> OnboardingStepNew.WELCOME
                OnboardingStepNew.GENERATE_SEED -> OnboardingStepNew.WELCOME
                OnboardingStepNew.VERIFY_SEED -> OnboardingStepNew.GENERATE_SEED
                OnboardingStepNew.SETUP_PIN -> {
                    if (state.seedWords.isNotEmpty()) OnboardingStepNew.VERIFY_SEED
                    else OnboardingStepNew.WELCOME
                }
                OnboardingStepNew.COMPLETED -> OnboardingStepNew.WELCOME
            }
            state.copy(currentStep = previousStep, error = null)
        }
    }

    private fun generateRandomString(length: Int): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}

data class OnboardingStateNew(
    val currentStep: OnboardingStepNew = OnboardingStepNew.WELCOME,
    val seedWords: List<String> = emptyList(),
    // True only for the create-new-wallet path. Drives whether we anchor the
    // balance scan at the current chain tip (new wallet, no past) or scan from
    // genesis (imported wallet, may have history). Default false = scan all.
    val isNewWallet: Boolean = false,
    // True from the PIN confirmation until the wallet is ready. The screen
    // shows a progress overlay meanwhile: the step may wait for the network.
    val finishing: Boolean = false,
    val error: String? = null
)

enum class OnboardingStepNew {
    WELCOME,
    GENERATE_SEED,
    VERIFY_SEED,
    SETUP_PIN,
    COMPLETED
}
