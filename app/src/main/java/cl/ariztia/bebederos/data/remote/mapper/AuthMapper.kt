package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.dto.UsuarioResponseDto

/** Convierte el perfil JSON en una sesión Android que no contiene contraseña. */
fun UsuarioResponseDto.toSessionUser(): SessionUser {
    val remoteRole = when (rol) {
        "OPERARIO" -> UserRole.OPERARIO
        "SUPERVISOR" -> UserRole.SUPERVISOR
        else -> throw IllegalArgumentException("Rol remoto no reconocido.")
    }

    if (remoteRole == UserRole.OPERARIO && (granjaId == null || granjaNombre.isNullOrBlank())) {
        throw IllegalArgumentException("El operario remoto no tiene una granja válida.")
    }

    return SessionUser(
        id = id,
        email = email,
        name = nombre,
        role = remoteRole,
        farmId = granjaId,
        farm = granjaNombre
    )
}
