package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.AmbientWeather
import cl.ariztia.bebederos.data.repository.AmbientWeatherError

sealed interface AmbientWeatherUiState {
    data object Loading : AmbientWeatherUiState
    data class Ready(val weather: AmbientWeather) : AmbientWeatherUiState
    data class Error(val error: AmbientWeatherError) : AmbientWeatherUiState
}

fun ambientWeatherErrorMessage(error: AmbientWeatherError): String = when (error) {
    AmbientWeatherError.NETWORK ->
        "No fue posible conectar con Open-Meteo."
    AmbientWeatherError.INVALID_RESPONSE ->
        "Open-Meteo devolvió datos ambientales inválidos."
    AmbientWeatherError.UNAVAILABLE ->
        "Open-Meteo no está disponible en este momento."
}
