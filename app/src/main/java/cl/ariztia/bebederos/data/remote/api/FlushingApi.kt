package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.ActualizarFlushingRequestDto
import cl.ariztia.bebederos.data.remote.dto.CrearFlushingRequestDto
import cl.ariztia.bebederos.data.remote.dto.FlushingResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface FlushingApi {
    @POST("api/v1/lineas/{id}/flushings")
    suspend fun create(
        @Path("id") lineId: Long,
        @Body request: CrearFlushingRequestDto
    ): Response<FlushingResponseDto>

    @GET("api/v1/flushings")
    suspend fun all(): List<FlushingResponseDto>

    @GET("api/v1/lineas/{id}/flushings")
    suspend fun forLine(
        @Path("id") lineId: Long
    ): List<FlushingResponseDto>

    @GET("api/v1/flushings/{id}")
    suspend fun byId(
        @Path("id") flushingId: Long
    ): FlushingResponseDto

    @PATCH("api/v1/flushings/{id}")
    suspend fun update(
        @Path("id") flushingId: Long,
        @Body request: ActualizarFlushingRequestDto
    ): Response<FlushingResponseDto>

    @DELETE("api/v1/flushings/{id}")
    suspend fun delete(
        @Path("id") flushingId: Long
    ): Response<Unit>
}
