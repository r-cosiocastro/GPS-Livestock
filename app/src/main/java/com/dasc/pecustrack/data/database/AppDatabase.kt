package com.dasc.pecustrack.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // Si necesitas TypeConverters
import com.dasc.pecustrack.data.database.dao.DispositivoDao
import com.dasc.pecustrack.data.database.dao.PoligonoDao
import com.dasc.pecustrack.data.model.Dispositivo // Importa tu entidad Dispositivo
import com.dasc.pecustrack.data.model.Poligono
import com.dasc.pecustrack.utils.LatLngListConverter

// Importa otras entidades si las tienes, por ejemplo:
// import com.dasc.pecustrack.data.model.PoligonoData
// Importa tus DAOs
// Importa otros DAOs si los tienes, por ejemplo:
// import com.dasc.pecustrack.data.source.local.dao.PoligonoDao
// Importa tus TypeConverters si los tienes, por ejemplo:
// import com.dasc.pecustrack.data.source.local.typeconverters.LatLngListConverter

@Database(
    entities = [
        Dispositivo::class,
        Poligono::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LatLngListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    // Define métodos abstractos para cada DAO
    abstract fun dispositivoDao(): DispositivoDao
    abstract fun poligonoDao(): PoligonoDao

    companion object {
        // La anotación @Volatile asegura que el valor de INSTANCE sea siempre actualizado
        // y visible para todos los hilos de ejecución. Significa que los cambios hechos
        // por un hilo a INSTANCE son visibles para todos los demás hilos inmediatamente.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Si INSTANCE no es nulo, devuélvelo.
            // Si es nulo, entonces crea la base de datos.
            return INSTANCE ?: synchronized(this) {
                // synchronized: Solo un hilo a la vez puede ejecutar este bloque de código,
                // lo que previene que se creen múltiples instancias de la base de datos
                // si varios hilos intentan acceder al mismo tiempo.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pecus_track_db" // Nombre del archivo de la base de datos
                )
                    // Aquí puedes añadir estrategias de migración si es necesario
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration() // Opción si no quieres manejar migraciones complejas (borra y recrea la DB)
                    .build()
                INSTANCE = instance
                // Devuelve la instancia
                instance
            }
        }
    }
}