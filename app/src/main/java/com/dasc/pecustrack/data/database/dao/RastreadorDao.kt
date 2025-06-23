package com.dasc.pecustrack.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dasc.pecustrack.data.model.Rastreador
import kotlinx.coroutines.flow.Flow

@Dao
interface RastreadorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispositivo(rastreador: Rastreador)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDispositivos(rastreadores: List<Rastreador>)

    @Update
    suspend fun updateDispositivo(rastreador: Rastreador)

    @Delete
    suspend fun deleteDispositivo(rastreador: Rastreador)

    @Query("DELETE FROM rastreadores")
    suspend fun deleteAllDispositivos()

    @Query("SELECT * FROM rastreadores")
    fun getAllDispositivos(): Flow<List<Rastreador>>

    @Query("SELECT * FROM rastreadores")
    suspend fun getAllDispositivosOnce(): List<Rastreador>

    @Query("SELECT * FROM rastreadores WHERE id = :id")
    fun getDispositivoById(id: Int): Flow<Rastreador?>

    @Query("SELECT * FROM rastreadores WHERE id = :id")
    suspend fun getDispositivoByIdOnce(id: Int): Rastreador?
}
