package com.zoka.zkapriv.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.core.model.Token
import com.zoka.zkapriv.core.util.Constants
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.network.repository.ZokaRepository
import com.zoka.zkapriv.price.repository.PriceRepository
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

internal data class ZokaPaymentQr(
    val address: String,
    val amount: String?
)

/** Parses the opaque format emitted by Receive: `zoka:address?amount=...`. */
internal fun parseZokaPaymentQr(qrData: String): ZokaPaymentQr {
    require(qrData.startsWith(Constants.ZOKA_QR_SCHEME)) { "Unsupported QR scheme" }
    val payload = qrData.removePrefix(Constants.ZOKA_QR_SCHEME)
    val address = payload.substringBefore('?').trim()
    require(address.isNotBlank()) { "Missing recipient address" }
    val query = payload.substringAfter('?', missingDelimiterValue = "")
    val amount = query
        .split('&')
        .asSequence()
        .mapNotNull { pair ->
            val key = pair.substringBefore('=', missingDelimiterValue = pair)
            if (key != Constants.QR_PARAM_AMOUNT) return@mapNotNull null
            pair.substringAfter('=', missingDelimiterValue = "")
        }
        .firstOrNull()
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        ?.takeIf { it.isNotBlank() }
    return ZokaPaymentQr(address, amount)
}

@HiltViewModel
class SendViewModel @Inject constructor(
    private val zokaRepository: ZokaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage,
    private val walletManager: WalletManager,
    private val accountManager: com.zoka.zkapriv.security.wallet.AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendState())
    val uiState: StateFlow<SendState> = _uiState
    
    private val _biometricRequest = MutableSharedFlow<Unit>()
    val biometricRequest: SharedFlow<Unit> = _biometricRequest
    
    fun getBiometricEnabled(): Boolean = secureStorage.isBiometricEnabled()
    
    fun verifyPin(pin: String): Boolean = secureStorage.getPin() == pin
    
    fun requestBiometric() {
        viewModelScope.launch {
            _biometricRequest.emit(Unit)
        }
    }

    init {
        viewModelScope.launch {
            try {
                // Get current wallet balance from AccountManager or SecureStorage
                val activeAccount = accountManager.activeAccount.value
                val address = activeAccount?.address ?: secureStorage.getActiveWallet()
                
                if (address == null) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        balanceError = "No active wallet is available"
                    ) }
                } else {
                    loadBalance(address)
                }

                // Start USD price updates
                priceRepository.getPrice("zka").collect { result ->
                    when (result) {
                        is com.zoka.zkapriv.core.util.Result.Success -> {
                            val price = result.data
                            _uiState.update { it.copy(
                                usdPrice = price,
                                usdAmount = calculateUsdAmount(it.amount, price)
                            ) }
                        }
                        else -> { /* ignore errors/loading */ }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    isBalanceVerified = false,
                    balanceError = e.message ?: "Could not verify the current balance"
                ) }
            }
        }
    }

    private suspend fun loadBalance(address: String) {
        val matchingAccount = accountManager.activeAccount.value
            ?.takeIf { it.address == address }
        val scanKey = matchingAccount?.let { accountManager.getScanKeyForAccount(it.id) }
            ?: secureStorage.getMnemonic("default_wallet")?.let(walletManager::getScanKey)
        val restoreHeight = matchingAccount?.restoreHeight ?: 0L
        // Refresca si la red activo la zkq1, para que el campo de destino la
        // acepte. Con la altura nula (hoy) una zkq1 sigue siendo invalida.
        scanKey?.copyOf()?.let { copia ->
            try { zokaRepository.hybridWalletInfo(copia) } finally { copia.fill(0) }
        }
        when (val balRes = zokaRepository.getAddressBalance(address, scanKey, restoreHeight)) {
            is com.zoka.zkapriv.core.util.Result.Success -> {
                val info = balRes.data
                // Validate sends against `availableAtoms`, not `matureAtoms`.
                // `available = mature - pendingOut`, so the user cannot
                // accidentally try to spend atoms already reserved by an
                // in-flight outbound send (which would double-spend the
                // same UTXOs and get rejected by the node).
                _uiState.update { it.copy(
                    availableBalance = zokaRepository.formatAtoms(info.availableAtoms),
                    isBalanceIncomplete = info.isIncomplete,
                    missingTxCount = info.missingTxCount,
                    scannedHeight = info.scannedHeight,
                    isBalanceVerified = info.isVerified,
                    isValidAmount = isAmountSpendable(
                        amount = it.amount,
                        fee = it.estimatedFee,
                        availableBalance = zokaRepository.formatAtoms(info.availableAtoms),
                        balanceVerified = info.isVerified
                    ),
                    balanceError = info.verificationIssue,
                    isLoading = false
                ) }
            }
            is com.zoka.zkapriv.core.util.Result.Error -> {
                _uiState.update { it.copy(
                    isBalanceVerified = false,
                    balanceError = balRes.exception.message ?: "Could not verify the current balance",
                    isLoading = false
                ) }
            }
            else -> { /* no loading result is emitted by this repository */ }
        }
    }

    fun updateAmount(amount: String) {
        try {
            val currentState = _uiState.value
            
            if (currentState.isUsdMode) {
                // User is entering USD, convert to ZKA
                val usdAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val masAmount = if (currentState.usdPrice > 0 && usdAmount > BigDecimal.ZERO) {
                    usdAmount.divide(BigDecimal.valueOf(currentState.usdPrice), 8, BigDecimal.ROUND_HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
                
                _uiState.update { it.copy(
                    usdAmount = amount,
                    amount = if (masAmount > BigDecimal.ZERO) masAmount.toPlainString() else "",
                    isValidAmount = isAmountSpendable(
                        amount = masAmount.toPlainString(),
                        fee = it.estimatedFee,
                        availableBalance = it.availableBalance,
                        balanceVerified = it.isBalanceVerified
                    )
                ) }
                
                if (masAmount > BigDecimal.ZERO) {
                    estimateFee(masAmount.toPlainString())
                }
            } else {
                // User is entering ZKA
                val numericAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _uiState.update { it.copy(
                    amount = amount,
                    usdAmount = calculateUsdAmount(amount, it.usdPrice),
                    isValidAmount = isAmountSpendable(
                        amount = numericAmount.toPlainString(),
                        fee = it.estimatedFee,
                        availableBalance = it.availableBalance,
                        balanceVerified = it.isBalanceVerified
                    )
                ) }
                
                if (numericAmount > BigDecimal.ZERO) {
                    estimateFee(amount)
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                amount = if (!it.isUsdMode) amount else it.amount,
                usdAmount = if (it.isUsdMode) amount else it.usdAmount,
                isValidAmount = false
            ) }
        }
    }
    
    fun toggleCurrency() {
        _uiState.update { it.copy(
            isUsdMode = !it.isUsdMode,
            amount = "",
            usdAmount = "",
            isValidAmount = false
        ) }
    }
    
    fun setMaxAmount() {
        if (!_uiState.value.isBalanceVerified) return
        val availableBalance = _uiState.value.availableBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
        
        val fee = _uiState.value.estimatedFee.toBigDecimalOrNull() ?: return
        val maxAmount = (availableBalance - fee).max(BigDecimal.ZERO)
        
        // If in USD mode, convert to USD
        if (_uiState.value.isUsdMode) {
            val maxUsd = maxAmount * BigDecimal.valueOf(_uiState.value.usdPrice)
            updateAmount(maxUsd.toPlainString())
        } else {
            updateAmount(maxAmount.toPlainString())
        }
    }

    fun updateAddress(address: String) {
        _uiState.update { it.copy(
            recipientAddress = address,
            isValidAddress = zokaRepository.validateZokaAddress(address)
        ) }
    }

    private fun estimateFee(amount: String) {
        viewModelScope.launch {
            when (val feeResult = zokaRepository.estimateTransactionFee(amount)) {
                is Result.Success -> {
                    val feeAtoms = feeResult.data.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val feeZka = feeAtoms.divide(BigDecimal.valueOf(Constants.ATOMS_PER_ZKA))
                    _uiState.update {
                        val fee = feeZka.stripTrailingZeros().toPlainString()
                        it.copy(
                            estimatedFee = fee,
                            isValidAmount = isAmountSpendable(
                                it.amount,
                                fee,
                                it.availableBalance,
                                it.isBalanceVerified
                            )
                        )
                    }
                }
                else -> {
                    // Use fallback fee
                    _uiState.update {
                        it.copy(
                            estimatedFee = "0.1",
                            isValidAmount = isAmountSpendable(
                                it.amount,
                                "0.1",
                                it.availableBalance,
                                it.isBalanceVerified
                            )
                        )
                    }
                }
            }
        }
    }

    fun requestAuthentication() {
        if (!canSubmit(_uiState.value)) return
        _uiState.update { it.copy(showAuthDialog = true) }
    }
    
    fun onAuthenticationSuccess() {
        _uiState.update { it.copy(showAuthDialog = false) }
        if (!canSubmit(_uiState.value)) return
        sendTransaction()
    }
    
    fun onAuthenticationCancelled() {
        _uiState.update { it.copy(showAuthDialog = false) }
    }
    
    private fun sendTransaction() {
        viewModelScope.launch {
            if (!canSubmit(_uiState.value)) return@launch
            _uiState.update { it.copy(isLoading = true, error = null, showFailureScreen = false) }
            
            val activeAccount = accountManager.activeAccount.value
            val fromAddress = activeAccount?.address ?: secureStorage.getActiveWallet()
            if (fromAddress == null) {
                _uiState.update { it.copy(
                    error = "No active wallet is available",
                    isLoading = false,
                    showFailureScreen = false
                ) }
                return@launch
            }
            
            // Get scan key from AccountManager
            val scanKeyBytes = if (activeAccount != null) {
                accountManager.getScanKeyForAccount(activeAccount.id)
            } else {
                // Fallback for the original single-account wallet.
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                if (mnemonic != null) {
                    walletManager.getScanKey(mnemonic)
                } else {
                    null
                }
            }

            if (scanKeyBytes == null) {
                _uiState.update { it.copy(
                    error = "Could not retrieve scan key",
                    isLoading = false,
                    showFailureScreen = false
                ) }
                return@launch
            }
            
            val scanKeyHex = scanKeyBytes.joinToString("") { "%02x".format(it) }
            val publicKey = activeAccount?.publicKey.orEmpty()
            
            val sendRes = zokaRepository.sendTransaction(
                from = fromAddress,
                to = _uiState.value.recipientAddress,
                amount = _uiState.value.amount,
                fee = _uiState.value.estimatedFee,
                scanKeyHex = scanKeyHex,
                publicKey = publicKey
            )

            when (sendRes) {
                is com.zoka.zkapriv.core.util.Result.Success -> {
                    _uiState.update { it.copy(
                        transactionHash = sendRes.data,
                        availableBalance = reserveOutgoingBalance(
                            availableBalance = it.availableBalance,
                            amount = it.amount,
                            fee = it.estimatedFee
                        ),
                        isSuccess = true,
                        isLoading = false,
                        showSuccessScreen = true
                    ) }
                }
                is com.zoka.zkapriv.core.util.Result.Error -> {
                    _uiState.update { it.copy(
                        error = sendRes.exception.message,
                        isLoading = false,
                        showFailureScreen = false
                    ) }
                }
                else -> { /* ignore */ }
            }
        }
    }

    fun parseQrCode(qrData: String) {
        try {
            if (qrData.startsWith(Constants.ZOKA_QR_SCHEME)) {
                val payment = parseZokaPaymentQr(qrData)
                updateAddress(payment.address)
                payment.amount?.let(::updateAmount)
            } else {
                // Treat as plain address
                val address = qrData.trim()
                _uiState.update { it.copy(
                    recipientAddress = address,
                    isValidAddress = zokaRepository.validateZokaAddress(address)
                ) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                error = "Invalid QR code: ${e.message}"
            ) }
        }
    }
    
    fun dismissResultScreen() {
        _uiState.update { it.copy(
            showSuccessScreen = false,
            showFailureScreen = false,
            error = null
        ) }
    }

    private fun canSubmit(state: SendState): Boolean =
        state.isBalanceVerified &&
            state.isValidAddress &&
            state.recipientAddress.isNotBlank() &&
            isAmountSpendable(
                state.amount,
                state.estimatedFee,
                state.availableBalance,
                state.isBalanceVerified
            )

    private fun isAmountSpendable(
        amount: String,
        fee: String,
        availableBalance: String,
        balanceVerified: Boolean
    ): Boolean {
        if (!balanceVerified) return false
        val value = amount.toBigDecimalOrNull() ?: return false
        val feeValue = fee.toBigDecimalOrNull() ?: return false
        val available = availableBalance.toBigDecimalOrNull() ?: return false
        return value > BigDecimal.ZERO && feeValue >= BigDecimal.ZERO && value + feeValue <= available
    }

    private fun calculateUsdAmount(amount: String, usdPrice: Double): String {
        return try {
            val masaAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            (masaAmount * BigDecimal.valueOf(usdPrice)).toString()
        } catch (e: Exception) {
            "0"
        }
    }

    private fun reserveOutgoingBalance(
        availableBalance: String,
        amount: String,
        fee: String
    ): String {
        val available = availableBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val outgoing = (amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) +
            (fee.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        return (available - outgoing)
            .max(BigDecimal.ZERO)
            .stripTrailingZeros()
            .toPlainString()
    }
}

data class SendState(
    val amount: String = "",
    val usdAmount: String = "0",
    val usdPrice: Double = 0.0,
    val recipientAddress: String = "",
    val availableBalance: String = "0",
    val isBalanceIncomplete: Boolean = false,
    val isBalanceVerified: Boolean = false,
    val missingTxCount: Int = 0,
    val scannedHeight: Long = 0,
    val balanceError: String? = null,
    val estimatedFee: String = "0.1",
    val isValidAmount: Boolean = false,
    val isValidAddress: Boolean = true,
    val isLoading: Boolean = true,
    val isSuccess: Boolean = false,
    val showAuthDialog: Boolean = false,
    val transactionHash: String? = null,
    val error: String? = null,
    val isUsdMode: Boolean = false,  // Toggle between ZKA and USD input
    val showSuccessScreen: Boolean = false,
    val showFailureScreen: Boolean = false
)
