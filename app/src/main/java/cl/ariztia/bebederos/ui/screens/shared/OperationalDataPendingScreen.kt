package cl.ariztia.bebederos.ui.screens.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.ariztia.bebederos.data.model.UserRole
import cl.ariztia.bebederos.ui.components.AppTopBar
import cl.ariztia.bebederos.ui.navigation.OperatorBottomBar
import cl.ariztia.bebederos.ui.navigation.Routes
import cl.ariztia.bebederos.ui.navigation.SupervisorBottomBar
import cl.ariztia.bebederos.ui.theme.TextSecondary

/**
 * Mantiene la navegación del rol, pero evita presentar catálogos demostrativos como datos remotos.
 */
@Composable
fun OperationalDataPendingScreen(
    role: UserRole,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    description: String = "Esta sección todavía no se encuentra integrada con el backend."
) {
    val profileRoute = if (role == UserRole.SUPERVISOR) {
        Routes.ProfileSupervisor
    } else {
        Routes.ProfileOperario
    }

    Scaffold(
        topBar = { AppTopBar("Datos operativos") },
        bottomBar = {
            if (role == UserRole.SUPERVISOR) {
                SupervisorBottomBar(currentRoute, onNavigate)
            } else {
                OperatorBottomBar(currentRoute, onNavigate)
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Sesión remota activa",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "$description Los datos demostrativos se mantienen ocultos para no confundirlos " +
                        "con información real.",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onNavigate(profileRoute) }) {
                Text("Ver perfil remoto")
            }
        }
    }
}
