package com.example.billetera.data.remote

import com.example.billetera.Config
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Clientes de red compartidos por toda la app. */
object RedCliente {

    val gson: Gson = Gson()

    /** Cliente para REST: con timeouts y log de peticiones (ver Logcat "okhttp"). */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    /**
     * Cliente para el WebSocket: sin timeout de lectura (el tunel queda
     * abierto indefinidamente) y con ping cada 20 s para detectar caidas.
     */
    val okHttpWebSocket: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val api: BilleteraApi = Retrofit.Builder()
        .baseUrl(Config.BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(BilleteraApi::class.java)
}
