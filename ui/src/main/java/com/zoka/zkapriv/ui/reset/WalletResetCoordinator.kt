package com.zoka.zkapriv.ui.reset

import android.content.Context
import com.zoka.zkapriv.core.preferences.ThemeManager
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.network.config.RpcSettings
import com.zoka.zkapriv.network.repository.ZokaRepository
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.wallet.AccountManager
import com.zoka.zkapriv.security.wallet.WalletManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletResetCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val accountManager: AccountManager,
    private val walletManager: WalletManager,
    private val zokaRepository: ZokaRepository,
    private val rpcSettings: RpcSettings,
    private val themeManager: ThemeManager
) {
    suspend fun resetLocalWalletState() = withContext(Dispatchers.IO) {
        accountManager.clearAllAccounts()
        walletManager.deleteWallet()
        secureStorage.clear()
        zokaRepository.invalidateAllInMemoryCaches()
        rpcSettings.clearAll()
        themeManager.setThemeMode(ThemeMode.LIGHT)

        clearSharedPreferences(
            "zoka_accounts_encrypted",
            "zoka_transactions",
            "zoka_rpc_settings"
        )

        context.deleteDatabase("price_database")
        context.cacheDir.deleteRecursively()
        context.codeCacheDir.deleteRecursively()
    }

    private fun clearSharedPreferences(vararg names: String) {
        names.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
