package cl.ariztia.bebederos.data.remote.dto

import java.math.BigDecimal

data class GranjaResponseDto(
    val id: Long,
    val nombre: String
)

data class GalponResponseDto(
    val id: Long,
    val nombre: String,
    val granjaId: Long,
    val granjaNombre: String
)

data class TemperaturaEfectivaResponseDto(
    val valorCelsius: BigDecimal,
    val estado: String,
    val origen: String,
    val registradaEn: String
)

data class LineaBebederoResponseDto(
    val id: Long,
    val nombre: String,
    val galponId: Long,
    val galponNombre: String,
    val granjaId: Long,
    val granjaNombre: String,
    val temperaturaEfectiva: TemperaturaEfectivaResponseDto
)
