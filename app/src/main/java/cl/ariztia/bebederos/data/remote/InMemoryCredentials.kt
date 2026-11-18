package cl.ariztia.bebederos.data.remote

/** Credenciales activas de HTTP Basic; nunca se escriben en almacenamiento persistente. */
internal data class BasicCredentials(
    val email: String,
    val password: String
)

/**
 * Conserva las credenciales únicamente mientras vive el proceso de la aplicación.
 *
 * El login remoto guardará aquí el correo y la contraseña solo después de recibir una respuesta
 * exitosa. [clear] elimina la referencia al cerrar sesión. Las funciones sincronizadas permiten
 * que OkHttp lea el valor con seguridad aunque ejecute peticiones en distintos hilos.
 */
class InMemoryCredentials {
    private var current: BasicCredentials? = null

    @Synchronized
    fun save(email: String, password: String) {
        current = BasicCredentials(email, password)
    }

    @Synchronized
    fun clear() {
        current = null
    }

    @Synchronized
    fun hasCredentials(): Boolean = current != null

    @Synchronized
    internal fun snapshot(): BasicCredentials? = current?.copy()
}
