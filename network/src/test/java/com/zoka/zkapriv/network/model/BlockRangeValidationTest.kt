package com.zoka.zkapriv.network.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlockRangeValidationTest {
    @Test
    fun acceptsAnUnorderedButCompleteContiguousRange() {
        val response = response(block(3), block(1), block(2))

        val ordered = response.requireCompleteRange(1, 3)

        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.header?.blockHeight })
    }

    @Test
    fun rejectsMissingDuplicateAndForeignHeights() {
        listOf(
            response(block(1), block(3)),
            response(block(1), block(1), block(3)),
            response(block(0), block(1), block(2))
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                invalid.requireCompleteRange(1, 3)
            }
        }
    }

    @Test
    fun rejectsAChainThatDoesNotContinueItsAnchor() {
        val response = ZokaRawBlocksRangeResponse(
            fromHeight = 2,
            toHeight = 3,
            blocks = listOf(block(2), block(3))
        )

        assertThrows(IllegalArgumentException::class.java) {
            response.requireCompleteRange(2, 3, expectedPrevHash = "another-block")
        }
    }

    private fun response(vararg blocks: ZokaRawBlockResponse) =
        ZokaRawBlocksRangeResponse(
            fromHeight = 1,
            toHeight = 3,
            blocks = blocks.toList()
        )

    private fun block(height: Long) = ZokaRawBlockResponse(
        header = ZokaRawBlockHeader(
            blockHeight = height,
            hash = "hash-$height",
            prevHash = "hash-${height - 1}"
        )
    )
}
