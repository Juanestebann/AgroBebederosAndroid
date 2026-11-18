package cl.ariztia.bebederos.data.remote.dto

import java.math.BigDecimal

data class AlertaResponseDto(
    val id: Long,
    val lineaId: Long,
    val lineaNombre: String,
    val galponId: Long,
    val galponNombre: String,
    val granjaId: Long,
    val granjaNombre: String,
    val temperaturaC: BigDecimal,
    val estado: String,
    val generadaEn: String
)
