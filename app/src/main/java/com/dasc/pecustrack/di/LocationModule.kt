package com.dasc.pecustrack.di

import android.content.Context
import com.dasc.pecustrack.location.ILocationProvider // Importa la interfaz
import com.dasc.pecustrack.location.LocationProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent // O ViewModelComponent si el ciclo de vida es más corto
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Instalar en SingletonComponent para que viva mientras la app viva
abstract class LocationModule {

    @Binds
    @Singleton // Asegura que solo haya una instancia de LocationProviderImpl
    abstract fun bindLocationProvider(
        locationProviderImpl: LocationProviderImpl
    ): ILocationProvider // Provee la interfaz
}