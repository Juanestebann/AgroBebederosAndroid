package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.GalponResponseDto
import cl.ariztia.bebederos.data.remote.dto.GranjaResponseDto
import cl.ariztia.bebederos.data.remote.dto.LineaBebederoResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface CatalogApi {
    @GET("api/v1/granjas")
    suspend fun farms(): List<GranjaResponseDto>

    @GET("api/v1/granjas/{id}/galpones")
    suspend fun sheds(
        @Path("id") farmId: Long
    ): List<GalponResponseDto>

    @GET("api/v1/galpones/{id}/lineas")
    suspend fun lines(
        @Path("id") shedId: Long
    ): List<LineaBebederoResponseDto>

    @GET("api/v1/lineas/{id}")
    suspend fun line(
        @Path("id") lineId: Long
    ): LineaBebederoResponseDto
}
