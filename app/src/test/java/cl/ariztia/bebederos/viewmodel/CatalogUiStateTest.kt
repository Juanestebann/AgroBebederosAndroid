package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemoteCatalog
import cl.ariztia.bebederos.data.model.RemoteFarm
import cl.ariztia.bebederos.data.model.RemoteShed
import cl.ariztia.bebederos.data.model.RemoteWaterLine
import cl.ariztia.bebederos.data.model.TemperatureStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class CatalogUiStateTest {

    @Test
    fun responseFromPreviousSessionIsRejectedAfterGenerationChanges() {
        val requestGeneration = 7L

        assertTrue(isCurrentSessionResult(requestGeneration, currentGeneration = 7L))
        assertFalse(isCurrentSessionResult(requestGeneration, currentGeneration = 8L))
    }

    @Test
    fun catalogCanOnlyBeReadFromContentState() {
        val catalog = RemoteCatalog(emptyList(), emptyList(), emptyList())

        assertSame(catalog, CatalogUiState.Content(catalog).catalogOrNull)
        assertNull(CatalogUiState.Loading.catalogOrNull)
        assertNull(CatalogUiState.Empty.catalogOrNull)
        assertNull(CatalogUiState.Idle.catalogOrNull)
    }

    @Test
    fun refreshedLineReplacesEffectiveTemperatureWithoutUsingRoom() {
        val original = remoteLine(BigDecimal("28.00"), TemperatureStatus.CRITICAL)
        val catalog = RemoteCatalog(
            farms = listOf(RemoteFarm(1L, "Granja")),
            sheds = listOf(RemoteShed(2L, 1L, "Galpón", "Granja")),
            lines = listOf(original)
        )
        val refreshed = original.copy(
            temperatureC = BigDecimal("21.50"),
            status = TemperatureStatus.NORMAL,
            temperatureOrigin = "FLUSHING"
        )

        val updatedCatalog = catalog.withUpdatedLine(refreshed)

        assertEquals(BigDecimal("21.50"), updatedCatalog?.line(3L)?.temperatureC)
        assertEquals(TemperatureStatus.NORMAL, updatedCatalog?.line(3L)?.status)
        assertEquals("FLUSHING", updatedCatalog?.line(3L)?.temperatureOrigin)
    }

    private fun remoteLine(temperature: BigDecimal, status: TemperatureStatus) = RemoteWaterLine(
        id = 3L,
        farmId = 1L,
        farmName = "Granja",
        shedId = 2L,
        shedName = "Galpón",
        name = "Línea",
        temperatureC = temperature,
        status = status,
        temperatureOrigin = "LECTURA",
        registeredAt = OffsetDateTime.parse("2026-11-07T20:05:00-03:00")
    )
}
