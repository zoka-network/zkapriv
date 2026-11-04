package com.zoka.zkapriv.ui.quantic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeFormatTest {
    @Test
    fun amounts_keep_two_to_six_decimals_and_group_thousands() {
        assertEquals("12,450.00", displayZka(12_450_000_000))
        assertEquals("5,000,000.0092", displayZka(5_000_000_009_200))
        assertEquals("0.0002", displayZka(200))
        assertEquals("12.50", displayZka(12_500_000))
        assertEquals("0.00", displayZka(0))
    }

    @Test
    fun activity_times_read_like_the_design() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val today = LocalDate.of(2026, 11, 3)
        fun at(day: Int, hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toEpochSecond()
        assertEquals("Today · 14:32", activityTime(at(3, 14, 32), zone, today))
        assertEquals("Yesterday · 18:06", activityTime(at(2, 18, 6), zone, today))
        assertEquals("Oct 24 · 09:17", activityTime(ZonedDateTime.of(2026, 10, 24, 9, 17, 0, 0, zone).toEpochSecond(), zone, today))
    }

    @Test
    fun hashrate_uses_the_explorer_units() {
        assertEquals("841 H/s", displayHashrate(841))
        assertEquals("1.5 kH/s", displayHashrate(1_501))
        assertEquals("2.4 MH/s", displayHashrate(2_400_000))
        assertEquals("—", displayHashrate(0))
    }
}
