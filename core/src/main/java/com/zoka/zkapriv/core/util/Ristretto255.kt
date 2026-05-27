package com.zoka.zkapriv.core.util

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Minimal Ristretto255 implementation for Zoka address derivation.
 *
 * Matches the Zoka node path based on curve25519-dalek:
 * scalar = SHA256(secretBytes) interpreted little-endian mod L
 * point = scalar * RISTRETTO_BASEPOINT
 * pubkey = ristretto_encode(point)
 */
object Ristretto255 {

    private val P = BigInteger.TWO.pow(255) - BigInteger.valueOf(19)
    private val L = BigInteger("7237005577332262213973186563042994240857116359379907606001950938285454250989")
    private val SQRT_M1 = BigInteger("19681161376707505956807079304988542015446066515923890162744021073123829784752")
    private val INVSQRT_A_MINUS_D = BigInteger("25063068953384623474111414158702152701244531502492656460079210482610430750235")
    private val D = BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555")
    private val P58 = (P - BigInteger.valueOf(5)) / BigInteger.valueOf(8)

    private data class Point(val X: BigInteger, val Y: BigInteger, val Z: BigInteger, val T: BigInteger)

    private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private val BASE = Point(
        X = BigInteger("15112221349535807912866137220509078750507884956996801868913811283870284295234"),
        Y = BigInteger("46316835694926478169428394003475163141307993866256225615783033011972563504410"),
        Z = BigInteger.ONE,
        T = BigInteger("46827403850823179245885197657976651541823052238390700758014975586879097887992")
    )

    private fun fadd(a: BigInteger, b: BigInteger) = (a + b).mod(P)
    private fun fsub(a: BigInteger, b: BigInteger) = ((a - b) % P + P) % P
    private fun fmul(a: BigInteger, b: BigInteger) = (a * b).mod(P)
    private fun fneg(a: BigInteger) = if (a.signum() == 0) BigInteger.ZERO else P - a.mod(P)
    private fun fisNeg(a: BigInteger) = a.mod(P).testBit(0)
    private fun inv(a: BigInteger): BigInteger = a.modPow(P - BigInteger.TWO, P)

    /** sqrt(u/v): matches Python sqm1(u,v) — no sign forcing. */
    private fun sqm1(u: BigInteger, v: BigInteger): BigInteger {
        val v3 = fmul(fmul(v, v), v)
        val v7 = fmul(fmul(v3, v3), v)
        val r = fmul(fmul(u, v3), fmul(u, v7).modPow(P58, P))
        val c = fmul(v, fmul(r, r))
        return if (c == u.mod(P)) r else fmul(r, SQRT_M1)
    }

    /** sqrt(u/v) with was_square flag: matches Python sqm1_flag(u,v). */
    private fun sqm1Flag(u: BigInteger, v: BigInteger): Pair<Boolean, BigInteger> {
        val v3 = fmul(fmul(v, v), v)
        val v7 = fmul(fmul(v3, v3), v)
        val r = fmul(fmul(u, v3), fmul(u, v7).modPow(P58, P))
        val c = fmul(v, fmul(r, r))
        val uMod = u.mod(P)
        return if (c == uMod) Pair(true, r) else Pair(false, fmul(r, SQRT_M1))
    }

    private fun pointAdd(p1: Point, p2: Point): Point {
        val a = fmul(fsub(p1.Y, p1.X), fsub(p2.Y, p2.X))
        val b = fmul(fadd(p1.Y, p1.X), fadd(p2.Y, p2.X))
        val c = fmul(fmul(p1.T, fmul(BigInteger.TWO, D)), p2.T)
        val d = fmul(fmul(p1.Z, BigInteger.TWO), p2.Z)
        val e = fsub(b, a)
        val f = fsub(d, c)
        val g = fadd(d, c)
        val h = fadd(b, a)
        return Point(fmul(e, f), fmul(g, h), fmul(f, g), fmul(e, h))
    }

    private fun scalarMul(k: BigInteger, base: Point): Point {
        var result = IDENTITY
        var cur = base
        var n = k
        while (n > BigInteger.ZERO) {
            if (n.testBit(0)) result = pointAdd(result, cur)
            cur = pointAdd(cur, cur)
            n = n.shiftRight(1)
        }
        return result
    }

    /** Normalize projective point to Z=1. */
    private fun normalize(p: Point): Point {
        if (p.Z == BigInteger.ONE) return p
        val zInv = inv(p.Z)
        val x = fmul(p.X, zInv)
        val y = fmul(p.Y, zInv)
        return Point(x, y, BigInteger.ONE, fmul(x, y))
    }

    /**
     * Ristretto255 encode — normalizes first, then matches Python renc() exactly.
     */
    private fun encode(p: Point): ByteArray {
        val n = normalize(p)
        val X = n.X
        val Y = n.Y
        val Z = BigInteger.ONE

        val u1 = fmul(fadd(Z, Y), fsub(Z, Y))   // (1+Y)(1-Y) = 1-Y^2
        val u2 = fmul(X, Y)
        val invsqrt = sqm1(BigInteger.ONE, fmul(u1, fmul(u2, u2)))
        val i1 = fmul(invsqrt, u1)
        val i2 = fmul(invsqrt, u2)
        val ristrettoSqrt = fmul(i1, INVSQRT_A_MINUS_D)

        var denInv = i2
        var x: BigInteger
        var y: BigInteger

        if (fisNeg(i2)) {
            x = fmul(Y, SQRT_M1)
            y = fmul(X, SQRT_M1)
            denInv = ristrettoSqrt
        } else {
            x = X
            y = Y
        }

        val iXEff = fmul(x, SQRT_M1)
        val iy = fmul(denInv, fsub(Z, y))
        val enchantedDen = fmul(denInv, fadd(Z, y))
        var s = if (fisNeg(iXEff)) enchantedDen else iy
        if (fisNeg(s)) s = fneg(s)
        return fieldToLittleEndian32(s)
    }

    private fun fieldToLittleEndian32(n: BigInteger): ByteArray {
        val out = ByteArray(32)
        var be = n.mod(P).toByteArray()
        if (be.size == 33 && be[0] == 0.toByte()) be = be.copyOfRange(1, 33)
        for (i in be.indices) out[i] = be[be.size - 1 - i]
        return out
    }

    private fun leScalar(bytes: ByteArray): BigInteger =
        BigInteger(1, bytes.copyOf(32).reversedArray()).mod(L)

    fun publicKey(secretBytes: ByteArray): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256").digest(secretBytes)
        val scalar = leScalar(hash)
        val point = scalarMul(scalar, BASE)
        return encode(point)
    }

    // ── Stealth-output scanning operations ──────────────────────────────────

    /**
     * Decode 32-byte Ristretto255 encoding to internal Point.
     * Returns null if invalid. Matches Python rdec() / dalek decode.
     */
    private fun decode(bytes: ByteArray): Point? {
        if (bytes.size != 32) return null
        if (bytes[31].toInt() and 0x80 != 0) return null
        val s = BigInteger(1, bytes.copyOf(32).reversedArray())
        if (!fieldToLittleEndian32(s).contentEquals(bytes)) return null
        if (s.testBit(0)) return null

        val ss = fmul(s, s)
        val u1 = fsub(BigInteger.ONE, ss)        // 1 - s^2
        val u2 = fadd(BigInteger.ONE, ss)        // 1 + s^2
        val u2sq = fmul(u2, u2)
        val u1sq = fmul(u1, u1)
        val v = fsub(fmul(D, u1sq), u2sq)        // D*u1^2 - u2^2

        val (wasSquare, bigI) = sqm1Flag(BigInteger.ONE, fmul(v, u2sq))
        if (!wasSquare) return null

        val dx = fmul(bigI, u2)
        val dxs = fmul(fadd(s, s), dx)
        val x = if (fisNeg(dxs)) fneg(dxs) else dxs

        val denY = fmul(fmul(bigI, dx), v)
        val yAbs = fmul(u1, denY)
        val y = if (fisNeg(yAbs)) fneg(yAbs) else yAbs

        val t = fmul(x, y)
        if (fisNeg(t)) return null
        if (y == BigInteger.ZERO) return null

        return Point(x, y, BigInteger.ONE, t)
    }

    /** SHA256(secretBytes) as little-endian scalar mod L. */
    fun scalarFromSecretBytes(secretBytes: ByteArray): BigInteger =
        leScalar(MessageDigest.getInstance("SHA-256").digest(secretBytes))

    /** SHA-512 of parts separated by 0x1f, result reduced mod L. */
    fun hashToScalar(vararg parts: ByteArray): BigInteger {
        val sha512 = MessageDigest.getInstance("SHA-512")
        for (part in parts) {
            sha512.update(part)
            sha512.update(0x1f.toByte())
        }
        return BigInteger(1, sha512.digest().reversedArray()).mod(L)
    }

    fun scalarAdd(a: BigInteger, b: BigInteger): BigInteger = (a + b).mod(L)

    fun encodeFromScalar(scalar: BigInteger): ByteArray = encode(scalarMul(scalar, BASE))

    /**
     * Decode [pointBytes] as Ristretto255, multiply by [scalar], return encoded bytes.
     * Returns null if [pointBytes] is not a valid Ristretto encoding.
     */
    fun scalarMulPointBytes(scalar: BigInteger, pointBytes: ByteArray): ByteArray? {
        val point = decode(pointBytes) ?: return null
        return encode(scalarMul(scalar, point))
    }
}
