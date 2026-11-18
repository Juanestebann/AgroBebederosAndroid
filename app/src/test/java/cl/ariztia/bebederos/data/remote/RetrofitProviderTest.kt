package cl.ariztia.bebederos.data.remote

import cl.ariztia.bebederos.BuildConfig
import cl.ariztia.bebederos.data.remote.api.AuthApi
import cl.ariztia.bebederos.data.remote.api.FlushingApi
import cl.ariztia.bebederos.data.remote.api.PhotoApi
import cl.ariztia.bebederos.data.remote.dto.CrearFlushingRequestDto
import com.google.gson.JsonParser
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class RetrofitProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        client = RetrofitProvider.createOkHttpClient(InMemoryCredentials())
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun buildTypeUsesTheExpectedBaseUrl() {
        val expectedUrl = if (BuildConfig.DEBUG) {
            "http://10.0.2.2:8080/"
        } else {
            "https://backend.invalid/"
        }

        assertEquals(expectedUrl, BuildConfig.BACKEND_BASE_URL)
    }

    @Test
    fun retrofitReadsTheDocumentedUserJson() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 1,
                      "nombre": "Operario Demo",
                      "email": "operario@ariztia.cl",
                      "rol": "OPERARIO",
                      "granjaId": 1,
                      "granjaNombre": "El Sauce"
                    }
                    """.trimIndent()
                )
        )
        val api = retrofit().create(AuthApi::class.java)

        val user = api.currentUser()

        assertEquals(1L, user.id)
        assertEquals("Operario Demo", user.nombre)
        assertEquals("OPERARIO", user.rol)
        assertEquals(1L, user.granjaId)
        assertEquals("El Sauce", user.granjaNombre)
        assertEquals("/api/v1/me", server.takeRequest().path)
    }

    @Test
    fun supervisorJsonAllowsANullFarm() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 2,
                      "nombre": "Supervisor Demo",
                      "email": "supervisor@ariztia.cl",
                      "rol": "SUPERVISOR",
                      "granjaId": null,
                      "granjaNombre": null
                    }
                    """.trimIndent()
                )
        )
        val api = retrofit().create(AuthApi::class.java)

        val user = api.currentUser()

        assertNull(user.granjaId)
        assertNull(user.granjaNombre)
    }

    @Test
    fun createFlushingUsesTheDocumentedPathAndJsonNames() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(FLUSHING_RESPONSE)
        )
        val api = retrofit().create(FlushingApi::class.java)
        val request = CrearFlushingRequestDto(
            clientOperationId = "61a7048d-cc44-4e6a-a38d-536235097da0",
            motivo = "Limpieza preventiva",
            observacion = "Sin novedades",
            temperaturaAntesC = BigDecimal("28.0"),
            temperaturaDespuesC = BigDecimal("21.5")
        )

        val response = api.create(1L, request)
        val body = requireNotNull(response.body())
        val recordedRequest = server.takeRequest()
        val json = JsonParser.parseString(recordedRequest.body.readUtf8()).asJsonObject

        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/lineas/1/flushings", recordedRequest.path)
        assertEquals(
            "61a7048d-cc44-4e6a-a38d-536235097da0",
            json.get("clientOperationId").asString
        )
        assertEquals("Limpieza preventiva", json.get("motivo").asString)
        assertEquals(BigDecimal("28.0"), json.get("temperaturaAntesC").asBigDecimal)
        assertEquals(201, response.code())
        assertEquals(42L, body.id)
        assertEquals(0L, body.version)
    }

    @Test
    fun photoUploadUsesTheDocumentedMultipartPart() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(PHOTO_RESPONSE)
        )
        val api = retrofit().create(PhotoApi::class.java)
        val photoBody = "contenido-jpeg".toRequestBody("image/jpeg".toMediaType())
        val archivo = MultipartBody.Part.createFormData(
            "archivo",
            "evidencia.jpg",
            photoBody
        )

        val response = api.upload(42L, archivo)
        val recordedRequest = server.takeRequest()
        val multipartBody = recordedRequest.body.readUtf8()

        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/flushings/42/foto", recordedRequest.path)
        assertTrue(recordedRequest.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
        assertTrue(multipartBody.contains("name=\"archivo\""))
        assertTrue(multipartBody.contains("filename=\"evidencia.jpg\""))
        assertTrue(multipartBody.contains("Content-Type: image/jpeg"))
        assertEquals(201, response.code())
        assertEquals(42L, response.body()?.flushingId)
    }

    private fun retrofit() = RetrofitProvider.createRetrofit(
        baseUrl = server.url("/").toString(),
        client = client
    )

    private companion object {
        val FLUSHING_RESPONSE =
            """
            {
              "id": 42,
              "lineaId": 1,
              "lineaNombre": "Línea 1",
              "galponId": 1,
              "galponNombre": "Galpón 1",
              "granjaId": 1,
              "granjaNombre": "El Sauce",
              "operarioId": 1,
              "operarioNombre": "Operario Demo",
              "registradoEn": "2026-11-06T20:00:00-03:00",
              "motivo": "Limpieza preventiva",
              "observacion": "Sin novedades",
              "completado": true,
              "temperaturaAntesC": 28.0,
              "temperaturaDespuesC": 21.5,
              "clientOperationId": "61a7048d-cc44-4e6a-a38d-536235097da0",
              "version": 0
            }
            """.trimIndent()

        val PHOTO_RESPONSE =
            """
            {
              "id": 7,
              "flushingId": 42,
              "nombreOriginal": "evidencia.jpg",
              "tipoContenido": "image/jpeg",
              "tamanoBytes": 14,
              "creadaEn": "2026-11-06T20:10:00-03:00"
            }
            """.trimIndent()
    }
}
