package com.rafaelcosio.gpslivestock.di

import com.rafaelcosio.gpslivestock.location.ILocationProvider // Importa la interfaz
import com.rafaelcosio.gpslivestock.location.LocationProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent // O ViewModelComponent si el ciclo de vida es más corto
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(
        locationProviderImpl: LocationProviderImpl
    ): ILocationProvider
}