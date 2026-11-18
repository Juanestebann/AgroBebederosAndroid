package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.remote.dto.OpenMeteoCurrentDto
import cl.ariztia.bebederos.data.remote.dto.OpenMeteoCurrentUnitsDto
import cl.ariztia.bebederos.data.remote.dto.OpenMeteoForecastDto
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class WeatherMapperTest {
    @Test
    fun mapsAValidEnvironmentalReading() {
        val weather = validDto().toAmbientWeather("Talca · ubicación demostrativa")

        assertEquals(20.3, weather.temperatureC)
        assertEquals("°C", weather.unit)
        assertEquals("2026-11-10T22:45", weather.observedAt)
        assertEquals(-35.39543, weather.latitude)
        assertEquals(-71.67734, weather.longitude)
    }

    @Test
    fun rejectsMissingOrNonFiniteTemperature() {
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(
                current = OpenMeteoCurrentDto("2026-11-10T22:45", null)
            ).toAmbientWeather("Demo")
        }
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(
                current = OpenMeteoCurrentDto("2026-11-10T22:45", Double.NaN)
            ).toAmbientWeather("Demo")
        }
    }

    @Test
    fun rejectsMissingCurrentBlockDateOrCelsiusUnit() {
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(current = null).toAmbientWeather("Demo")
        }
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(
                current = OpenMeteoCurrentDto(" ", 20.3)
            ).toAmbientWeather("Demo")
        }
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(
                currentUnits = OpenMeteoCurrentUnitsDto("°F")
            ).toAmbientWeather("Demo")
        }
    }

    @Test
    fun rejectsMissingCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(latitude = null).toAmbientWeather("Demo")
        }
        assertFailsWith<IllegalArgumentException> {
            validDto().copy(longitude = null).toAmbientWeather("Demo")
        }
    }

    private fun validDto() = OpenMeteoForecastDto(
        latitude = -35.39543,
        longitude = -71.67734,
        timezone = "America/Santiago",
        currentUnits = OpenMeteoCurrentUnitsDto("°C"),
        current = OpenMeteoCurrentDto("2026-11-10T22:45", 20.3)
    )
}
