package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemoteFlushing
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.repository.FlushingError
import java.util.UUID

sealed interface RemoteFlushingListUiState {
    data object Idle : RemoteFlushingListUiState
    data object Loading : RemoteFlushingListUiState
    data class Ready(val records: List<RemoteFlushing>) : RemoteFlushingListUiState
    data class Error(val error: FlushingError) : RemoteFlushingListUiState
}

val RemoteFlushingListUiState.records: List<RemoteFlushing>
    get() = (this as? RemoteFlushingListUiState.Ready)?.records.orEmpty()

sealed interface FlushingEditPreparationUiState {
    data object Idle : FlushingEditPreparationUiState
    data object Loading : FlushingEditPreparationUiState
    data class Ready(val recordId: Long) : FlushingEditPreparationUiState
    data class Error(val error: FlushingError) : FlushingEditPreparationUiState
}

/** Mantiene el mismo UUID durante los reintentos y crea otro para un formulario nuevo. */
internal class FlushingOperationId(
    private val generator: () -> UUID = UUID::randomUUID
) {
    private var prepared: UUID? = null

    fun prepareNew(): UUID = generator().also { prepared = it }

    fun currentOrPrepare(): UUID = prepared ?: prepareNew()

    fun clear() {
        prepared = null
    }
}

/** La UI oculta mutaciones ajenas; Spring Boot vuelve a autorizar siempre. */
internal fun canManageRemoteFlushing(
    user: SessionUser?,
    record: RemoteFlushing
): Boolean = user?.role == UserRole.OPERARIO &&
    user.id == record.operatorId &&
    user.farmId == record.farmId

/** Comprueba que PATCH no haya alterado los campos que el cliente nunca envía como editables. */
internal fun hasSameRemoteFlushingIdentity(
    original: RemoteFlushing,
    updated: RemoteFlushing
): Boolean = original.id == updated.id &&
    original.lineId == updated.lineId &&
    original.shedId == updated.shedId &&
    original.farmId == updated.farmId &&
    original.operatorId == updated.operatorId &&
    original.registeredAt == updated.registeredAt &&
    original.clientOperationId == updated.clientOperationId &&
    original.completed == updated.completed

fun flushingErrorMessage(error: FlushingError): String = when (error) {
    FlushingError.BAD_REQUEST -> "El backend rechazó los datos del flushing. Revisa el formulario."
    FlushingError.UNAUTHORIZED -> "La sesión ya no es válida."
    FlushingError.FORBIDDEN -> "No tienes autorización para realizar esta operación."
    FlushingError.NOT_FOUND -> "El flushing solicitado ya no existe."
    FlushingError.CONFLICT -> "El flushing cambió en el servidor. Se cargó su versión más reciente."
    FlushingError.NETWORK -> "No fue posible conectar con el backend."
    FlushingError.UNEXPECTED -> "El backend devolvió una respuesta inesperada."
}
