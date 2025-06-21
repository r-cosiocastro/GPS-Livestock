package com.dasc.pecustrack.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "dispositivos")
data class Dispositivo(
    @PrimaryKey val id: Int = 0,
    val nombre: String? = null,
    val descripcion: String? = null,
    val latitud: Double,
    val longitud: Double,
    val tipoAnimal: Int, // 0: Vaca, 1: Caballo, 2: Oveja, 3: Cabra, 4: Cerdo
    val primeraConexion: Long? = null,
    val ultimaConexion: Long? = null,
    val activo: Boolean = true,
    var dentroDelArea: Boolean = false
) : Parcelable
