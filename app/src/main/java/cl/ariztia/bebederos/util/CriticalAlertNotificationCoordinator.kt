package cl.ariztia.bebederos.util

import cl.ariztia.bebederos.data.local.CriticalAlertNotificationHistory
import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.TemperatureStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Selecciona y publica todas las alertas críticas pendientes de una cuenta.
 *
 * La exclusión mutua evita carreras si Compose vuelve a evaluar los datos mientras se está
 * escribiendo DataStore. Una alerta se persiste como notificada únicamente cuando [publish]
 * devuelve `true`.
 */
class CriticalAlertNotificationCoordinator(
    private val history: CriticalAlertNotificationHistory
) {
    private val mutex = Mutex()

    suspend fun publishPending(
        userId: Long,
        alerts: List<RemoteAlert>,
        notificationsEnabled: Boolean,
        notificationsAvailable: Boolean,
        publish: (RemoteAlert) -> Boolean
    ): List<Long> = mutex.withLock {
        if (!notificationsEnabled || !notificationsAvailable) return@withLock emptyList()

        val publishedIds = mutableListOf<Long>()
        alerts
            .asSequence()
            .filter { it.status == TemperatureStatus.CRITICAL }
            .distinctBy { it.id }
            .forEach { alert ->
                if (!history.wasNotified(userId, alert.id) && publish(alert)) {
                    history.markNotified(userId, alert.id)
                    publishedIds += alert.id
                }
            }
        publishedIds
    }
}
