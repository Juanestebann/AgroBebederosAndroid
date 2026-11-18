package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.UsuarioResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(
        @Header("Authorization") authorization: String
    ): UsuarioResponseDto

    @GET("api/v1/me")
    suspend fun currentUser(): UsuarioResponseDto
}
