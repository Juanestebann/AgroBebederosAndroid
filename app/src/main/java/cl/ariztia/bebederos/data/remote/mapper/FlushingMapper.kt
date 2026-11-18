package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.RemoteFlushing
import cl.ariztia.bebederos.data.remote.dto.FlushingResponseDto
import java.time.OffsetDateTime
import java.util.UUID

/** Convierte la respuesta canónica sin sustituir IDs, autor, hora, UUID ni versión. */
fun FlushingResponseDto.toRemoteFlushing(): RemoteFlushing = RemoteFlushing(
    id = id,
    lineId = lineaId,
    lineName = lineaNombre,
    shedId = galponId,
    shedName = galponNombre,
    farmId = granjaId,
    farmName = granjaNombre,
    operatorId = operarioId,
    operatorName = operarioNombre,
    registeredAt = OffsetDateTime.parse(registradoEn),
    reason = motivo,
    observation = observacion,
    completed = completado,
    beforeTemperatureC = temperaturaAntesC,
    afterTemperatureC = temperaturaDespuesC,
    clientOperationId = UUID.fromString(clientOperationId),
    version = version
)
