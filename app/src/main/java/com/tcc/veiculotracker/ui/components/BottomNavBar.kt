package com.tcc.veiculotracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : BottomNavItem("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Tracking : BottomNavItem("tracking", "Rastrear", Icons.Default.LocationOn)
    data object History : BottomNavItem("history", "Histórico", Icons.Default.History)
    data object Settings : BottomNavItem("settings", "Configurações", Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Tracking,
        BottomNavItem.History,
        BottomNavItem.Settings
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(imageVector = item.icon, contentDescription = item.label)
                },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}
