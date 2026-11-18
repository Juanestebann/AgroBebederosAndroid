package cl.ariztia.bebederos.data.model

/** Temperatura ambiental externa; nunca reemplaza la temperatura del agua. */
data class AmbientWeather(
    val locationLabel: String,
    val temperatureC: Double,
    val observedAt: String,
    val unit: String,
    val latitude: Double,
    val longitude: Double
)
