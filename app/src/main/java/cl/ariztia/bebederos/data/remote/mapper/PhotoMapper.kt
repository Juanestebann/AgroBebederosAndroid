package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.RemotePhoto
import cl.ariztia.bebederos.data.remote.dto.EvidenciaResponseDto
import java.time.OffsetDateTime

/** Conserva IDs, tamaño, MIME y fecha entregados por Spring Boot. */
fun EvidenciaResponseDto.toRemotePhoto(): RemotePhoto = RemotePhoto(
    id = id,
    flushingId = flushingId,
    originalName = nombreOriginal,
    contentType = tipoContenido,
    sizeBytes = tamanoBytes,
    createdAt = OffsetDateTime.parse(creadaEn)
)
