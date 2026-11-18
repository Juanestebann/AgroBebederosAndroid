package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.AlertApi
import cl.ariztia.bebederos.data.remote.dto.AlertaResponseDto
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertIs
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AlertRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AlertRepository

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        val client = RetrofitProvider.createOkHttpClient(InMemoryCredentials())
        val retrofit = RetrofitProvider.createRetrofit(server.url("/").toString(), client)
        repository = AlertRepository(retrofit.create(AlertApi::class.java))
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun allMapsEveryVisibleAlertWithoutRecalculatingRemoteStatus() = runBlocking {
        server.enqueue(jsonResponse("[${alertJson(41L, "WARNING")},${alertJson(42L, "CRITICAL")} ]"))

        val success = assertIs<AlertResult.Success<*>>(repository.all())
        val alerts = success.data as List<*>

        assertEquals(2, alerts.size)
        assertEquals(
            listOf(TemperatureStatus.WARNING, TemperatureStatus.CRITICAL),
            alerts.map { (it as cl.ariztia.bebederos.data.model.RemoteAlert).status }
        )
        assertEquals("/api/v1/alertas", server.takeRequest().path)
    }

    @Test
    fun byIdUsesTheDocumentedIdAndKeepsLongIdentifiers() = runBlocking {
        val alertId = 4_000_000_041L
        server.enqueue(jsonResponse(alertJson(alertId, "CRITICAL")))

        val success = assertIs<AlertResult.Success<*>>(repository.byId(alertId))
        val alert = success.data as cl.ariztia.bebederos.data.model.RemoteAlert

        assertEquals(alertId, alert.id)
        assertEquals("/api/v1/alertas/$alertId", server.takeRequest().path)
    }

    @Test
    fun forbiddenAndMissingIdsProduceSafeResults() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(404))

        assertFailure(AlertError.FORBIDDEN, repository.byId(1L))
        assertFailure(AlertError.NOT_FOUND, repository.byId(2L))
    }

    @Test
    fun networkAndInvalidJsonFailuresAreControlled() = runBlocking {
        assertFailure(AlertError.NETWORK, AlertRepository(NetworkFailureAlertApi).all())
        server.enqueue(jsonResponse("{"))
        assertFailure(AlertError.UNEXPECTED, repository.all())
    }

    private fun assertFailure(expected: AlertError, result: AlertResult<*>) {
        assertEquals(expected, assertIs<AlertResult.Failure>(result).error)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun alertJson(id: Long, status: String) = """
        {
          "id":$id,
          "lineaId":30,
          "lineaNombre":"Línea 30",
          "galponId":20,
          "galponNombre":"Galpón 20",
          "granjaId":10,
          "granjaNombre":"Granja 10",
          "temperaturaC":28.00,
          "estado":"$status",
          "generadaEn":"2026-11-10T10:00:00-03:00"
        }
    """.trimIndent()

    private object NetworkFailureAlertApi : AlertApi {
        override suspend fun all(): List<AlertaResponseDto> = throw IOException("Sin red")
        override suspend fun byId(alertId: Long): AlertaResponseDto = throw IOException("Sin red")
    }
}
