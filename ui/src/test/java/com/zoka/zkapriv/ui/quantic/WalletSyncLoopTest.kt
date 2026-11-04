package com.zoka.zkapriv.ui.quantic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletSyncLoopTest {
    @Test fun aFailedReadDoesNotStopForegroundBalanceUpdates() = runTest {
        var calls = 0
        val loop = WalletSyncLoop(backgroundScope, {
            calls++
            if (calls == 1) error("La red se cortó")
        }, { 10_000 })
        loop.start()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, calls)
        loop.stop()
    }

    @Test fun repeatedRefreshesNeverOverlapAndAreCoalesced() = runTest {
        var calls = 0
        var active = 0
        var maximum = 0
        val loop = WalletSyncLoop(backgroundScope, {
            calls++; active++; maximum = maxOf(maximum, active)
            delay(1_000)
            active--
        }, { 10_000 })
        loop.start()
        loop.start()
        runCurrent()
        repeat(20) { loop.request() }
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, maximum)
        loop.stop()
    }

    @Test fun returningToTheAppReadsImmediatelyWithoutAnOldTimer() = runTest {
        var calls = 0
        val loop = WalletSyncLoop(backgroundScope, { calls++ }, { 10_000 })
        loop.start()
        runCurrent()
        loop.stop()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, calls)
        loop.start()
        runCurrent()
        assertEquals(2, calls)
        loop.stop()
    }

    @Test fun finishingASendWakesTheReaderBeforeTheNextPoll() = runTest {
        var calls = 0
        val loop = WalletSyncLoop(backgroundScope, { calls++ }, { 10_000 })
        loop.start()
        runCurrent()
        advanceTimeBy(250)
        loop.request()
        runCurrent()
        assertEquals(2, calls)
        loop.stop()
    }
}
