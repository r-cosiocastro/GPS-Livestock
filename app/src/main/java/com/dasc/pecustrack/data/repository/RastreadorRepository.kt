package com.dasc.pecustrack.data.repository

import com.dasc.pecustrack.data.model.Rastreador
import kotlinx.coroutines.flow.Flow

interface RastreadorRepository {
    fun getAllDispositivos(): Flow<List<Rastreador>>
    suspend fun getAllDispositivosOnce(): List<Rastreador>
    fun getDispositivoById(id: Int): Flow<Rastreador?>
    suspend fun getDispositivoByIdOnce(id: Int): Rastreador?
    suspend fun insertDispositivo(rastreador: Rastreador)
    suspend fun insertAllDispositivos(rastreadores: List<Rastreador>)
    suspend fun updateDispositivo(rastreador: Rastreador)
    suspend fun deleteDispositivo(rastreador: Rastreador)
    suspend fun deleteAllDispositivos()
}