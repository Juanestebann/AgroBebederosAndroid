package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.LecturaTemperaturaRequestDto
import cl.ariztia.bebederos.data.remote.dto.LecturaTemperaturaResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TemperatureApi {
    @POST("api/v1/lineas/{id}/temperaturas")
    suspend fun register(
        @Path("id") lineId: Long,
        @Body request: LecturaTemperaturaRequestDto
    ): LecturaTemperaturaResponseDto

    @GET("api/v1/lineas/{id}/temperaturas")
    suspend fun history(
        @Path("id") lineId: Long
    ): List<LecturaTemperaturaResponseDto>
}
