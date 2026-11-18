package cl.ariztia.bebederos.ui.screens.shared

import cl.ariztia.bebederos.data.model.DemoUser
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import org.junit.Test
import kotlin.test.assertEquals

class ProfileAccessInfoTest {
    @Test
    fun operatorProfileShowsTheAuthenticatedUsersFarm() {
        val operator = DemoUser(
            email = "operator@example.test",
            password = "secret",
            name = "Operario de prueba",
            role = UserRole.OPERARIO,
            farm = "Granja asignada de prueba"
        )

        assertEquals(
            ProfileAccessInfo("Granja asignada", operator.farm),
            profileAccessInfo(operator)
        )
    }

    @Test
    fun supervisorProfileShowsAccessToAllFarms() {
        val supervisor = DemoUser(
            email = "supervisor@example.test",
            password = "secret",
            name = "Supervisor de prueba",
            role = UserRole.SUPERVISOR,
            farm = "Valor que no debe mostrarse"
        )

        assertEquals(
            ProfileAccessInfo("Acceso a granjas", "Todas las granjas"),
            profileAccessInfo(supervisor)
        )
    }

    @Test
    fun remoteOperatorProfileUsesTheFarmReturnedByTheBackend() {
        val operator = SessionUser(
            id = 40L,
            email = "operator@example.test",
            name = "Operario remoto",
            role = UserRole.OPERARIO,
            farmId = 900L,
            farm = "Granja remota"
        )

        assertEquals(
            ProfileAccessInfo("Granja asignada", "Granja remota"),
            profileAccessInfo(operator)
        )
    }

    @Test
    fun remoteSupervisorProfileKeepsGlobalAccess() {
        val supervisor = SessionUser(
            id = 41L,
            email = "supervisor@example.test",
            name = "Supervisor remoto",
            role = UserRole.SUPERVISOR,
            farmId = null,
            farm = null
        )

        assertEquals(
            ProfileAccessInfo("Acceso a granjas", "Todas las granjas"),
            profileAccessInfo(supervisor)
        )
    }
}
