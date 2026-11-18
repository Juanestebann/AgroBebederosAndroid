package cl.ariztia.bebederos.data.repository

import cl.ariztia.bebederos.data.model.AmbientWeather
import cl.ariztia.bebederos.data.remote.api.WeatherApi
import cl.ariztia.bebederos.data.remote.mapper.toAmbientWeather
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

data class DemoWeatherLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

enum class AmbientWeatherError {
    NETWORK,
    INVALID_RESPONSE,
    UNAVAILABLE
}

sealed interface AmbientWeatherResult {
    data class Success(val weather: AmbientWeather) : AmbientWeatherResult
    data class Failure(val error: AmbientWeatherError) : AmbientWeatherResult
}

/** Consulta Open-Meteo para una ubicación urbana demostrativa, no para una granja real. */
class AmbientWeatherRepository(
    private val api: WeatherApi,
    private val location: DemoWeatherLocation = TALCA_DEMO_LOCATION
) {
    suspend fun current(): AmbientWeatherResult = try {
        val response = api.currentWeather(location.latitude, location.longitude)
        AmbientWeatherResult.Success(response.toAmbientWeather(location.label))
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        AmbientWeatherResult.Failure(AmbientWeatherError.NETWORK)
    } catch (_: HttpException) {
        AmbientWeatherResult.Failure(AmbientWeatherError.UNAVAILABLE)
    } catch (_: Exception) {
        AmbientWeatherResult.Failure(AmbientWeatherError.INVALID_RESPONSE)
    }

    companion object {
        val TALCA_DEMO_LOCATION = DemoWeatherLocation(
            label = "Talca · ubicación demostrativa",
            latitude = -35.4232,
            longitude = -71.64974
        )
    }
}
