package com.tcc.veiculotracker

import android.app.Application
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.remote.FirebaseDataSource
import com.tcc.veiculotracker.data.repository.ApiConfigRepository
import com.tcc.veiculotracker.data.repository.AuthRepository
import com.tcc.veiculotracker.data.repository.RouteRepository
import com.tcc.veiculotracker.data.repository.VehicleRepository
import com.tcc.veiculotracker.data.sync.SyncManager
import org.osmdroid.config.Configuration

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val firebaseDataSource: FirebaseDataSource by lazy { FirebaseDataSource() }

    val syncManager: SyncManager by lazy { SyncManager(firebaseDataSource, database) }

    val authRepository: AuthRepository by lazy { AuthRepository(database.userDao(), syncManager) }

    val vehicleRepository: VehicleRepository by lazy {
        VehicleRepository(database.vehicleDao(), syncManager)
    }

    val routeRepository: RouteRepository by lazy { RouteRepository(database.routeDao(), syncManager) }

    val apiConfigRepository: ApiConfigRepository by lazy { ApiConfigRepository(database.apiConfigDao()) }

    override fun onCreate() {
        super.onCreate()
        // Obrigatório no osmdroid, antes de qualquer MapView ser criado.
        Configuration.getInstance().userAgentValue = packageName
    }
}
