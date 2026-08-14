package com.zoka.zkapriv.network.api

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class MobileNativeCommandGateTest {
    @Test
    fun sixConcurrentBodiesEnterTheNativeQueueOneAtATime() = runBlocking {
        val gate = SerializedNativeCommandGate()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        val results = (0 until 6).map { body ->
            async(Dispatchers.Default) {
                gate.run(timeoutMs = 1_000L) {
                    val nowActive = active.incrementAndGet()
                    maximumActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                    try {
                        delay(15L)
                        body
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()

        assertEquals((0 until 6).toSet(), results.toSet())
        assertEquals(1, maximumActive.get())
    }

    @Test
    fun waitingForTheGateDoesNotConsumeTheCommandsTimeout() = runBlocking {
        withTimeout(2_000L) {
            val gate = SerializedNativeCommandGate()
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val first = async(Dispatchers.Default) {
                gate.run(timeoutMs = 1_000L) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
            }
            firstEntered.await()

            val second = async(Dispatchers.Default) {
                gate.run(timeoutMs = 50L) {
                    delay(10L)
                    "completed"
                }
            }

            // The second command waits longer than its own execution budget,
            // but that budget must not begin until the first command exits.
            delay(100L)
            assertFalse(second.isCompleted)
            releaseFirst.complete(Unit)
            first.await()
            assertEquals("completed", second.await())
        }
    }

    @Test
    fun kotlinBudgetsCoverTheNativeDeadlineAndJniDelivery() {
        assertEquals(180_000L, MobileNativeTimeoutPolicy.privateTxBodyMs())
        assertEquals(180_000L, MobileNativeTimeoutPolicy.rawBlocksMs(1L, 1L))
        assertEquals(240_000L, MobileNativeTimeoutPolicy.blockDigestsMs(18_121L, 18_121L))
        assertEquals(240_000L, MobileNativeTimeoutPolicy.chainHeightMs())
        assertEquals(125_000L, MobileNativeTimeoutPolicy.publishPrivateTxMs())
    }

    @Test
    fun digestBudgetTracksBothNativeChunkBoundariesAndSaturates() {
        assertEquals(240_000L, MobileNativeTimeoutPolicy.blockDigestsMs(1L, 32L))
        assertEquals(300_000L, MobileNativeTimeoutPolicy.blockDigestsMs(1L, 33L))
        assertEquals(2_100_000L, MobileNativeTimeoutPolicy.blockDigestsMs(1L, 1_000L))
        assertEquals(2_160_000L, MobileNativeTimeoutPolicy.blockDigestsMs(1L, 1_001L))
        assertThrows(IllegalArgumentException::class.java) {
            MobileNativeTimeoutPolicy.blockDigestsMs(2L, 1L)
        }
        assertEquals(
            Long.MAX_VALUE,
            MobileNativeTimeoutPolicy.blockDigestsMs(0L, Long.MAX_VALUE)
        )
    }
}
