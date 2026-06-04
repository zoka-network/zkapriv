package com.zoka.zkapriv.security.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zoka.zkapriv.core.model.Account
import com.zoka.zkapriv.core.model.AccountColor
import com.zoka.zkapriv.core.model.CreateAccountRequest
import com.zoka.zkapriv.core.model.UpdateAccountRequest
import com.zoka.zkapriv.security.storage.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple accounts for the wallet
 * 
 * Architecture (Zoka-compatible):
 * - Each account has its own mnemonic or Zoka scan key
 * - Zoka private wallets do not derive child accounts from a master seed
 * - Each account is completely independent
 * - Keys are stored encrypted in EncryptedSharedPreferences
 * 
 * Features:
 * - Create new accounts with unique seed phrases
 * - Import existing accounts via scan key or mnemonic
 * - Switch between accounts
 * - Rename and customize accounts
 * - Delete accounts (except primary)
 * - Track balances per account
 */
@Singleton
class AccountManager @Inject constructor(
    private val walletManager: WalletManager,
    private val mnemonicManager: MnemonicManager,
    private val secureStorage: SecureStorage,
    @ApplicationContext private val context: Context
) {
    
    private companion object {
        const val PREFS_NAME = "zoka_accounts_encrypted"
        const val KEY_ACCOUNTS = "accounts_json"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val DEFAULT_ACCOUNT_NAME = "Account"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // State flows for reactive UI
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _activeAccount = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = _activeAccount.asStateFlow()
    
    init {
        loadAccounts()
    }
    
    /**
     * Initialize from existing wallet (migration from old system).
     * Always re-derives the Zoka zka1... address from the stored key.
     */
    fun initializeFromExistingWallet(restoreHeight: Long = 0): Result<Account> {
        return try {
            if (_accounts.value.isNotEmpty()) {
                return Result.success(_activeAccount.value ?: _accounts.value.first())
            }

            val existingMnemonic = secureStorage.getMnemonic("default_wallet")
            val existingScanKey = secureStorage.getMnemonic("scan_key_hex")

            val finalAddress: String
            val finalScanKeyHex: String
            val finalMnemonic: String
            val finalPubKey: String

            when {
                existingScanKey != null -> {
                    val scanKey = walletManager.getScanKeyFromHex(existingScanKey)
                    val zokaAddress = walletManager.importFromScanKeyHex(existingScanKey)
                    finalAddress = walletManager.deriveZokaNetworkAddressFromScanKey(scanKey)
                    finalScanKeyHex = walletManager.normalizeScanKeyHex(existingScanKey)
                    finalMnemonic = ""
                    finalPubKey = zokaAddress.publicKey
                }
                existingMnemonic != null -> {
                    val zokaAddress = walletManager.deriveAddress(existingMnemonic)
                    finalAddress = zokaAddress.address
                    finalScanKeyHex = walletManager.getScanKeyHex(existingMnemonic)
                    finalMnemonic = existingMnemonic
                    finalPubKey = zokaAddress.publicKey
                }
                else -> return Result.failure(Exception("No wallet credentials found"))
            }

            secureStorage.setActiveWallet(finalAddress)

            val account = Account(
                id = UUID.randomUUID().toString(),
                name = "Main Account",
                accountIndex = 0,
                address = finalAddress,
                publicKey = finalPubKey,
                scanKeyHex = finalScanKeyHex,
                mnemonic = finalMnemonic,
                isActive = true,
                color = AccountColor.BLUE,
                // Only a freshly CREATED wallet passes a non-zero restoreHeight
                // (the chain tip at creation). Imported wallets pass 0 so their
                // older history is still scanned — never skip an imported past.
                restoreHeight = restoreHeight
            )

            // Save account
            saveAccount(account)
            setActiveAccount(account.id)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to initialize from existing wallet", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a new account with a fresh mnemonic/scan key
     */
    fun createAccount(request: CreateAccountRequest, restoreHeight: Long = 0): Result<Account> {
        return try {
            val newMnemonic = mnemonicManager.generateMnemonic(24)

            val zokaAddress = walletManager.deriveAddress(newMnemonic)
            val address = zokaAddress.address
            val scanKeyHex = walletManager.getScanKeyHex(newMnemonic)
            val publicKey = zokaAddress.publicKey

            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1
            val color = request.color ?: AccountColor.fromIndex(nextIndex)

            val account = Account(
                id = UUID.randomUUID().toString(),
                name = request.name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = address,
                publicKey = publicKey,
                scanKeyHex = scanKeyHex,
                mnemonic = newMnemonic,
                isActive = false,
                color = color,
                // A brand-new wallet has no prior on-chain history; record the
                // current tip so balance scans skip everything before it.
                restoreHeight = restoreHeight
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to create account", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import an account from a Zoka scan key.
     */
    fun importAccountFromScanKey(name: String, scanKeyHex: String, color: AccountColor? = null): Result<Account> {
        return try {
            val scanKey = walletManager.getScanKeyFromHex(scanKeyHex)
            val zokaAddress = walletManager.importFromScanKeyHex(scanKeyHex)
            val address = walletManager.deriveZokaNetworkAddressFromScanKey(scanKey)
            val publicKey = zokaAddress.publicKey
            val normalizedScanKey = walletManager.normalizeScanKeyHex(scanKeyHex)

            if (_accounts.value.any { it.address == address }) {
                return Result.failure(Exception("This wallet is already imported"))
            }

            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1

            val account = Account(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = address,
                publicKey = publicKey,
                scanKeyHex = normalizedScanKey,
                mnemonic = "",
                isActive = false,
                color = color ?: AccountColor.fromIndex(nextIndex)
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to import account from scan key", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import an account from mnemonic
     */
    fun importAccountFromMnemonic(name: String, mnemonic: String, color: AccountColor? = null): Result<Account> {
        return try {
            val zokaAddress = walletManager.deriveAddress(mnemonic)
            val address = zokaAddress.address
            val scanKeyHex = walletManager.getScanKeyHex(mnemonic)
            val publicKey = zokaAddress.publicKey

            if (_accounts.value.any { it.address == address }) {
                return Result.failure(Exception("This wallet is already imported"))
            }

            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1

            val account = Account(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = address,
                publicKey = publicKey,
                scanKeyHex = scanKeyHex,
                mnemonic = mnemonic,
                isActive = false,
                color = color ?: AccountColor.fromIndex(nextIndex)
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to import account from mnemonic", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update account metadata (name, color)
     */
    fun updateAccount(request: UpdateAccountRequest): Result<Account> {
        return try {
            val currentAccounts = _accounts.value.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == request.accountId }
            
            if (index == -1) {
                return Result.failure(Exception("Account not found"))
            }
            
            val account = currentAccounts[index]
            val updatedAccount = account.copy(
                name = request.name ?: account.name,
                color = request.color ?: account.color
            )
            
            currentAccounts[index] = updatedAccount
            saveAccounts(currentAccounts)
            
            // Update active account if it was the one updated
            if (_activeAccount.value?.id == request.accountId) {
                _activeAccount.value = updatedAccount
            }
            
            Result.success(updatedAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete an account (cannot delete the primary account or active account)
     */
    fun deleteAccount(accountId: String): Result<Unit> {
        return try {
            val currentAccounts = _accounts.value
            val account = currentAccounts.find { it.id == accountId }
                ?: return Result.failure(Exception("Account not found"))
            
            // Cannot delete first account (index 0)
            if (account.accountIndex == 0) {
                return Result.failure(Exception("Cannot delete the main account"))
            }
            
            // Cannot delete active account
            if (account.isActive) {
                return Result.failure(Exception("Cannot delete active account. Switch to another account first."))
            }
            
            val updatedAccounts = currentAccounts.filter { it.id != accountId }
            saveAccounts(updatedAccounts)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Switch to a different account
     */
    fun setActiveAccount(accountId: String): Result<Account> {
        return try {
            val currentAccounts = _accounts.value.toMutableList()
            val account = currentAccounts.find { it.id == accountId }
                ?: return Result.failure(Exception("Account not found"))
            
            // Deactivate all accounts
            val updatedAccounts = currentAccounts.map { it.copy(isActive = false) }
            
            // Activate selected account
            val activeIndex = updatedAccounts.indexOfFirst { it.id == accountId }
            val activeAccount = updatedAccounts[activeIndex].copy(
                isActive = true,
                lastUsed = System.currentTimeMillis()
            )
            
            val finalAccounts = updatedAccounts.toMutableList()
            finalAccounts[activeIndex] = activeAccount
            
            saveAccounts(finalAccounts)
            
            // Update active account ID
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
            _activeAccount.value = activeAccount
            
            // Also update SecureStorage for compatibility with other parts of the app
            secureStorage.setActiveWallet(activeAccount.address)
            
            Result.success(activeAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get account by ID
     */
    fun getAccount(accountId: String): Account? {
        return _accounts.value.find { it.id == accountId }
    }
    
    /**
     * Get the scan key for the active account.
     */
    fun getActiveAccountScanKeyHex(): String? {
        return _activeAccount.value?.scanKeyHex
    }
    
    /**
     * Get the mnemonic for the active account (if available)
     */
    fun getActiveAccountMnemonic(): String? {
        val mnemonic = _activeAccount.value?.mnemonic
        return if (mnemonic.isNullOrBlank()) null else mnemonic
    }
    
    /**
     * Get the zpriv receive material for the active account.
     */
    fun getActiveAccountZpriv(): String? {
        return _activeAccount.value?.publicKey
    }
    
    /**
     * Update account balance (called after fetching from network)
     */
    fun updateAccountBalance(accountId: String, balance: String) {
        val currentAccounts = _accounts.value.toMutableList()
        val index = currentAccounts.indexOfFirst { it.id == accountId }
        
        if (index != -1) {
            currentAccounts[index] = currentAccounts[index].copy(balance = balance)
            saveAccounts(currentAccounts)
            
            // Update active account if it was the one updated
            if (_activeAccount.value?.id == accountId) {
                _activeAccount.value = currentAccounts[index]
            }
        }
    }
    
    /**
     * Get scan key bytes for transaction signing
     */
    fun getScanKeyForAccount(accountId: String): ByteArray? {
        val account = getAccount(accountId) ?: return null
        return try {
            if (account.scanKeyHex.isNotBlank()) {
                walletManager.getScanKeyFromHex(account.scanKeyHex)
            } else if (account.mnemonic.isNotBlank()) {
                walletManager.getScanKey(account.mnemonic)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to decode scan key", e)
            null
        }
    }
    
    /**
     * Get total balance across all accounts
     */
    fun getTotalBalance(): Double {
        return _accounts.value.sumOf { 
            it.balance.toDoubleOrNull() ?: 0.0 
        }
    }
    
    // ========== Private Helper Methods ==========
    
    private fun saveAccount(account: Account) {
        val currentAccounts = _accounts.value.toMutableList()
        currentAccounts.add(account)
        saveAccounts(currentAccounts)
    }
    
    private fun saveAccounts(accounts: List<Account>) {
        val jsonArray = JSONArray()
        accounts.forEach { account ->
            val json = JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("accountIndex", account.accountIndex)
                put("address", account.address)
                put("publicKey", account.publicKey)
                put("scanKeyHex", account.scanKeyHex)
                put("mnemonic", account.mnemonic)
                put("balance", account.balance)
                put("isActive", account.isActive)
                put("createdAt", account.createdAt)
                put("lastUsed", account.lastUsed)
                put("color", account.color.name)
                put("restoreHeight", account.restoreHeight)
            }
            jsonArray.put(json)
        }
        
        prefs.edit().putString(KEY_ACCOUNTS, jsonArray.toString()).apply()
        _accounts.value = accounts
    }
    
    private fun loadAccounts() {
        try {
            val jsonString = prefs.getString(KEY_ACCOUNTS, null)
            
            if (jsonString == null) {
                // No accounts stored yet - try to initialize from existing wallet
                initializeFromExistingWallet()
                return
            }
            
            val jsonArray = JSONArray(jsonString)
            val accounts = mutableListOf<Account>()
            
            var migratedAccounts = false

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val storedScanKeyHex = json.optString("scanKeyHex", "")
                val storedAddress = json.getString("address")
                val storedPublicKey = json.getString("publicKey")

                val (address, publicKey) = if (storedScanKeyHex.isNotBlank()) {
                    try {
                        val scanKey = walletManager.getScanKeyFromHex(storedScanKeyHex)
                        val rederivedAddress = walletManager.deriveZokaNetworkAddressFromScanKey(scanKey)
                        val rederivedPublicKey = walletManager.importFromScanKeyHex(storedScanKeyHex).publicKey
                        if (rederivedAddress != storedAddress || rederivedPublicKey != storedPublicKey) {
                            migratedAccounts = true
                            android.util.Log.i(
                                "AccountManager",
                                "Migrated account address ${storedAddress.take(10)}... to ${rederivedAddress.take(10)}..."
                            )
                        }
                        rederivedAddress to rederivedPublicKey
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "AccountManager",
                            "Failed to rederive account ${storedAddress.take(10)}..., keeping stored address",
                            e
                        )
                        storedAddress to storedPublicKey
                    }
                } else {
                    storedAddress to storedPublicKey
                }

                val account = Account(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    accountIndex = json.getInt("accountIndex"),
                    address = address,
                    publicKey = publicKey,
                    scanKeyHex = storedScanKeyHex,
                    mnemonic = json.optString("mnemonic", ""),
                    balance = json.optString("balance", "0.0"),
                    isActive = json.getBoolean("isActive"),
                    createdAt = json.getLong("createdAt"),
                    lastUsed = json.getLong("lastUsed"),
                    color = try {
                        AccountColor.valueOf(json.getString("color"))
                    } catch (e: Exception) {
                        AccountColor.BLUE
                    },
                    // Older stored accounts predate restoreHeight; default 0
                    // makes them scan from genesis (safe, no history lost).
                    restoreHeight = json.optLong("restoreHeight", 0L)
                )
                accounts.add(account)
            }

            if (migratedAccounts) {
                saveAccounts(accounts)
            } else {
                _accounts.value = accounts
            }

            _accounts.value = accounts
            
            // Load active account
            val activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
            _activeAccount.value = accounts.find { it.id == activeId && it.isActive }
                ?: accounts.find { it.isActive }
                ?: accounts.firstOrNull()
            _activeAccount.value?.let { secureStorage.setActiveWallet(it.address) }
                
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to load accounts", e)
            _accounts.value = emptyList()
            // Try to recover from existing wallet
            initializeFromExistingWallet()
        }
    }
    
    /**
     * Clear all accounts (used when deleting wallet)
     */
    fun clearAllAccounts() {
        prefs.edit().clear().apply()
        _accounts.value = emptyList()
        _activeAccount.value = null
    }
}
