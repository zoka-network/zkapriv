package com.zoka.zkapriv.ui.avatar

import java.security.MessageDigest

/**
 * Public visual identity, version 1. No Android, storage, network or wallet dependencies.
 * Input is the canonical, case-sensitive public identifier, never a local wallet id.
 * Do not change these domains, palette order or byte mapping in a future version.
 */
object AvatarMosaic {
    const val VERSION = 1
    const val SIDE = 9
    const val CELL_COUNT = SIDE * SIDE
    private const val SEED_DOMAIN = "ZKAPriv/avatar/v1/seed\u0000"
    private const val GRID_DOMAIN = "ZKAPriv/avatar/v1/grid\u0000"

    // Fixed sRGB: black, charcoal, light grey, white, Quantic pink.
    val palette: List<Long> = listOf(0xFF080809, 0xFF303036, 0xFFAAAAAF, 0xFFFAFAFA, 0xFFF2ABD0)
    const val BACKGROUND: Long = 0xFF101012

    private fun valid(accountId: String) = accountId.length in 1..256 && accountId.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in ":._-"
    }

    private fun seed(accountId: String): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        hash.update(SEED_DOMAIN.toByteArray(Charsets.UTF_8))
        return hash.digest(accountId.toByteArray(Charsets.UTF_8))
    }

    /**
     * The readable form of the same identity: the first 4 bytes of the v1 seed,
     * upper-case hex in two groups (`F4A4-595C`). Recognition aid, not a check.
     */
    fun code(accountId: String): String? {
        if (!valid(accountId)) return null
        val hex = seed(accountId).take(4).joinToString("") { "%02X".format(it.toInt() and 0xff) }
        return hex.substring(0, 4) + "-" + hex.substring(4)
    }

    /**
     * The Zoka ID code, version 2: the first 10 bytes of the same seed (80 bits),
     * upper-case hex in five groups (`F4A4-595C-AF1B-A80B-914F`). The first two
     * groups are the version 1 [code]. Long enough to confirm a recipient: see
     * docs/ZOKA-ID.md in the node repository. The input is a short address.
     */
    fun codeV2(accountId: String): String? {
        if (!valid(accountId)) return null
        val hex = seed(accountId).take(10).joinToString("") { "%02X".format(it.toInt() and 0xff) }
        return hex.chunked(4).joinToString("-")
    }

    /** Null means unavailable/invalid: the view draws a neutral placeholder, not an identity. */
    fun generate(accountId: String): List<Int>? {
        if (!valid(accountId)) return null
        val hash = MessageDigest.getInstance("SHA-256")
        val seed = seed(accountId)
        val cells = ArrayList<Int>(CELL_COUNT)
        var counter = 0
        while (cells.size < CELL_COUNT) {
            hash.update(GRID_DOMAIN.toByteArray(Charsets.UTF_8))
            hash.update(seed)
            val block = hash.digest(byteArrayOf(
                (counter ushr 24).toByte(), (counter ushr 16).toByte(),
                (counter ushr 8).toByte(), counter.toByte(),
            ))
            counter++
            for (byte in block) {
                val value = byte.toInt() and 0xff
                // 255 is divisible by 5: rejection avoids a modulo bias.
                if (value < 255) cells.add(value % 5)
                if (cells.size == CELL_COUNT) break
            }
        }
        return cells.toList()
    }
}
