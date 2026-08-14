package com.zoka.zkapriv.network.model

/**
 * Exige que una respuesta cubra exactamente el rango solicitado y forme una
 * cadena continua. Un resumen parcial nunca puede convertirse en un
 * checkpoint: omitir una sola altura podrÃ­a ocultar un ingreso o un gasto.
 */
internal fun ZokaRawBlocksRangeResponse.requireCompleteRange(
    from: Long,
    to: Long,
    expectedPrevHash: String? = null
): List<ZokaRawBlockResponse> {
    require(from <= to) { "Invalid block range $from..$to" }
    require(fromHeight == from && toHeight == to) {
        "Block response metadata ${fromHeight}..${toHeight} does not match $from..$to"
    }
    val expectedCount = to - from + 1
    require(expectedCount <= Int.MAX_VALUE && blocks.size == expectedCount.toInt()) {
        "Incomplete block range $from..$to: received ${blocks.size} entries"
    }

    val byHeight = linkedMapOf<Long, ZokaRawBlockResponse>()
    for (block in blocks) {
        val header = requireNotNull(block.header) { "Block range contains an entry without header" }
        require(header.blockHeight in from..to) {
            "Block ${header.blockHeight} is outside requested range $from..$to"
        }
        require(header.hash.isNotBlank()) { "Block ${header.blockHeight} has no hash" }
        require(byHeight.put(header.blockHeight, block) == null) {
            "Block ${header.blockHeight} is duplicated"
        }
    }

    val ordered = (from..to).map { height ->
        byHeight[height] ?: error("Block $height is missing from range $from..$to")
    }
    expectedPrevHash?.takeIf(String::isNotBlank)?.let { expected ->
        require(ordered.first().header?.prevHash == expected) {
            "Block $from does not continue the saved chain anchor"
        }
    }
    ordered.zipWithNext().forEach { (previous, current) ->
        require(current.header?.prevHash == previous.header?.hash) {
            "Blocks ${previous.header?.blockHeight} and ${current.header?.blockHeight} are not contiguous"
        }
    }
    return ordered
}
