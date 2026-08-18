package com.tcc.veiculotracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tcc.veiculotracker.data.local.dao.*
import com.tcc.veiculotracker.data.local.entity.*

@Database(
    entities = [User::class, Vehicle::class, Route::class, RoutePoint::class, ApiConfig::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun routeDao(): RouteDao
    abstract fun apiConfigDao(): ApiConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "veiculotracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
