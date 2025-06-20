package com.dasc.pecustrack.di

import com.dasc.pecustrack.data.repository.DispositivoRepository
import com.dasc.pecustrack.data.repository.DispositivoRepositoryImpl
import com.dasc.pecustrack.data.repository.PoligonoRepository
import com.dasc.pecustrack.data.repository.PoligonoRepositoryImpl
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
    abstract fun bindDispositivoRepository(
        impl: DispositivoRepositoryImpl
    ): DispositivoRepository

    @Binds
    @Singleton
    abstract fun bindPoligonoRepository(
        impl: PoligonoRepositoryImpl
    ): PoligonoRepository
}