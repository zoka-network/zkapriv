package com.zoka.zkapriv.security.di

import android.content.Context
import com.zoka.zkapriv.security.biometric.BiometricManager
import com.zoka.zkapriv.security.crypto.KeystoreManager
import com.zoka.zkapriv.security.storage.SecureStorage
import com.zoka.zkapriv.security.storage.SecureStorageManager
import com.zoka.zkapriv.security.wallet.AccountManager
import com.zoka.zkapriv.security.wallet.MnemonicManager
import com.zoka.zkapriv.security.wallet.WalletManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager()
    }

    @Provides
    @Singleton
    fun provideMnemonicManager(): MnemonicManager {
        return MnemonicManager()
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideAccountManager(
        walletManager: WalletManager,
        mnemonicManager: MnemonicManager,
        secureStorage: SecureStorage,
        @ApplicationContext context: Context
    ): AccountManager {
        return AccountManager(walletManager, mnemonicManager, secureStorage, context)
    }

    @Provides
    @Singleton
    fun provideSecureStorageManager(): SecureStorageManager {
        return SecureStorageManager()
    }
    
    @Provides
    @Singleton
    fun provideBiometricManager(
        @ApplicationContext context: Context
    ): BiometricManager {
        return BiometricManager(context)
    }
}