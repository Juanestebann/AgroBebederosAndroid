package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.remote.ExternalWeatherProvider
import cl.ariztia.bebederos.data.remote.api.WeatherApi
import cl.ariztia.bebederos.data.remote.dto.OpenMeteoForecastDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AmbientWeatherRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AmbientWeatherRepository

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        val retrofit = ExternalWeatherProvider.createRetrofit(server.url("/").toString())
        repository = AmbientWeatherRepository(retrofit.create(WeatherApi::class.java))
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun currentUsesHttpsApiContractWithoutBackendAuthorization() = runBlocking {
        server.enqueue(successResponse())

        val result = assertIs<AmbientWeatherResult.Success>(repository.current())
        val request = server.takeRequest()

        assertEquals(20.3, result.weather.temperatureC)
        assertEquals("°C", result.weather.unit)
        assertEquals("Talca · ubicación demostrativa", result.weather.locationLabel)
        assertEquals("2026-11-10T22:45", result.weather.observedAt)
        assertTrue(request.path.orEmpty().startsWith("/v1/forecast?"))
        assertTrue(request.path.orEmpty().contains("current=temperature_2m"))
        assertTrue(request.path.orEmpty().contains("timezone=America%2FSantiago"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun networkFailureHasAControlledResult() = runBlocking {
        val result = AmbientWeatherRepository(NetworkFailureWeatherApi).current()

        assertEquals(
            AmbientWeatherError.NETWORK,
            assertIs<AmbientWeatherResult.Failure>(result).error
        )
    }

    @Test
    fun invalidJsonDataHasAControlledResult() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"latitude":-35.4,"longitude":-71.6,"timezone":"America/Santiago"}""")
        )

        assertEquals(
            AmbientWeatherError.INVALID_RESPONSE,
            assertIs<AmbientWeatherResult.Failure>(repository.current()).error
        )
    }

    @Test
    fun httpFailureIsDifferentFromAConnectivityFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(
            AmbientWeatherError.UNAVAILABLE,
            assertIs<AmbientWeatherResult.Failure>(repository.current()).error
        )
    }

    @Test
    fun coroutineCancellationIsNotConvertedIntoAVisibleFailure() {
        assertFailsWith<CancellationException> {
            runBlocking {
                AmbientWeatherRepository(CancelledWeatherApi).current()
            }
        }
    }

    @Test
    fun demoCoordinatesAreExplicitAndNotPresentedAsAFarm() {
        val location = AmbientWeatherRepository.TALCA_DEMO_LOCATION

        assertTrue(location.label.contains("demostrativa"))
        assertEquals(-35.4232, location.latitude)
        assertEquals(-71.64974, location.longitude)
    }

    private fun successResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "latitude": -35.39543,
              "longitude": -71.67734,
              "timezone": "America/Santiago",
              "current_units": {"temperature_2m": "°C"},
              "current": {
                "time": "2026-11-10T22:45",
                "temperature_2m": 20.3
              }
            }
            """.trimIndent()
        )

    private object NetworkFailureWeatherApi : WeatherApi {
        override suspend fun currentWeather(
            latitude: Double,
            longitude: Double,
            currentVariables: String,
            timezone: String
        ): OpenMeteoForecastDto = throw IOException("Sin red")
    }

    private object CancelledWeatherApi : WeatherApi {
        override suspend fun currentWeather(
            latitude: Double,
            longitude: Double,
            currentVariables: String,
            timezone: String
        ): OpenMeteoForecastDto = throw CancellationException("Trabajo cancelado")
    }
}
