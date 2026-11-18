package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.FlushingRecord
import cl.ariztia.bebederos.data.model.RemoteFlushing
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class RemoteFlushingUiStateTest {

    @Test
    fun operationIdRemainsStableForRetriesAndChangesForANewForm() {
        val first = UUID.fromString("61a7048d-cc44-4e6a-a38d-536235097da0")
        val second = UUID.fromString("99a7048d-cc44-4e6a-a38d-536235097da0")
        val values = ArrayDeque(listOf(first, second))
        val holder = FlushingOperationId { values.removeFirst() }

        assertEquals(first, holder.prepareNew())
        assertEquals(first, holder.currentOrPrepare())
        assertEquals(first, holder.currentOrPrepare())
        assertEquals(second, holder.prepareNew())
        assertEquals(second, holder.currentOrPrepare())
    }

    @Test
    fun onlyTheOperatorAuthorInsideAssignedFarmCanManageRecord() {
        val record = remoteRecord()
        val owner = session(UserRole.OPERARIO, id = record.operatorId, farmId = record.farmId)
        val otherOperator = session(UserRole.OPERARIO, id = record.operatorId + 1L, farmId = record.farmId)
        val supervisor = session(UserRole.SUPERVISOR, id = 99L, farmId = null)

        assertTrue(canManageRemoteFlushing(owner, record))
        assertFalse(canManageRemoteFlushing(otherOperator, record))
        assertFalse(canManageRemoteFlushing(supervisor, record))
        assertFalse(canManageRemoteFlushing(null, record))
    }

    @Test
    fun editableResponseMustPreserveImmutableServerFields() {
        val original = remoteRecord()

        assertTrue(
            hasSameRemoteFlushingIdentity(
                original,
                original.copy(reason = "Otro", version = original.version + 1L)
            )
        )
        assertFalse(
            hasSameRemoteFlushingIdentity(
                original,
                original.copy(operatorId = original.operatorId + 1L, version = original.version + 1L)
            )
        )
        assertFalse(
            hasSameRemoteFlushingIdentity(
                original,
                original.copy(registeredAt = original.registeredAt.plusMinutes(1))
            )
        )
    }

    @Test
    fun remoteStateNeverAddsLegacyRoomRecordWithSameNumericId() {
        val remote = remoteRecord()
        val local = FlushingRecord(
            id = remote.id,
            farmId = 1,
            shedId = 2,
            lineId = 3,
            operatorName = "Registro Room",
            date = "01/01/2020",
            time = "10:00",
            reason = "Demo",
            observation = "Legado"
        )
        val state = RemoteFlushingListUiState.Ready(listOf(remote))

        assertEquals(listOf(remote), state.records)
        assertIs<RemoteFlushing>(state.records.single())
        assertNotEquals(local.operatorName, state.records.single().operatorName)
    }

    @Test
    fun flushingResponseFromSessionBeforeLogoutIsRejected() {
        val requestGeneration = 12L
        val generationAfterLogout = 13L

        assertFalse(isCurrentSessionResult(requestGeneration, generationAfterLogout))
    }

    private fun session(role: UserRole, id: Long, farmId: Long?) = SessionUser(
        id = id,
        email = "persona@example.cl",
        name = "Persona",
        role = role,
        farmId = farmId,
        farm = farmId?.let { "Granja" }
    )

    private fun remoteRecord() = RemoteFlushing(
        id = 50L,
        lineId = 30L,
        lineName = "Línea",
        shedId = 20L,
        shedName = "Galpón",
        farmId = 10L,
        farmName = "Granja",
        operatorId = 40L,
        operatorName = "Operario",
        registeredAt = OffsetDateTime.parse("2026-11-07T20:05:00-03:00"),
        reason = "Limpieza",
        observation = null,
        completed = true,
        beforeTemperatureC = BigDecimal("28.90"),
        afterTemperatureC = BigDecimal("21.50"),
        clientOperationId = UUID.fromString("61a7048d-cc44-4e6a-a38d-536235097da0"),
        version = 7L
    )
}
