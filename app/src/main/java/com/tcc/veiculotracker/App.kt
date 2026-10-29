package com.tcc.veiculotracker

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.tcc.veiculotracker.data.remote.FirebaseDataSource
import com.tcc.veiculotracker.data.remote.LocationService
import com.tcc.veiculotracker.data.repository.ApiConfigRepository
import com.tcc.veiculotracker.data.repository.AuthRepository
import com.tcc.veiculotracker.data.repository.BackendRepository
import com.tcc.veiculotracker.data.repository.RouteRepository
import com.tcc.veiculotracker.data.repository.VehicleRepository
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class App : Application() {

    // Escopo de vida longa (não é cancelado enquanto o app existir).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Sessão restaurada (Firebase Auth): o usuário já está logado, mas o
        // backend ainda não conhece este aparelho. Registra uma vez no boot,
        // sem bloquear a inicialização da UI.
        if (FirebaseAuth.getInstance().currentUser != null) {
            appScope.launch {
                authRepository.ensureDeviceRegistered()
                // Re-registra o token de push (novos aparelhos/reinstalação).
                com.tcc.veiculotracker.data.remote.PushTokenManager.registerCurrentToken(this@App)
            }
        }
    }
}
