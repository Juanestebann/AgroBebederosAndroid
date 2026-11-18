package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.AlertaResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AlertApi {
    @GET("api/v1/alertas")
    suspend fun all(): List<AlertaResponseDto>

    @GET("api/v1/alertas/{id}")
    suspend fun byId(
        @Path("id") alertId: Long
    ): AlertaResponseDto
}
