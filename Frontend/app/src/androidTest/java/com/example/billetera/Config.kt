package com.example.billetera

import android.content.Context

/**
 * Configuracion de conexion con el backend.
 *
 * La direccion del servidor se puede cambiar DESDE LA APP (icono de engranaje)
 * y se guarda en SharedPreferences, asi el mismo APK sirve en:
 *  - Emulador:            http://10.0.2.2:3000   (10.0.2.2 = "localhost" de la PC)
 *  - Celular en el WiFi:  http://192.168.x.x:3000 (IP de la PC con ipconfig)
 *  - Servidor en la nube: https://mi-billetera.onrender.com
 */
object Config {
    const val CUENTA = "1001"
    const val SERVIDOR_POR_DEFECTO = "http://10.0.2.2:3000"

    private const val PREFS = "config_billetera"
    private const val CLAVE_SERVIDOR = "servidor"

    fun leerServidor(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CLAVE_SERVIDOR, SERVIDOR_POR_DEFECTO) ?: SERVIDOR_POR_DEFECTO

    fun guardarServidor(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CLAVE_SERVIDOR, url).apply()
    }

    /** Limpia lo que escribe el usuario: agrega http:// si falta y quita "/" final. */
    fun normalizar(entrada: String): String {
        var url = entrada.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url
    }

    /** Retrofit necesita la URL terminada en "/". */
    fun baseUrl(servidor: String) = "$servidor/"

    /** http -> ws  y  https -> wss */
    fun wsUrl(servidor: String) = servidor.replaceFirst("http", "ws") + "/ws?cuenta=$CUENTA"
}
