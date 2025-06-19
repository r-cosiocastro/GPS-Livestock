package com.dasc.pecustrack.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dasc.pecustrack.data.model.Dispositivo

@Dao
interface DispositivoDao {
    @Query("SELECT * FROM dispositivos")
    suspend fun obtenerTodos(): List<Dispositivo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(dispositivo: Dispositivo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(dispositivos: List<Dispositivo>)

    @Query("DELETE FROM dispositivos")
    suspend fun eliminarTodos()
}
