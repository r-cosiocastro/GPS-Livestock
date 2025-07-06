package com.rafaelcosio.gpslivestock.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // Si necesitas TypeConverters
import com.rafaelcosio.gpslivestock.data.database.dao.RastreadorDao
import com.rafaelcosio.gpslivestock.data.database.dao.PoligonoDao
import com.rafaelcosio.gpslivestock.data.model.Rastreador // Importa tu entidad Dispositivo
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.utils.LatLngListConverter

@Database(
    entities = [
        Rastreador::class,
        Poligono::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LatLngListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rastreadorDao(): RastreadorDao
    abstract fun poligonoDao(): PoligonoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pecus_track_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}