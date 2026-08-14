package com.zoka.zkapriv.security.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivateTxOutboxCodecTest {
    @Test
    fun roundTripPreservesAllCrashRecoveryMetadata() {
        val record = PrivateTxOutboxRecord(
            hash = "tx-hash",
            address = "zka1destino",
            reservedAtoms = 1_250_000L,
            requestKey = "request-key",
            bodyJson = """{"tx_hash":"tx-hash","memo":"separators | and ñ"}"""
        )

        assertEquals(record, PrivateTxOutboxCodec.decode(PrivateTxOutboxCodec.encode(record)))
    }

    @Test
    fun rejectsRecordsThatCannotSafelyRestoreAReservation() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivateTxOutboxCodec.encode(
                PrivateTxOutboxRecord(
                    hash = "tx-hash",
                    address = "zka1destino",
                    reservedAtoms = 0L,
                    requestKey = "request-key",
                    bodyJson = "{}"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrivateTxOutboxCodec.decode("legacy-body-without-safety-metadata")
        }
    }
}
