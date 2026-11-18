package cl.ariztia.bebederos.data.remote.api

import cl.ariztia.bebederos.data.remote.dto.OpenMeteoForecastDto
import retrofit2.http.GET
import retrofit2.http.Query

/** API pública externa. No utiliza credenciales ni endpoints de AgroBebederos. */
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun currentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") currentVariables: String = "temperature_2m",
        @Query("timezone") timezone: String = "America/Santiago"
    ): OpenMeteoForecastDto
}
