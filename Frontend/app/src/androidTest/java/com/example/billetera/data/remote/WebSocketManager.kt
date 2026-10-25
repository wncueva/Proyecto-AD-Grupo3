package com.example.billetera.data.remote

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class EstadoConexion { CONECTADO, CONECTANDO, DESCONECTADO }

/**
 * Maneja el tunel WebSocket con el servidor (escucha asincrona continua).
 *
 * - OkHttp ejecuta los callbacks (onOpen, onMessage...) en SU PROPIO hilo
 *   de fondo, nunca en el hilo principal.
 * - Los mensajes se publican en un SharedFlow; el ViewModel los recoge.
 * - Si la conexion se cae, se reintenta automaticamente en Dispatchers.IO.
 */
class WebSocketManager(
    private val cliente: OkHttpClient,
    private val gson: Gson,
    url: String
) {
    @Volatile var url: String = url
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _estado = MutableStateFlow(EstadoConexion.DESCONECTADO)
    val estado: StateFlow<EstadoConexion> = _estado.asStateFlow()

    private val _mensajes = MutableSharedFlow<WsMensaje>(extraBufferCapacity = 32)
    val mensajes: SharedFlow<WsMensaje> = _mensajes.asSharedFlow()

    @Volatile private var socket: WebSocket? = null
    private var reconexion: Job? = null
    private var cerradoManualmente = false
    private var intentos = 0

    fun conectar() {
        if (_estado.value != EstadoConexion.DESCONECTADO) return
        cerradoManualmente = false
        _estado.value = EstadoConexion.CONECTANDO
        // La apertura del WebSocket se despacha en el hilo de red (Dispatchers.IO)
        scope.launch {
            Log.d(TAG, "Conectando a $url (hilo: ${Thread.currentThread().name})")
            try {
                val request = Request.Builder().url(url).build()
                socket = cliente.newWebSocket(request, listener)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "URL invalida: $url")
                _estado.value = EstadoConexion.DESCONECTADO
            }
        }
    }

    /** Cambia de servidor: cierra el tunel actual y abre uno nuevo. */
    fun cambiarUrl(nueva: String) {
        desconectar()
        url = nueva
        intentos = 0
        conectar()
    }

    fun desconectar() {
        cerradoManualmente = true
        reconexion?.cancel()
        socket?.close(1000, "Cierre de la app")
        socket = null
        _estado.value = EstadoConexion.DESCONECTADO
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) return
            Log.d(TAG, "Conectado (hilo: ${Thread.currentThread().name})")
            intentos = 0
            _estado.value = EstadoConexion.CONECTADO
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Mensaje recibido: $text")
            runCatching { gson.fromJson(text, WsMensaje::class.java) }
                .onSuccess { _mensajes.tryEmit(it) }
                .onFailure { Log.e(TAG, "JSON invalido", it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Cerrado: $code $reason")
            manejarDesconexion(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Fallo de conexion: ${t.message}")
            manejarDesconexion(webSocket)
        }
    }

    /** Marca desconectado y programa un reintento (2s, 4s, 8s... max 15s). */
    private fun manejarDesconexion(webSocket: WebSocket) {
        if (webSocket !== socket) return       // ignora sockets viejos ya cerrados
        _estado.value = EstadoConexion.DESCONECTADO
        socket = null
        if (cerradoManualmente) return
        reconexion?.cancel()
        reconexion = scope.launch {
            val espera = (2000L shl intentos.coerceAtMost(3)).coerceAtMost(15_000L)
            intentos++
            Log.d(TAG, "Reintentando en ${espera / 1000}s...")
            delay(espera)
            conectar()
        }
    }

    companion object {
        private const val TAG = "WebSocket"
    }
}
