package com.zoka.zkapriv.network.quantic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ViewKeyScopeTest {
    @Test fun anIncomingRequestNeverAcceptsTheFullKeyReturnedByAnOldEngine() {
        assertThrows(IllegalStateException::class.java) {
            ViewKeyScope.INCOMING.validateExport("", "zqtestview2fixture")
        }
        assertThrows(IllegalStateException::class.java) {
            ViewKeyScope.INCOMING.validateExport("full", "zqtestview2fixture")
        }
        assertThrows(IllegalStateException::class.java) {
            ViewKeyScope.INCOMING.validateExport("incoming", "zqtestview2fixture")
        }
    }

    @Test fun eachScopeRequiresItsOwnKindAndNetworkPrefix() {
        assertEquals("zqtestviewin2fixture", ViewKeyScope.INCOMING.validateExport("incoming", "zqtestviewin2fixture"))
        assertEquals("zqtestview2fixture", ViewKeyScope.FULL.validateExport("full", "zqtestview2fixture"))
        for (invalid in listOf("zqviewin2fixture", "zqtestviewin2", "")) {
            assertThrows(IllegalStateException::class.java) {
                ViewKeyScope.INCOMING.validateExport("incoming", invalid)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            ViewKeyScope.FULL.validateExport("incoming", "zqtestviewin2fixture")
        }
    }

    @Test fun aWalletChangedWhileWaitingIsNeverExported() = runBlocking {
        var called = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                exportViewKeyForWallet("first", { "second" }, ViewKeyScope.INCOMING) {
                    called = true
                    "must not be returned"
                }
            }
        }
        assertFalse(called)
    }

    @Test fun aLateResultForAnotherWalletIsDiscarded() {
        var active = "first"
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                exportViewKeyForWallet("first", { active }, ViewKeyScope.INCOMING) {
                    active = "second"
                    "late result"
                }
            }
        }
    }

    @Test fun cancellationDuringNativeDerivationDiscardsTheKey() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                val job = coroutineContext[Job]!!
                exportViewKeyForWallet("first", { "first" }, ViewKeyScope.INCOMING) {
                    job.cancel()
                    "late result"
                }
            }
        }
    }

    @Test fun aSuccessfulExportKeepsTheSelectedScopeAndWallet() = runBlocking {
        val result = exportViewKeyForWallet("first", { "first" }, ViewKeyScope.INCOMING) {
            assertEquals(ViewKeyScope.INCOMING, it)
            "incoming result"
        }
        assertEquals("incoming result", result)
    }
}
