package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.RemoteFarm
import cl.ariztia.bebederos.data.model.RemoteShed
import cl.ariztia.bebederos.data.model.RemoteWaterLine
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.remote.dto.GalponResponseDto
import cl.ariztia.bebederos.data.remote.dto.GranjaResponseDto
import cl.ariztia.bebederos.data.remote.dto.LineaBebederoResponseDto
import java.time.OffsetDateTime

fun GranjaResponseDto.toRemoteFarm(): RemoteFarm = RemoteFarm(
    id = id,
    name = nombre
)

fun GalponResponseDto.toRemoteShed(): RemoteShed = RemoteShed(
    id = id,
    farmId = granjaId,
    name = nombre,
    farmName = granjaNombre
)

/** No recalcula el estado: utiliza literalmente la clasificación entregada por Spring Boot. */
fun LineaBebederoResponseDto.toRemoteWaterLine(): RemoteWaterLine {
    val remoteStatus = when (temperaturaEfectiva.estado) {
        "NORMAL" -> TemperatureStatus.NORMAL
        "WARNING" -> TemperatureStatus.WARNING
        "CRITICAL" -> TemperatureStatus.CRITICAL
        else -> throw IllegalArgumentException("Estado térmico remoto no reconocido.")
    }

    return RemoteWaterLine(
        id = id,
        farmId = granjaId,
        farmName = granjaNombre,
        shedId = galponId,
        shedName = galponNombre,
        name = nombre,
        temperatureC = temperaturaEfectiva.valorCelsius,
        status = remoteStatus,
        temperatureOrigin = temperaturaEfectiva.origen,
        registeredAt = OffsetDateTime.parse(temperaturaEfectiva.registradaEn)
    )
}
