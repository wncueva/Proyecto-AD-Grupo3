package com.example.billetera.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tipos de operacion que maneja la billetera. */
object TipoOperacion {
    const val DEPOSITO = "DEPOSITO"
    const val RETIRO = "RETIRO"
    const val TRANSFERENCIA_ENVIADA = "TRANSFERENCIA_ENVIADA"
    const val TRANSFERENCIA_RECIBIDA = "TRANSFERENCIA_RECIBIDA"
    const val PAGO_SERVICIO = "PAGO_SERVICIO"
}

object Categoria {
    const val GENERAL = "Transaccion"
    const val SERVICIO_BASICO = "Servicio Basico"
}

/**
 * Tabla "transacciones": cada comprobante que devuelve el servidor
 * se guarda aqui para mantener el historial aun sin internet.
 */
@Entity(tableName = "transacciones")
data class TransaccionEntity(
    @PrimaryKey val id: String,          // id que genera el servidor (TX-...)
    val tipo: String,                    // ver TipoOperacion
    val monto: Double,
    val saldoResultante: Double,
    val fecha: Long,                     // epoch en milisegundos
    val categoria: String,               // "Transaccion" o "Servicio Basico"
    val contraparte: String? = null,     // cuenta destino/origen en transferencias
    val servicio: String? = null,        // LUZ, AGUA, INTERNET, TELEFONO
    val codigoContrato: String? = null
) {
    /** Ingresos: depositos y transferencias recibidas (se pintan en verde). */
    val esIngreso: Boolean
        get() = tipo == TipoOperacion.DEPOSITO || tipo == TipoOperacion.TRANSFERENCIA_RECIBIDA
}

/** Tabla "cuenta": guarda el ultimo saldo conocido para mostrarlo offline. */
@Entity(tableName = "cuenta")
data class CuentaEntity(
    @PrimaryKey val numero: String,
    val saldo: Double
)
