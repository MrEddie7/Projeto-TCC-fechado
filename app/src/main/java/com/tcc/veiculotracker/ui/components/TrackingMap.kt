package com.tcc.veiculotracker.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Mantém o ciclo de vida e o estado (mapa + marcadores) do MapView do osmdroid,
 * separado da recomposição do Compose.
 *
 * Exibe DOIS marcadores juntos:
 *  - Veículo (teardrop padrão do osmdroid), que acompanha a telemetria;
 *  - Celular do usuário (círculo azul), posição GPS do aparelho.
 * Quando ambos estão no mapa, a câmera enquadra os dois automaticamente.
 */
private class TrackingMapController(private val appContext: Context) {

    var map: MapView? = null
        private set

    private var vehicleMarker: Marker? = null
    private var phoneMarker: Marker? = null

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

        if (animate) {
            if (phoneMarker?.position != null) {
                fitToBothMarkers(animated = true)
            } else {
                mapView.controller.animateTo(geoPoint, 17.0, 1200L, null)
            }
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

        // Enquadra veículo + celular quando o celular surge no mapa.
        if (vehicleMarker?.position != null) {
            fitToBothMarkers(animated = true)
        }
    }

    private fun fitToBothMarkers(animated: Boolean) {
        val mapView = map ?: return
        val vehicle = vehicleMarker?.position ?: return
        val phone = phoneMarker?.position ?: return
        val bbox = BoundingBox.fromGeoPoints(listOf(vehicle, phone))
        mapView.zoomToBoundingBox(bbox, animated, 80)
    }

    fun onDetach() {
        map?.onDetach()
        map = null
        vehicleMarker = null
        phoneMarker = null
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
    val context = LocalContext.current
    val controller = remember { TrackingMapController(context.applicationContext) }

    DisposableEffect(controller) {
        onDispose { controller.onDetach() }
    }

    AndroidView(
        factory = { ctx ->
            controller.onAttach(ctx).also {
                controller.updateVehiclePosition(latitude, longitude, animate = false)
                controller.updatePhonePosition(phoneLatitude, phoneLongitude)
            }
        },
        modifier = modifier,
        update = {
            controller.updateVehiclePosition(latitude, longitude, animate)
            controller.updatePhonePosition(phoneLatitude, phoneLongitude)
        }
    )
}