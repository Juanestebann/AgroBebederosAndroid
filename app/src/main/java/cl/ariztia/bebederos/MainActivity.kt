package cl.ariztia.bebederos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import cl.ariztia.bebederos.data.model.TemperatureStatus
import cl.ariztia.bebederos.ui.navigation.AppNavGraph
import cl.ariztia.bebederos.ui.theme.BebederosTheme
import cl.ariztia.bebederos.util.NotificationHelper
import cl.ariztia.bebederos.util.shouldRequestNotificationPermission
import cl.ariztia.bebederos.viewmodel.AppViewModel
import cl.ariztia.bebederos.viewmodel.alerts
import kotlinx.coroutines.launch

/**
 * Punto de entrada Android que instala el árbol Compose y conecta servicios del sistema.
 *
 * La actividad crea un único [AppViewModel], observa sus `StateFlow` como estado Compose y entrega
 * callbacks de permisos/ajustes al grafo. Una nueva emisión provoca recomposición de los lectores;
 * no se recrea manualmente cada pantalla.
 */
class MainActivity : ComponentActivity() {

    private val notificationAvailability = mutableStateOf(false)
    private val runtimeNotificationPermissionGranted = mutableStateOf(false)
    private val notificationAlertId = mutableStateOf<Long?>(null)
    private var pendingNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            refreshNotificationAvailability()
            pendingNotificationPermissionResult?.invoke(notificationAvailability.value)
            pendingNotificationPermissionResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)
        refreshNotificationAvailability()
        handleNotificationIntent(intent)

        setContent {

            BebederosTheme {

                val navController = rememberNavController()

                val appViewModel: AppViewModel = viewModel()

                // collectAsState convierte el flujo de sesión en estado observable por Compose.
                val currentUser by
                appViewModel.currentUser.collectAsState()

                val criticalNotificationsEnabled by
                appViewModel.criticalNotifications.collectAsState()
                val permissionRequestRecorded by
                appViewModel.notificationPermissionRequested.collectAsState()
                val remoteAlerts by appViewModel.remoteAlerts.collectAsState()
                val notificationsAvailable = notificationAvailability.value
                val runtimePermissionGranted = runtimeNotificationPermissionGranted.value
                val pendingAlertId = notificationAlertId.value

                LaunchedEffect(pendingAlertId) {
                    if (pendingAlertId != null) {
                        appViewModel.openRemoteAlertFromNotification(pendingAlertId)
                        notificationAlertId.value = null
                    }
                }

                // El permiso se solicita una sola vez, al existir una alerta crítica visible.
                LaunchedEffect(
                    currentUser,
                    remoteAlerts,
                    criticalNotificationsEnabled,
                    runtimePermissionGranted,
                    permissionRequestRecorded
                ) {
                    if (
                        shouldRequestNotificationPermission(
                            sdkInt = Build.VERSION.SDK_INT,
                            userAuthenticated = currentUser != null,
                            hasCriticalAlerts = remoteAlerts.alerts.any { alert ->
                                alert.status == TemperatureStatus.CRITICAL
                            },
                            notificationsEnabled = criticalNotificationsEnabled,
                            runtimePermissionGranted = runtimePermissionGranted,
                            permissionRequestRecorded = permissionRequestRecorded
                        )
                    ) {
                        appViewModel.markNotificationPermissionRequested()
                        requestNotificationPermission { }
                    }
                }

                // Procesa todas las alertas críticas y persiste únicamente las publicadas.
                LaunchedEffect(
                    currentUser,
                    remoteAlerts,
                    criticalNotificationsEnabled,
                    notificationsAvailable
                ) {
                    appViewModel.publishVisibleCriticalNotifications(
                        notificationsAvailable = notificationsAvailable
                    ) { alert ->
                        NotificationHelper.showCritical(this@MainActivity, alert)
                    }
                }

                AppNavGraph(
                    navController = navController,
                    viewModel = appViewModel,
                    notificationsAvailable = notificationsAvailable,
                    onRequestNotificationPermission = { onResult ->
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !runtimeNotificationPermissionGranted.value
                        ) {
                            if (permissionRequestRecorded == false) {
                                lifecycleScope.launch {
                                    appViewModel.markNotificationPermissionRequested()
                                    requestNotificationPermission(onResult)
                                }
                            } else {
                                onResult(false)
                            }
                        } else {
                            requestNotificationPermission(onResult)
                        }
                    },
                    onOpenNotificationSettings = ::openNotificationSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationAvailability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun refreshNotificationAvailability() {
        runtimeNotificationPermissionGranted.value = hasRuntimeNotificationPermission()
        notificationAvailability.value =
            NotificationHelper.canPostCriticalNotifications(this)
    }

    private fun hasRuntimeNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun handleNotificationIntent(sourceIntent: Intent?) {
        if (sourceIntent?.action != NotificationHelper.ACTION_OPEN_ALERT) return
        val alertId = sourceIntent.getLongExtra(NotificationHelper.EXTRA_ALERT_ID, -1L)
        if (alertId > 0L) notificationAlertId.value = alertId
        sourceIntent.removeExtra(NotificationHelper.EXTRA_ALERT_ID)
    }

    private fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (pendingNotificationPermissionResult != null) {
                onResult(false)
                return
            }
            pendingNotificationPermissionResult = onResult
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            refreshNotificationAvailability()
            onResult(notificationAvailability.value)
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }
}
