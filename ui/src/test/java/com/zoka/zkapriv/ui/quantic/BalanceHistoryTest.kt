package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.network.quantic.QuanticMovement
import org.junit.Assert.*
import org.junit.Test

class BalanceHistoryTest {
    private fun movement(id: String, height: Int, kind: String, amount: Long, fee: Long = 0) =
        QuanticMovement(id, height, 1000L + height, kind, amount, fee, "", 3)

    @Test fun reconstructsIncomingAndOutgoingIncludingFees() {
        val history = balanceHistory(124L, listOf(movement("b", 2, "sent", 25, 1), movement("a", 1, "received", 50)))!!
        assertEquals(listOf(100L, 150L, 124L), history.balances)
        assertEquals(24L, history.change)
        assertEquals(24.0, history.percent!!, 0.001)
    }
    @Test fun zeroOpeningBalanceDoesNotInventAPercentage() {
        assertNull(balanceHistory(50, listOf(movement("a", 1, "mined", 50)))!!.percent)
    }
    @Test fun unavailableHistoryDoesNotInventAChart() {
        assertNull(balanceHistory(100, emptyList()))
        assertNull(balanceHistory(10, listOf(movement("a", 1, "received", 50))))
        assertNull(balanceHistory(10, listOf(movement("a", 1, "unknown", 1))))
        assertNull(balanceHistory(10, listOf(movement("a", 1, "sent", Long.MAX_VALUE, 1))))
        assertNull(balanceHistory(10, listOf(movement("a", 1, "received", 1).copy(confirmations = 0))))
    }
    @Test fun duplicatedEntriesDoNotChangeTheChart() {
        val entry = movement("a", 1, "received", 50)
        assertEquals(balanceHistory(100, listOf(entry)), balanceHistory(100, listOf(entry, entry)))
    }
}
