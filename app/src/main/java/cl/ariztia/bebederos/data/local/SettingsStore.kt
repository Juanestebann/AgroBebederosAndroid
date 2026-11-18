package cl.ariztia.bebederos.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("bebederos_settings")

/**
 * Preferencias pequeñas persistidas con DataStore.
 *
 * Se almacenan la presentación ampliada del origen de datos y la habilitación de avisos locales;
 * los registros de flushing pertenecen a Room y no a este almacén.
 */
class SettingsStore(private val context: Context) : CriticalAlertNotificationHistory {
    private val offlineKey = booleanPreferencesKey("offline_mode")
    private val notificationsKey = booleanPreferencesKey("critical_notifications")
    private val notificationPermissionRequestedKey =
        booleanPreferencesKey("notification_permission_requested")
    private val notifiedCriticalAlertsKey =
        stringSetPreferencesKey("notified_critical_alerts_by_user")
    private val demoFlushingsSeededKey = booleanPreferencesKey("demo_flushings_seeded")

    /** Flujo que vuelve a emitir cuando cambia la preferencia visual de origen de datos. */
    val offline: Flow<Boolean> =
        context.dataStore.data.map { it[offlineKey] ?: false }

    /** Flujo de preferencia para avisos locales de alertas críticas remotas. */
    val notifications: Flow<Boolean> =
        context.dataStore.data.map { it[notificationsKey] ?: true }

    /** Evita volver a abrir automáticamente el diálogo de permiso tras una decisión del usuario. */
    val notificationPermissionRequested: Flow<Boolean> =
        context.dataStore.data.map { it[notificationPermissionRequestedKey] ?: false }

    suspend fun setOffline(value: Boolean) {
        context.dataStore.edit { it[offlineKey] = value }
    }

    suspend fun setNotifications(value: Boolean) {
        context.dataStore.edit { it[notificationsKey] = value }
    }

    suspend fun markNotificationPermissionRequested() {
        context.dataStore.edit { it[notificationPermissionRequestedKey] = true }
    }

    /**
     * La clave compuesta mantiene separado el historial de cada cuenta sin guardar credenciales.
     */
    override suspend fun wasNotified(userId: Long, alertId: Long): Boolean =
        notificationHistoryKey(userId, alertId) in
            context.dataStore.data.first()[notifiedCriticalAlertsKey].orEmpty()

    /** Solo se llama después de que Android confirmó la publicación de la notificación. */
    override suspend fun markNotified(userId: Long, alertId: Long) {
        val historyKey = notificationHistoryKey(userId, alertId)
        context.dataStore.edit { preferences ->
            preferences[notifiedCriticalAlertsKey] =
                preferences[notifiedCriticalAlertsKey].orEmpty() + historyKey
        }
    }

    /** Distingue el primer sembrado de una tabla vaciada intencionalmente por el usuario. */
    suspend fun wereDemoFlushingsSeeded(): Boolean =
        context.dataStore.data.first()[demoFlushingsSeededKey] ?: false

    suspend fun markDemoFlushingsSeeded() {
        context.dataStore.edit { it[demoFlushingsSeededKey] = true }
    }
}

/** Contrato pequeño para probar la deduplicación sin depender del framework Android. */
interface CriticalAlertNotificationHistory {
    suspend fun wasNotified(userId: Long, alertId: Long): Boolean
    suspend fun markNotified(userId: Long, alertId: Long)
}

internal fun notificationHistoryKey(userId: Long, alertId: Long): String =
    "$userId:$alertId"
