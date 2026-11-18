package cl.ariztia.bebederos.data.remote.dto

/**
 * Error del backend. Los campos son anulables porque las respuestas 401/403 son más breves que
 * los errores de validación.
 */
data class ApiErrorDto(
    val timestamp: String?,
    val status: Int?,
    val error: String?,
    val message: String?,
    val path: String?,
    val fieldErrors: Map<String, String>?
)
