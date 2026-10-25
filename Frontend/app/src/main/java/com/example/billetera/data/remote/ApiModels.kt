package com.example.billetera.data.remote

import androidx.annotation.Keep
import com.example.billetera.data.local.Categoria
import com.example.billetera.data.local.TipoOperacion
import com.example.billetera.data.local.TransaccionEntity
import java.time.Instant

// ---------- Cuerpos que ENVIAMOS al servidor (JSON) ----------
@Keep data class MontoRequest(val monto: Double)

@Keep data class TransferenciaRequest(
    val origen: String,
    val destino: String,
    val monto: Double
)

@Keep data class PagoServicioRequest(
    val cuenta: String,
    val servicio: String,
    val codigoContrato: String,
    val monto: Double
)

// ---------- Respuestas que RECIBIMOS del servidor ----------
@Keep data class TransaccionDto(
    val id: String,
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,
    val fecha: String,
    val contraparte: String? = null,
    val servicio: String? = null,
    val codigoContrato: String? = null
)

@Keep data class OperacionResponse(
    val ok: Boolean,
    val transaccion: TransaccionDto?
)

@Keep data class ErrorResponse(
    val ok: Boolean,
    val codigo: String?,
    val mensaje: String?
)

@Keep data class CuentaResponse(
    val ok: Boolean,
    val cuenta: String,
    val titular: String,
    val saldo: Double
)

/** Mensajes que llegan por el WebSocket. */
@Keep data class WsMensaje(
    val evento: String,                    // CONECTADO | TRANSFERENCIA_RECIBIDA | ERROR
    val cuenta: String? = null,
    val saldo: Double? = null,
    val transaccion: TransaccionDto? = null,
    val mensaje: String? = null
)

/** Convierte el comprobante del servidor en una fila de Room. */
fun TransaccionDto.aEntidad(): TransaccionEntity = TransaccionEntity(
    id = id,
    tipo = tipo,
    monto = monto,
    saldoResultante = saldo,
    fecha = runCatching { Instant.parse(fecha).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis()),
    categoria = if (tipo == TipoOperacion.PAGO_SERVICIO) Categoria.SERVICIO_BASICO else Categoria.GENERAL,
    contraparte = contraparte,
    servicio = servicio,
    codigoContrato = codigoContrato
)
