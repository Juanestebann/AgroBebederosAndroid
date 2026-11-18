package cl.ariztia.bebederos.data.remote

import okhttp3.Credentials

/** Construye el encabezado del login sin persistir la contraseña. */
object BasicAuthorization {
    fun create(email: String, password: String): String =
        Credentials.basic(email, password, Charsets.UTF_8)
}
