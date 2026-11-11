package com.zoka.zkapriv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zoka.zkapriv.network.nativebridge.ZokaStark
import com.zoka.zkapriv.network.quantic.ViewKeyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sin red: solo una frase efímera y su propio directorio. Nunca abre el almacén del usuario. */
@RunWith(AndroidJUnit4::class)
class StarkViewKeyInstrumentedTest {
    @Test fun bothScopesUseTheExistingWalletWithoutChangingItsState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.cacheDir.resolve("stark-view-key-test-${System.nanoTime()}")
        check(directory.mkdir())
        val phrase = ZokaStark.newPhrase()
        try {
            val beforeAddress = ZokaStark.walletInfo(phrase).getString("address")
            val beforeState = ZokaStark.status(phrase, directory.absolutePath)
            val incoming = ZokaStark.viewKey(phrase)
            val full = ZokaStark.viewKey(phrase, ViewKeyScope.FULL)
            assertTrue(incoming.startsWith("zqtestviewin2"))
            assertTrue(full.startsWith("zqtestview2"))
            assertNotEquals(incoming, full)
            assertEquals(incoming, ZokaStark.viewKey(phrase, ViewKeyScope.INCOMING))
            assertEquals(beforeAddress, ZokaStark.walletInfo(phrase).getString("address"))
            assertEquals(beforeState.toString(), ZokaStark.status(phrase, directory.absolutePath).toString())
        } finally {
            directory.deleteRecursively()
        }
    }
}
