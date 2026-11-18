package cl.ariztia.bebederos.data.remote

import cl.ariztia.bebederos.data.remote.api.WeatherApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Cliente Retrofit aislado de Spring Boot y sin el interceptor HTTP Basic. */
object ExternalWeatherProvider {
    const val BASE_URL = "https://api.open-meteo.com/"

    val api: WeatherApi by lazy {
        createRetrofit(BASE_URL).create(WeatherApi::class.java)
    }

    fun createRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
