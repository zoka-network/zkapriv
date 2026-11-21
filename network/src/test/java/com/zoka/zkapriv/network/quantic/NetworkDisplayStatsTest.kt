package com.zoka.zkapriv.network.quantic

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class NetworkDisplayStatsTest {
    private fun response(hashrate: Long, bits: Int) = mock(JSONObject::class.java).also {
        `when`(it.optLong("hashrate_estimate_hps", 0)).thenReturn(hashrate)
        `when`(it.optInt("next_difficulty_bits", -1)).thenReturn(bits)
    }
    @Test fun readsBothFiguresFromTheExistingStatusResponse() {
        assertEquals(NetworkDisplayStats(3_480_000_000, 29), NetworkDisplayStats.from(response(3_480_000_000, 29)))
    }
    @Test fun missingAndOutOfRangeValuesStayUnavailable() {
        assertEquals(NetworkDisplayStats(0, null), NetworkDisplayStats.from(response(-1, -1)))
        assertNull(NetworkDisplayStats.from(response(0, 257)).difficultyBits)
    }
    @Test fun zeroDifficultyIsAValidValue() {
        assertEquals(0, NetworkDisplayStats.from(response(0, 0)).difficultyBits)
    }
}
