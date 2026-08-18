package com.tcc.veiculotracker.ui.screens.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tcc.veiculotracker.ui.theme.Active
import com.tcc.veiculotracker.ui.theme.Blocked
import com.tcc.veiculotracker.ui.components.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: RemoteControlViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.lastAction) {
        if (state.lastAction != null) {
            kotlinx.coroutines.delay(3000L)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controle Remoto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    text = "Selecione um veículo",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.vehicles) { vehicle ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectVehicle(vehicle) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.selectedVehicle?.id == vehicle.id)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${vehicle.brand} ${vehicle.model}", fontWeight = FontWeight.SemiBold)
                                    Text(vehicle.plate, style = MaterialTheme.typography.bodySmall)
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (vehicle.isBlocked) Blocked.copy(alpha = 0.1f) else Active.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = if (vehicle.isBlocked) "Bloqueado" else "Ativo",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (vehicle.isBlocked) Blocked else Active
                                    )
                                }
                            }
                        }
                    }
                }

                state.selectedVehicle?.let { vehicle ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Controle: ${vehicle.brand} ${vehicle.model}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(vehicle.plate, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                            Spacer(modifier = Modifier.height(20.dp))

                            state.lastAction?.let { action ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Active.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = action,
                                        modifier = Modifier.padding(12.dp),
                                        color = Active,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            state.error?.let { error ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = error,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (!vehicle.isBlocked) {
                                Button(
                                    onClick = { viewModel.blockVehicle() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isExecuting,
                                    colors = ButtonDefaults.buttonColors(containerColor = Blocked)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bloquear Veículo")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.unblockVehicle() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isExecuting,
                                    colors = ButtonDefaults.buttonColors(containerColor = Active)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Desbloquear Veículo")
                                }
                            }
                        }
                    }
                }
            }

            if (state.isExecuting) {
                LoadingOverlay("Processando comando...")
            }
        }
    }
}
