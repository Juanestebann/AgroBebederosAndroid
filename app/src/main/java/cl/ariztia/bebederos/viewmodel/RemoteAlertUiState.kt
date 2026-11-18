package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.RemoteAlert
import cl.ariztia.bebederos.data.model.RemoteCatalog
import cl.ariztia.bebederos.data.model.SessionUser
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.data.repository.AlertError

sealed interface RemoteAlertUiState {
    data object Idle : RemoteAlertUiState
    data object Loading : RemoteAlertUiState
    data class Ready(val alerts: List<RemoteAlert>) : RemoteAlertUiState
    data class Error(val error: AlertError) : RemoteAlertUiState
}

val RemoteAlertUiState.alerts: List<RemoteAlert>
    get() = (this as? RemoteAlertUiState.Ready)?.alerts.orEmpty()

/** Progreso de una navegación iniciada por el ID incluido en una notificación Android. */
sealed interface NotificationAlertNavigationState {
    data object Idle : NotificationAlertNavigationState
    data class WaitingForSession(val alertId: Long) : NotificationAlertNavigationState
    data class WaitingForCatalog(val alertId: Long) : NotificationAlertNavigationState
    data class Loading(val alertId: Long) : NotificationAlertNavigationState
    data class Ready(val alert: RemoteAlert) : NotificationAlertNavigationState
    data class Unavailable(val alertId: Long, val message: String) :
        NotificationAlertNavigationState
}

/**
 * Valida nuevamente el alcance local aunque Spring Boot ya proteja el endpoint por rol y granja.
 */
internal fun canAccessRemoteAlert(
    user: SessionUser,
    alert: RemoteAlert,
    catalog: RemoteCatalog
): Boolean {
    if (alert.id <= 0L) return false
    if (user.role == UserRole.OPERARIO && user.farmId != alert.farmId) return false

    val farm = catalog.farm(alert.farmId) ?: return false
    val shed = catalog.shed(alert.shedId) ?: return false
    val line = catalog.line(alert.lineId) ?: return false

    return farm.id == alert.farmId &&
        shed.farmId == alert.farmId &&
        line.farmId == alert.farmId &&
        line.shedId == alert.shedId
}

fun alertErrorMessage(error: AlertError): String = when (error) {
    AlertError.UNAUTHORIZED -> "La sesión dejó de ser válida."
    AlertError.FORBIDDEN -> "No tienes permiso para consultar estas alertas."
    AlertError.NOT_FOUND -> "La alerta ya no está disponible."
    AlertError.NETWORK -> "No se pudo conectar con el servidor."
    AlertError.UNEXPECTED -> "El servidor devolvió una respuesta inesperada."
}

fun notificationAlertErrorMessage(error: AlertError): String = when (error) {
    AlertError.FORBIDDEN -> "La alerta no pertenece al alcance de esta sesión."
    AlertError.NOT_FOUND -> "La alerta ya no está disponible."
    AlertError.NETWORK -> "No se pudo verificar la alerta con el servidor."
    AlertError.UNEXPECTED -> "No fue posible abrir la alerta de forma segura."
    AlertError.UNAUTHORIZED -> "La sesión dejó de ser válida."
}
