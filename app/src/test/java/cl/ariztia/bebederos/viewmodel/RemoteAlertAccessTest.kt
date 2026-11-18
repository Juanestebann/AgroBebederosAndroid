package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.RemoteCatalog
import cl.ariztia.bebederos.data.model.RemoteFarm
import cl.ariztia.bebederos.data.model.RemoteShed
import cl.ariztia.bebederos.data.model.RemoteWaterLine
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.data.model.UserRole
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RemoteAlertAccessTest {

    @Test
    fun operatorCanOpenOnlyAnAlertFromTheAssignedFarm() {
        assertTrue(canAccessRemoteAlert(operator(10L), alert(farmId = 10L), catalog()))
        assertFalse(canAccessRemoteAlert(operator(20L), alert(farmId = 10L), catalog()))
    }

    @Test
    fun supervisorCanOpenAValidAlertFromAnyCatalogFarm() {
        assertTrue(canAccessRemoteAlert(supervisor(), alert(farmId = 10L), catalog()))
    }

    @Test
    fun manipulatedOrInconsistentHierarchyIsRejected() {
        assertFalse(canAccessRemoteAlert(operator(10L), alert(id = -1L), catalog()))
        assertFalse(canAccessRemoteAlert(operator(10L), alert(shedId = 999L), catalog()))
        assertFalse(canAccessRemoteAlert(operator(10L), alert(lineId = 999L), catalog()))
    }

    private fun operator(farmId: Long) = SessionUser(
        id = 1L,
        email = "operario@example.cl",
        name = "Operario",
        role = UserRole.OPERARIO,
        farmId = farmId,
        farm = "Granja $farmId"
    )

    private fun supervisor() = SessionUser(
        id = 2L,
        email = "supervisor@example.cl",
        name = "Supervisor",
        role = UserRole.SUPERVISOR,
        farmId = null,
        farm = null
    )

    private fun catalog() = RemoteCatalog(
        farms = listOf(RemoteFarm(10L, "Granja 10")),
        sheds = listOf(RemoteShed(20L, 10L, "Galpón 20", "Granja 10")),
        lines = listOf(
            RemoteWaterLine(
                id = 30L,
                farmId = 10L,
                farmName = "Granja 10",
                shedId = 20L,
                shedName = "Galpón 20",
                name = "Línea 30",
                temperatureC = BigDecimal("28.0"),
                status = TemperatureStatus.CRITICAL,
                temperatureOrigin = "LECTURA",
                registeredAt = OffsetDateTime.parse("2026-11-10T10:00:00-03:00")
            )
        )
    )

    private fun alert(
        id: Long = 40L,
        farmId: Long = 10L,
        shedId: Long = 20L,
        lineId: Long = 30L
    ) = RemoteAlert(
        id = id,
        lineId = lineId,
        lineName = "Línea 30",
        shedId = shedId,
        shedName = "Galpón 20",
        farmId = farmId,
        farmName = "Granja 10",
        temperatureC = BigDecimal("28.0"),
        status = TemperatureStatus.CRITICAL,
        generatedAt = OffsetDateTime.parse("2026-11-10T10:00:00-03:00")
    )
}
