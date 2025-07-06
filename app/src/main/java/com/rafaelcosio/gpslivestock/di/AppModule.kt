package com.rafaelcosio.gpslivestock.di

import android.content.Context
import androidx.room.Room
import com.rafaelcosio.gpslivestock.data.database.AppDatabase
import com.rafaelcosio.gpslivestock.data.database.dao.RastreadorDao
import com.rafaelcosio.gpslivestock.data.database.dao.PoligonoDao
import com.rafaelcosio.gpslivestock.utils.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "pecus_track_db"
        ).fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideRastreadorDao(appDatabase: AppDatabase): RastreadorDao {
        return appDatabase.rastreadorDao()
    }

    @Provides
    fun providePoligonoDao(appDatabase: AppDatabase): PoligonoDao {
        return appDatabase.poligonoDao()
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper
    }

}