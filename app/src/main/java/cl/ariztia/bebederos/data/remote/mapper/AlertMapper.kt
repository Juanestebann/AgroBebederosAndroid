package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.remote.dto.AlertaResponseDto
import java.time.OffsetDateTime

/** Convierte una alerta del backend sin recalcular su estado térmico. */
fun AlertaResponseDto.toRemoteAlert(): RemoteAlert {
    val remoteStatus = when (estado) {
        "WARNING" -> TemperatureStatus.WARNING
        "CRITICAL" -> TemperatureStatus.CRITICAL
        else -> throw IllegalArgumentException("Estado de alerta remoto no reconocido.")
    }

    return RemoteAlert(
        id = id,
        lineId = lineaId,
        lineName = lineaNombre,
        shedId = galponId,
        shedName = galponNombre,
        farmId = granjaId,
        farmName = granjaNombre,
        temperatureC = temperaturaC,
        status = remoteStatus,
        generatedAt = OffsetDateTime.parse(generadaEn)
    )
}
