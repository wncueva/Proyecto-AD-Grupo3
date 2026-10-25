package com.example.billetera

/**
 * Configuracion de conexion con el backend.
 *
 * 10.0.2.2 es la direccion especial con la que el EMULADOR de Android
 * llega al "localhost" de la PC donde corre el servidor Node.js.
 * Si pruebas en un celular fisico, cambia HOST por la IP de tu PC
 * (ej. "192.168.1.15:3000") y conecta ambos al mismo WiFi.
 */
object Config {
    const val CUENTA = "1001"
    const val HOST = "10.0.2.2:3000"
    const val BASE_URL = "http://$HOST/"
    const val WS_URL = "ws://$HOST/ws?cuenta=$CUENTA"
}
