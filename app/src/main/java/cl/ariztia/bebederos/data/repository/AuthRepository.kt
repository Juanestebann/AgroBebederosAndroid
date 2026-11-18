package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.remote.BasicAuthorization
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.api.AuthApi
import cl.ariztia.bebederos.data.remote.mapper.toSessionUser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Resultado controlado del login remoto. */
sealed interface AuthResult {
    data class Success(val user: SessionUser) : AuthResult
    data object InvalidCredentials : AuthResult
    data object NetworkError : AuthResult
    data object UnexpectedResponse : AuthResult
}

/** Coordina el endpoint de login y la sesión HTTP Basic mantenida solo en memoria. */
class AuthRepository(
    private val authApi: AuthApi,
    private val credentials: InMemoryCredentials
) {
    suspend fun login(email: String, password: String): AuthResult {
        credentials.clear()

        return try {
            val authorization = BasicAuthorization.create(email, password)
            val user = authApi.login(authorization).toSessionUser()

            // Solo una respuesta exitosa y válida permite conservar las credenciales.
            credentials.save(email, password)
            AuthResult.Success(user)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            credentials.clear()
            if (error.code() == 401) {
                AuthResult.InvalidCredentials
            } else {
                AuthResult.UnexpectedResponse
            }
        } catch (_: IOException) {
            credentials.clear()
            AuthResult.NetworkError
        } catch (_: Exception) {
            credentials.clear()
            AuthResult.UnexpectedResponse
        }
    }

    fun logout() {
        credentials.clear()
    }
}
