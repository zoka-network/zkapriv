package com.zoka.zkapriv.ui.avatar

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AvatarMosaicTest {
    @Test fun knownVectorMatchesIndependentSha256Implementation() {
        // Computed independently with Python hashlib and a 32-bit big-endian counter.
        assertEquals("432401304002143331324404132221311134034044240240320441133232224123324403241422013",
            AvatarMosaic.generate("public-account:alice")!!.joinToString(""))
    }

    @Test fun readableCodeComesFromTheSameSeed() {
        // Python: sha256(b"ZKAPriv/avatar/v1/seed" + NUL + b"public-account:alice").digest()[:4]
        assertEquals("F4A4-595C", AvatarMosaic.code("public-account:alice"))
        assertNull(AvatarMosaic.code(""))
        assertNull(AvatarMosaic.code("bad id"))
    }
    @Test fun all81CellsUseTheFixedPalette() {
        val cells = AvatarMosaic.generate("public-account:bob")!!
        assertEquals(81, cells.size)
        assertTrue(cells.all { it in 0..4 })
        assertEquals(listOf(0xFF080809, 0xFF303036, 0xFFAAAAAF, 0xFFFAFAFA, 0xFFF2ABD0), AvatarMosaic.palette)
    }

    @Test fun resultsAreIndependentOfCallOrderAndLocale() {
        val expected = AvatarMosaic.generate("public-account:Ii")
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            repeat(50) {
                AvatarMosaic.generate("unrelated:$it")
                assertEquals(expected, AvatarMosaic.generate("public-account:Ii"))
            }
        } finally { Locale.setDefault(locale) }
    }

    @Test fun sampleOf5000PublicIdentifiersHasDifferentPatterns() {
        // This finite sample is a regression check, not a guarantee of collision freedom.
        val patterns = (0 until 5000).map { AvatarMosaic.generate("public-account:$it") }
        assertEquals(5000, patterns.toSet().size)
    }

    @Test fun malformedOrMissingIdentifiersHaveNoIdentity() {
        listOf("", " ", " address", "address ", "a\nb", "a\u0000b", "é", "\uD800", "a".repeat(257),
            "abandon ".repeat(23) + "art").forEach { assertNull(AvatarMosaic.generate(it)) }
        assertNotNull(AvatarMosaic.generate("a".repeat(256)))
    }

    @Test fun caseIsPreservedForBase58Identifiers() {
        assertNotEquals(AvatarMosaic.generate("zqtest2Abc"), AvatarMosaic.generate("zqtest2abc"))
    }

    @Test fun pureGeneratorHasNoWalletAndroidOrNetworkDependencies() {
        // Source boundary guard: changes that introduce access to private state must fail review/tests.
        val root = java.io.File("src/main/java/com/zoka/zkapriv/ui/avatar")
        listOf("AvatarMosaic.kt", "ZKAPrivAvatar.kt").forEach { file ->
            val source = root.resolve(file).readText()
            listOf("QuanticVault", "WalletViewModel", "ZokaStark", "SharedPreferences", "java.net.",
                "okhttp", "LocalContext", "SecureRandom", "System.currentTimeMillis").forEach {
                assertFalse("$file must not depend on $it", source.contains(it))
            }
        }
    }
}
