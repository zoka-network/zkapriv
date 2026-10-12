package com.zoka.zkapriv.core.util

object Constants {
    // Semillas de la red P2P.
    //
    // La wallet ya no le pide los datos de la cadena a ningún servidor: se
    // conecta a la red y le pide los bloques a los pares. Estas son las
    // direcciones a las que marca al arrancar, tomadas del manifiesto
    // docs/mainnet-network.v1.json.
    //
    // Son direcciones onion y solo se alcanzan por Tor, que la app levanta
    // adentro (Arti). Un nodo casero no tiene puerto abierto ni IP fija; la
    // onion es lo que lo hace alcanzable sin depender de eso.
    // Mas de una, a proposito.
    //
    // Con un solo seed la wallet no tiene descubrimiento: se conecta a ese o a
    // ninguno, y cada vez que ese par tarda, la app entera espera los noventa
    // segundos de PEER_WAIT. Medido contra la red real el 16/08: habia tres
    // onions vivas y la app conocia una.
    //
    // Que Kademlia este encendida no alcanza. El movil sale por Tor y solo
    // puede alcanzar direcciones onion; los pares caseros que descubra por IP
    // no tienen puerto abierto y no le sirven. Si las onions de la red no
    // llegan hasta el, el descubrimiento no tiene con que trabajar.
    val ZOKA_P2P_SEEDS = listOf(
        "/onion3/d5pk3nhesk5wlvz4f5unbhcozxgtj7u6mhagauyprjrxndzpwbaoeiid:28080",
        "/onion3/wtrwvlzli37utagttkwmqipm25uqtr4oiib4k6jpgjpyddqk2352btad:28080",
        "/onion3/uzn6e2cxyihxuue2j2wsp4dhtloltqq4ai6gkspjoruu54ikhmtfgayd:28080"
    )

    // RPC HTTP heredado.
    //
    // Los dos nodos de DigitalOcean a los que apuntaba esto se dieron de baja y
    // dejaron la wallet muerta. Ya nadie le pregunta la cadena a esta URL —los
    // datos salen de la red P2P—, pero sigue existiendo porque Retrofit exige
    // una URL base válida al construirse y porque la pantalla de ajustes deja
    // apuntar a un nodo propio.
    const val ZOKA_MAINNET_RPC         = "http://127.0.0.1:3000/"
    const val ZOKA_MAINNET_RPC_FALLBACK = "http://127.0.0.1:3000/"
    const val COINGECKO_API = "https://api.coingecko.com/api/v3/"
    const val ZOKA_CHAIN_ID = "zoka-mainnet-zenith-v1"

    // La cadena actual (Zenith) es la testnet de Zoka Quantic desde octubre de
    // 2026 y la app se presenta como cliente de Quantic: sus ZKA son de prueba
    // y los saldos no pasan a la mainnet. El chain id conserva "mainnet"
    // porque de el sale el hash del genesis y no se puede renombrar; lo que se
    // le muestra a la persona es este nombre, nunca el chain id. Es el mismo
    // nombre que muestra ZSilent.
    const val ZOKA_NETWORK_DISPLAY_NAME = "Zoka Quantic · Testnet"

    // Altura que ya existia cuando se compilo esta version (la cadena tenia
    // 25.204 bloques el 11/10/2026). Una wallet creada con esta app no puede
    // tener nada antes, asi que es un piso seguro para empezar a escanear
    // cuando la red todavia no respondio. Subirla en cada release.
    const val WALLET_BIRTHDAY_FLOOR = 25_200L

    // Token economics (economics.testnet.v1.json / mainnet)
    const val ATOMS_PER_ZKA = 1_000_000L          // 1 ZKA = 1,000,000 atoms (decimals = 6)
    const val MIN_FEE_ATOMS = 100L                 // fee_market_step = 100 atoms = 0.0001 ZKA
    const val DEFAULT_FEE_ATOMS = 1_000L           // 0.001 ZKA default fee

    // Zoka public URLs
    const val ZOKA_WEBSITE_URL = "https://zoka.network"
    const val ZOKA_EXPLORER_URL = "https://www.zokascan.cc"
    const val ZOKA_EXPLORER_TX_URL = "https://www.zokascan.cc/tx/"
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
