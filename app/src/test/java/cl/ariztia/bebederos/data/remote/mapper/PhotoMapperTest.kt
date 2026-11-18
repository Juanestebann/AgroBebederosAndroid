package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.remote.dto.EvidenciaResponseDto
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import org.junit.Test

class PhotoMapperTest {
    @Test
    fun dtoKeepsLongIdsMimeSizeAndOffsetDate() {
        val dto = EvidenciaResponseDto(
            id = 4_000_000_001L,
            flushingId = 4_000_000_002L,
            nombreOriginal = "evidencia.png",
            tipoContenido = "image/png",
            tamanoBytes = 5_242_880L,
            creadaEn = "2026-11-07T20:05:00-03:00"
        )

        val photo = dto.toRemotePhoto()

        assertEquals(4_000_000_001L, photo.id)
        assertEquals(4_000_000_002L, photo.flushingId)
        assertEquals("image/png", photo.contentType)
        assertEquals(5_242_880L, photo.sizeBytes)
        assertEquals(OffsetDateTime.parse(dto.creadaEn), photo.createdAt)
    }
}
