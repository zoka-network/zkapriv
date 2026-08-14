package com.zoka.zkapriv.ui.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZokaPaymentQrTest {
    @Test
    fun parsesOpaqueZokaQrWithAmount() {
        val parsed = parseZokaPaymentQr("zoka:zka1recipient?amount=1.25")

        assertEquals("zka1recipient", parsed.address)
        assertEquals("1.25", parsed.amount)
    }

    @Test
    fun parsesOpaqueZokaQrWithoutAmount() {
        val parsed = parseZokaPaymentQr("zoka:zka1recipient")

        assertEquals("zka1recipient", parsed.address)
        assertNull(parsed.amount)
    }

    @Test
    fun findsAmountAmongOtherQueryParameters() {
        val parsed = parseZokaPaymentQr("zoka:zka1recipient?label=wallet&amount=0.001")

        assertEquals("0.001", parsed.amount)
    }

    @Test
    fun rejectsMissingRecipient() {
        assertThrows(IllegalArgumentException::class.java) {
            parseZokaPaymentQr("zoka:?amount=1")
        }
    }
}
