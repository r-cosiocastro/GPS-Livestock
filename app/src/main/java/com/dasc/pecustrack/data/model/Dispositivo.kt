package com.dasc.pecustrack.data.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Dispositivo(
    val id: Int = 0,
    val nombre: String? = null,
    val descripcion: String? = null,
    val latitud: Double,
    val longitud: Double
) : Parcelable
