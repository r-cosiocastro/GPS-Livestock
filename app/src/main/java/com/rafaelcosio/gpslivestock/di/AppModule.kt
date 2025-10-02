package com.rafaelcosio.gpslivestock.di

import android.content.Context
// No es necesario Room aquí si AppDatabase.getDatabase lo maneja
import com.rafaelcosio.gpslivestock.data.database.AppDatabase
import com.rafaelcosio.gpslivestock.data.database.dao.RastreadorDao
import com.rafaelcosio.gpslivestock.data.database.dao.PoligonoDao
import com.rafaelcosio.gpslivestock.data.database.dao.UserDao
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
        // Llama al método estático en AppDatabase para obtener la instancia.
        // Este método ya contendrá la lógica de .fallbackToDestructiveMigration()
        return AppDatabase.getDatabase(appContext)
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
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper
    }

    @Provides
    @Singleton
    fun provideFirebaseModule(): FirebaseModule {
        return FirebaseModule
    }
}