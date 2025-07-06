package com.rafaelcosio.gpslivestock.di

import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepository
import com.rafaelcosio.gpslivestock.data.repository.RastreadorRepositoryImpl
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepository
import com.rafaelcosio.gpslivestock.data.repository.PoligonoRepositoryImpl
import dagger.Binds
import dagger.Module
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
}