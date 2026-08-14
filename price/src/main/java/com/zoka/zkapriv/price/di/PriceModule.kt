package com.zoka.zkapriv.price.di

import android.content.Context
import androidx.room.Room
import com.zoka.zkapriv.price.db.PriceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PriceModule {

    @Provides
    @Singleton
    fun providePriceDatabase(
        @ApplicationContext context: Context
    ): PriceDatabase {
        return Room.databaseBuilder(
            context,
            PriceDatabase::class.java,
            "price_database"
        ).build()
    }

    @Provides
    @Singleton
    fun providePriceCacheDao(database: PriceDatabase) = database.priceCacheDao()
}
