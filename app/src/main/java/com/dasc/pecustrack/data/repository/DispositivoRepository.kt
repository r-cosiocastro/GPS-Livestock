package com.dasc.pecustrack.data.repository

import com.dasc.pecustrack.data.model.Dispositivo
import kotlinx.coroutines.flow.Flow

interface DispositivoRepository {
    fun getAllDispositivos(): Flow<List<Dispositivo>>
    suspend fun getAllDispositivosOnce(): List<Dispositivo>
    fun getDispositivoById(id: Int): Flow<Dispositivo?>
    suspend fun insertDispositivo(dispositivo: Dispositivo)
    suspend fun insertAllDispositivos(dispositivos: List<Dispositivo>)
    suspend fun updateDispositivo(dispositivo: Dispositivo)
    suspend fun deleteDispositivo(dispositivo: Dispositivo)
    suspend fun deleteAllDispositivos()
}