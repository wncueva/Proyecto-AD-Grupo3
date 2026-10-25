package com.example.billetera.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object): todas las consultas a SQLite.
 * - Las funciones "suspend" se llaman desde corrutinas (en Dispatchers.IO).
 * - Las que devuelven Flow son REACTIVAS: cada vez que cambia la tabla,
 *   Room emite la lista nueva y la UI se actualiza sola.
 */
@Dao
abstract class TransaccionDao {

    @Query("SELECT * FROM transacciones ORDER BY fecha DESC")
    abstract fun observarHistorial(): Flow<List<TransaccionEntity>>

    @Query("SELECT * FROM cuenta WHERE numero = :numero")
    abstract fun observarCuenta(numero: String): Flow<CuentaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarTransaccion(tx: TransaccionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarTransacciones(lista: List<TransaccionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarCuenta(cuenta: CuentaEntity)

    /** Guarda el comprobante y el nuevo saldo en una sola transaccion SQL (atomica). */
    @Transaction
    open suspend fun registrarOperacion(tx: TransaccionEntity, cuenta: CuentaEntity) {
        insertarTransaccion(tx)
        guardarCuenta(cuenta)
    }
}
