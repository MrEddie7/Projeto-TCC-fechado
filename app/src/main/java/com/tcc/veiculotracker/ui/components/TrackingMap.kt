package com.tcc.veiculotracker.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Mantém o ciclo de vida e o estado (mapa + marcador) do MapView do osmdroid,
 * separado da recomposição do Compose.
 */
private class TrackingMapController(private val appContext: Context) {

    var map: MapView? = null
        private set

    private var marker: Marker? = null

    fun onAttach(context: Context): MapView {
        val existing = map
        if (existing != null) return existing

        return MapView(context).also { mapView ->
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(15.0)
            map = mapView
        }
    }

    /**
     * Move o marcador (e opcionalmente a câmera) para a posição recebida
     * da telemetria. Posições (0,0) são ignoradas (nenhuma leitura ainda).
     */
    fun updatePosition(latitude: Double, longitude: Double, animate: Boolean) {
        if (latitude == 0.0 && longitude == 0.0) return

        val mapView = map ?: return
        val geoPoint = GeoPoint(latitude, longitude)

        val currentMarker = marker
            ?: Marker(mapView).also { newMarker ->
                newMarker.position = geoPoint
                newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                newMarker.title = "Veículo"
                newMarker.setIcon(appContext.getDrawable(org.osmdroid.library.R.drawable.marker_default))
                mapView.overlays.add(newMarker)
                marker = newMarker
            }

        currentMarker.position = geoPoint
        mapView.invalidate()

        if (animate) {
            mapView.controller.animateTo(geoPoint, 17.0, 1200L, null)
        }
    }

    fun onDetach() {
        map?.onDetach()
        map = null
        marker = null
    }
}

@Composable
fun TrackingMap(
    latitude: Double,
    longitude: Double,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { TrackingMapController(context.applicationContext) }

    DisposableEffect(controller) {
        onDispose { controller.onDetach() }
    }

    AndroidView(
        factory = { ctx ->
            controller.onAttach(ctx).also {
                controller.updatePosition(latitude, longitude, animate = false)
            }
        },
        modifier = modifier,
        update = {
            controller.updatePosition(latitude, longitude, animate)
        }
    )
}