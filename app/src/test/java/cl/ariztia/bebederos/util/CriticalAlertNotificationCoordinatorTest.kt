package cl.ariztia.bebederos.util

import cl.ariztia.bebederos.data.local.CriticalAlertNotificationHistory
import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.TemperatureStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CriticalAlertNotificationCoordinatorTest {

    @Test
    fun oneCriticalAlertIsPublishedAndPersisted() = runBlocking {
        val history = FakeHistory()
        val published = mutableListOf<Long>()

        val result = coordinator(history).publishPending(
            userId = 10L,
            alerts = listOf(alert(1L, TemperatureStatus.CRITICAL)),
            notificationsEnabled = true,
            notificationsAvailable = true
        ) { remoteAlert ->
            published += remoteAlert.id
            true
        }

        assertEquals(listOf(1L), result)
        assertEquals(listOf(1L), published)
        assertTrue(history.wasNotified(10L, 1L))
    }

    @Test
    fun everyCriticalAlertIsProcessedAndNonCriticalAlertsAreIgnored() = runBlocking {
        val history = FakeHistory()
        val published = mutableListOf<Long>()

        coordinator(history).publishPending(
            userId = 10L,
            alerts = listOf(
                alert(1L, TemperatureStatus.CRITICAL),
                alert(2L, TemperatureStatus.WARNING),
                alert(3L, TemperatureStatus.CRITICAL),
                alert(3L, TemperatureStatus.CRITICAL)
            ),
            notificationsEnabled = true,
            notificationsAvailable = true
        ) { remoteAlert ->
            published += remoteAlert.id
            true
        }

        assertEquals(listOf(1L, 3L), published)
    }

    @Test
    fun repeatedAlertStaysDeduplicatedAfterCoordinatorRestart() = runBlocking {
        val persistentHistory = FakeHistory()
        val published = mutableListOf<Long>()
        val critical = alert(7L, TemperatureStatus.CRITICAL)

        coordinator(persistentHistory).publishPending(1L, listOf(critical), true, true) {
            published += it.id
            true
        }
        coordinator(persistentHistory).publishPending(1L, listOf(critical), true, true) {
            published += it.id
            true
        }

        assertEquals(listOf(7L), published)
    }

    @Test
    fun notificationHistoryIsIndependentForEachUser() = runBlocking {
        val history = FakeHistory()
        val published = mutableListOf<Pair<Long, Long>>()
        val critical = alert(9L, TemperatureStatus.CRITICAL)

        coordinator(history).publishPending(1L, listOf(critical), true, true) {
            published += 1L to it.id
            true
        }
        coordinator(history).publishPending(2L, listOf(critical), true, true) {
            published += 2L to it.id
            true
        }

        assertEquals(listOf(1L to 9L, 2L to 9L), published)
    }

    @Test
    fun disabledPreferenceDeniedPermissionAndFailedPublicationDoNotConsumeAlert() = runBlocking {
        val history = FakeHistory()
        val critical = alert(11L, TemperatureStatus.CRITICAL)
        var attempts = 0

        coordinator(history).publishPending(1L, listOf(critical), false, true) {
            attempts++
            true
        }
        coordinator(history).publishPending(1L, listOf(critical), true, false) {
            attempts++
            true
        }
        coordinator(history).publishPending(1L, listOf(critical), true, true) {
            attempts++
            false
        }

        assertEquals(1, attempts)
        assertTrue(!history.wasNotified(1L, 11L))

        val published = coordinator(history).publishPending(1L, listOf(critical), true, true) {
            true
        }
        assertEquals(listOf(11L), published)
    }

    private fun coordinator(history: FakeHistory) =
        CriticalAlertNotificationCoordinator(history)

    private fun alert(id: Long, status: TemperatureStatus) = RemoteAlert(
        id = id,
        lineId = 100L + id,
        lineName = "Línea $id",
        shedId = 20L,
        shedName = "Galpón",
        farmId = 10L,
        farmName = "Granja",
        temperatureC = BigDecimal("28.0"),
        status = status,
        generatedAt = OffsetDateTime.parse("2026-11-10T10:00:00-03:00")
    )

    private class FakeHistory : CriticalAlertNotificationHistory {
        private val entries = mutableSetOf<Pair<Long, Long>>()

        override suspend fun wasNotified(userId: Long, alertId: Long): Boolean =
            userId to alertId in entries

        override suspend fun markNotified(userId: Long, alertId: Long) {
            entries += userId to alertId
        }
    }
}
