package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.EvidenciaResponseDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface PhotoApi {
    @Multipart
    @POST("api/v1/flushings/{id}/foto")
    suspend fun upload(
        @Path("id") flushingId: Long,
        @Part archivo: MultipartBody.Part
    ): Response<EvidenciaResponseDto>

    @GET("api/v1/flushings/{id}/foto/metadata")
    suspend fun metadata(
        @Path("id") flushingId: Long
    ): Response<EvidenciaResponseDto>

    @Streaming
    @GET("api/v1/flushings/{id}/foto")
    suspend fun download(
        @Path("id") flushingId: Long
    ): Response<ResponseBody>

    @DELETE("api/v1/flushings/{id}/foto")
    suspend fun delete(
        @Path("id") flushingId: Long
    ): Response<Unit>
}
