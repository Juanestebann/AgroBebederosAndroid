package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.AmbientWeather
import cl.ariztia.bebederos.data.remote.dto.OpenMeteoForecastDto

fun OpenMeteoForecastDto.toAmbientWeather(locationLabel: String): AmbientWeather {
    val currentWeather = requireNotNull(current) {
        "Open-Meteo no devolvió condiciones actuales."
    }
    val temperature = requireNotNull(currentWeather.temperatureC) {
        "Open-Meteo no devolvió temperatura ambiental."
    }
    require(temperature.isFinite()) {
        "Open-Meteo devolvió una temperatura inválida."
    }

    val observedAt = requireNotNull(currentWeather.time).trim()
    val unit = requireNotNull(currentUnits?.temperatureUnit).trim()
    require(observedAt.isNotEmpty() && unit == "°C") {
        "Open-Meteo devolvió unidades o fecha inválidas."
    }

    return AmbientWeather(
        locationLabel = locationLabel,
        temperatureC = temperature,
        observedAt = observedAt,
        unit = unit,
        latitude = requireNotNull(latitude),
        longitude = requireNotNull(longitude)
    )
}
