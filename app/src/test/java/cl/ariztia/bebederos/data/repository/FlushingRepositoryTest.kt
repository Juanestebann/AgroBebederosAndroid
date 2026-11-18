package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.FlushingApi
import cl.ariztia.bebederos.data.remote.dto.ActualizarFlushingRequestDto
import cl.ariztia.bebederos.data.remote.dto.CrearFlushingRequestDto
import cl.ariztia.bebederos.data.remote.dto.FlushingResponseDto
import com.google.gson.JsonParser
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class FlushingRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: FlushingRepository

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        val client = RetrofitProvider.createOkHttpClient(InMemoryCredentials())
        val retrofit = RetrofitProvider.createRetrofit(server.url("/").toString(), client)
        repository = FlushingRepository(retrofit.create(FlushingApi::class.java))
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun createUsesDocumentedEndpointAndSendsOnlyTrustedRequestFields() = runBlocking {
        server.enqueue(jsonResponse(flushingJson(), status = 201))

        val result = repository.create(LINE_ID, createCommand())
        val created = assertIs<FlushingResult.Success<*>>(result).data
        val request = server.takeRequest()
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("POST", request.method)
        assertEquals("/api/v1/lineas/$LINE_ID/flushings", request.path)
        assertEquals(FLUSHING_ID, (created as cl.ariztia.bebederos.data.model.RemoteFlushing).id)
        assertEquals(OPERATION_ID.toString(), json["clientOperationId"].asString)
        assertEquals("Limpieza preventiva", json["motivo"].asString)
        assertEquals(BigDecimal("28.90"), json["temperaturaAntesC"].asBigDecimal)
        assertEquals(BigDecimal("21.50"), json["temperaturaDespuesC"].asBigDecimal)
        assertFalse(json.has("operarioId"))
        assertFalse(json.has("granjaId"))
        assertFalse(json.has("galponId"))
        assertFalse(json.has("lineaId"))
        assertFalse(json.has("registradoEn"))
        assertFalse(json.has("photoUri"))
        assertFalse(json.has("version"))
    }

    @Test
    fun createRequires201Created() = runBlocking {
        server.enqueue(jsonResponse(flushingJson(), status = 200))

        assertFailure(FlushingError.UNEXPECTED, repository.create(LINE_ID, createCommand()))
    }

    @Test
    fun lineHistoryAndDetailUseDocumentedEndpoints() = runBlocking {
        server.enqueue(jsonResponse("[${flushingJson()}]"))
        server.enqueue(jsonResponse(flushingJson()))

        val history = assertIs<FlushingResult.Success<*>>(repository.forLine(LINE_ID))
        val detail = assertIs<FlushingResult.Success<*>>(repository.byId(FLUSHING_ID))

        assertEquals(1, (history.data as List<*>).size)
        assertEquals(FLUSHING_ID, (detail.data as cl.ariztia.bebederos.data.model.RemoteFlushing).id)
        assertEquals("/api/v1/lineas/$LINE_ID/flushings", server.takeRequest().path)
        assertEquals("/api/v1/flushings/$FLUSHING_ID", server.takeRequest().path)
    }

    @Test
    fun supervisorCanReadGlobalListAndOperatorRejectsAnotherFarm() = runBlocking {
        server.enqueue(jsonResponse("[${flushingJson()}]"))
        server.enqueue(jsonResponse("[${flushingJson()}]"))

        val supervisorResult = repository.all(supervisor())
        val operatorResult = repository.all(operator(farmId = FARM_ID + 1L))

        assertIs<FlushingResult.Success<*>>(supervisorResult)
        assertFailure(FlushingError.UNEXPECTED, operatorResult)
        assertEquals("/api/v1/flushings", server.takeRequest().path)
        assertEquals("/api/v1/flushings", server.takeRequest().path)
    }

    @Test
    fun updateSendsCurrentVersionAndAcceptsOnlyIncrementedResponse() = runBlocking {
        server.enqueue(jsonResponse(flushingJson(version = 8L)))

        val result = repository.update(FLUSHING_ID, updateCommand(version = 7L))
        val request = server.takeRequest()
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertIs<FlushingResult.Success<*>>(result)
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/flushings/$FLUSHING_ID", request.path)
        assertEquals(7L, json["version"].asLong)
        assertFalse(json.has("operarioId"))
        assertFalse(json.has("registradoEn"))
        assertFalse(json.has("clientOperationId"))
        assertFalse(json.has("photoUri"))
    }

    @Test
    fun updateConflictIsExposedWithoutSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409))

        assertFailure(
            FlushingError.CONFLICT,
            repository.update(FLUSHING_ID, updateCommand(version = 7L))
        )
    }

    @Test
    fun deleteRequires204NoContent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200))

        assertIs<FlushingResult.Success<Unit>>(repository.delete(FLUSHING_ID))
        assertFailure(FlushingError.UNEXPECTED, repository.delete(FLUSHING_ID))
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/api/v1/flushings/$FLUSHING_ID", server.takeRequest().path)
    }

    @Test
    fun documentedHttpErrorsAreMapped() = runBlocking {
        listOf(
            400 to FlushingError.BAD_REQUEST,
            401 to FlushingError.UNAUTHORIZED,
            403 to FlushingError.FORBIDDEN,
            404 to FlushingError.NOT_FOUND
        ).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status))
            assertFailure(expected, repository.byId(FLUSHING_ID))
        }
    }

    @Test
    fun invalidJsonAndConnectionFailureAreControlled() = runBlocking {
        server.enqueue(jsonResponse("{"))

        assertFailure(FlushingError.UNEXPECTED, repository.all(supervisor()))
        assertFailure(
            FlushingError.NETWORK,
            FlushingRepository(NetworkFailureApi).all(supervisor())
        )
    }

    @Test
    fun inconsistentLineAndOperationIdAreRejected() = runBlocking {
        server.enqueue(jsonResponse("[${flushingJson(lineId = LINE_ID + 1L)}]"))
        server.enqueue(jsonResponse(flushingJson(operationId = UUID.randomUUID()), status = 201))

        assertFailure(FlushingError.UNEXPECTED, repository.forLine(LINE_ID))
        assertFailure(FlushingError.UNEXPECTED, repository.create(LINE_ID, createCommand()))
    }

    private fun assertFailure(expected: FlushingError, result: FlushingResult<*>) {
        assertEquals(expected, assertIs<FlushingResult.Failure>(result).error)
    }

    private fun createCommand() = CreateFlushingCommand(
        clientOperationId = OPERATION_ID,
        reason = "Limpieza preventiva",
        observation = "Sin novedades",
        beforeTemperatureC = BigDecimal("28.90"),
        afterTemperatureC = BigDecimal("21.50")
    )

    private fun updateCommand(version: Long) = UpdateFlushingCommand(
        reason = "Corrección",
        observation = null,
        beforeTemperatureC = BigDecimal("28.90"),
        afterTemperatureC = BigDecimal("23.50"),
        version = version
    )

    private fun operator(farmId: Long = FARM_ID) = SessionUser(
        id = OPERATOR_ID,
        email = "operario@example.cl",
        name = "Operario remoto",
        role = UserRole.OPERARIO,
        farmId = farmId,
        farm = "Granja remota"
    )

    private fun supervisor() = SessionUser(
        id = 90L,
        email = "supervisor@example.cl",
        name = "Supervisor",
        role = UserRole.SUPERVISOR,
        farmId = null,
        farm = null
    )

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun flushingJson(
        lineId: Long = LINE_ID,
        version: Long = 7L,
        operationId: UUID = OPERATION_ID
    ) = """
        {
          "id":$FLUSHING_ID,
          "lineaId":$lineId,
          "lineaNombre":"Línea remota",
          "galponId":$SHED_ID,
          "galponNombre":"Galpón remoto",
          "granjaId":$FARM_ID,
          "granjaNombre":"Granja remota",
          "operarioId":$OPERATOR_ID,
          "operarioNombre":"Operario remoto",
          "registradoEn":"2026-11-07T20:05:00-03:00",
          "motivo":"Limpieza preventiva",
          "observacion":"Sin novedades",
          "completado":true,
          "temperaturaAntesC":28.90,
          "temperaturaDespuesC":21.50,
          "clientOperationId":"$operationId",
          "version":$version
        }
    """.trimIndent()

    private object NetworkFailureApi : FlushingApi {
        override suspend fun create(
            lineId: Long,
            request: CrearFlushingRequestDto
        ): Response<FlushingResponseDto> = throw IOException("Sin red")

        override suspend fun all(): List<FlushingResponseDto> = throw IOException("Sin red")
        override suspend fun forLine(lineId: Long): List<FlushingResponseDto> =
            throw IOException("Sin red")
        override suspend fun byId(flushingId: Long): FlushingResponseDto =
            throw IOException("Sin red")
        override suspend fun update(
            flushingId: Long,
            request: ActualizarFlushingRequestDto
        ): Response<FlushingResponseDto> = throw IOException("Sin red")
        override suspend fun delete(flushingId: Long): Response<Unit> = throw IOException("Sin red")
    }

    private companion object {
        val OPERATION_ID: UUID = UUID.fromString("61a7048d-cc44-4e6a-a38d-536235097da0")
        const val FARM_ID = 4_000_000_001L
        const val SHED_ID = 4_000_000_002L
        const val LINE_ID = 4_000_000_003L
        const val OPERATOR_ID = 4_000_000_004L
        const val FLUSHING_ID = 4_000_000_005L
    }
}
