package com.example.billetera.data

import android.content.Context
import android.util.Log
import com.example.billetera.Config
import com.example.billetera.data.local.BilleteraDatabase
import com.example.billetera.data.local.CuentaEntity
import com.example.billetera.data.local.TransaccionDao
import com.example.billetera.data.local.TransaccionEntity
import com.example.billetera.data.remote.BilleteraApi
import com.example.billetera.data.remote.ErrorResponse
import com.example.billetera.data.remote.MontoRequest
import com.example.billetera.data.remote.OperacionResponse
import com.example.billetera.data.remote.PagoServicioRequest
import com.example.billetera.data.remote.RedCliente
import com.example.billetera.data.remote.TransferenciaRequest
import com.example.billetera.data.remote.WebSocketManager
import com.example.billetera.data.remote.WsMensaje
import com.example.billetera.data.remote.aEntidad
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/** Error de negocio con un mensaje listo para mostrar al usuario. */
class OperacionException(mensaje: String) : Exception(mensaje)

/**
 * Repositorio: unico punto de acceso a los datos.
 * Coordina REST (Retrofit), tiempo real (WebSocket) y persistencia (Room).
 *
 * REGLA DE HILOS: todo lo de red y base de datos corre en Dispatchers.IO
 * (withContext / flowOn). El ViewModel recibe el resultado de vuelta en
 * Dispatchers.Main para pintar la pantalla.
 */
class BilleteraRepository(
    private val dao: TransaccionDao,
    private val api: BilleteraApi,
    private val webSocket: WebSocketManager,
    private val gson: Gson,
    private val cuenta: String = Config.CUENTA
) {
    // ---------- Lecturas reactivas desde Room ----------
    val historial: Flow<List<TransaccionEntity>> =
        dao.observarHistorial().flowOn(Dispatchers.IO)

    val saldo: Flow<Double?> =
        dao.observarCuenta(cuenta).map { it?.saldo }.flowOn(Dispatchers.IO)

    // ---------- WebSocket ----------
    val estadoConexion = webSocket.estado
    val mensajesTiempoReal = webSocket.mensajes

    fun conectarTiempoReal() = webSocket.conectar()
    fun desconectarTiempoReal() = webSocket.desconectar()

    // ---------- Operaciones REST ----------
    suspend fun depositar(monto: Double) =
        ejecutar { api.depositar(cuenta, MontoRequest(monto)) }

    suspend fun retirar(monto: Double) =
        ejecutar { api.retirar(cuenta, MontoRequest(monto)) }

    suspend fun transferir(destino: String, monto: Double) =
        ejecutar { api.transferir(TransferenciaRequest(cuenta, destino, monto)) }

    suspend fun pagarServicio(servicio: String, contrato: String, monto: Double) =
        ejecutar { api.pagarServicio(PagoServicioRequest(cuenta, servicio, contrato, monto)) }

    /**
     * Plantilla comun: llama al servidor en Dispatchers.IO, guarda el
     * comprobante en Room y traduce los errores HTTP a mensajes claros.
     */
    private suspend fun ejecutar(
        llamada: suspend () -> Response<OperacionResponse>
    ): Result<TransaccionEntity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Operacion REST en hilo: ${Thread.currentThread().name}")
        try {
            val respuesta = llamada()
            if (respuesta.isSuccessful) {
                val dto = respuesta.body()?.transaccion
                    ?: return@withContext Result.failure(OperacionException("Respuesta vacia del servidor"))
                val entidad = dto.aEntidad()
                dao.registrarOperacion(entidad, CuentaEntity(cuenta, dto.saldo))
                Result.success(entidad)
            } else {
                Result.failure(OperacionException(leerError(respuesta)))
            }
        } catch (e: IOException) {
            Result.failure(OperacionException("Sin conexion con el servidor"))
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado", e)
            Result.failure(OperacionException("Error inesperado: ${e.message}"))
        }
    }

    private fun leerError(respuesta: Response<*>): String {
        val cuerpo = respuesta.errorBody()?.string()
        val mensaje = runCatching { gson.fromJson(cuerpo, ErrorResponse::class.java)?.mensaje }.getOrNull()
        return mensaje ?: "Error del servidor (HTTP ${respuesta.code()})"
    }

    // ---------- Sincronizacion ----------
    /** Trae saldo e historial del servidor (por si llegaron cosas estando offline). */
    suspend fun sincronizar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val info = api.consultarCuenta(cuenta)
            val transacciones = api.obtenerTransacciones(cuenta).map { it.aEntidad() }
            dao.insertarTransacciones(transacciones)
            dao.guardarCuenta(CuentaEntity(cuenta, info.saldo))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(OperacionException("No se pudo sincronizar: modo sin conexion"))
        }
    }

    /**
     * Procesa un mensaje del WebSocket. Si es una transferencia recibida,
     * la guarda en Room y devuelve la entidad para avisar al usuario.
     */
    suspend fun procesarMensaje(mensaje: WsMensaje): TransaccionEntity? = withContext(Dispatchers.IO) {
        when (mensaje.evento) {
            "TRANSFERENCIA_RECIBIDA" -> {
                val dto = mensaje.transaccion ?: return@withContext null
                val entidad = dto.aEntidad()
                dao.registrarOperacion(entidad, CuentaEntity(cuenta, dto.saldo))
                entidad
            }
            "CONECTADO" -> {
                mensaje.saldo?.let { dao.guardarCuenta(CuentaEntity(cuenta, it)) }
                null
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "Repositorio"

        @Volatile
        private var INSTANCIA: BilleteraRepository? = null

        fun obtener(context: Context): BilleteraRepository =
            INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: BilleteraRepository(
                    dao = BilleteraDatabase.obtener(context).transaccionDao(),
                    api = RedCliente.api,
                    webSocket = WebSocketManager(RedCliente.okHttpWebSocket, RedCliente.gson, Config.WS_URL),
                    gson = RedCliente.gson
                ).also { INSTANCIA = it }
            }
    }
}
