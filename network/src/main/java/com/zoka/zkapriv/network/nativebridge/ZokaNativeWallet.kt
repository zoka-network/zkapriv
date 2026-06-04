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
    private external fun benchmarkPrivateTransaction(
        inputCount: Int,
        trustedSetupDirectory: String
    ): String

    @JvmStatic
    private external fun preloadTrustedSetup(trustedSetupDirectory: String): String

    @JvmStatic
    private external fun benchmarkPrivateTransactionV2I1Lab(trustedSetupDirectory: String): String
}
