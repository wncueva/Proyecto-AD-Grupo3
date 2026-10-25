package com.example.billetera.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billetera.data.BilleteraRepository
import com.example.billetera.data.local.TransaccionEntity
import com.example.billetera.data.remote.EstadoConexion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel: puente entre la pantalla y el repositorio.
 *
 * viewModelScope trabaja sobre Dispatchers.Main: cuando el repositorio
 * termina su trabajo en Dispatchers.IO, el resultado "vuelve" al hilo
 * principal automaticamente y la UI se actualiza sin congelarse (sin ANR).
 */
class BilleteraViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BilleteraRepository.obtener(app)

    val saldo: StateFlow<Double> = repo.saldo
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val historial: StateFlow<List<TransaccionEntity>> = repo.historial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conexion: StateFlow<EstadoConexion> = repo.estadoConexion

    val servidor: StateFlow<String> = repo.servidor

    private val _procesando = MutableStateFlow(false)
    val procesando: StateFlow<Boolean> = _procesando.asStateFlow()

    /** Mensajes de una sola vez para el Snackbar (exito / error / ingreso). */
    private val _avisos = Channel<Aviso>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    init {
        // 1) Abrir el tunel WebSocket
        repo.conectarTiempoReal()

        // 2) Escuchar mensajes en tiempo real
        viewModelScope.launch {
            repo.mensajesTiempoReal.collect { mensaje ->
                if (mensaje.evento == "CONECTADO") {
                    repo.sincronizar()          // recupera lo que paso estando offline
                }
                val recibida = repo.procesarMensaje(mensaje)
                if (recibida != null) {
                    Log.d("ViewModel", "UI actualizada en hilo: ${Thread.currentThread().name}")
                    _avisos.send(
                        Aviso("Recibiste $${dinero(recibida.monto)} de la cuenta ${recibida.contraparte}", true)
                    )
                }
            }
        }

        // 3) Sincronizacion inicial (si falla, se muestra lo guardado en Room)
        viewModelScope.launch {
            repo.sincronizar().onFailure { _avisos.send(Aviso(it.message ?: "Sin conexion", false)) }
        }
    }

    // ---------- Acciones de los botones ----------
    fun depositar(monto: Double) = operar("Deposito realizado") { repo.depositar(monto) }

    fun retirar(monto: Double) = operar("Retiro realizado") { repo.retirar(monto) }

    fun transferir(destino: String, monto: Double) =
        operar("Transferencia enviada a la cuenta $destino") { repo.transferir(destino, monto) }

    fun pagarServicio(servicio: String, contrato: String, monto: Double) =
        operar("Pago de $servicio realizado") { repo.pagarServicio(servicio, contrato, monto) }

    private fun operar(exito: String, accion: suspend () -> Result<TransaccionEntity>) {
        viewModelScope.launch {                 // Dispatchers.Main
            _procesando.value = true
            val resultado = accion()            // internamente en Dispatchers.IO
            _procesando.value = false           // de vuelta en Main
            resultado
                .onSuccess { _avisos.send(Aviso(exito, true)) }
                .onFailure { _avisos.send(Aviso(it.message ?: "Error", false)) }
        }
    }

    fun reconectar() = repo.conectarTiempoReal()

    /** Cambia la direccion del backend desde el dialogo de configuracion. */
    fun cambiarServidor(direccion: String) {
        viewModelScope.launch {
            repo.cambiarServidor(direccion)
                .onSuccess { _avisos.send(Aviso("Servidor: $it", true)) }
                .onFailure { _avisos.send(Aviso(it.message ?: "Direccion invalida", false)) }
        }
    }

    override fun onCleared() {
        repo.desconectarTiempoReal()
        super.onCleared()
    }
}

data class Aviso(val texto: String, val exito: Boolean)

fun dinero(valor: Double): String = String.format(Locale.US, "%,.2f", valor)
