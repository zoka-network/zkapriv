package com.zoka.zkapriv.core.util

object Constants {
    // Network
    const val ZOKA_MAINNET_RPC = "http://157.230.5.103:3000/"
    const val COINGECKO_API = "https://api.coingecko.com/api/v3/"
    const val ZOKA_CHAIN_ID = "zoka-mainnet-v1"

    // Token economics (economics.testnet.v1.json / mainnet)
    const val ATOMS_PER_ZKA = 1_000_000L          // 1 ZKA = 1,000,000 atoms (decimals = 6)
    const val MIN_FEE_ATOMS = 100L                 // fee_market_step = 100 atoms = 0.0001 ZKA
    const val DEFAULT_FEE_ATOMS = 1_000L           // 0.001 ZKA default fee

    // Zoka public URLs
    const val ZOKA_WEBSITE_URL = "https://zoka.network"
    const val ZOKA_EXPLORER_URL = "https://www.zokascan.cc"
    const val ZOKA_EXPLORER_TX_URL = "https://www.zokascan.cc/?q="
    const val ZOKA_EXPLORER_ACCOUNT_URL = "https://www.zokascan.cc/address"
    
    // Preferences
    const val PREF_ENCRYPTED_FILE = "zoka_wallet_prefs"
    const val PREF_KEY_ACTIVE_WALLET = "active_wallet"
    const val PREF_KEY_RPC_ENDPOINT = "rpc_endpoint"
    
    // BIP-39
    const val MNEMONIC_WORD_COUNT = 24
    
    // QR Code
    const val ZOKA_QR_SCHEME = "zoka:"
    const val QR_PARAM_AMOUNT = "amount"
    const val QR_PARAM_TOKEN = "token"
    
    // Cache
    const val PRICE_CACHE_DURATION = 30_000L // 30 seconds
    
    // UI
    const val ADDRESS_DISPLAY_CHARS = 8
}
