package com.zoka.zkapriv.network.nativebridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StarkPageHexTest {
    @Test fun binaryPagesKeepLeadingZerosAndUnsignedBytes() {
        assertEquals("", encodeScanPage(byteArrayOf()))
        assertEquals("0000ff010080", encodeScanPage(byteArrayOf(0, 0, -1, 1, 0, -128)))
    }

    @Test fun everyByteValueSurvivesTheJniWireFormat() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = encodeScanPage(bytes)
        assertEquals(bytes.size * 2, encoded.length)
        val decoded = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(bytes, decoded)
    }
}
