package com.rafaelcosio.gpslivestock.data.repository

import com.rafaelcosio.gpslivestock.data.model.Poligono
import kotlinx.coroutines.flow.Flow

interface PoligonoRepository {
    fun getAllPoligonos(): Flow<List<Poligono>>
    suspend fun getAllPoligonosOnce(): List<Poligono>
    suspend fun getPoligonoById(id: Int): Poligono?
    suspend fun insertPoligono(poligono: Poligono): Long
    suspend fun updatePoligono(poligono: Poligono)
    suspend fun deletePoligono(poligono: Poligono)
    suspend fun deletePoligonoById(id: Int)
    suspend fun deleteAllPoligonos()
}