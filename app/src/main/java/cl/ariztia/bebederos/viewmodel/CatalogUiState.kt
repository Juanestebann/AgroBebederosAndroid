package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemoteCatalog
import cl.ariztia.bebederos.data.model.RemoteWaterLine
import cl.ariztia.bebederos.data.repository.CatalogError

sealed interface CatalogUiState {
    data object Idle : CatalogUiState
    data object Loading : CatalogUiState
    data object Empty : CatalogUiState
    data class Content(val catalog: RemoteCatalog) : CatalogUiState
    data class Error(val error: CatalogError) : CatalogUiState
}

sealed interface LineDetailUiState {
    data object Idle : LineDetailUiState
    data object Loading : LineDetailUiState
    data class Content(val line: RemoteWaterLine) : LineDetailUiState
    data class Error(val error: CatalogError) : LineDetailUiState
}

internal data class RemoteCatalogSelection(
    val farmId: Long? = null,
    val shedId: Long? = null,
    val lineId: Long? = null
)

val CatalogUiState.catalogOrNull: RemoteCatalog?
    get() = (this as? CatalogUiState.Content)?.catalog

internal fun isCurrentSessionResult(
    requestGeneration: Long,
    currentGeneration: Long
): Boolean = requestGeneration == currentGeneration

fun catalogErrorMessage(error: CatalogError): String = when (error) {
    CatalogError.UNAUTHORIZED -> "La sesión ya no es válida."
    CatalogError.FORBIDDEN -> "No tienes permiso para consultar estos datos."
    CatalogError.NOT_FOUND -> "El recurso solicitado ya no existe."
    CatalogError.NETWORK -> "No fue posible conectar con el backend."
    CatalogError.UNEXPECTED -> "El backend devolvió una respuesta inesperada."
}
