package com.dasc.pecustrack.data.repository

import com.dasc.pecustrack.data.database.dao.DispositivoDao
import com.dasc.pecustrack.data.model.Dispositivo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispositivoRepositoryImpl @Inject constructor(
    private val dispositivoDao: DispositivoDao
) : DispositivoRepository {

    override fun getAllDispositivos(): Flow<List<Dispositivo>> {
        return dispositivoDao.getAllDispositivos()
    }

    override suspend fun getAllDispositivosOnce(): List<Dispositivo> {
        return dispositivoDao.getAllDispositivosOnce()
    }

    override fun getDispositivoById(id: Int): Flow<Dispositivo?> {
        return dispositivoDao.getDispositivoById(id)
    }

    override suspend fun getDispositivoByIdOnce(id: Int): Dispositivo? {
        return dispositivoDao.getDispositivoByIdOnce(id)
    }

    override suspend fun insertDispositivo(dispositivo: Dispositivo) {
        return dispositivoDao.insertDispositivo(dispositivo)
    }

    override suspend fun insertAllDispositivos(dispositivos: List<Dispositivo>) {
        return dispositivoDao.insertAllDispositivos(dispositivos)
    }

    override suspend fun updateDispositivo(dispositivo: Dispositivo) {
        dispositivoDao.updateDispositivo(dispositivo)
    }

    override suspend fun deleteDispositivo(dispositivo: Dispositivo) {
        dispositivoDao.deleteDispositivo(dispositivo)
    }

    override suspend fun deleteAllDispositivos() {
        dispositivoDao.deleteAllDispositivos()
    }


}