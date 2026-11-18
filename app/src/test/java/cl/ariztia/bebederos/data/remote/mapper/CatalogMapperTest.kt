package cl.ariztia.bebederos.data.remote.mapper

import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.remote.dto.GalponResponseDto
import cl.ariztia.bebederos.data.remote.dto.GranjaResponseDto
import cl.ariztia.bebederos.data.remote.dto.LineaBebederoResponseDto
import cl.ariztia.bebederos.data.remote.dto.TemperaturaEfectivaResponseDto
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class CatalogMapperTest {

    @Test
    fun farmAndShedIdsRemainLong() {
        val largeId = Int.MAX_VALUE.toLong() + 100L

        val farm = GranjaResponseDto(largeId, "Granja Long").toRemoteFarm()
        val shed = GalponResponseDto(
            id = largeId + 1L,
            nombre = "Galpón Long",
            granjaId = largeId,
            granjaNombre = "Granja Long"
        ).toRemoteShed()

        assertEquals(largeId, farm.id)
        assertEquals(largeId + 1L, shed.id)
        assertEquals(largeId, shed.farmId)
        assertTrue(farm.id > Int.MAX_VALUE)
    }

    @Test
    fun lineKeepsLongIdsDecimalDateAndRemoteThermalState() {
        val line = lineDto(
            estado = "CRITICAL",
            temperatura = BigDecimal("20.12")
        ).toRemoteWaterLine()

        assertTrue(line.id > Int.MAX_VALUE)
        assertTrue(line.farmId > Int.MAX_VALUE)
        assertTrue(line.shedId > Int.MAX_VALUE)
        assertEquals(BigDecimal("20.12"), line.temperatureC)
        assertEquals(TemperatureStatus.CRITICAL, line.status)
        assertEquals("LECTURA", line.temperatureOrigin)
        assertEquals(
            OffsetDateTime.parse("2026-11-07T20:05:00-03:00"),
            line.registeredAt
        )
    }

    @Test
    fun everyDocumentedThermalStateIsMapped() {
        assertEquals(TemperatureStatus.NORMAL, lineDto("NORMAL").toRemoteWaterLine().status)
        assertEquals(TemperatureStatus.WARNING, lineDto("WARNING").toRemoteWaterLine().status)
        assertEquals(TemperatureStatus.CRITICAL, lineDto("CRITICAL").toRemoteWaterLine().status)
    }

    @Test
    fun unknownThermalStateIsRejectedInsteadOfRecalculated() {
        assertFailsWith<IllegalArgumentException> {
            lineDto(estado = "DESCONOCIDO").toRemoteWaterLine()
        }
    }

    @Test
    fun invalidRemoteDateIsRejected() {
        val dto = lineDto().copy(
            temperaturaEfectiva = lineDto().temperaturaEfectiva.copy(
                registradaEn = "fecha-invalida"
            )
        )

        assertFailsWith<Exception> { dto.toRemoteWaterLine() }
    }

    private fun lineDto(
        estado: String = "NORMAL",
        temperatura: BigDecimal = BigDecimal("22.40")
    ) = LineaBebederoResponseDto(
        id = 4_000_000_003L,
        nombre = "Línea remota",
        galponId = 4_000_000_002L,
        galponNombre = "Galpón remoto",
        granjaId = 4_000_000_001L,
        granjaNombre = "Granja remota",
        temperaturaEfectiva = TemperaturaEfectivaResponseDto(
            valorCelsius = temperatura,
            estado = estado,
            origen = "LECTURA",
            registradaEn = "2026-11-07T20:05:00-03:00"
        )
    )
}
