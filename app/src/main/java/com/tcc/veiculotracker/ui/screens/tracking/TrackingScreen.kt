package com.tcc.veiculotracker.ui.screens.tracking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.ui.components.MapFocusTarget
import com.tcc.veiculotracker.ui.components.TrackingMap
import com.tcc.veiculotracker.ui.theme.Active
import com.tcc.veiculotracker.ui.theme.Blocked
import com.tcc.veiculotracker.ui.theme.Primary
import com.tcc.veiculotracker.ui.theme.Warning

/**
 * Aba Rastrear.
 *
 * Antes a tela era um `Column` sem rolagem com dois cartões de altura fixa
 * (veículo ~205dp + mapa 240dp + legendas ~320dp) antes da lista: num telefone
 * de 360×800dp sobravam ~0dp para a lista de veículos, então o usuário não
 * conseguia trocar de veículo — e em telas pequenas/paisagem o conteúdo era
 * cortado sem scroll nenhum.
 *
 * Agora a tela inteira é um único `LazyColumn`, o veículo é trocado pelo
 * seletor da barra superior (sempre visível, custo vertical zero) e o mapa tem
 * altura adaptativa com opção de recolher.
 *
 * A lista de veículos que existia no fim da tela foi removida: era um segundo
 * seletor fazendo o que a barra superior já faz, e empurrava o mapa para baixo
 * em telas pequenas.
 */
@Composable
fun TrackingScreen(
    viewModel: TrackingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pede a permissão de localização em tempo de execução: sem ela o celular
    // não é rastreado nem aparece como "conectado" no painel.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onLocationPermissionResult(result.values.any { it })
    }
    val requestLocationPermission: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Resultado do comando de bloqueio/desbloqueio: falha do POST ou ACK
    // pendente. O aviso também fica fixo no cartão, porque um snackbar curto
    // some antes de o usuário ler a causa enviada pela API.
    LaunchedEffect(state.commandMessage) {
        state.commandMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = if (state.commandFailed) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    TrackingContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onSelectVehicle = viewModel::selectVehicle,
        onToggleTracking = {
            if (state.isTracking) {
                viewModel.stopTracking()
            } else {
                if (!state.phonePermissionGranted) requestLocationPermission()
                viewModel.startTracking()
            }
        },
        onToggleBlock = {
            if (state.selectedVehicle?.isBlocked == true) viewModel.unblockVehicle()
            else viewModel.blockVehicle()
        },
        onClearCommandMessage = viewModel::clearCommandMessage,
        onRequestLocationPermission = requestLocationPermission,
        onFocusTargetChange = viewModel::setMapFocusTarget
    )
}

/**
 * Corpo da tela sem estado (o ViewModel fica no [TrackingScreen]).
 *
 * Ser sem estado é o que permite testar o layout em vários tamanhos de tela
 * sem login/Firebase (`TrackingLayoutTest`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingContent(
    state: TrackingState,
    onSelectVehicle: (VehicleModel) -> Unit,
    onToggleTracking: () -> Unit,
    onToggleBlock: () -> Unit,
    onClearCommandMessage: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onFocusTargetChange: (MapFocusTarget) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // Recolher o mapa é preferência de leitura, não dado de negócio: sobrevive
    // a rotação/troca de tema.
    var mapCollapsed by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TrackingTopBar(state = state, onSelectVehicle = onSelectVehicle) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Métricas responsivas num só lugar: largura manda no tamanho do
            // mapa, altura útil só influencia os paddings (paisagem).
            val compact = maxWidth < 400.dp || maxHeight < 520.dp
            val horizontal = if (compact) 12.dp else 16.dp
            val mapHeight = when {
                mapCollapsed -> 140.dp
                maxWidth >= 600.dp -> 280.dp
                maxWidth >= 420.dp -> 240.dp
                else -> 200.dp
            }
            val vehicle = state.selectedVehicle

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tracking-list"),
                contentPadding = PaddingValues(
                    start = horizontal,
                    end = horizontal,
                    top = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vehicle != null) {
                    item(key = "vehicle-card") {
                        SelectedVehicleCard(
                            vehicle = vehicle,
                            state = state,
                            compact = compact,
                            onToggleTracking = onToggleTracking,
                            onToggleBlock = onToggleBlock,
                            onClearCommandMessage = onClearCommandMessage
                        )
                    }
                    item(key = "map-card") {
                        MapCard(
                            state = state,
                            height = mapHeight,
                            collapsed = mapCollapsed,
                            compact = compact,
                            onToggleCollapsed = { mapCollapsed = !mapCollapsed },
                            onRequestLocationPermission = onRequestLocationPermission,
                            onFocusTargetChange = onFocusTargetChange
                        )
                    }
                }

                // Sem o seletor antigo do rodapé: quando ainda não há veículo
                // escolhido, a única saída é a barra superior, então o aviso
                // aponta para lá.
                if (vehicle == null && !state.isLoading) {
                    item(key = "empty") {
                        NoVehicleSelected(hasVehicles = state.vehicles.isNotEmpty())
                    }
                }
            }
        }
    }
}

// ── Barra superior: seletor de veículo ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingTopBar(
    state: TrackingState,
    onSelectVehicle: (VehicleModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedVehicle

    TopAppBar(
        title = {
            Surface(
                onClick = {
                    if (state.vehicles.isNotEmpty() || selected != null) expanded = true
                },
                enabled = state.vehicles.isNotEmpty() || selected != null,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("tracking-vehicle-selector")
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                        .widthIn(max = 260.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = selected?.let { "${it.plate} · ${it.brand} ${it.model}" }
                                ?: "Escolher veículo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Trocar veículo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                state.vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("vehicle-option-${vehicle.id}"),
                        text = {
                            Column {
                                Text(
                                    text = "${vehicle.brand} ${vehicle.model}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = vehicle.plate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (vehicle.isBlocked) Icons.Default.Lock else Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = if (vehicle.isBlocked) Blocked else MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = if (selected?.id == vehicle.id) {
                            { Icon(Icons.Default.Check, contentDescription = "Em rastreamento", tint = Active) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelectVehicle(vehicle)
                        }
                    )
                }
            }
        }
    )
}

// ── Cartão do veículo selecionado ──────────────────────────────────────

@Composable
private fun SelectedVehicleCard(
    vehicle: VehicleModel,
    state: TrackingState,
    compact: Boolean,
    onToggleTracking: () -> Unit,
    onToggleBlock: () -> Unit,
    onClearCommandMessage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle-card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${vehicle.brand} ${vehicle.model}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vehicle.plate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        // Estado do bloqueio: o mesmo comando que o botão abaixo
                        // dispara, mostrado de cara.
                        if (vehicle.isBlocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Blocked.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "BLOQUEADO",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Blocked
                                )
                            }
                        }
                    }
                }
                if (state.isTracking) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Active.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Active,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AO VIVO", style = MaterialTheme.typography.labelLarge, color = Active)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))

            VehicleStats(
                vehicle = vehicle,
                latitude = state.currentLatitude,
                longitude = state.currentLongitude
            )

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))

            CommandButtons(
                state = state,
                onToggleTracking = onToggleTracking,
                onToggleBlock = onToggleBlock
            )

            // Retorno do comando: erro do POST (vermelho) ou
            // confirmação/aguardando o rastreador (neutro).
            state.commandMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                CommandMessageBar(
                    message = message,
                    failed = state.commandFailed,
                    onDismiss = onClearCommandMessage
                )
            }
        }
    }
}

/**
 * Velocidade/Latitude/Longitude em `FlowRow`: 3 colunas quando cabe, 2 linhas
 * em telas de 320dp ou com fonte ampliada — antes a `Row` estourava a largura.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleStats(vehicle: VehicleModel, latitude: Double, longitude: Double) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle-stats"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        StatItem("Velocidade", String.format("%.1f km/h", vehicle.speed))
        StatItem("Latitude", String.format("%.6f", latitude))
        StatItem("Longitude", String.format("%.6f", longitude))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(modifier = Modifier.widthIn(min = 88.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "Iniciar/Parar" + "Bloquear/Desbloquear". Lado a lado com pesos quando há
 * largura; empilhados (largura total) abaixo de 360dp ou com fonte grande,
 * em que os dois rótulos não cabem na mesma linha.
 */
@Composable
private fun CommandButtons(
    state: TrackingState,
    onToggleTracking: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 300dp é o ponto em que "Iniciar Rastreamento" + "Bloquear" deixam
        // de caber lado a lado (medido no cartão, não na tela inteira: em
        // 411dp o espaço interno é ~347dp e os dois ainda cabem).
        val stacked = maxWidth < 300.dp || fontScale > 1.3f
        val trackButton: @Composable (Modifier) -> Unit = { m ->
            TrackingButton(state = state, onClick = onToggleTracking, modifier = m)
        }
        val blockButton: @Composable (Modifier) -> Unit = { m ->
            BlockButton(state = state, onClick = onToggleBlock, modifier = m)
        }

        if (stacked) {
            Column(
                modifier = Modifier.testTag("command-buttons"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trackButton(Modifier.fillMaxWidth())
                blockButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.testTag("command-buttons"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                trackButton(Modifier.weight(1f))
                blockButton(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrackingButton(
    state: TrackingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("btn-tracking"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = if (state.isTracking) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Icon(
            if (state.isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (state.isTracking) "Parar" else "Iniciar Rastreamento",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BlockButton(
    state: TrackingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBlocked = state.selectedVehicle?.isBlocked == true
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("btn-block"),
        enabled = !state.isBlocking,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isBlocked) Active else Blocked
        )
    ) {
        Icon(
            if (isBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when {
                state.isBlocking -> "Enviando..."
                isBlocked -> "Desbloquear"
                else -> "Bloquear"
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CommandMessageBar(message: String, failed: Boolean, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (failed) Icons.Default.ErrorOutline else Icons.Default.Info,
                contentDescription = null,
                tint = if (failed) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Fechar aviso",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Cartão do mapa ─────────────────────────────────────────────────────

@Composable
private fun MapCard(
    state: TrackingState,
    height: Dp,
    collapsed: Boolean,
    compact: Boolean,
    onToggleCollapsed: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onFocusTargetChange: (MapFocusTarget) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("map-card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mapa em tempo real",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onToggleCollapsed,
                    modifier = Modifier.testTag("map-toggle")
                ) {
                    Icon(
                        imageVector = if (collapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                        contentDescription = if (collapsed) "Expandir mapa" else "Recolher mapa"
                    )
                }
            }

            TrackingMap(
                latitude = state.currentLatitude,
                longitude = state.currentLongitude,
                animate = state.isTracking,
                phoneLatitude = if (state.isPhoneVisible) state.phoneLatitude else 0.0,
                phoneLongitude = if (state.isPhoneVisible) state.phoneLongitude else 0.0,
                focusTarget = state.mapFocusTarget,
                onFocusTargetChange = onFocusTargetChange,
                // Com 140dp de altura os três botões de zoom não cabem sem
                // cobrir o mapa; a tela cheia continua a um toque.
                showZoomControls = !collapsed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .testTag("tracking-map")
            )

            if (!collapsed) {
                // Legenda: veículo + celular no mesmo mapa (funções vinculadas)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MapLegendDot(color = Warning, label = "Veículo")
                    MapLegendDot(color = Primary, label = "Celular (você)")
                }

                Text(
                    text = phoneStatusText(state),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = if (compact) 8.dp else 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (state.isTracking && !state.phonePermissionGranted) {
                    TextButton(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Conceder permissão de localização")
                    }
                }
            }
        }
    }
}

// ── Estado vazio ──────────────────────────────────────────────────────

/**
 * Sem veículo escolhido. Antes existia a lista de veículos no rodapé para
 * resolver isso; agora o único seletor é o da barra superior, então o aviso
 * aponta para ele.
 */
@Composable
private fun NoVehicleSelected(hasVehicles: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no-vehicle"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasVehicles) {
                    "Nenhum veículo selecionado. Abra o seletor no topo da tela para escolher um."
                } else {
                    "Nenhum veículo cadastrado ainda."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Auxiliares ─────────────────────────────────────────────────────────

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Status textual de como a localização do celular está vinculada ao
 * rastreamento do veículo (GPS local + envio à API).
 */
private fun phoneStatusText(state: TrackingState): String = when {
    state.isTracking && !state.phonePermissionGranted ->
        "Celular não rastreado: conceda a permissão de localização para vê-lo junto do veículo"

    state.isTracking && state.isPhoneVisible && state.phoneLastSentAt > 0 -> {
        val elapsed = (System.currentTimeMillis() - state.phoneLastSentAt) / 1000
        "Celular ao vivo junto do veículo · posição enviada à API há ${elapsed}s"
    }

    state.isTracking && state.isPhoneVisible ->
        "Celular ao vivo junto do veículo · aguardando envio à API"

    state.isPhoneVisible ->
        "Última posição do celular: ${String.format("%.5f", state.phoneLatitude)}, ${String.format("%.5f", state.phoneLongitude)}"

    else -> "Inicie o rastreamento para localizar o celular junto do veículo"
}
