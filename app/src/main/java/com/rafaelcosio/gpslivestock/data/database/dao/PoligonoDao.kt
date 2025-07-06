package com.rafaelcosio.gpslivestock.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rafaelcosio.gpslivestock.data.model.Poligono
import kotlinx.coroutines.flow.Flow

@Dao
interface PoligonoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoligono(poligono: Poligono): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(poligono: List<Poligono>)

    @Update
    suspend fun updatePoligono(poligono: Poligono)

    @Delete
    suspend fun deletePoligono(poligono: Poligono)

    @Query("DELETE FROM poligonos WHERE id = :id")
    suspend fun deletePoligonoById(id: Int)

    @Query("DELETE FROM poligonos")
    suspend fun deleteAllPoligonos()

    @Query("SELECT * FROM poligonos ORDER BY id ASC")
    fun getAllPoligonos(): Flow<List<Poligono>>

    @Query("SELECT * FROM poligonos")
    suspend fun getAllPoligonosOnce(): List<Poligono>

    @Query("SELECT * FROM poligonos WHERE id = :id")
    suspend fun getPoligonoById(id: Int): Poligono?
}