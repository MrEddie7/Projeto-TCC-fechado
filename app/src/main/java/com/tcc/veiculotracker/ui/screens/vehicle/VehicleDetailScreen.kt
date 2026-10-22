package com.tcc.veiculotracker.ui.screens.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tcc.veiculotracker.data.remote.ApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: Long,
    onNavigateBack: () -> Unit,
    viewModel: VehicleDetailViewModel = viewModel(
        factory = VehicleDetailViewModelFactory(
            application = LocalContext.current.applicationContext as android.app.Application,
            vehicleId = vehicleId
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val vehicle = state.vehicle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes do Veículo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar rastreador")
                    }
                }
            )
        }
    ) { padding ->
        if (vehicle != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${vehicle.brand} ${vehicle.model}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Placa: ${vehicle.plate}")
                        Text("Ano: ${vehicle.year}")
                        Text("Cor: ${vehicle.color}")
                        Text(
                            text = if (vehicle.isBlocked) "Bloqueado" else "Ativo",
                            color = if (vehicle.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                TrackerCard(device = state.device, telemetry = state.telemetry)

                state.error?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Cartão do rastreador: mostra se o veículo tem GPS, de que tipo, se o
 * aparelho está falando com a API e qual a última telemetria recebida.
 */
@Composable
private fun TrackerCard(
    device: ApiClient.DeviceDto?,
    telemetry: ApiClient.TelemetryDto?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Rastreador GPS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (device == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nenhum rastreador vinculado a este veículo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Bloqueio e rastreamento em tempo real só funcionam com um rastreador vinculado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text("Dispositivo: ${device.deviceId}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Tipo: " + if (device.isSimulated) "Simulador do servidor" else "GPS real (ESP32)",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Status: " + if (device.isConnected) "Conectado" else "Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = "Último contato: ${relativeTime(device.lastSeen)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (device.firmware.isNotBlank()) {
                Text(
                    text = "Firmware: ${device.firmware}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Última telemetria",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (telemetry == null) {
                Text(
                    text = "Nenhuma posição recebida do rastreador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Velocidade: ${String.format(Locale.getDefault(), "%.1f", telemetry.speed)} km/h",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (telemetry.battery > 0) {
                    Text(
                        text = "Bateria: ${String.format(Locale.getDefault(), "%.1f", telemetry.battery)} V",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "Recebida em: ${formatMoment(telemetry.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                telemetry.deviceId?.let {
                    Text(
                        text = "Enviada por: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "agora", "há 5 min", "há 2 h" — a partir do último contato do rastreador. */
private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "nunca"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "agora"
        diff < 3_600_000 -> "há ${diff / 60_000} min"
        diff < 86_400_000 -> "há ${diff / 3_600_000} h"
        else -> "há ${diff / 86_400_000} d"
    }
}

private fun formatMoment(timestamp: Long): String =
    if (timestamp <= 0L) "-"
    else SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
