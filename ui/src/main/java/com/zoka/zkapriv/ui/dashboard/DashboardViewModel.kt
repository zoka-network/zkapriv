package com.zoka.zkapriv.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.core.model.Transaction
import com.zoka.zkapriv.network.repository.ZokaRepository
import com.zoka.zkapriv.price.repository.PriceRepository
import com.zoka.zkapriv.price.model.ZokaStats
import com.zoka.zkapriv.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val zokaRepository: ZokaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage,
    private val accountManager: com.zoka.zkapriv.security.wallet.AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState
    private var walletLoadJob: Job? = null
    private var lastWalletLoadStartedAt = 0L

    companion object {
        private const val DASHBOARD_REFRESH_THROTTLE_MS = 20_000L
        private const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }

    init {
        viewModelScope.launch {
            accountManager.activeAccount.collect { account ->
                account?.let {
                    val persistedBalance = zokaRepository.cachedBalanceFor(it.address)
                    val cachedBalance = persistedBalance?.let { info -> zokaRepository.formatAtoms(info.matureAtoms) }
                        ?: it.balance.takeIf { balance -> balance.isUsableCachedBalance() }
                    val cachedAvailable = persistedBalance?.let { info -> zokaRepository.formatAtoms(info.availableAtoms) }
                        ?: cachedBalance
                    val cachedImmature = persistedBalance?.let { info ->
                        if (info.immatureAtoms > 0) zokaRepository.formatAtoms(info.immatureAtoms) else "0"
                    } ?: "0"
                    val cachedPendingOut = persistedBalance?.let { info ->
                        if (info.pendingOutAtoms > 0) zokaRepository.formatAtoms(info.pendingOutAtoms) else "0"
                    } ?: "0"
                    _uiState.update { state ->
                        state.copy(
                            activeWallet = it.address,
                            activeAccountName = it.name,
                            activeAccountColor = it.color.hex,
                            accountCount = accountManager.accounts.value.size,
                            balance = cachedBalance ?: state.balance,
                            availableBalance = cachedAvailable ?: state.availableBalance,
                            immatureBalance = if (persistedBalance != null) cachedImmature else state.immatureBalance,
                            pendingOutBalance = if (persistedBalance != null) cachedPendingOut else state.pendingOutBalance,
                            hasPendingOut = persistedBalance?.hasPendingOut ?: false,
                            hasMining = persistedBalance?.hasMining ?: state.hasMining,
                            isBalanceIncomplete = persistedBalance?.isIncomplete ?: false,
                            missingTxCount = persistedBalance?.missingTxCount ?: 0,
                            isLoading = cachedBalance == null && state.balance == "0"
                        )
                    }
                    loadWalletData(it.address, showLoading = cachedBalance == null)
                } ?: run {
                    val address = secureStorage.getActiveWallet()
                    address?.let {
                        _uiState.update { state -> state.copy(activeWallet = it) }
                        loadWalletData(it, showLoading = true)
                    }
                }
            }
        }
        // Auto-refresh every 30 seconds, but only re-scan (expensive: walks
        // every private tx on chain) when a new block has actually landed.
        // The target block time is ~120s, so most 30s ticks see no new
        // block — invalidating unconditionally here used to retrigger a
        // 1-2 minute full scan every cycle, which never let the cache stay
        // fresh long enough for Send/Home to get a fast hit.
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                val address = accountManager.activeAccount.value?.address
                    ?: secureStorage.getActiveWallet()
                address?.let {
                    val chainHeight = zokaRepository.getChainHeightCheap()
                    val scannedHeight = zokaRepository.cachedBalanceFor(it)?.scannedHeight ?: 0L
                    // Only blow away the balance cache (forcing the expensive
                    // full re-scan) when a new block actually landed. Price
                    // and transaction-history refresh keep running every tick
                    // either way.
                    if (chainHeight != null && chainHeight > scannedHeight) {
                        zokaRepository.invalidateBalanceCacheFor(it)
                    }
                    loadWalletData(it, force = true)
                }
            }
        }
    }

    private fun loadWalletData(
        address: String,
        force: Boolean = false,
        showLoading: Boolean = false
    ) {
        if (walletLoadJob?.isActive == true) return

        val now = System.currentTimeMillis()
        if (!force && now - lastWalletLoadStartedAt < DASHBOARD_REFRESH_THROTTLE_MS) return
        lastWalletLoadStartedAt = now

        if (showLoading && _uiState.value.balance == "0") {
            _uiState.update { it.copy(isLoading = true) }
        }

        walletLoadJob = viewModelScope.launch {
            var matureBalance = "0"
            try {
                // Resolve the scan key for this address. Match against the active
                // account first; if that doesn't line up (e.g. address came from
                // secureStorage before the account flow loaded), fall back to any
                // managed account whose address matches so received private
                // outputs are still scanned (without it we only see coinbases).
                val matchingAccount = accountManager.activeAccount.value
                    ?.takeIf { it.address == address }
                    ?: accountManager.accounts.value.firstOrNull { it.address == address }
                val scanKey = matchingAccount?.let { accountManager.getScanKeyForAccount(it.id) }
                if (scanKey == null) {
                    android.util.Log.w("DashboardVM", "No scan key for $address — received private outputs will be invisible (coinbase-only scan)")
                }
                val restoreHeight = matchingAccount?.restoreHeight ?: 0L
                when (val balRes = zokaRepository.getAddressBalance(address, scanKey, restoreHeight)) {
                    is com.zoka.zkapriv.core.util.Result.Success -> {
                        val info = balRes.data
                        matureBalance = zokaRepository.formatAtoms(info.matureAtoms)
                        val availableBalance = zokaRepository.formatAtoms(info.availableAtoms)
                        val immatureBalance = if (info.immatureAtoms > 0)
                            zokaRepository.formatAtoms(info.immatureAtoms) else "0"
                        val pendingOutBalance = if (info.pendingOutAtoms > 0)
                            zokaRepository.formatAtoms(info.pendingOutAtoms) else "0"
                        _uiState.update { it.copy(
                            balance = matureBalance,
                            availableBalance = availableBalance,
                            immatureBalance = immatureBalance,
                            pendingOutBalance = pendingOutBalance,
                            hasPendingOut = info.hasPendingOut,
                            hasMining = info.hasMining,
                            isBalanceIncomplete = info.isIncomplete,
                            missingTxCount = info.missingTxCount,
                            isLoading = false
                        )}
                        accountManager.activeAccount.value?.let { acc ->
                            if (acc.address == address) {
                                // Persist the AVAILABLE balance (what the
                                // user can actually spend right now), not
                                // the full mature pile that still includes
                                // amounts locked in pending sends.
                                accountManager.updateAccountBalance(acc.id, availableBalance)
                            }
                        }
                    }
                    is com.zoka.zkapriv.core.util.Result.Error -> {
                        android.util.Log.e("DashboardVM", "Balance error", balRes.exception)
                        _uiState.update { it.copy(
                            balance = "0",
                            availableBalance = "0",
                            immatureBalance = "0",
                            pendingOutBalance = "0",
                            hasPendingOut = false,
                            hasMining = false,
                            isLoading = false
                        ) }
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "loadWalletData error", e)
                _uiState.update { it.copy(balance = "0", immatureBalance = "0", hasMining = false, isLoading = false) }
            }

            loadMarketStats(matureBalance)

            try {
                when (val txRes = zokaRepository.getTransactionHistory(address)) {
                    is com.zoka.zkapriv.core.util.Result.Success -> {
                        _uiState.update { it.copy(recentTransactions = txRes.data.take(5), isLoading = false) }
                    }
                    else -> _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Transactions error", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadMarketStats(balance: String) {
        try {
            priceRepository.getZokaStats()
                .catch { e -> android.util.Log.e("DashboardVM", "Stats flow error: ${e.message}") }
                .collect { result ->
                    if (result is com.zoka.zkapriv.core.util.Result.Success) {
                        val stats = result.data
                        val usdValue = balance.toBigDecimal().multiply(stats.price.toBigDecimal())
                        _uiState.update { state ->
                            state.copy(
                                usdValue = usdValue.toString(),
                                currentPrice = stats.price,
                                priceChange24h = stats.percentChange24h,
                                zokaStats = stats,
                                totalPortfolioValue = usdValue
                            )
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DashboardVM", "Price fetch failed: ${e.message}")
        }
    }

    fun refreshData() {
        val address = accountManager.activeAccount.value?.address ?: secureStorage.getActiveWallet()
        address?.let { loadWalletData(it, force = true, showLoading = true) }
    }

    fun refreshDataIfStale() {
        val address = accountManager.activeAccount.value?.address ?: secureStorage.getActiveWallet()
        address?.let {
            zokaRepository.invalidateBalanceCacheFor(it)
            loadWalletData(it, force = true)
        }
    }

    fun toggleUsdDisplay() {
        _uiState.update { it.copy(showUsdValue = !it.showUsdValue) }
    }

    fun toggleBalanceVisibility() {
        _uiState.update { it.copy(isBalanceHidden = !it.isBalanceHidden) }
    }

    private fun String.isUsableCachedBalance(): Boolean {
        return isNotBlank() && (toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
    }
}

data class DashboardState(
    /**
     * Mature balance from the chain scan, ignoring local pending-send
     * reservations. Use [availableBalance] for what the user can
     * actually spend right now.
     */
    val balance: String = "0",
    /** Mature balance minus locally reserved outgoing transfers. */
    val availableBalance: String = "0",
    val immatureBalance: String = "0",
    /** Atoms locked in unconfirmed outbound sends (in ZKA, formatted). */
    val pendingOutBalance: String = "0",
    val hasMining: Boolean = false,
    val hasPendingOut: Boolean = false,
    val usdValue: String = "0",
    val totalPortfolioValue: BigDecimal = BigDecimal.ZERO,
    val currentPrice: Double = 0.0,
    val priceChange24h: Double = 0.0,
    val zokaStats: ZokaStats? = null,
    val showUsdValue: Boolean = true,
    val isBalanceHidden: Boolean = false,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val activeWallet: String? = null,
    val activeAccountName: String = "Main Account",
    val activeAccountColor: String = "#2196F3",
    val accountCount: Int = 1,
    val isBalanceIncomplete: Boolean = false,
    val missingTxCount: Int = 0
)
