package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.CatalogApi
import cl.ariztia.bebederos.data.remote.dto.GalponResponseDto
import cl.ariztia.bebederos.data.remote.dto.GranjaResponseDto
import cl.ariztia.bebederos.data.remote.dto.LineaBebederoResponseDto
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: CatalogRepository

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        val client = RetrofitProvider.createOkHttpClient(InMemoryCredentials())
        val retrofit = RetrofitProvider.createRetrofit(server.url("/").toString(), client)
        repository = CatalogRepository(retrofit.create(CatalogApi::class.java))
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun operatorLoadsOnlyTheAssignedFarmAndItsHierarchy() = runBlocking {
        server.enqueue(jsonResponse("""[{"id":10,"nombre":"Asignada"},{"id":20,"nombre":"Otra"}]"""))
        server.enqueue(jsonResponse("""[{"id":100,"nombre":"Galpón 100","granjaId":10,"granjaNombre":"Asignada"}]"""))
        server.enqueue(jsonResponse("[${lineJson(1_000L, 100L, 10L)}]"))

        val success = assertIs<CatalogResult.Success<*>>(repository.loadCatalog(operator()))
        val catalog = success.data as cl.ariztia.bebederos.data.model.RemoteCatalog

        assertEquals(listOf(10L), catalog.farms.map { it.id })
        assertEquals(listOf(100L), catalog.sheds.map { it.id })
        assertEquals(listOf(1_000L), catalog.lines.map { it.id })
        assertEquals("/api/v1/granjas", server.takeRequest().path)
        assertEquals("/api/v1/granjas/10/galpones", server.takeRequest().path)
        assertEquals("/api/v1/galpones/100/lineas", server.takeRequest().path)
        assertEquals(0, server.requestCount - 3)
    }

    @Test
    fun supervisorLoadsEveryFarmAndCompleteHierarchy() = runBlocking {
        server.enqueue(jsonResponse("""[{"id":10,"nombre":"Norte"},{"id":20,"nombre":"Sur"}]"""))
        server.enqueue(jsonResponse("""[{"id":100,"nombre":"G100","granjaId":10,"granjaNombre":"Norte"}]"""))
        server.enqueue(jsonResponse("""[{"id":200,"nombre":"G200","granjaId":20,"granjaNombre":"Sur"}]"""))
        server.enqueue(jsonResponse("[${lineJson(1_000L, 100L, 10L)}]"))
        server.enqueue(jsonResponse("[${lineJson(2_000L, 200L, 20L)}]"))

        val success = assertIs<CatalogResult.Success<*>>(repository.loadCatalog(supervisor()))
        val catalog = success.data as cl.ariztia.bebederos.data.model.RemoteCatalog

        assertEquals(listOf(10L, 20L), catalog.farms.map { it.id })
        assertEquals(listOf(100L, 200L), catalog.sheds.map { it.id })
        assertEquals(listOf(1_000L, 2_000L), catalog.lines.map { it.id })
        assertEquals("/api/v1/granjas", server.takeRequest().path)
        assertEquals("/api/v1/granjas/10/galpones", server.takeRequest().path)
        assertEquals("/api/v1/granjas/20/galpones", server.takeRequest().path)
        assertEquals("/api/v1/galpones/100/lineas", server.takeRequest().path)
        assertEquals("/api/v1/galpones/200/lineas", server.takeRequest().path)
    }

    @Test
    fun emptyFarmListProducesAnEmptyRemoteCatalog() = runBlocking {
        server.enqueue(jsonResponse("[]"))

        val success = assertIs<CatalogResult.Success<*>>(repository.loadCatalog(supervisor()))
        val catalog = success.data as cl.ariztia.bebederos.data.model.RemoteCatalog

        assertTrue(catalog.farms.isEmpty())
        assertTrue(catalog.sheds.isEmpty())
        assertTrue(catalog.lines.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lineDetailUsesTheDocumentedEndpointAndRemoteTemperature() = runBlocking {
        server.enqueue(jsonResponse(lineJson(4_000_000_003L, 4_000_000_002L, 4_000_000_001L)))

        val success = assertIs<CatalogResult.Success<*>>(repository.line(4_000_000_003L))
        val line = success.data as cl.ariztia.bebederos.data.model.RemoteWaterLine

        assertEquals(4_000_000_003L, line.id)
        assertEquals(TemperatureStatus.CRITICAL, line.status)
        assertEquals("20.12", line.temperatureC.toPlainString())
        assertEquals("/api/v1/lineas/4000000003", server.takeRequest().path)
    }

    @Test
    fun unauthorizedResponseIsControlled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        assertFailure(CatalogError.UNAUTHORIZED, repository.loadCatalog(supervisor()))
    }

    @Test
    fun forbiddenResponseIsControlled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        assertFailure(CatalogError.FORBIDDEN, repository.loadCatalog(supervisor()))
    }

    @Test
    fun missingLineIsControlled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        assertFailure(CatalogError.NOT_FOUND, repository.line(999L))
    }

    @Test
    fun networkFailureIsControlled() = runBlocking {
        val repositoryWithoutNetwork = CatalogRepository(NetworkFailureCatalogApi)

        assertFailure(CatalogError.NETWORK, repositoryWithoutNetwork.loadCatalog(supervisor()))
    }

    @Test
    fun invalidJsonIsControlled() = runBlocking {
        server.enqueue(jsonResponse("{"))

        assertFailure(CatalogError.UNEXPECTED, repository.loadCatalog(supervisor()))
    }

    @Test
    fun inconsistentHierarchyIsRejected() = runBlocking {
        server.enqueue(jsonResponse("""[{"id":10,"nombre":"Asignada"}]"""))
        server.enqueue(jsonResponse("""[{"id":100,"nombre":"Galpón ajeno","granjaId":20,"granjaNombre":"Otra"}]"""))

        assertFailure(CatalogError.UNEXPECTED, repository.loadCatalog(operator()))
    }

    private fun assertFailure(expected: CatalogError, result: CatalogResult<*>) {
        assertEquals(expected, assertIs<CatalogResult.Failure>(result).error)
    }

    private fun operator() = SessionUser(
        id = 1L,
        email = "operario@example.cl",
        name = "Operario",
        role = UserRole.OPERARIO,
        farmId = 10L,
        farm = "Asignada"
    )

    private fun supervisor() = SessionUser(
        id = 2L,
        email = "supervisor@example.cl",
        name = "Supervisor",
        role = UserRole.SUPERVISOR,
        farmId = null,
        farm = null
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun lineJson(lineId: Long, shedId: Long, farmId: Long) = """
        {
          "id":$lineId,
          "nombre":"Línea $lineId",
          "galponId":$shedId,
          "galponNombre":"Galpón $shedId",
          "granjaId":$farmId,
          "granjaNombre":"Granja $farmId",
          "temperaturaEfectiva":{
            "valorCelsius":20.12,
            "estado":"CRITICAL",
            "origen":"LECTURA",
            "registradaEn":"2026-11-07T20:05:00-03:00"
          }
        }
    """.trimIndent()

    private object NetworkFailureCatalogApi : CatalogApi {
        override suspend fun farms(): List<GranjaResponseDto> = throw IOException("Sin red")
        override suspend fun sheds(farmId: Long): List<GalponResponseDto> = throw IOException("Sin red")
        override suspend fun lines(shedId: Long): List<LineaBebederoResponseDto> = throw IOException("Sin red")
        override suspend fun line(lineId: Long): LineaBebederoResponseDto = throw IOException("Sin red")
    }
}
