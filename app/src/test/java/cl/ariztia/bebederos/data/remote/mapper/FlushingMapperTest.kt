package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.remote.dto.FlushingResponseDto
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class FlushingMapperTest {

    @Test
    fun responseKeepsLongIdsDecimalsOfficialTimestampAuthorUuidAndVersion() {
        val dto = responseDto()

        val record = dto.toRemoteFlushing()

        assertTrue(record.id > Int.MAX_VALUE)
        assertTrue(record.lineId > Int.MAX_VALUE)
        assertEquals(4_000_000_004L, record.operatorId)
        assertEquals(BigDecimal("28.90"), record.beforeTemperatureC)
        assertEquals(BigDecimal("21.50"), record.afterTemperatureC)
        assertEquals(OffsetDateTime.parse("2026-11-07T20:05:00-03:00"), record.registeredAt)
        assertEquals(UUID.fromString(OPERATION_ID), record.clientOperationId)
        assertEquals(7L, record.version)
        assertNull(record.observation)
    }

    @Test
    fun invalidServerTimestampOrUuidIsRejected() {
        assertFailsWith<Exception> {
            responseDto().copy(registradoEn = "fecha-invalida").toRemoteFlushing()
        }
        assertFailsWith<IllegalArgumentException> {
            responseDto().copy(clientOperationId = "no-es-uuid").toRemoteFlushing()
        }
    }

    private fun responseDto() = FlushingResponseDto(
        id = 4_000_000_005L,
        lineaId = 4_000_000_003L,
        lineaNombre = "Línea remota",
        galponId = 4_000_000_002L,
        galponNombre = "Galpón remoto",
        granjaId = 4_000_000_001L,
        granjaNombre = "Granja remota",
        operarioId = 4_000_000_004L,
        operarioNombre = "Operario remoto",
        registradoEn = "2026-11-07T20:05:00-03:00",
        motivo = "Limpieza",
        observacion = null,
        completado = true,
        temperaturaAntesC = BigDecimal("28.90"),
        temperaturaDespuesC = BigDecimal("21.50"),
        clientOperationId = OPERATION_ID,
        version = 7L
    )

    private companion object {
        const val OPERATION_ID = "61a7048d-cc44-4e6a-a38d-536235097da0"
    }
}
