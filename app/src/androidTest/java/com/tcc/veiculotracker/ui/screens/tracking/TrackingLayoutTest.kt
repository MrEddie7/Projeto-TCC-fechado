package com.tcc.veiculotracker.ui.screens.tracking

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.ui.theme.SecuritasTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout da aba Rastrear em vários tamanhos de janela.
 *
 * O problema que estes testes travam: a tela era um `Column` sem rolagem com
 * cards de altura fixa, então num telefone pequeno a lista de veículos ficava
 * com altura zero e o último veículo era inalcançável — o usuário não conseguia
 * trocar de veículo. Renderizar [TrackingContent] (sem estado) permite testar
 * isso sem login, backend ou Firebase.
 */
@RunWith(AndroidJUnit4::class)
class TrackingLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Estado de exemplo ──────────────────────────────────────────────

    private fun vehicle(id: Long, plate: String, brand: String, model: String, blocked: Boolean = false) =
        VehicleModel(
            id = id,
            plate = plate,
            brand = brand,
            model = model,
            year = 2020,
            color = "Preto",
            isBlocked = blocked,
            latitude = -23.561234,
            longitude = -46.656789,
            speed = 42.5
        )

    private fun sampleState(selectedId: Long = 1) = TrackingState(
        vehicles = listOf(
            vehicle(1, "ABC1D23", "Chevrolet", "Camaro"),
            vehicle(2, "DEF4G56", "Honda", "CB 500", blocked = true),
            vehicle(3, "GHI7J89", "Toyota", "Corolla")
        ),
        selectedVehicle = listOf(1L, 2L, 3L)
            .map { vehicle(it, "ABC1D23", "Chevrolet", "Camaro") }
            .first { it.id == selectedId },
        currentLatitude = -23.561234,
        currentLongitude = -46.656789,
        isTracking = true,
        isLoading = false,
        phonePermissionGranted = true
    )

    private fun render(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        state: TrackingState = sampleState(),
        onSelectVehicle: (VehicleModel) -> Unit = {}
    ) {
        rule.setContent {
            val base = rule.density
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale)
            ) {
                SecuritasTheme {
                    Box(modifier = Modifier.requiredSize(width, height)) {
                        TrackingContent(
                            state = state,
                            onSelectVehicle = onSelectVehicle,
                            onToggleTracking = {},
                            onToggleBlock = {},
                            onClearCommandMessage = {},
                            onRequestLocationPermission = {},
                            onFocusTargetChange = {}
                        )
                    }
                }
            }
        }
    }

    private fun assertDp(actual: Dp, expected: Dp, label: String, tolerance: Dp = 2.dp) {
        assertTrue(
            "$label: esperado ~$expected, obtido $actual",
            abs(actual.value - expected.value) <= tolerance.value
        )
    }

    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir(null) ?: return
        dir.mkdirs()
        File(dir, name).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // ── Testes ─────────────────────────────────────────────────────────

    /** O seletor do topo lista todos os veículos e devolve a escolha. */
    @Test
    fun selector_lists_all_vehicles_and_reports_selection() {
        var picked: VehicleModel? = null
        render(width = 360.dp, height = 800.dp, onSelectVehicle = { picked = it })

        rule.onNodeWithTag("tracking-vehicle-selector").assertIsDisplayed()
        rule.onNodeWithTag("tracking-vehicle-selector").performClick()

        rule.onNodeWithTag("vehicle-option-1").assertIsDisplayed()
        rule.onNodeWithTag("vehicle-option-2").assertIsDisplayed()
        rule.onNodeWithTag("vehicle-option-3").assertIsDisplayed()

        rule.onNodeWithTag("vehicle-option-3").performClick()

        assertTrue("seleção não chegou ao callback", picked?.id == 3L)
    }

    /**
     * A lista de veículos do rodapé foi removida: o único seletor é o da barra
     * superior, para o mapa não perder espaço em telas pequenas.
     */
    @Test
    fun bottom_vehicle_list_is_gone() {
        render(width = 320.dp, height = 568.dp)

        rule.onNodeWithTag("vehicle-item-1").assertDoesNotExist()
        rule.onNodeWithText("Veículos").assertDoesNotExist()
        rule.onNodeWithTag("tracking-vehicle-selector").assertIsDisplayed()
    }

    /**
     * 320×568dp: com a lista removida o mapa cabe na primeira rolagem e os
     * ícones sobrepostos ficam menores para não cobrir o mapa.
     */
    @Test
    fun compact_screen_shows_shrunk_map_icons() {
        render(width = 320.dp, height = 568.dp)

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("tracking-map"))

        val fullscreen = rule.onNodeWithTag("map-btn-fullscreen").getUnclippedBoundsInRoot()
        assertDp(fullscreen.width, 34.dp, "largura do ícone de tela cheia (mapa estreito)")
        assertDp(fullscreen.height, 34.dp, "altura do ícone de tela cheia (mapa estreito)")
        screenshot("tracking-320x568.png")
    }

    /** Recolher o mapa devolve espaço: o mapa cai de 200dp para 140dp. */
    @Test
    fun collapsing_map_reduces_map_and_hides_legend() {
        render(width = 320.dp, height = 568.dp)

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("tracking-map"))
        assertDp(
            rule.onNodeWithTag("tracking-map").getUnclippedBoundsInRoot().height,
            200.dp,
            "altura do mapa expandido (320dp de largura)"
        )
        rule.onNodeWithText("Celular (você)").assertIsDisplayed()

        val cardBefore = rule.onNodeWithTag("map-card").getUnclippedBoundsInRoot().height

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("map-toggle"))
        rule.onNodeWithTag("map-toggle").performClick()

        assertDp(
            rule.onNodeWithTag("tracking-map").getUnclippedBoundsInRoot().height,
            140.dp,
            "altura do mapa recolhido"
        )
        rule.onNodeWithText("Celular (você)").assertDoesNotExist()

        val cardAfter = rule.onNodeWithTag("map-card").getUnclippedBoundsInRoot().height
        assertTrue(
            "recolher o mapa deveria liberar >= 90dp (antes $cardBefore, depois $cardAfter)",
            (cardBefore.value - cardAfter.value) >= 90f
        )
        screenshot("tracking-collapsed.png")
    }

    /** 411dp de largura: mapa de 240dp e os três números em uma linha só. */
    @Test
    fun medium_width_keeps_map_at_240() {
        render(width = 411.dp, height = 891.dp)

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("tracking-map"))
        assertDp(
            rule.onNodeWithTag("tracking-map").getUnclippedBoundsInRoot().height,
            240.dp,
            "altura do mapa em 411dp"
        )
        screenshot("tracking-411x891.png")
    }

    /**
     * Janela larga/paisagem (800×360): mapa mais alto e botões lado a lado
     * (há largura de sobra), sem o layout quebrar.
     */
    @Test
    fun wide_window_uses_taller_map_and_row_buttons() {
        render(width = 800.dp, height = 360.dp)

        val track = rule.onNodeWithTag("btn-tracking").getUnclippedBoundsInRoot()
        val block = rule.onNodeWithTag("btn-block").getUnclippedBoundsInRoot()
        assertTrue(
            "em 800dp os botões deveriam ficar lado a lado; largura do primeiro = ${track.width}",
            track.width > 300.dp
        )
        assertDp(track.top, block.top, "botões na mesma linha (topo)")

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("tracking-map"))
        assertDp(
            rule.onNodeWithTag("tracking-map").getUnclippedBoundsInRoot().height,
            280.dp,
            "altura do mapa em 800dp"
        )
    }

    /**
     * 320dp + fonte 200%: os dois botões não cabem na mesma linha, então
     * precisam empilhar com largura total em vez de cortar o rótulo.
     */
    @Test
    fun large_font_stacks_action_buttons() {
        render(width = 320.dp, height = 568.dp, fontScale = 2f)

        rule.onNodeWithTag("tracking-list")
            .performScrollToNode(hasTestTag("btn-tracking"))

        val track = rule.onNodeWithTag("btn-tracking").getUnclippedBoundsInRoot()
        val block = rule.onNodeWithTag("btn-block").getUnclippedBoundsInRoot()

        assertTrue(
            "com fonte 2x o botão deveria ocupar a largura toda; largura = ${track.width}",
            track.width > 240.dp
        )
        assertTrue(
            "com fonte 2x os botões deveriam ficar empilhados (topos $track vs $block)",
            block.top > track.top + 20.dp
        )
    }
}
