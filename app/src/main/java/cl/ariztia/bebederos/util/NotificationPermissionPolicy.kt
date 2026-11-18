package cl.ariztia.bebederos.util

private const val ANDROID_13_API_LEVEL = 33

/** Solicita el permiso una sola vez y solo cuando existe una razón visible para hacerlo. */
internal fun shouldRequestNotificationPermission(
    sdkInt: Int,
    userAuthenticated: Boolean,
    hasCriticalAlerts: Boolean,
    notificationsEnabled: Boolean,
    runtimePermissionGranted: Boolean,
    permissionRequestRecorded: Boolean?
): Boolean =
    sdkInt >= ANDROID_13_API_LEVEL &&
        userAuthenticated &&
        hasCriticalAlerts &&
        notificationsEnabled &&
        !runtimePermissionGranted &&
        permissionRequestRecorded == false
