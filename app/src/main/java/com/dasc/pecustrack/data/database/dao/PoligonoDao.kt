package com.dasc.pecustrack.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dasc.pecustrack.data.model.Poligono

@Dao
interface PoligonoDao {
    @Query("SELECT * FROM poligonos")
    suspend fun obtenerTodos(): List<Poligono>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(poligono: Poligono)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(poligono: List<Poligono>)

    @Query("DELETE FROM poligonos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("DELETE FROM poligonos")
    suspend fun eliminarTodos()
}