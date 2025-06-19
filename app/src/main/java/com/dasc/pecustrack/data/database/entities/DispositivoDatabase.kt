package com.dasc.pecustrack.data.database.entities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dasc.pecustrack.data.database.dao.DispositivoDao
import com.dasc.pecustrack.data.model.Dispositivo
import kotlin.jvm.java

@Database(entities = [Dispositivo::class], version = 1)
abstract class DispositivoDatabase : RoomDatabase() {
    abstract fun dispositivoDao(): DispositivoDao

    companion object {
        @Volatile private var instancia: DispositivoDatabase? = null

        fun obtenerInstancia(context: Context): DispositivoDatabase {
            return instancia ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DispositivoDatabase::class.java,
                    "dispositivos.db"
                ).build().also { instancia = it }
            }
        }
    }
}