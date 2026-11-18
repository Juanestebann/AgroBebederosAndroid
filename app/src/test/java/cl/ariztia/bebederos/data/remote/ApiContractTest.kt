package cl.ariztia.bebederos.data.remote

import cl.ariztia.bebederos.data.remote.api.AlertApi
import cl.ariztia.bebederos.data.remote.api.AuthApi
import cl.ariztia.bebederos.data.remote.api.CatalogApi
import cl.ariztia.bebederos.data.remote.api.FlushingApi
import cl.ariztia.bebederos.data.remote.api.PhotoApi
import cl.ariztia.bebederos.data.remote.api.TemperatureApi
import java.lang.reflect.Method
import kotlin.test.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

class ApiContractTest {

    @Test
    fun retrofitInterfacesContainExactlyTheDocumentedEndpoints() {
        val services = listOf(
            AuthApi::class.java,
            CatalogApi::class.java,
            TemperatureApi::class.java,
            FlushingApi::class.java,
            AlertApi::class.java,
            PhotoApi::class.java
        )
        val actualEndpoints = services
            .flatMap { service -> service.declaredMethods.mapNotNull(::endpointOf) }
            .toSet()

        assertEquals(EXPECTED_ENDPOINTS, actualEndpoints)
    }

    private fun endpointOf(method: Method): String? = when {
        method.isAnnotationPresent(GET::class.java) ->
            "GET ${requireNotNull(method.getAnnotation(GET::class.java)).value}"
        method.isAnnotationPresent(POST::class.java) ->
            "POST ${requireNotNull(method.getAnnotation(POST::class.java)).value}"
        method.isAnnotationPresent(PATCH::class.java) ->
            "PATCH ${requireNotNull(method.getAnnotation(PATCH::class.java)).value}"
        method.isAnnotationPresent(DELETE::class.java) ->
            "DELETE ${requireNotNull(method.getAnnotation(DELETE::class.java)).value}"
        else -> null
    }

    private companion object {
        val EXPECTED_ENDPOINTS = setOf(
            "POST api/v1/auth/login",
            "GET api/v1/me",
            "GET api/v1/granjas",
            "GET api/v1/granjas/{id}/galpones",
            "GET api/v1/galpones/{id}/lineas",
            "GET api/v1/lineas/{id}",
            "POST api/v1/lineas/{id}/temperaturas",
            "GET api/v1/lineas/{id}/temperaturas",
            "POST api/v1/lineas/{id}/flushings",
            "GET api/v1/flushings",
            "GET api/v1/lineas/{id}/flushings",
            "GET api/v1/flushings/{id}",
            "PATCH api/v1/flushings/{id}",
            "DELETE api/v1/flushings/{id}",
            "GET api/v1/alertas",
            "GET api/v1/alertas/{id}",
            "POST api/v1/flushings/{id}/foto",
            "GET api/v1/flushings/{id}/foto/metadata",
            "GET api/v1/flushings/{id}/foto",
            "DELETE api/v1/flushings/{id}/foto"
        )
    }
}
