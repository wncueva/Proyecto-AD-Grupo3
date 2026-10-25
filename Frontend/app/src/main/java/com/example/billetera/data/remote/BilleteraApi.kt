package com.example.billetera.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Endpoints REST del backend (Retrofit).
 * Son funciones "suspend": se ejecutan dentro de una corrutina y
 * NUNCA en el hilo principal (las llamamos desde Dispatchers.IO).
 * Usamos Response<...> para poder leer los errores HTTP (400, 404, 422).
 */
interface BilleteraApi {

    @GET("api/cuentas/{id}")
    suspend fun consultarCuenta(@Path("id") cuenta: String): CuentaResponse

    @GET("api/cuentas/{id}/transacciones")
    suspend fun obtenerTransacciones(@Path("id") cuenta: String): List<TransaccionDto>

    @POST("api/cuentas/{id}/deposito")
    suspend fun depositar(
        @Path("id") cuenta: String,
        @Body body: MontoRequest
    ): Response<OperacionResponse>

    @POST("api/cuentas/{id}/retiro")
    suspend fun retirar(
        @Path("id") cuenta: String,
        @Body body: MontoRequest
    ): Response<OperacionResponse>

    @POST("api/transferencias")
    suspend fun transferir(@Body body: TransferenciaRequest): Response<OperacionResponse>

    @POST("api/servicios/pagar")
    suspend fun pagarServicio(@Body body: PagoServicioRequest): Response<OperacionResponse>
}
