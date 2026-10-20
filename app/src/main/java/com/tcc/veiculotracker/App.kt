package com.tcc.veiculotracker

import android.app.Application
import com.tcc.veiculotracker.data.remote.FirebaseDataSource
import com.tcc.veiculotracker.data.remote.LocationService
import com.tcc.veiculotracker.data.repository.ApiConfigRepository
import com.tcc.veiculotracker.data.repository.AuthRepository
import com.tcc.veiculotracker.data.repository.BackendRepository
import com.tcc.veiculotracker.data.repository.RouteRepository
import com.tcc.veiculotracker.data.repository.VehicleRepository
import com.tcc.veiculotracker.data.sync.SyncManager
import org.osmdroid.config.Configuration

class App : Application() {

    val firebaseDataSource: FirebaseDataSource by lazy { FirebaseDataSource() }

    val backendRepository: BackendRepository by lazy { BackendRepository(this@App) }

    val syncManager: SyncManager by lazy { SyncManager(firebaseDataSource, this@App, backendRepository) }

    val authRepository: AuthRepository by lazy { AuthRepository(this@App, syncManager) }

    val vehicleRepository: VehicleRepository by lazy { VehicleRepository(syncManager) }

    val routeRepository: RouteRepository by lazy { RouteRepository(syncManager) }

    // ApiConfigRepository: mantém a URL/token do backend em SharedPreferences.
    // Não usa mais Room — o ApiConfigDao foi removido; a configuração fica
    // apenas nas prefs (KEY_API_URL / KEY_API_TOKEN), que o ApiClient já lê.
    val apiConfigRepository: ApiConfigRepository by lazy { ApiConfigRepository(this@App) }

    val locationService: LocationService by lazy { LocationService(this@App) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
