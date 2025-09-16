package com.rafaelcosio.gpslivestock.data.database.dao // Actualizado

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rafaelcosio.gpslivestock.data.model.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT) // Evita insertar si el email ya existe, generando un error.
    suspend fun insertUser(user: User): Long // Devuelve el ID del usuario insertado o -1 si hay conflicto y no se inserta.

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    // Podrías añadir más métodos según necesites, por ejemplo:
    // @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    // suspend fun getUserById(userId: Int): User?
}