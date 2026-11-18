package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.RemoteFlushing
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.api.FlushingApi
import cl.ariztia.bebederos.data.remote.dto.ActualizarFlushingRequestDto
import cl.ariztia.bebederos.data.remote.dto.CrearFlushingRequestDto
import cl.ariztia.bebederos.data.remote.mapper.toRemoteFlushing
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response

enum class FlushingError {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    NETWORK,
    UNEXPECTED
}

sealed interface FlushingResult<out T> {
    data class Success<T>(val data: T) : FlushingResult<T>
    data class Failure(val error: FlushingError) : FlushingResult<Nothing>
}

data class CreateFlushingCommand(
    val clientOperationId: UUID,
    val reason: String,
    val observation: String?,
    val beforeTemperatureC: BigDecimal,
    val afterTemperatureC: BigDecimal
)

data class UpdateFlushingCommand(
    val reason: String,
    val observation: String?,
    val beforeTemperatureC: BigDecimal,
    val afterTemperatureC: BigDecimal,
    val version: Long
)

/** Única puerta de acceso al CRUD remoto de flushing. No conoce Room ni fotografías locales. */
class FlushingRepository(
    private val api: FlushingApi
) {
    suspend fun all(user: SessionUser): FlushingResult<List<RemoteFlushing>> = execute {
        api.all().map { it.toRemoteFlushing() }.also { records ->
            if (user.role == UserRole.OPERARIO) {
                val farmId = user.farmId
                    ?: throw IllegalArgumentException("El operario no tiene granja asignada.")
                require(records.all { it.farmId == farmId }) {
                    "La respuesta contiene registros fuera de la granja asignada."
                }
            }
        }
    }

    suspend fun forLine(lineId: Long): FlushingResult<List<RemoteFlushing>> = execute {
        api.forLine(lineId).map { it.toRemoteFlushing() }.also { records ->
            require(records.all { it.lineId == lineId }) {
                "La respuesta contiene registros de otra línea."
            }
        }
    }

    suspend fun byId(flushingId: Long): FlushingResult<RemoteFlushing> = execute {
        api.byId(flushingId).toRemoteFlushing().also { record ->
            require(record.id == flushingId) { "El backend devolvió otro flushing." }
        }
    }

    suspend fun create(
        lineId: Long,
        command: CreateFlushingCommand
    ): FlushingResult<RemoteFlushing> = execute {
        val response = api.create(
            lineId,
            CrearFlushingRequestDto(
                clientOperationId = command.clientOperationId.toString(),
                motivo = command.reason,
                observacion = command.observation,
                temperaturaAntesC = command.beforeTemperatureC,
                temperaturaDespuesC = command.afterTemperatureC
            )
        )
        responseBody(response, expectedCode = 201).toRemoteFlushing().also { record ->
            require(record.lineId == lineId) { "El flushing se creó para otra línea." }
            require(record.clientOperationId == command.clientOperationId) {
                "El backend devolvió otro identificador de operación."
            }
        }
    }

    suspend fun update(
        flushingId: Long,
        command: UpdateFlushingCommand
    ): FlushingResult<RemoteFlushing> = execute {
        val response = api.update(
            flushingId,
            ActualizarFlushingRequestDto(
                motivo = command.reason,
                observacion = command.observation,
                temperaturaAntesC = command.beforeTemperatureC,
                temperaturaDespuesC = command.afterTemperatureC,
                version = command.version
            )
        )
        responseBody(response, expectedCode = 200).toRemoteFlushing().also { record ->
            require(record.id == flushingId) { "El backend actualizó otro flushing." }
            require(record.version > command.version) {
                "El backend no incrementó la versión del flushing."
            }
        }
    }

    suspend fun delete(flushingId: Long): FlushingResult<Unit> = execute {
        val response = api.delete(flushingId)
        if (response.code() != 204) throw UnexpectedHttpCode(response.code())
    }

    private fun <T> responseBody(response: Response<T>, expectedCode: Int): T {
        if (response.code() != expectedCode) throw UnexpectedHttpCode(response.code())
        return response.body() ?: throw IllegalStateException("Respuesta HTTP sin cuerpo.")
    }

    private suspend fun <T> execute(block: suspend () -> T): FlushingResult<T> = try {
        FlushingResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: UnexpectedHttpCode) {
        FlushingResult.Failure(errorForStatus(error.code))
    } catch (error: HttpException) {
        FlushingResult.Failure(errorForStatus(error.code()))
    } catch (_: IOException) {
        FlushingResult.Failure(FlushingError.NETWORK)
    } catch (_: Exception) {
        FlushingResult.Failure(FlushingError.UNEXPECTED)
    }

    private fun errorForStatus(status: Int): FlushingError = when (status) {
        400 -> FlushingError.BAD_REQUEST
        401 -> FlushingError.UNAUTHORIZED
        403 -> FlushingError.FORBIDDEN
        404 -> FlushingError.NOT_FOUND
        409 -> FlushingError.CONFLICT
        else -> FlushingError.UNEXPECTED
    }

    private class UnexpectedHttpCode(val code: Int) : RuntimeException()
}
