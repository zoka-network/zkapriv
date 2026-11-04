package com.zoka.zkapriv.network.quantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class QuanticAmountTest {
    @Test fun decimalCommaIsNeverTurnedIntoTenTimesThePayment() {
        assertEquals(1_500_000L, QuanticWallet.parseZka("1,5"))
        assertEquals(1_500_000L, QuanticWallet.parseZka("1.5"))
        assertEquals(1L, QuanticWallet.parseZka("0,000001"))
        assertNull(QuanticWallet.parseZka("1.234,56"))
        assertNull(QuanticWallet.parseZka("1,234.56"))
        assertNull(QuanticWallet.parseZka("0.0000001"))
        assertNull(QuanticWallet.parseZka("-1"))
    }

    @Test fun maxAmountRoundTripsOnASpanishPhone() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("es", "AR"))
            val atoms = 1_234_567_890L
            val shown = QuanticWallet.formatZka(atoms)
            assertEquals("1,234.567890", shown)
            assertEquals(atoms, QuanticWallet.parseZka(shown.replace(",", "")))
        } finally { Locale.setDefault(previous) }
    }
}
