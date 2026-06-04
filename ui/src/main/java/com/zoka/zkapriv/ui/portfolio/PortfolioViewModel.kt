package com.zoka.zkapriv.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoka.zkapriv.core.model.Account
import com.zoka.zkapriv.core.util.Result
import com.zoka.zkapriv.network.repository.ZokaRepository
import com.zoka.zkapriv.security.wallet.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSummary(
    val account: Account,
    val matureBalance: String,
    val immatureBalance: String = "0",
    val hasMining: Boolean = false,
    val isLoadingBalance: Boolean = true
) {
    val balance: String get() = matureBalance
}

data class AllAccountsState(
    val accounts: List<AccountSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val zokaRepository: ZokaRepository,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllAccountsState())
    val uiState: StateFlow<AllAccountsState> = _uiState

    init {
        viewModelScope.launch {
            accountManager.accounts.collect { accounts ->
                if (accounts.isNotEmpty()) loadBalances(accounts)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val accounts = accountManager.accounts.value
                if (accounts.isNotEmpty()) loadBalances(accounts)
            }
        }
    }

    private fun loadBalances(accounts: List<Account>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val summaries = accounts.map { account ->
                async {
                    try {
                        val scanKey = accountManager.getScanKeyForAccount(account.id)
                        when (val res = zokaRepository.getAddressBalance(account.address, scanKey, account.restoreHeight)) {
                            is Result.Success -> {
                                val info = res.data
                                AccountSummary(
                                    account = account,
                                    matureBalance = zokaRepository.formatAtoms(info.matureAtoms),
                                    immatureBalance = if (info.immatureAtoms > 0)
                                        zokaRepository.formatAtoms(info.immatureAtoms) else "0",
                                    hasMining = info.hasMining,
                                    isLoadingBalance = false
                                )
                            }
                            else -> AccountSummary(
                                account = account,
                                matureBalance = account.balance.ifBlank { "0" },
                                isLoadingBalance = false
                            )
                        }
                    } catch (e: Exception) {
                        AccountSummary(
                            account = account,
                            matureBalance = account.balance.ifBlank { "0" },
                            isLoadingBalance = false
                        )
                    }
                }
            }.awaitAll()

            _uiState.update { it.copy(accounts = summaries, isLoading = false) }
        }
    }

    fun refresh() {
        val accounts = accountManager.accounts.value
        if (accounts.isNotEmpty()) loadBalances(accounts)
    }

    fun switchToAccount(accountId: String) {
        accountManager.setActiveAccount(accountId)
    }
}
