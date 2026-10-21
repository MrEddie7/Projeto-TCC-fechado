package com.tcc.veiculotracker.ui.components

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private const val DEFAULT_ZOOM = 16.0
private const val FOCUS_ZOOM = 17.0

/**
 * Onde a câmera do mapa focaliza quando o usuário pede para localizar:
 * a posição do VEÍCULO (telemetria) ou a do CELULAR (GPS do aparelho).
 * Escolhido na hora pelo usuário pela caixa de opção sobreposta ao mapa.
 */
enum class MapFocusTarget { VEHICLE, PHONE }

// Tamanho mínimo (em graus) do enquadramento inicial. Sem esse piso, dois
// pontos praticamente coincidentes geram um BoundingBox degenerado; o osmdroid
// calcula um zoom "infinito" e entra em loop infinito em
// Projection.getCloserPixel (o laço `while (pPixel >= limite) pPixel -= mapSize`
// nunca termina). Isso travava a main thread e causava ANR.
private const val MIN_FIT_SPAN_DEG = 0.004

/**
 * Mantém o ciclo de vida e o estado (mapa + marcadores) do MapView do osmdroid,
 * separado da recomposição do Compose.
 *
 * Exibe DOIS marcadores juntos:
 *  - Veículo (teardrop padrão do osmdroid), que acompanha a telemetria;
 *  - Celular do usuário (círculo azul), posição GPS do aparelho.
 *
 * Comportamento de câmera:
 *  - na primeira exibição, enquadra os dois marcadores;
 *  - depois disso, a câmera acompanha o alvo escolhido na caixa de opção do
 *    mapa (VEÍCULO por padrão ou CELULAR), MANTENDO o zoom escolhido
 *    (não "puxa" o zoom de volta);
 *  - assim que o usuário arrasta/pinça o mapa, o acompanhamento é desligado
 *    (o botão "centralizar" religa e volta a aproximar no alvo escolhido).
 */
private class TrackingMapController(
    private val appContext: Context,
    initialFocus: MapFocusTarget = MapFocusTarget.VEHICLE
) {

    var map: MapView? = null
        private set

    private var vehicleMarker: Marker? = null
    private var phoneMarker: Marker? = null

    /** Foco escolhido pelo usuário (veículo ou celular). */
    private var chosenFocus: MapFocusTarget = initialFocus

    /**
     * Alvo que a câmera segue (sem mexer no zoom). `null` = o usuário arrastou
     * o mapa e assumiu o controle; o botão "centralizar" volta a seguir o
     * [chosenFocus].
     */
    private var following: MapFocusTarget? = initialFocus

    /** Garante que o enquadramento inicial (veículo + celular) ocorra só 1x. */
    private var initialViewDone = false

    fun onAttach(context: Context): MapView {
        val existing = map
        if (existing != null) return existing

        return MapView(context).also { mapView ->
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(DEFAULT_ZOOM)
            // Qualquer gesto (arrastar ou pinçar) desliga o acompanhamento
            // automático; retornar false deixa o MapView tratar o toque.
            mapView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> following = null
                }
                false
            }
            map = mapView
        }
    }

    fun zoomIn() {
        map?.controller?.zoomIn()
    }

    fun zoomOut() {
        map?.controller?.zoomOut()
    }

    /** Religa o controle manual e aproxima a câmera no alvo escolhido. */
    fun recenterOnFocus() {
        following = chosenFocus
        val mapView = map ?: return
        val target = markerFor(chosenFocus)?.position ?: return
        mapView.controller.animateTo(target, FOCUS_ZOOM, 800L, null)
    }

    /**
     * Troca o foco do mapa (veículo ↔ celular) e centraliza no novo alvo.
     * Idempotente: só age quando o foco realmente muda, porque é chamado a
     * cada recomposição — sem isso o mapa "puxaria" a câmera de volta mesmo
     * quando o usuário tivesse arrastado.
     */
    fun setFocusTarget(target: MapFocusTarget) {
        if (target == chosenFocus) return
        chosenFocus = target
        following = target
        // O usuário já escolheu onde focalizar: não enquadra mais os dois.
        initialViewDone = true
        val mapView = map ?: return
        val point = markerFor(target)?.position ?: return
        mapView.controller.animateTo(point, FOCUS_ZOOM, 800L, null)
    }

    private fun markerFor(target: MapFocusTarget): Marker? =
        if (target == MapFocusTarget.VEHICLE) vehicleMarker else phoneMarker

    /**
     * Move o marcador do VEÍCULO para a posição da telemetria.
     * Posições (0,0) são ignoradas (nenhuma leitura ainda).
     */
    fun updateVehiclePosition(latitude: Double, longitude: Double, animate: Boolean) {
        if (latitude == 0.0 && longitude == 0.0) return

        val mapView = map ?: return
        val geoPoint = GeoPoint(latitude, longitude)

        val existingMarker = vehicleMarker
            ?: Marker(mapView).also { newMarker ->
                newMarker.position = geoPoint
                newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                newMarker.title = "Veículo"
                newMarker.setIcon(appContext.getDrawable(org.osmdroid.library.R.drawable.marker_default))
                mapView.overlays.add(newMarker)
                vehicleMarker = newMarker
            }

        existingMarker.position = geoPoint
        mapView.invalidate()

        if (following != MapFocusTarget.VEHICLE) return

        if (phoneMarker?.position != null && !initialViewDone) {
            fitToBothMarkers(animated = animate)
            initialViewDone = true
        } else {
            // Centraliza mantendo o zoom atual — o usuário pode aproximar à vontade.
            mapView.controller.animateTo(geoPoint, mapView.zoomLevelDouble, if (animate) 1000L else 0L, null)
        }
    }

    /**
     * Move o marcador do CELULAR para a posição GPS do aparelho.
     * Posições (0,0) REMOVEM o marcador (sem leitura disponível).
     */
    fun updatePhonePosition(latitude: Double, longitude: Double) {
        val mapView = map ?: return

        if (latitude == 0.0 && longitude == 0.0) {
            phoneMarker?.let { mapView.overlays.remove(it) }
            phoneMarker = null
            mapView.invalidate()
            return
        }

        val geoPoint = GeoPoint(latitude, longitude)
        val existingMarker = phoneMarker
            ?: Marker(mapView).also { newMarker ->
                newMarker.position = geoPoint
                newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                newMarker.title = "Celular"
                newMarker.setIcon(appContext.getDrawable(com.tcc.veiculotracker.R.drawable.ic_marker_phone))
                mapView.overlays.add(newMarker)
                phoneMarker = newMarker
            }

        existingMarker.position = geoPoint
        mapView.invalidate()

        // Enquadra veículo + celular apenas quando o celular surge no mapa.
        if (following != null && !initialViewDone && vehicleMarker?.position != null) {
            fitToBothMarkers(animated = true)
            initialViewDone = true
            return
        }

        // Foco no celular: a câmera acompanha a posição do aparelho.
        if (following == MapFocusTarget.PHONE) {
            mapView.controller.animateTo(geoPoint, mapView.zoomLevelDouble, 400L, null)
        }
    }

    private fun fitToBothMarkers(animated: Boolean) {
        val mapView = map ?: return
        val vehicle = vehicleMarker?.position ?: return
        val phone = phoneMarker?.position ?: return

        val north = maxOf(vehicle.latitude, phone.latitude)
        val south = minOf(vehicle.latitude, phone.latitude)
        val east = maxOf(vehicle.longitude, phone.longitude)
        val west = minOf(vehicle.longitude, phone.longitude)

        // Garante um enquadramento com tamanho mínimo para nunca degenerar.
        val latPad = ((MIN_FIT_SPAN_DEG - (north - south)) / 2).coerceAtLeast(0.0)
        val lngPad = ((MIN_FIT_SPAN_DEG - (east - west)) / 2).coerceAtLeast(0.0)
        val box = BoundingBox(north + latPad, east + lngPad, south - latPad, west - lngPad)

        // Só enquadra após o MapView ter dimensões; antes do layout o osmdroid
        // pode divergir o cálculo de zoom (mesmo problema do loop acima).
        mapView.post {
            if (mapView.width > 0 && mapView.height > 0) {
                mapView.zoomToBoundingBox(box, animated, 80)
            } else {
                mapView.controller.setZoom(FOCUS_ZOOM)
                mapView.controller.setCenter(vehicle)
            }
        }
    }

    fun onDetach() {
        map?.onDetach()
        map = null
        vehicleMarker = null
        phoneMarker = null
        following = chosenFocus
        initialViewDone = false
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * Caixa de opção do mapa: o usuário escolhe, na hora de localizar, se a
 * câmera focaliza a posição do VEÍCULO ou a do CELULAR.
 */
@Composable
private fun FocusTargetSelector(
    current: MapFocusTarget,
    onSelected: (MapFocusTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isVehicle = current == MapFocusTarget.VEHICLE

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            )
        ) {
            Icon(
                imageVector = if (isVehicle) Icons.Default.DirectionsCar else Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isVehicle) "Veículo" else "Celular",
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Escolher onde focalizar o mapa"
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Localização do veículo") },
                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSelected(MapFocusTarget.VEHICLE)
                }
            )
            DropdownMenuItem(
                text = { Text("Localização do celular") },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSelected(MapFocusTarget.PHONE)
                }
            )
        }
    }
}

/**
 * Superfície do mapa com os controles sobrepostos (tela cheia, zoom e
 * centralizar). Usada tanto no cartão quanto no modo tela cheia.
 */
@Composable
private fun MapSurface(
    latitude: Double,
    longitude: Double,
    animate: Boolean,
    phoneLatitude: Double,
    phoneLongitude: Double,
    focusTarget: MapFocusTarget,
    onFocusTargetChange: (MapFocusTarget) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // O foco escolhido nasce junto com o mapa (a tela cheia cria o seu).
    val controller = remember { TrackingMapController(context.applicationContext, focusTarget) }

    DisposableEffect(controller) {
        onDispose { controller.onDetach() }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                controller.onAttach(ctx).also {
                    controller.updateVehiclePosition(latitude, longitude, animate = false)
                    controller.updatePhonePosition(phoneLatitude, phoneLongitude)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                controller.setFocusTarget(focusTarget)
                controller.updateVehiclePosition(latitude, longitude, animate)
                controller.updatePhonePosition(phoneLatitude, phoneLongitude)
            }
        )

        // Caixa de opção: onde o mapa deve focalizar (veículo ↔ celular).
        FocusTargetSelector(
            current = focusTarget,
            onSelected = onFocusTargetChange,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )

        // Controles no canto superior direito.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (isFullscreen) Modifier.systemBarsPadding() else Modifier)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControlButton(
                    icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Sair da tela cheia" else "Tela cheia",
                    onClick = onToggleFullscreen
                )
                MapControlButton(
                    icon = Icons.Default.ZoomIn,
                    contentDescription = "Aproximar",
                    onClick = { controller.zoomIn() }
                )
                MapControlButton(
                    icon = Icons.Default.ZoomOut,
                    contentDescription = "Afastar",
                    onClick = { controller.zoomOut() }
                )
            }

            MapControlButton(
                icon = Icons.Default.MyLocation,
                contentDescription = if (focusTarget == MapFocusTarget.VEHICLE)
                    "Centralizar no veículo" else "Centralizar no celular",
                onClick = { controller.recenterOnFocus() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun TrackingMap(
    latitude: Double,
    longitude: Double,
    animate: Boolean,
    phoneLatitude: Double = 0.0,
    phoneLongitude: Double = 0.0,
    focusTarget: MapFocusTarget = MapFocusTarget.VEHICLE,
    onFocusTargetChange: (MapFocusTarget) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var fullscreen by remember { mutableStateOf(false) }

    MapSurface(
        latitude = latitude,
        longitude = longitude,
        animate = animate,
        phoneLatitude = phoneLatitude,
        phoneLongitude = phoneLongitude,
        focusTarget = focusTarget,
        onFocusTargetChange = onFocusTargetChange,
        isFullscreen = false,
        onToggleFullscreen = { fullscreen = true },
        modifier = modifier
    )

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MapSurface(
                    latitude = latitude,
                    longitude = longitude,
                    animate = animate,
                    phoneLatitude = phoneLatitude,
                    phoneLongitude = phoneLongitude,
                    focusTarget = focusTarget,
                    onFocusTargetChange = onFocusTargetChange,
                    isFullscreen = true,
                    onToggleFullscreen = { fullscreen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
