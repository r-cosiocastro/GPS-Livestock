package com.rafaelcosio.gpslivestock.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "poligonos")
data class Poligono(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val puntos: List<LatLng> = emptyList()
) : Parcelable