package com.tcc.veiculotracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tcc.veiculotracker.ui.components.BottomNavBar
import com.tcc.veiculotracker.ui.screens.apilink.ApiLinkScreen
import com.tcc.veiculotracker.ui.screens.dashboard.DashboardScreen
import com.tcc.veiculotracker.ui.screens.history.RouteHistoryScreen
import com.tcc.veiculotracker.ui.screens.login.LoginScreen
import com.tcc.veiculotracker.ui.screens.register.RegisterScreen
import com.tcc.veiculotracker.ui.screens.remote.RemoteControlScreen
import com.tcc.veiculotracker.ui.screens.settings.SettingsScreen
import com.tcc.veiculotracker.ui.screens.tracking.TrackingScreen
import com.tcc.veiculotracker.ui.screens.vehicle.VehicleRegisterScreen

@Composable
fun NavGraph(startDestination: String = Screen.Login.route) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = listOf(
        Screen.Dashboard.route,
        Screen.Tracking.route,
        Screen.History.route,
        Screen.Settings.route
    )

    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToVehicleRegister = {
                        navController.navigate(Screen.VehicleRegister.route)
                    },
                    onNavigateToRemoteControl = {
                        navController.navigate(Screen.RemoteControl.route)
                    },
                    onNavigateToApiLink = {
                        navController.navigate(Screen.ApiLink.route)
                    },
                    onNavigateToVehicleDetail = { vehicleId ->
                        navController.navigate(Screen.VehicleDetail.createRoute(vehicleId))
                    }
                )
            }

            composable(Screen.VehicleRegister.route) {
                VehicleRegisterScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Tracking.route) {
                TrackingScreen()
            }

            composable(Screen.RemoteControl.route) {
                RemoteControlScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.History.route) {
                RouteHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ApiLink.route) {
                ApiLinkScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
