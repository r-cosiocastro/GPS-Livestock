package com.dasc.pecustrack.data.repository

import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.data.database.dao.PoligonoDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoligonoRepositoryImpl @Inject constructor(
    private val poligonoDao: PoligonoDao
) : PoligonoRepository {

    override fun getAllPoligonos(): Flow<List<Poligono>> {
        return poligonoDao.getAllPoligonos()
    }

    override suspend fun getPoligonoById(id: Int): Poligono? {
        return poligonoDao.getPoligonoById(id)
    }

    override suspend fun insertPoligono(poligono: Poligono): Long {
        return poligonoDao.insertPoligono(poligono)
    }

    override suspend fun updatePoligono(poligono: Poligono) {
        poligonoDao.updatePoligono(poligono)
    }

    override suspend fun deletePoligono(poligono: Poligono) {
        poligonoDao.deletePoligono(poligono)
    }

    override suspend fun deletePoligonoById(id: Int) {
        poligonoDao.deletePoligonoById(id)
    }

    override suspend fun deleteAllPoligonos() {
        poligonoDao.deleteAllPoligonos()
    }

    override suspend fun getAllPoligonosOnce(): List<Poligono> {
        return poligonoDao.getAllPoligonosOnce()
    }
}