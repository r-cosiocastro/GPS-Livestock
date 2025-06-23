package com.dasc.pecustrack.data.repository

import com.dasc.pecustrack.data.database.dao.RastreadorDao
import com.dasc.pecustrack.data.model.Rastreador
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RastreadorRepositoryImpl @Inject constructor(
    private val rastreadorDao: RastreadorDao
) : RastreadorRepository {

    override fun getAllDispositivos(): Flow<List<Rastreador>> {
        return rastreadorDao.getAllDispositivos()
    }

    override suspend fun getAllDispositivosOnce(): List<Rastreador> {
        return rastreadorDao.getAllDispositivosOnce()
    }

    override fun getDispositivoById(id: Int): Flow<Rastreador?> {
        return rastreadorDao.getDispositivoById(id)
    }

    override suspend fun getDispositivoByIdOnce(id: Int): Rastreador? {
        return rastreadorDao.getDispositivoByIdOnce(id)
    }

    override suspend fun insertDispositivo(rastreador: Rastreador) {
        return rastreadorDao.insertDispositivo(rastreador)
    }

    override suspend fun insertAllDispositivos(rastreadores: List<Rastreador>) {
        return rastreadorDao.insertAllDispositivos(rastreadores)
    }

    override suspend fun updateDispositivo(rastreador: Rastreador) {
        rastreadorDao.updateDispositivo(rastreador)
    }

    override suspend fun deleteDispositivo(rastreador: Rastreador) {
        rastreadorDao.deleteDispositivo(rastreador)
    }

    override suspend fun deleteAllDispositivos() {
        rastreadorDao.deleteAllDispositivos()
    }


}