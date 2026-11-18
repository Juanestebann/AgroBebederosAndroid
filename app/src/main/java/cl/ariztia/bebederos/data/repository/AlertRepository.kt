package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.remote.api.AlertApi
import cl.ariztia.bebederos.data.remote.mapper.toRemoteAlert
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

enum class AlertError {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    NETWORK,
    UNEXPECTED
}

sealed interface AlertResult<out T> {
    data class Success<T>(val data: T) : AlertResult<T>
    data class Failure(val error: AlertError) : AlertResult<Nothing>
}

/** Consulta las alertas que Spring Boot ya filtró según el usuario autenticado. */
class AlertRepository(private val api: AlertApi) {
    suspend fun all(): AlertResult<List<RemoteAlert>> = execute {
        api.all().map { it.toRemoteAlert() }
    }

    suspend fun byId(alertId: Long): AlertResult<RemoteAlert> = execute {
        require(alertId > 0) { "La alerta remota no es válida." }
        api.byId(alertId).toRemoteAlert().also { alert ->
            require(alert.id == alertId) { "El backend devolvió otra alerta." }
        }
    }

    private suspend fun <T> execute(block: suspend () -> T): AlertResult<T> = try {
        AlertResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        AlertResult.Failure(
            when (error.code()) {
                401 -> AlertError.UNAUTHORIZED
                403 -> AlertError.FORBIDDEN
                404 -> AlertError.NOT_FOUND
                else -> AlertError.UNEXPECTED
            }
        )
    } catch (_: IOException) {
        AlertResult.Failure(AlertError.NETWORK)
    } catch (_: Exception) {
        AlertResult.Failure(AlertError.UNEXPECTED)
    }
}
