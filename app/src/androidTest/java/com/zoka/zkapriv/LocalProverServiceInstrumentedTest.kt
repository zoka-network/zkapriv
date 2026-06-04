package com.zoka.zkapriv

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zoka.zkapriv.network.nativebridge.LocalProverClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalProverServiceInstrumentedTest {
    @Test
    fun pingCrossesPrivateProverProcess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("pong", LocalProverClient(context).ping())
    }

    @Test
    fun largePingPayloadBypassesBinderTransactionLimit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val largerThanBinderTransaction = "x".repeat(1024 * 1024 + 64 * 1024)

        assertEquals("pong", LocalProverClient(context).ping(largerThanBinderTransaction))
    }

    @Test
    fun v2I1LabBenchmarkRunsInPrivateProverProcess() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val setupDirectory = File(context.filesDir, "trusted-setup-v2-i1-service-lab")
        check(setupDirectory.mkdirs() || setupDirectory.isDirectory)
        instrumentation.context.assets
            .open("trusted-setup-lab/private_tx_v2_i1.params")
            .use { source ->
                File(setupDirectory, "private_tx_v2_i1.params").outputStream().use { target ->
                    source.copyTo(target)
                }
            }

        val result = LocalProverClient(context).benchmarkV2I1Lab(setupDirectory.absolutePath)
        val response = Gson().fromJson(result, JsonObject::class.java)

        check(!response.has("error")) { result }
        assertEquals("private_tx_v2_i1", response.get("profile").asString)
        assertEquals(192, response.get("proof_bytes").asInt)
        Log.i("ZokaMobileBenchmark", "V2_I1_SERVICE_RESULT=$result")
        Unit
    }
}
