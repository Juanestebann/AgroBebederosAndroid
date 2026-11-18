package cl.ariztia.bebederos.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.ariztia.bebederos.MainActivity
import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.ui.components.remoteAlertTimestamp
import cl.ariztia.bebederos.ui.components.remoteDecimalText

/**
 * Gestiona avisos Android locales basados en alertas críticas del backend.
 *
 * No ejecuta monitoreo en segundo plano. La deduplicación persistente se realiza antes de llamar a
 * [showCritical]; aquí se vuelve a validar el estado crítico y la autorización del sistema.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "critical_temperature"
    const val ACTION_OPEN_ALERT = "cl.ariztia.bebederos.action.OPEN_ALERT"
    const val EXTRA_ALERT_ID = "cl.ariztia.bebederos.extra.ALERT_ID"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas críticas de temperatura",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos locales basados en alertas entregadas por AgroBebederos"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Comprueba permiso global, habilitación de la app y disponibilidad del canal. */
    fun canPostCriticalNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        val channel = context
            .getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)

        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Publica una alerta crítica y confirma mediante el retorno si Android aceptó la operación. */
    fun showCritical(context: Context, alert: RemoteAlert): Boolean {
        if (
            alert.status != TemperatureStatus.CRITICAL ||
            !canPostCriticalNotifications(context)
        ) {
            return false
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERT
            data = Uri.parse("agrobebederos://alert/${alert.id}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALERT_ID, alert.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Alerta crítica de temperatura")
            .setContentText(
                "${alert.lineName}: ${remoteDecimalText(alert.temperatureC)} °C · " +
                    remoteAlertTimestamp(alert)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
