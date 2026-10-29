package com.tcc.veiculotracker.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tcc.veiculotracker.R
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.ui.components.LoadingOverlay
import com.tcc.veiculotracker.ui.components.VehicleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToVehicleRegister: () -> Unit,
    onNavigateToApiLink: () -> Unit,
    onNavigateToDeviceLocation: () -> Unit,
    onNavigateToVehicleDetail: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Veículo com o diálogo aberto (editar/excluir). A lógica fica no
    // ViewModel; a tela só guarda qual cartão disparou a ação.
    var editingVehicle by remember { mutableStateOf<VehicleModel?>(null) }
    var deletingVehicle by remember { mutableStateOf<VehicleModel?>(null) }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Marca: logo Securitas (escudo) em vez do raio.
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Securitas", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDeviceLocation) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Localização do Celular")
                    }
                    IconButton(onClick = onNavigateToApiLink) {
                        Icon(Icons.Default.Link, contentDescription = "API")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToVehicleRegister,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Cadastrar veículo")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text("Olá, ${state.userName}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sua moto está segura agora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Veículos",
                            value = state.totalVehicles.toString(),
                            icon = Icons.Default.DirectionsCar,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Protegidos",
                            value = state.activeVehicles.toString(),
                            icon = Icons.Default.CheckCircle,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Alertas",
                            value = state.blockedVehicles.toString(),
                            icon = Icons.Default.Block,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Meus veículos",
                            style = MaterialTheme.typography.titleLarge
                        )
                        // O bloqueio do veículo ficou na aba "Rastrear", junto do
                        // botão de iniciar o rastreamento.
                        Text(
                            text = "Bloqueio na aba Rastrear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.vehicles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Nenhum veículo cadastrado ainda",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Toque em + para adicionar seu primeiro veículo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                } else {
                    items(state.vehicles) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onClick = { onNavigateToVehicleDetail(vehicle.id) },
                            onEdit = { editingVehicle = vehicle },
                            onDelete = { deletingVehicle = vehicle }
                        )
                    }
                }
            }

            if (state.isSaving) {
                LoadingOverlay("Salvando...")
            }
        }
    }

    editingVehicle?.let { vehicle ->
        EditVehicleDialog(
            vehicle = vehicle,
            onDismiss = { editingVehicle = null },
            onConfirm = { plate, brand, model, year, color ->
                editingVehicle = null
                viewModel.updateVehicle(vehicle, plate, brand, model, year, color)
            }
        )
    }

    deletingVehicle?.let { vehicle ->
        DeleteVehicleDialog(
            vehicle = vehicle,
            onDismiss = { deletingVehicle = null },
            onConfirm = {
                deletingVehicle = null
                viewModel.deleteVehicle(vehicle)
            }
        )
    }
}

/**
 * Edição do cadastro do veículo. Fica na aba principal, aberta pelo menu do
 * cartão onde o status (Ativo/Bloqueado) é mostrado.
 */
@Composable
private fun EditVehicleDialog(
    vehicle: VehicleModel,
    onDismiss: () -> Unit,
    onConfirm: (plate: String, brand: String, model: String, year: String, color: String) -> Unit
) {
    var plate by remember(vehicle.id) { mutableStateOf(vehicle.plate) }
    var brand by remember(vehicle.id) { mutableStateOf(vehicle.brand) }
    var model by remember(vehicle.id) { mutableStateOf(vehicle.model) }
    var year by remember(vehicle.id) { mutableStateOf(if (vehicle.year > 0) vehicle.year.toString() else "") }
    var color by remember(vehicle.id) { mutableStateOf(vehicle.color) }
    val valid = plate.isNotBlank() && brand.isNotBlank() && model.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar veículo") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Placa *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modelo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { if (it.length <= 4) year = it.filter(Char::isDigit) },
                    label = { Text("Ano") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Cor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(plate, brand, model, year, color) },
                enabled = valid
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Confirmação da exclusão: é irreversível, então pede o "sim" explícito. */
@Composable
private fun DeleteVehicleDialog(
    vehicle: VehicleModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Excluir veículo?") },
        text = {
            Text(
                "${vehicle.brand} ${vehicle.model} (${vehicle.plate}) será removido da sua conta. " +
                    "O histórico de rotas e o rastreador vinculado deixam de aparecer no app."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Excluir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.14f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
