package com.dasc.pecustrack.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dasc.pecustrack.data.model.Dispositivo
import kotlinx.coroutines.flow.Flow

@Dao
interface DispositivoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispositivo(dispositivo: Dispositivo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDispositivos(dispositivos: List<Dispositivo>)

    @Update
    suspend fun updateDispositivo(dispositivo: Dispositivo)

    @Delete
    suspend fun deleteDispositivo(dispositivo: Dispositivo)

    @Query("DELETE FROM dispositivos")
    suspend fun deleteAllDispositivos()

    @Query("SELECT * FROM dispositivos")
    fun getAllDispositivos(): Flow<List<Dispositivo>>

    @Query("SELECT * FROM dispositivos")
    suspend fun getAllDispositivosOnce(): List<Dispositivo>

    @Query("SELECT * FROM dispositivos WHERE id = :id")
    fun getDispositivoById(id: Int): Flow<Dispositivo?>
}
