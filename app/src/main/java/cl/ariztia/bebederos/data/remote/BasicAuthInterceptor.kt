package cl.ariztia.bebederos.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Añade HTTP Basic a las peticiones posteriores a un login exitoso. */
class BasicAuthInterceptor(
    private val credentials: InMemoryCredentials
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // El login puede entregar su propio encabezado sin guardarlo antes de validar la cuenta.
        if (originalRequest.header(AUTHORIZATION_HEADER) != null) {
            return chain.proceed(originalRequest)
        }

        val activeCredentials = credentials.snapshot()
            ?: return chain.proceed(originalRequest)
        val authorization = BasicAuthorization.create(
            activeCredentials.email,
            activeCredentials.password
        )
        val authenticatedRequest = originalRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, authorization)
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
    }
}
