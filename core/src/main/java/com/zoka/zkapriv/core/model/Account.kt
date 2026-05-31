package com.zoka.zkapriv.core.model

/**
 * Represents a single account in a multi-account wallet
 * Each account has its own unique scan key and seed phrase.
 * Zoka private wallets use BIP-39 entropy as the scan key. The zka1 address is
 * derived from scan/spend Ristretto public keys.
 */
data class Account(
    val id: String,                    // Unique identifier (UUID)
    val name: String,                  // User-friendly name (e.g., "Main Account", "Savings")
    val accountIndex: Int,             // Sequential index (0, 1, 2, ...) for ordering
    val address: String,               // Zoka private address (zka1...)
    val publicKey: String,             // Canonical zpriv public receive material
    val scanKeyHex: String = "",       // Raw Zoka scan key in hex (encrypted in storage)
    val mnemonic: String = "",         // 24-word seed phrase for this account (encrypted)
    val balance: String = "0.0",       // Current balance in ZKA
    val isActive: Boolean = false,     // Whether this is the currently active account
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val color: AccountColor = AccountColor.BLUE  // UI color for visual distinction
)

/**
 * Predefined colors for account visual distinction
 */
enum class AccountColor(val hex: String) {
    BLUE("#2196F3"),
    GREEN("#4CAF50"),
    PURPLE("#9C27B0"),
    ORANGE("#FF9800"),
    RED("#F44336"),
    TEAL("#009688"),
    PINK("#E91E63"),
    INDIGO("#3F51B5"),
    CYAN("#00BCD4"),
    LIME("#CDDC39");
    
    companion object {
        fun fromIndex(index: Int): AccountColor {
            val values = values()
            return values[index % values.size]
        }
    }
}

/**
 * Request to create a new account
 */
data class CreateAccountRequest(
    val name: String,
    val color: AccountColor? = null  // Optional, will auto-assign if null
)

/**
 * Request to update an existing account
 */
data class UpdateAccountRequest(
    val accountId: String,
    val name: String? = null,
    val color: AccountColor? = null
)
