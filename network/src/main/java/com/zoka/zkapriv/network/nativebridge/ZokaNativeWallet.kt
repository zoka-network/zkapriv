package com.zoka.zkapriv.network.nativebridge

object ZokaNativeWallet {
    private val loadError: Throwable? = try {
        System.loadLibrary("zoka_mobile")
        null
    } catch (error: Throwable) {
        error
    }

    fun build(requestJson: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return buildPrivateTransaction(requestJson)
    }

    fun benchmark(inputCount: Int, trustedSetupDirectory: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return benchmarkPrivateTransaction(inputCount, trustedSetupDirectory)
    }

    fun preload(trustedSetupDirectory: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return preloadTrustedSetup(trustedSetupDirectory)
    }

    /**
     * Dirección zkq1 de la wallet y altura de activación de la red, en JSON:
     * `supported`, `activation_height` (nulo = no activada) y `hybrid_address`.
     * La app muestra la zkq1 solo con altura: antes nadie puede pagarle.
     */
    fun walletHybridInfo(seedHex: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return hybridWalletInfo(seedHex)
    }

    /**
     * Reconoce salidas v2 (pagos a una zkq1) con X-Wing y devuelve, en el mismo
     * orden, el monto de las propias y nulo para las ajenas. Hasta
     * [MAX_HYBRID_OUTPUTS_PER_CALL] salidas por llamada.
     */
    fun scanHybrid(requestJson: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return scanHybridOutputs(requestJson)
    }

    /** El mismo tope que `zoka::mobile::MOBILE_HYBRID_SCAN_MAX_OUTPUTS`. */
    const val MAX_HYBRID_OUTPUTS_PER_CALL = 512

    fun benchmarkV2I1Lab(trustedSetupDirectory: String): String {
        val error = loadError
        check(error == null) {
            "Local Zoka transaction engine is not packaged for this device: ${error?.message}"
        }
        return benchmarkPrivateTransactionV2I1Lab(trustedSetupDirectory)
    }

    @JvmStatic
    private external fun buildPrivateTransaction(requestJson: String): String

    @JvmStatic
    private external fun hybridWalletInfo(seedHex: String): String

    @JvmStatic
    private external fun scanHybridOutputs(requestJson: String): String

    @JvmStatic
    private external fun benchmarkPrivateTransaction(
        inputCount: Int,
        trustedSetupDirectory: String
    ): String

    @JvmStatic
    private external fun preloadTrustedSetup(trustedSetupDirectory: String): String

    @JvmStatic
    private external fun benchmarkPrivateTransactionV2I1Lab(trustedSetupDirectory: String): String
}
