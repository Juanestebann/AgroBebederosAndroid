package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.remote.BasicAuthorization
import cl.ariztia.bebederos.data.remote.InMemoryCredentials
import cl.ariztia.bebederos.data.remote.RetrofitProvider
import cl.ariztia.bebederos.data.remote.api.AuthApi
import cl.ariztia.bebederos.data.remote.dto.UsuarioResponseDto
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var credentials: InMemoryCredentials
    private lateinit var repository: AuthRepository

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        credentials = InMemoryCredentials()
        val client = RetrofitProvider.createOkHttpClient(credentials)
        val retrofit = RetrofitProvider.createRetrofit(server.url("/").toString(), client)
        repository = AuthRepository(retrofit.create(AuthApi::class.java), credentials)
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun correctOperatorCredentialsCreateAnAuthenticatedSession() = runBlocking {
        server.enqueue(successfulResponse(OPERATOR_JSON))

        val result = repository.login("operario@example.cl", "clave-prueba")
        val success = assertIs<AuthResult.Success>(result)
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/login", request.path)
        assertEquals(
            BasicAuthorization.create("operario@example.cl", "clave-prueba"),
            request.getHeader("Authorization")
        )
        assertEquals(1L, success.user.id)
        assertEquals(UserRole.OPERARIO, success.user.role)
        assertEquals(10L, success.user.farmId)
        assertEquals("Granja Remota", success.user.farm)
        assertTrue(credentials.hasCredentials())
    }

    @Test
    fun supervisorResponseKeepsGlobalScopeWithoutAFarm() = runBlocking {
        server.enqueue(successfulResponse(SUPERVISOR_JSON))

        val success = assertIs<AuthResult.Success>(
            repository.login("supervisor@example.cl", "clave-prueba")
        )

        assertEquals(UserRole.SUPERVISOR, success.user.role)
        assertNull(success.user.farmId)
        assertNull(success.user.farm)
        assertTrue(credentials.hasCredentials())
    }

    @Test
    fun incorrectCredentialsReturnAControlledResultAndAreNotStored() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.login("persona@example.cl", "incorrecta")

        assertIs<AuthResult.InvalidCredentials>(result)
        assertFalse(credentials.hasCredentials())
    }

    @Test
    fun a401ClearsCredentialsFromAPreviousSession() = runBlocking {
        credentials.save("anterior@example.cl", "clave-anterior")
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.login("persona@example.cl", "incorrecta")

        assertIs<AuthResult.InvalidCredentials>(result)
        assertFalse(credentials.hasCredentials())
    }

    @Test
    fun networkFailureDoesNotEscapeAsAnException() = runBlocking {
        val networkRepository = AuthRepository(
            authApi = FailingAuthApi(IOException("Sin conexión")),
            credentials = credentials
        )

        val result = networkRepository.login("persona@example.cl", "clave-prueba")

        assertIs<AuthResult.NetworkError>(result)
        assertFalse(credentials.hasCredentials())
    }

    @Test
    fun unexpectedHttpResponseDoesNotCreateASession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.login("persona@example.cl", "clave-prueba")

        assertIs<AuthResult.UnexpectedResponse>(result)
        assertFalse(credentials.hasCredentials())
    }

    @Test
    fun unexpectedRoleDoesNotCreateASession() = runBlocking {
        server.enqueue(successfulResponse(OPERATOR_JSON.replace("OPERARIO", "ADMIN")))

        val result = repository.login("persona@example.cl", "clave-prueba")

        assertIs<AuthResult.UnexpectedResponse>(result)
        assertFalse(credentials.hasCredentials())
    }

    @Test
    fun logoutClearsCredentialsAfterASuccessfulLogin() = runBlocking {
        server.enqueue(successfulResponse(OPERATOR_JSON))
        assertIs<AuthResult.Success>(
            repository.login("operario@example.cl", "clave-prueba")
        )

        repository.logout()

        assertFalse(credentials.hasCredentials())
    }

    private fun successfulResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private class FailingAuthApi(
        private val error: IOException
    ) : AuthApi {
        override suspend fun login(authorization: String): UsuarioResponseDto = throw error

        override suspend fun currentUser(): UsuarioResponseDto = throw error
    }

    private companion object {
        val OPERATOR_JSON =
            """
            {
              "id": 1,
              "nombre": "Operario Remoto",
              "email": "operario@example.cl",
              "rol": "OPERARIO",
              "granjaId": 10,
              "granjaNombre": "Granja Remota"
            }
            """.trimIndent()

        val SUPERVISOR_JSON =
            """
            {
              "id": 2,
              "nombre": "Supervisor Remoto",
              "email": "supervisor@example.cl",
              "rol": "SUPERVISOR",
              "granjaId": null,
              "granjaNombre": null
            }
            """.trimIndent()
    }
}
