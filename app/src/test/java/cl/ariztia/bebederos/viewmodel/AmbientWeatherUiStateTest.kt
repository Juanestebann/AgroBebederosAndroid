package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.AmbientWeather
import cl.ariztia.bebederos.data.repository.AmbientWeatherError
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AmbientWeatherUiStateTest {
    @Test
    fun everyExternalApiFailureHasAnEducationalMessage() {
        AmbientWeatherError.entries.forEach { error ->
            assertEquals(false, ambientWeatherErrorMessage(error).isBlank())
        }
    }

    @Test
    fun readyAndErrorKeepTheDataNeededByCompose() {
        val weather = AmbientWeather(
            locationLabel = "Talca · ubicación demostrativa",
            temperatureC = 20.3,
            observedAt = "2026-11-10T22:45",
            unit = "°C",
            latitude = -35.39543,
            longitude = -71.67734
        )

        assertEquals(
            weather,
            assertIs<AmbientWeatherUiState.Ready>(
                AmbientWeatherUiState.Ready(weather)
            ).weather
        )
        assertEquals(
            AmbientWeatherError.NETWORK,
            assertIs<AmbientWeatherUiState.Error>(
                AmbientWeatherUiState.Error(AmbientWeatherError.NETWORK)
            ).error
        )
    }
}
