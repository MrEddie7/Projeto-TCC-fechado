package com.tcc.veiculotracker.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Dashboard : Screen("dashboard")
    data object VehicleRegister : Screen("vehicle_register")
    data object Tracking : Screen("tracking")
    data object RemoteControl : Screen("remote_control")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object ApiLink : Screen("api_link")
    data object VehicleDetail : Screen("vehicle_detail/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "vehicle_detail/$vehicleId"
    }
}
