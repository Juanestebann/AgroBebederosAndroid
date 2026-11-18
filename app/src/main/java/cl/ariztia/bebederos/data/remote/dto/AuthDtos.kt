package cl.ariztia.bebederos.data.remote.dto

/** Perfil autenticado devuelto por POST /auth/login y GET /me. */
data class UsuarioResponseDto(
    val id: Long,
    val nombre: String,
    val email: String,
    val rol: String,
    val granjaId: Long?,
    val granjaNombre: String?
)
