package cl.ariztia.bebederos.ui.screens.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.ui.components.AppTopBar
import cl.ariztia.bebederos.ui.components.RemoteDataBanner
import cl.ariztia.bebederos.ui.components.RemoteStateMessage
import cl.ariztia.bebederos.ui.components.remoteAlertTimestamp
import cl.ariztia.bebederos.ui.components.remoteDecimalText
import cl.ariztia.bebederos.ui.components.statusColors
import cl.ariztia.bebederos.ui.navigation.OperatorBottomBar
import cl.ariztia.bebederos.ui.navigation.Routes
import cl.ariztia.bebederos.ui.navigation.SupervisorBottomBar
import cl.ariztia.bebederos.ui.theme.TextSecondary
import cl.ariztia.bebederos.viewmodel.AppViewModel
import cl.ariztia.bebederos.viewmodel.NotificationAlertNavigationState
import cl.ariztia.bebederos.viewmodel.RemoteAlertUiState
import cl.ariztia.bebederos.viewmodel.alertErrorMessage

/** Alertas activas que el backend ya restringió según la granja y el rol autenticado. */
@Composable
fun RemoteAlertsScreen(
    viewModel: AppViewModel,
    role: UserRole,
    onNavigate: (String) -> Unit
) {
    val state by viewModel.remoteAlerts.collectAsState()
    val notificationNavigation by viewModel.notificationAlertNavigation.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentRoute = if (role == UserRole.SUPERVISOR) {
        Routes.AlertsSupervisor
    } else {
        Routes.AlertsOperario
    }

    LaunchedEffect(notificationNavigation) {
        val unavailable =
            notificationNavigation as? NotificationAlertNavigationState.Unavailable
                ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = unavailable.message,
            duration = SnackbarDuration.Long
        )
        viewModel.consumeNotificationAlertNavigation()
    }

    Scaffold(
        topBar = { AppTopBar("Alertas térmicas") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (role == UserRole.SUPERVISOR) {
                SupervisorBottomBar(currentRoute, onNavigate)
            } else {
                OperatorBottomBar(currentRoute, onNavigate)
            }
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color.White)
        ) {
            RemoteDataBanner()
            when (val current = state) {
                RemoteAlertUiState.Idle,
                RemoteAlertUiState.Loading -> RemoteStateMessage(
                    "Cargando alertas remotas…",
                    loading = true
                )
                is RemoteAlertUiState.Error -> RemoteStateMessage(
                    alertErrorMessage(current.error),
                    isError = true,
                    onRetry = viewModel::retryRemoteAlerts
                )
                is RemoteAlertUiState.Ready -> {
                    if (current.alerts.isEmpty()) {
                        RemoteStateMessage("No existen alertas térmicas activas.")
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(current.alerts, key = { it.id }) { alert ->
                                val (color, background) = statusColors(alert.status)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = background),
                                    border = BorderStroke(1.dp, color),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "${alert.lineName} · ${alert.shedName}",
                                                    color = color,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "${alert.farmName} · ${remoteAlertTimestamp(alert)}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                "${remoteDecimalText(alert.temperatureC)} °C",
                                                color = color,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                if (viewModel.selectRemoteAlert(alert)) {
                                                    onNavigate(Routes.LineDetail)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Ver detalle")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
