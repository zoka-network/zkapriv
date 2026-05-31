package com.zoka.zkapriv.core.util

/**
 * Minimal bech32 encoder/validator (BIP-0173).
 * Used to produce and validate Zoka zka1... addresses.
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = longArrayOf(0x3b6a57b2L, 0x26508e6dL, 0x1ea119faL, 0x3d4233ddL, 0x2a1462b3L)

    private fun polymod(values: IntArray): Long {
        var chk = 1L
        for (v in values) {
            val b = (chk ushr 25).toInt()
            chk = ((chk and 0x1ffffffL) shl 5) xor v.toLong()
            for (i in 0..4) {
                if ((b ushr i) and 1 != 0) chk = chk xor GEN[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val out = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            out[i] = hrp[i].code ushr 5
            out[i + hrp.length + 1] = hrp[i].code and 31
        }
        out[hrp.length] = 0
        return out
    }

    private fun createChecksum(hrp: String, data: IntArray): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6)
        val poly = polymod(values) xor 1L
        return IntArray(6) { i -> ((poly ushr (5 * (5 - i))) and 31L).toInt() }
    }

    /** Convert between bit-width groups (e.g. 8→5 for encoding, 5→8 for decoding). */
    fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            val v = b.toInt() and 0xff
            if (v ushr fromBits != 0) return null
            acc = (acc shl fromBits) or v
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }
        return result.toIntArray()
    }

    /** Encode raw bytes with given HRP. Returns e.g. "zka1abc..." */
    fun encode(hrp: String, data8bit: ByteArray): String {
        val data5bit = convertBits(data8bit, 8, 5, true)
            ?: throw IllegalArgumentException("bech32 convertBits failed")
        val checksum = createChecksum(hrp, data5bit)
        val combined = data5bit + checksum
        return hrp + "1" + combined.joinToString("") { CHARSET[it].toString() }
    }

    /** Verify checksum and HRP. Returns true if address is a valid bech32 with given HRP. */
    fun isValid(address: String, hrp: String): Boolean {
        return try {
            val lower = address.lowercase()
            val sep = lower.lastIndexOf('1')
            if (sep < 1 || sep + 7 > lower.length) return false
            if (lower.substring(0, sep) != hrp) return false
            val dataPart = lower.substring(sep + 1)
            val data = IntArray(dataPart.length)
            for (i in dataPart.indices) {
                val idx = CHARSET.indexOf(dataPart[i])
                if (idx < 0) return false
                data[i] = idx
            }
            polymod(hrpExpand(hrp) + data) == 1L
        } catch (_: Exception) {
            false
        }
    }
}
