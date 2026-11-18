package cl.ariztia.bebederos.data.remote.dto

import java.math.BigDecimal

data class LecturaTemperaturaRequestDto(
    val valorCelsius: BigDecimal
)

data class LecturaTemperaturaResponseDto(
    val id: Long,
    val lineaId: Long,
    val valorCelsius: BigDecimal,
    val estado: String,
    val registradaEn: String
)
