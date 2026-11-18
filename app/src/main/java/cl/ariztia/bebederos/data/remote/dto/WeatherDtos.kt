package cl.ariztia.bebederos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OpenMeteoForecastDto(
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    @SerializedName("current_units") val currentUnits: OpenMeteoCurrentUnitsDto?,
    val current: OpenMeteoCurrentDto?
)

data class OpenMeteoCurrentUnitsDto(
    @SerializedName("temperature_2m") val temperatureUnit: String?
)

data class OpenMeteoCurrentDto(
    val time: String?,
    @SerializedName("temperature_2m") val temperatureC: Double?
)
