package com.dasc.pecustrack.data.database.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dasc.pecustrack.data.database.dao.PoligonoDao
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.utils.LatLngListConverter

@Database(entities = [Poligono::class], version = 1)
@TypeConverters(LatLngListConverter::class)
abstract class PoligonoDatabase : RoomDatabase() {
    abstract fun poligonoDao(): PoligonoDao

    companion object {
        @Volatile
        private var instancia: PoligonoDatabase? = null

        fun obtenerInstancia(context: Context): PoligonoDatabase {
            return instancia ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PoligonoDatabase::class.java,
                    "poligonos.db"
                ).build().also { instancia = it }
            }
        }
    }
}