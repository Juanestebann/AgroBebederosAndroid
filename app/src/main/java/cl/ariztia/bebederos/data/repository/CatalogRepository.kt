package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.RemoteCatalog
import cl.ariztia.bebederos.data.model.RemoteWaterLine
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.api.CatalogApi
import cl.ariztia.bebederos.data.remote.mapper.toRemoteFarm
import cl.ariztia.bebederos.data.remote.mapper.toRemoteShed
import cl.ariztia.bebederos.data.remote.mapper.toRemoteWaterLine
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

enum class CatalogError {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    NETWORK,
    UNEXPECTED
}

sealed interface CatalogResult<out T> {
    data class Success<T>(val data: T) : CatalogResult<T>
    data class Failure(val error: CatalogError) : CatalogResult<Nothing>
}

/** Carga y valida la jerarquía remota permitida para la sesión actual. */
class CatalogRepository(
    private val catalogApi: CatalogApi
) {
    suspend fun loadCatalog(user: SessionUser): CatalogResult<RemoteCatalog> = execute {
        val receivedFarms = catalogApi.farms().map { it.toRemoteFarm() }
        val allowedFarms = when (user.role) {
            UserRole.OPERARIO -> {
                val assignedFarmId = user.farmId
                    ?: throw IllegalArgumentException("El operario no tiene granja asignada.")
                receivedFarms.filter { it.id == assignedFarmId }
            }
            UserRole.SUPERVISOR -> receivedFarms
        }

        val sheds = allowedFarms.flatMap { farm ->
            catalogApi.sheds(farm.id)
                .map { it.toRemoteShed() }
                .onEach { shed ->
                    require(shed.farmId == farm.id) {
                        "El galpón no pertenece a la granja solicitada."
                    }
                }
        }

        val lines = sheds.flatMap { shed ->
            catalogApi.lines(shed.id)
                .map { it.toRemoteWaterLine() }
                .onEach { line ->
                    require(line.shedId == shed.id && line.farmId == shed.farmId) {
                        "La línea no pertenece a la jerarquía solicitada."
                    }
                }
        }

        RemoteCatalog(allowedFarms, sheds, lines)
    }

    suspend fun line(lineId: Long): CatalogResult<RemoteWaterLine> = execute {
        catalogApi.line(lineId).toRemoteWaterLine().also { line ->
            require(line.id == lineId) { "El backend devolvió otra línea." }
        }
    }

    private suspend fun <T> execute(block: suspend () -> T): CatalogResult<T> = try {
        CatalogResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        CatalogResult.Failure(
            when (error.code()) {
                401 -> CatalogError.UNAUTHORIZED
                403 -> CatalogError.FORBIDDEN
                404 -> CatalogError.NOT_FOUND
                else -> CatalogError.UNEXPECTED
            }
        )
    } catch (_: IOException) {
        CatalogResult.Failure(CatalogError.NETWORK)
    } catch (_: Exception) {
        CatalogResult.Failure(CatalogError.UNEXPECTED)
    }
}
