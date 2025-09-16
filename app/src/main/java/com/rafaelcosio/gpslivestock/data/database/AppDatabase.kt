package com.rafaelcosio.gpslivestock.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rafaelcosio.gpslivestock.data.database.dao.RastreadorDao
import com.rafaelcosio.gpslivestock.data.database.dao.PoligonoDao
import com.rafaelcosio.gpslivestock.data.database.dao.UserDao
import com.rafaelcosio.gpslivestock.data.model.Rastreador
import com.rafaelcosio.gpslivestock.data.model.Poligono
import com.rafaelcosio.gpslivestock.data.model.User
import com.rafaelcosio.gpslivestock.data.model.UserTypeConverter
import com.rafaelcosio.gpslivestock.utils.LatLngListConverter

@Database(
    entities = [
        Rastreador::class,
        Poligono::class,
        User::class
    ],
    version = 3, // <-- Versión incrementada a 3 (debido al campo 'salt' en User)
    exportSchema = false // Puedes mantenerlo en false si no gestionas esquemas
)
@TypeConverters(LatLngListConverter::class, UserTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rastreadorDao(): RastreadorDao
    abstract fun poligonoDao(): PoligonoDao
    abstract fun userDao(): UserDao

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
                    // En lugar de migraciones, usamos fallbackToDestructiveMigration.
                    // Esto borrará y recreará la base de datos si la versión cambia.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}