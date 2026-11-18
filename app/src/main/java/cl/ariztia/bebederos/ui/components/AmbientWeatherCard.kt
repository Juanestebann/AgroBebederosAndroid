package cl.ariztia.bebederos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.ariztia.bebederos.ui.theme.BrandRed
import cl.ariztia.bebederos.ui.theme.TextSecondary
import cl.ariztia.bebederos.viewmodel.AmbientWeatherUiState
import cl.ariztia.bebederos.viewmodel.ambientWeatherErrorMessage
import java.util.Locale

@Composable
fun AmbientWeatherCard(
    state: AmbientWeatherUiState,
    onRetry: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Temperatura ambiental",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "API externa · Open-Meteo",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(Icons.Default.Cloud, contentDescription = null, tint = BrandRed)
        }
        Spacer(Modifier.height(10.dp))

        when (state) {
            AmbientWeatherUiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Consultando condiciones ambientales…")
            }

            is AmbientWeatherUiState.Ready -> {
                val weather = state.weather
                Text(
                    String.format(Locale.US, "%.1f %s", weather.temperatureC, weather.unit),
                    color = BrandRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Text(weather.locationLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    "Actualizado ${weather.observedAt.replace('T', ' ')} · Open-Meteo (CC BY 4.0)",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            is AmbientWeatherUiState.Error -> {
                Text(
                    ambientWeatherErrorMessage(state.error),
                    color = BrandRed,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Dato ambiental demostrativo. No reemplaza la temperatura del agua ni modifica su clasificación.",
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}
