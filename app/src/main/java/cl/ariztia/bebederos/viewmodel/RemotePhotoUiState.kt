package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemotePhoto
import cl.ariztia.bebederos.data.repository.PhotoError
import cl.ariztia.bebederos.data.repository.RemotePhotoContent

data class LocalPhotoCandidate(
    val filePath: String,
    val contentUri: String
)

sealed interface RemotePhotoUiState {
    data object Idle : RemotePhotoUiState
    data object LoadingMetadata : RemotePhotoUiState
    data object NoPhoto : RemotePhotoUiState
    data class Downloading(val metadata: RemotePhoto) : RemotePhotoUiState
    data class Available(val content: RemotePhotoContent) : RemotePhotoUiState
    data class Uploading(val candidate: LocalPhotoCandidate) : RemotePhotoUiState
    data class UploadError(
        val candidate: LocalPhotoCandidate,
        val error: PhotoError
    ) : RemotePhotoUiState
    data class LoadError(val error: PhotoError) : RemotePhotoUiState
    data class Deleting(val content: RemotePhotoContent) : RemotePhotoUiState
    data class DeleteError(
        val content: RemotePhotoContent,
        val error: PhotoError
    ) : RemotePhotoUiState
}

internal class PhotoOperationGate {
    private val activeIds = mutableSetOf<Long>()

    @Synchronized
    fun tryLock(flushingId: Long): Boolean = activeIds.add(flushingId)

    @Synchronized
    fun unlock(flushingId: Long) {
        activeIds.remove(flushingId)
    }

    @Synchronized
    fun clear() {
        activeIds.clear()
    }
}

fun photoErrorMessage(error: PhotoError): String = when (error) {
    PhotoError.FILE_NOT_FOUND -> "La fotografía local ya no está disponible."
    PhotoError.EMPTY_FILE -> "La fotografía está vacía."
    PhotoError.TOO_LARGE -> "La fotografía supera el máximo de 5 MB."
    PhotoError.UNSUPPORTED_FORMAT -> "Solo se permiten fotografías JPEG o PNG válidas."
    PhotoError.BAD_REQUEST -> "El backend rechazó la fotografía."
    PhotoError.UNAUTHORIZED -> "La sesión ya no es válida."
    PhotoError.FORBIDDEN -> "No tienes autorización para modificar esta fotografía."
    PhotoError.NOT_FOUND -> "El flushing o su fotografía ya no están disponibles."
    PhotoError.NETWORK -> "No fue posible conectar con el backend."
    PhotoError.UNEXPECTED -> "El backend devolvió una respuesta fotográfica inesperada."
}
