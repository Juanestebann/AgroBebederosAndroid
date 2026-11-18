package cl.ariztia.bebederos.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class BasicAuthInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var credentials: InMemoryCredentials
    private lateinit var client: OkHttpClient

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        credentials = InMemoryCredentials()
        client = RetrofitProvider.createOkHttpClient(credentials)
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun aRequestWithoutStoredCredentialsHasNoAuthorizationHeader() {
        executeRequest()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun storedCredentialsAreAddedAsHttpBasic() {
        credentials.save("operario@example.cl", "clave-prueba")

        executeRequest()

        assertEquals(
            BasicAuthorization.create("operario@example.cl", "clave-prueba"),
            server.takeRequest().getHeader("Authorization")
        )
    }

    @Test
    fun clearingCredentialsStopsSendingAuthorization() {
        credentials.save("operario@example.cl", "clave-prueba")
        credentials.clear()

        executeRequest()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun anExplicitLoginHeaderIsNotReplacedByStoredCredentials() {
        credentials.save("guardado@example.cl", "clave-guardada")
        val explicitAuthorization = BasicAuthorization.create(
            "login@example.cl",
            "clave-login"
        )

        executeRequest(explicitAuthorization)

        assertEquals(
            explicitAuthorization,
            server.takeRequest().getHeader("Authorization")
        )
    }

    private fun executeRequest(authorization: String? = null) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = Request.Builder()
            .url(server.url("/api/v1/me"))
            .apply {
                authorization?.let { header("Authorization", it) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }
    }
}
