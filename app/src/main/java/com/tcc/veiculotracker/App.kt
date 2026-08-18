package com.tcc.veiculotracker

import android.app.Application
import com.tcc.veiculotracker.data.local.AppDatabase

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
