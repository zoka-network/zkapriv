package com.zoka.zkapriv

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zoka.zkapriv.network.nativebridge.ZokaNativeWallet
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileProverBenchmarkInstrumentedTest {
    @Test
    fun benchmarkSyntheticPrivateTransactions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val setupDirectory = File(context.filesDir, "trusted-setup-benchmark")
        copyTrustedSetup(setupDirectory)
        val preloadResult = ZokaNativeWallet.preload(setupDirectory.absolutePath)
        check(!preloadResult.contains("\"error\"")) { preloadResult }
        Log.i("ZokaMobileBenchmark", "PRELOAD_RESULT=$preloadResult")
        println("ZOKA_MOBILE_PRELOAD_RESULT=$preloadResult")
        val inputCounts = InstrumentationRegistry.getArguments()
            .getString("zokaBenchInputs", "1,2,4,8")
            .split(',')
            .map { it.trim().toInt() }

        for (inputCount in inputCounts) {
            val result = ZokaNativeWallet.benchmark(inputCount, setupDirectory.absolutePath)
            check(!result.contains("\"error\"")) { result }
            Log.i("ZokaMobileBenchmark", "BENCH_RESULT=$result")
            println("ZOKA_MOBILE_BENCH_RESULT=$result")
        }
    }

    @Test
    fun benchmarkSyntheticPrivateTxV2I1Lab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val setupDirectory = File(instrumentation.targetContext.filesDir, "trusted-setup-v2-i1-lab")
        check(setupDirectory.mkdirs() || setupDirectory.isDirectory)
        instrumentation.context.assets
            .open("trusted-setup-lab/private_tx_v2_i1.params")
            .use { source ->
                File(setupDirectory, "private_tx_v2_i1.params").outputStream().use { target ->
                    source.copyTo(target)
                }
            }

        val result = ZokaNativeWallet.benchmarkV2I1Lab(setupDirectory.absolutePath)
        check(!result.contains("\"error\"")) { result }
        Log.i("ZokaMobileBenchmark", "V2_I1_RESULT=$result")
        println("ZOKA_MOBILE_V2_I1_RESULT=$result")
    }

    private fun copyTrustedSetup(destinationDirectory: File) {
        check(destinationDirectory.mkdirs() || destinationDirectory.isDirectory)
        val names = arrayOf(
            "balance.params",
            "merkle.params",
            "nullifier.params",
            "private_tx.params",
            "DIGEST"
        )
        for (name in names) {
            val destination = File(destinationDirectory, name)
            InstrumentationRegistry.getInstrumentation().targetContext.assets
                .open("trusted-setup/$name")
                .use { source ->
                    destination.outputStream().use { target -> source.copyTo(target) }
                }
        }
    }
}
