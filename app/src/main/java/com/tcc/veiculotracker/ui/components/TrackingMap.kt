package com.tcc.veiculotracker.ui.components

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 *  - depois disso, se o usuário ainda não interagiu, apenas centraliza no
 *    veículo MANTENDO o zoom escolhido (não "puxa" o zoom de volta);
 *  - assim que o usuário arrasta/pinça o mapa, o acompanhamento é desligado
 *    (o botão "centralizar" religa e volta a aproximar no veículo).
 */
private class TrackingMapController(private val appContext: Context) {

    var map: MapView? = null
        private set

    private var vehicleMarker: Marker? = null
    private var phoneMarker: Marker? = null

    /** Quando true, a câmera acompanha o veículo (sem mexer no zoom). */
    private var followVehicle = true

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
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> followVehicle = false
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

    /** Religa o acompanhamento e aproxima a câmera no veículo. */
    fun recenterOnVehicle() {
        followVehicle = true
        val mapView = map ?: return
        val target = vehicleMarker?.position ?: return
        mapView.controller.animateTo(target, FOCUS_ZOOM, 800L, null)
    }

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

        if (!followVehicle) return

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
        if (followVehicle && !initialViewDone && vehicleMarker?.position != null) {
            fitToBothMarkers(animated = true)
            initialViewDone = true
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
        followVehicle = true
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
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { TrackingMapController(context.applicationContext) }

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
                controller.updateVehiclePosition(latitude, longitude, animate)
                controller.updatePhonePosition(phoneLatitude, phoneLongitude)
            }
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
                contentDescription = "Centralizar no veículo",
                onClick = { controller.recenterOnVehicle() },
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
    modifier: Modifier = Modifier
) {
    var fullscreen by remember { mutableStateOf(false) }

    MapSurface(
        latitude = latitude,
        longitude = longitude,
        animate = animate,
        phoneLatitude = phoneLatitude,
        phoneLongitude = phoneLongitude,
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
                    isFullscreen = true,
                    onToggleFullscreen = { fullscreen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
