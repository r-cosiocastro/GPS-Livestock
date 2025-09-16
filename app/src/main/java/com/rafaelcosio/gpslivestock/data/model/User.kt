package com.rafaelcosio.gpslivestock.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

// Recuerda añadir @TypeConverters(UserTypeConverter::class) a tu clase AppDatabase
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val email: String, // Usaremos email como nombre de usuario único
    val passwordHash: String, // Almacena el hash de la contraseña (hex string)
    val salt: String, // Almacena la sal utilizada para el hash (hex string)
    val userType: UserType,
    val displayName: String? = null, // Nombre para mostrar opcional
    val ranchName: String? = null // Nombre del rancho, opcional y relevante para Ganaderos
)