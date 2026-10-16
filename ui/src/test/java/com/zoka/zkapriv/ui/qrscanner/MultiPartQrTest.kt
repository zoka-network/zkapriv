package com.zoka.zkapriv.ui.qrscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPartQrTest {
    /** Una zkq1 de prueba de 1.715 caracteres, como las reales. */
    private val zkq1 = "zkq1" + (0 until 1711).joinToString("") {
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"[it % 58].toString()
    }

    @Test
    fun aShortTextStaysASingleQrWithoutHeader() {
        val zka1 = "zka1" + "x".repeat(100)
        assertEquals(listOf(zka1), MultiPartQr.split(zka1))
    }

    @Test
    fun aZkq1IsSplitIntoSixSmallParts() {
        val parts = MultiPartQr.split(zkq1)
        assertEquals(6, parts.size)
        assertTrue(parts.all { it.length <= 320 })
        // Vector compartido con ZSilent (MultiPartQrTest de zsilent-core): las
        // dos apps tienen que armar las partes exactamente igual.
        assertEquals("82a56d5f", MultiPartQr.fingerprint(zkq1))
        assertEquals("zkqp:1/6:82a56d5f:zkq112345678", parts.first().take(30))
    }

    @Test
    fun partsInAnyOrderAndRepeatedRebuildTheAddress() {
        val parts = MultiPartQr.split(zkq1)
        val collector = MultiPartQrCollector()
        val sequence = listOf(3, 0, 3, 5, 1, 0, 2, 4).map { parts[it] }
        val results = sequence.map(collector::accept)

        assertEquals(MultiPartQrCollector.Result.Partial(1, 6), results.first())
        assertEquals(MultiPartQrCollector.Result.Complete(zkq1), results.last())
    }

    @Test
    fun aPartOfAnotherAddressStartsOver() {
        val parts = MultiPartQr.split(zkq1)
        val other = MultiPartQr.split(zkq1.replaceFirst("zkq11", "zkq19"))
        val collector = MultiPartQrCollector()
        collector.accept(parts[0])
        collector.accept(parts[1])

        assertEquals(MultiPartQrCollector.Result.Partial(1, 6), collector.accept(other[2]))
    }

    @Test
    fun aMisreadPartNeverProducesAWrongAddress() {
        val parts = MultiPartQr.split(zkq1).toMutableList()
        parts[2] = parts[2].dropLast(1) + "Z"
        val collector = MultiPartQrCollector()
        val results = parts.map(collector::accept)

        assertEquals(MultiPartQrCollector.Result.Ignored, results.last())
    }

    @Test
    fun malformedHeadersAreIgnored() {
        val collector = MultiPartQrCollector()
        for (text in listOf("zkqp:", "zkqp:0/6:82a56d5f:x", "zkqp:7/6:82a56d5f:x", "zkqp:1/6:corta:x", "zka1abc")) {
            assertEquals(text, MultiPartQrCollector.Result.Ignored, collector.accept(text))
        }
    }
}
