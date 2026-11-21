package com.zoka.zkapriv.security.vault

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.*

/** An in-memory preference double; no Android keystore or user vault is opened. */
class PublicAccountMetadataTest {
    private val values = mutableMapOf("wallets" to "main,other", "name.main" to "Main wallet")
    private val prefs = mock(SharedPreferences::class.java)
    private val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)

    init {
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer {
            val key = it.getArgument<String>(0)
            check(!key.startsWith("phrase") && !key.startsWith("pin")) { "Private material must not be read" }
            values[key] ?: it.getArgument<String?>(1)
        }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            editor
        }
    }

    private fun vault(): QuanticVault = QuanticVault(mock(Context::class.java)).also {
        // Replace only the test object's lazy preferences, without adding a production backdoor.
        val field = QuanticVault::class.java.getDeclaredField("prefs\$delegate")
        field.isAccessible = true
        field.set(it, lazy { prefs })
    }

    @Test fun publicIdentitySurvivesNewInstancesAndRenameWithoutReadingSecrets() {
        vault().rememberPublicAccount("main", "public-account:alice")
        val reopened = vault()
        reopened.renameWallet("main", "Renamed")
        assertEquals("public-account:alice", reopened.wallets().first().publicAccountId)
        assertEquals("Renamed", reopened.wallets().first().name)
    }

    @Test fun restoredAccountDoesNotDependOnLocalWalletId() {
        val vault = vault()
        vault.rememberPublicAccount("main", "public-account:alice")
        vault.rememberPublicAccount("other", "public-account:alice")
        assertEquals(vault.wallets()[0].publicAccountId, vault.wallets()[1].publicAccountId)
    }

    @Test fun unavailableOrRemovedAccountsDoNotReceiveAnInventedIdentity() {
        val vault = vault()
        vault.rememberPublicAccount("main", " ")
        vault.rememberPublicAccount("removed", "public-account:alice")
        assertTrue(vault.wallets().all { it.publicAccountId.isEmpty() })
        verify(prefs, never()).edit()
    }
}
