package com.rafaelcosio.gpslivestock.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rafaelcosio.gpslivestock.data.repository.FirebaseAuthRepository
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepository
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepositoryImpl
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRastreadorRepository(
        impl: RastreadorRepositoryImpl
    ): RastreadorRepository

    @Binds
    @Singleton
    abstract fun bindPoligonoRepository(
        impl: PoligonoRepositoryImpl
    ): PoligonoRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuthRepository(
            firebaseAuth: FirebaseAuth,
            firestore: FirebaseFirestore
        ): FirebaseAuthRepository {
            return FirebaseAuthRepository(firebaseAuth, firestore)
        }
    }
}