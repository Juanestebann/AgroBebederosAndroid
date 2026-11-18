package cl.ariztia.bebederos.data.remote.dto

import java.math.BigDecimal

data class CrearFlushingRequestDto(
    val clientOperationId: String,
    val motivo: String,
    val observacion: String?,
    val temperaturaAntesC: BigDecimal,
    val temperaturaDespuesC: BigDecimal
)

data class ActualizarFlushingRequestDto(
    val motivo: String,
    val observacion: String?,
    val temperaturaAntesC: BigDecimal,
    val temperaturaDespuesC: BigDecimal,
    val version: Long
)

data class FlushingResponseDto(
    val id: Long,
    val lineaId: Long,
    val lineaNombre: String,
    val galponId: Long,
    val galponNombre: String,
    val granjaId: Long,
    val granjaNombre: String,
    val operarioId: Long,
    val operarioNombre: String,
    val registradoEn: String,
    val motivo: String,
    val observacion: String?,
    val completado: Boolean,
    val temperaturaAntesC: BigDecimal,
    val temperaturaDespuesC: BigDecimal,
    val clientOperationId: String,
    val version: Long
)
