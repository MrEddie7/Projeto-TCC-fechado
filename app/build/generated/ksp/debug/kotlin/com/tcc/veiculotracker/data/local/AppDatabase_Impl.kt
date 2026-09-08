package com.tcc.veiculotracker.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.tcc.veiculotracker.`data`.local.dao.ApiConfigDao
import com.tcc.veiculotracker.`data`.local.dao.ApiConfigDao_Impl
import com.tcc.veiculotracker.`data`.local.dao.RouteDao
import com.tcc.veiculotracker.`data`.local.dao.RouteDao_Impl
import com.tcc.veiculotracker.`data`.local.dao.UserDao
import com.tcc.veiculotracker.`data`.local.dao.UserDao_Impl
import com.tcc.veiculotracker.`data`.local.dao.VehicleDao
import com.tcc.veiculotracker.`data`.local.dao.VehicleDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _userDao: Lazy<UserDao> = lazy {
    UserDao_Impl(this)
  }

  private val _vehicleDao: Lazy<VehicleDao> = lazy {
    VehicleDao_Impl(this)
  }

  private val _routeDao: Lazy<RouteDao> = lazy {
    RouteDao_Impl(this)
  }

  private val _apiConfigDao: Lazy<ApiConfigDao> = lazy {
    ApiConfigDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2, "22d6d5392d46a0a942d883b3dc35862b", "9f5e48d2e555a99cd05bdbef160c7c51") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `password` TEXT NOT NULL, `phone` TEXT NOT NULL, `firebaseUid` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `plate` TEXT NOT NULL, `model` TEXT NOT NULL, `brand` TEXT NOT NULL, `year` INTEGER NOT NULL, `color` TEXT NOT NULL, `isBlocked` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `lastUpdate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `routes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `startLatitude` REAL NOT NULL, `startLongitude` REAL NOT NULL, `endLatitude` REAL NOT NULL, `endLongitude` REAL NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `distance` REAL NOT NULL, `status` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `route_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `api_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `apiUrl` TEXT NOT NULL, `apiKey` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '22d6d5392d46a0a942d883b3dc35862b')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `users`")
        connection.execSQL("DROP TABLE IF EXISTS `vehicles`")
        connection.execSQL("DROP TABLE IF EXISTS `routes`")
        connection.execSQL("DROP TABLE IF EXISTS `route_points`")
        connection.execSQL("DROP TABLE IF EXISTS `api_configs`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsUsers: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsUsers.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("email", TableInfo.Column("email", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("password", TableInfo.Column("password", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("phone", TableInfo.Column("phone", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("firebaseUid", TableInfo.Column("firebaseUid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUsers.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysUsers: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesUsers: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoUsers: TableInfo = TableInfo("users", _columnsUsers, _foreignKeysUsers, _indicesUsers)
        val _existingUsers: TableInfo = read(connection, "users")
        if (!_infoUsers.equals(_existingUsers)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |users(com.tcc.veiculotracker.data.local.entity.User).
              | Expected:
              |""".trimMargin() + _infoUsers + """
              |
              | Found:
              |""".trimMargin() + _existingUsers)
        }
        val _columnsVehicles: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsVehicles.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("userId", TableInfo.Column("userId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("plate", TableInfo.Column("plate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("model", TableInfo.Column("model", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("brand", TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("year", TableInfo.Column("year", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("color", TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("isBlocked", TableInfo.Column("isBlocked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("longitude", TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("speed", TableInfo.Column("speed", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("lastUpdate", TableInfo.Column("lastUpdate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVehicles.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysVehicles: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesVehicles: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoVehicles: TableInfo = TableInfo("vehicles", _columnsVehicles, _foreignKeysVehicles, _indicesVehicles)
        val _existingVehicles: TableInfo = read(connection, "vehicles")
        if (!_infoVehicles.equals(_existingVehicles)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |vehicles(com.tcc.veiculotracker.data.local.entity.Vehicle).
              | Expected:
              |""".trimMargin() + _infoVehicles + """
              |
              | Found:
              |""".trimMargin() + _existingVehicles)
        }
        val _columnsRoutes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRoutes.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("vehicleId", TableInfo.Column("vehicleId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("userId", TableInfo.Column("userId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("startLatitude", TableInfo.Column("startLatitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("startLongitude", TableInfo.Column("startLongitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("endLatitude", TableInfo.Column("endLatitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("endLongitude", TableInfo.Column("endLongitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("startTime", TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("endTime", TableInfo.Column("endTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("distance", TableInfo.Column("distance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutes.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRoutes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesRoutes: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoRoutes: TableInfo = TableInfo("routes", _columnsRoutes, _foreignKeysRoutes, _indicesRoutes)
        val _existingRoutes: TableInfo = read(connection, "routes")
        if (!_infoRoutes.equals(_existingRoutes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |routes(com.tcc.veiculotracker.data.local.entity.Route).
              | Expected:
              |""".trimMargin() + _infoRoutes + """
              |
              | Found:
              |""".trimMargin() + _existingRoutes)
        }
        val _columnsRoutePoints: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRoutePoints.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutePoints.put("routeId", TableInfo.Column("routeId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutePoints.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutePoints.put("longitude", TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutePoints.put("speed", TableInfo.Column("speed", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRoutePoints.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRoutePoints: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesRoutePoints: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoRoutePoints: TableInfo = TableInfo("route_points", _columnsRoutePoints, _foreignKeysRoutePoints, _indicesRoutePoints)
        val _existingRoutePoints: TableInfo = read(connection, "route_points")
        if (!_infoRoutePoints.equals(_existingRoutePoints)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |route_points(com.tcc.veiculotracker.data.local.entity.RoutePoint).
              | Expected:
              |""".trimMargin() + _infoRoutePoints + """
              |
              | Found:
              |""".trimMargin() + _existingRoutePoints)
        }
        val _columnsApiConfigs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsApiConfigs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsApiConfigs.put("userId", TableInfo.Column("userId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsApiConfigs.put("apiUrl", TableInfo.Column("apiUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsApiConfigs.put("apiKey", TableInfo.Column("apiKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsApiConfigs.put("isActive", TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsApiConfigs.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysApiConfigs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesApiConfigs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoApiConfigs: TableInfo = TableInfo("api_configs", _columnsApiConfigs, _foreignKeysApiConfigs, _indicesApiConfigs)
        val _existingApiConfigs: TableInfo = read(connection, "api_configs")
        if (!_infoApiConfigs.equals(_existingApiConfigs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |api_configs(com.tcc.veiculotracker.data.local.entity.ApiConfig).
              | Expected:
              |""".trimMargin() + _infoApiConfigs + """
              |
              | Found:
              |""".trimMargin() + _existingApiConfigs)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "users", "vehicles", "routes", "route_points", "api_configs")
  }

  public override fun clearAllTables() {
    super.performClear(false, "users", "vehicles", "routes", "route_points", "api_configs")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(UserDao::class, UserDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(VehicleDao::class, VehicleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(RouteDao::class, RouteDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ApiConfigDao::class, ApiConfigDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun userDao(): UserDao = _userDao.value

  public override fun vehicleDao(): VehicleDao = _vehicleDao.value

  public override fun routeDao(): RouteDao = _routeDao.value

  public override fun apiConfigDao(): ApiConfigDao = _apiConfigDao.value
}
