package com.example.billetera.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room (SQLite). El archivo "billetera.db" queda guardado
 * en el celular, por eso el historial sobrevive al cerrar/reiniciar la app.
 */
@Database(
    entities = [TransaccionEntity::class, CuentaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BilleteraDatabase : RoomDatabase() {

    abstract fun transaccionDao(): TransaccionDao

    companion object {
        @Volatile
        private var INSTANCIA: BilleteraDatabase? = null

        /** Singleton: una sola instancia de la BD para toda la app (thread-safe). */
        fun obtener(context: Context): BilleteraDatabase =
            INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: Room.databaseBuilder(
                    context.applicationContext,
                    BilleteraDatabase::class.java,
                    "billetera.db"
                ).build().also { INSTANCIA = it }
            }
    }
}
