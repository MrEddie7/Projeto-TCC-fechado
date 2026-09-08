package com.tcc.veiculotracker.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tcc.veiculotracker.`data`.local.entity.Route
import com.tcc.veiculotracker.`data`.local.entity.RoutePoint
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RouteDao_Impl(
  __db: RoomDatabase,
) : RouteDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfRoute: EntityInsertAdapter<Route>

  private val __insertAdapterOfRoutePoint: EntityInsertAdapter<RoutePoint>

  private val __deleteAdapterOfRoute: EntityDeleteOrUpdateAdapter<Route>

  private val __updateAdapterOfRoute: EntityDeleteOrUpdateAdapter<Route>
  init {
    this.__db = __db
    this.__insertAdapterOfRoute = object : EntityInsertAdapter<Route>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `routes` (`id`,`vehicleId`,`userId`,`startLatitude`,`startLongitude`,`endLatitude`,`endLongitude`,`startTime`,`endTime`,`distance`,`status`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Route) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.vehicleId)
        statement.bindLong(3, entity.userId)
        statement.bindDouble(4, entity.startLatitude)
        statement.bindDouble(5, entity.startLongitude)
        statement.bindDouble(6, entity.endLatitude)
        statement.bindDouble(7, entity.endLongitude)
        statement.bindLong(8, entity.startTime)
        statement.bindLong(9, entity.endTime)
        statement.bindDouble(10, entity.distance)
        statement.bindText(11, entity.status)
      }
    }
    this.__insertAdapterOfRoutePoint = object : EntityInsertAdapter<RoutePoint>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `route_points` (`id`,`routeId`,`latitude`,`longitude`,`speed`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: RoutePoint) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.routeId)
        statement.bindDouble(3, entity.latitude)
        statement.bindDouble(4, entity.longitude)
        statement.bindDouble(5, entity.speed)
        statement.bindLong(6, entity.timestamp)
      }
    }
    this.__deleteAdapterOfRoute = object : EntityDeleteOrUpdateAdapter<Route>() {
      protected override fun createQuery(): String = "DELETE FROM `routes` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Route) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfRoute = object : EntityDeleteOrUpdateAdapter<Route>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `routes` SET `id` = ?,`vehicleId` = ?,`userId` = ?,`startLatitude` = ?,`startLongitude` = ?,`endLatitude` = ?,`endLongitude` = ?,`startTime` = ?,`endTime` = ?,`distance` = ?,`status` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Route) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.vehicleId)
        statement.bindLong(3, entity.userId)
        statement.bindDouble(4, entity.startLatitude)
        statement.bindDouble(5, entity.startLongitude)
        statement.bindDouble(6, entity.endLatitude)
        statement.bindDouble(7, entity.endLongitude)
        statement.bindLong(8, entity.startTime)
        statement.bindLong(9, entity.endTime)
        statement.bindDouble(10, entity.distance)
        statement.bindText(11, entity.status)
        statement.bindLong(12, entity.id)
      }
    }
  }

  public override suspend fun insertRoute(route: Route): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfRoute.insertAndReturnId(_connection, route)
    _result
  }

  public override suspend fun insertRoutePoint(point: RoutePoint): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfRoutePoint.insertAndReturnId(_connection, point)
    _result
  }

  public override suspend fun deleteRoute(route: Route): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfRoute.handle(_connection, route)
  }

  public override suspend fun updateRoute(route: Route): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfRoute.handle(_connection, route)
  }

  public override fun getRoutesByUser(userId: Long): Flow<List<Route>> {
    val _sql: String = "SELECT * FROM routes WHERE userId = ? ORDER BY startTime DESC"
    return createFlow(__db, false, arrayOf("routes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfVehicleId: Int = getColumnIndexOrThrow(_stmt, "vehicleId")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfStartLatitude: Int = getColumnIndexOrThrow(_stmt, "startLatitude")
        val _columnIndexOfStartLongitude: Int = getColumnIndexOrThrow(_stmt, "startLongitude")
        val _columnIndexOfEndLatitude: Int = getColumnIndexOrThrow(_stmt, "endLatitude")
        val _columnIndexOfEndLongitude: Int = getColumnIndexOrThrow(_stmt, "endLongitude")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _columnIndexOfDistance: Int = getColumnIndexOrThrow(_stmt, "distance")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: MutableList<Route> = mutableListOf()
        while (_stmt.step()) {
          val _item: Route
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpVehicleId: Long
          _tmpVehicleId = _stmt.getLong(_columnIndexOfVehicleId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpStartLatitude: Double
          _tmpStartLatitude = _stmt.getDouble(_columnIndexOfStartLatitude)
          val _tmpStartLongitude: Double
          _tmpStartLongitude = _stmt.getDouble(_columnIndexOfStartLongitude)
          val _tmpEndLatitude: Double
          _tmpEndLatitude = _stmt.getDouble(_columnIndexOfEndLatitude)
          val _tmpEndLongitude: Double
          _tmpEndLongitude = _stmt.getDouble(_columnIndexOfEndLongitude)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          val _tmpDistance: Double
          _tmpDistance = _stmt.getDouble(_columnIndexOfDistance)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _item = Route(_tmpId,_tmpVehicleId,_tmpUserId,_tmpStartLatitude,_tmpStartLongitude,_tmpEndLatitude,_tmpEndLongitude,_tmpStartTime,_tmpEndTime,_tmpDistance,_tmpStatus)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRoutesByVehicle(vehicleId: Long): Flow<List<Route>> {
    val _sql: String = "SELECT * FROM routes WHERE vehicleId = ? ORDER BY startTime DESC"
    return createFlow(__db, false, arrayOf("routes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, vehicleId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfVehicleId: Int = getColumnIndexOrThrow(_stmt, "vehicleId")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfStartLatitude: Int = getColumnIndexOrThrow(_stmt, "startLatitude")
        val _columnIndexOfStartLongitude: Int = getColumnIndexOrThrow(_stmt, "startLongitude")
        val _columnIndexOfEndLatitude: Int = getColumnIndexOrThrow(_stmt, "endLatitude")
        val _columnIndexOfEndLongitude: Int = getColumnIndexOrThrow(_stmt, "endLongitude")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _columnIndexOfDistance: Int = getColumnIndexOrThrow(_stmt, "distance")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: MutableList<Route> = mutableListOf()
        while (_stmt.step()) {
          val _item: Route
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpVehicleId: Long
          _tmpVehicleId = _stmt.getLong(_columnIndexOfVehicleId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpStartLatitude: Double
          _tmpStartLatitude = _stmt.getDouble(_columnIndexOfStartLatitude)
          val _tmpStartLongitude: Double
          _tmpStartLongitude = _stmt.getDouble(_columnIndexOfStartLongitude)
          val _tmpEndLatitude: Double
          _tmpEndLatitude = _stmt.getDouble(_columnIndexOfEndLatitude)
          val _tmpEndLongitude: Double
          _tmpEndLongitude = _stmt.getDouble(_columnIndexOfEndLongitude)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          val _tmpDistance: Double
          _tmpDistance = _stmt.getDouble(_columnIndexOfDistance)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _item = Route(_tmpId,_tmpVehicleId,_tmpUserId,_tmpStartLatitude,_tmpStartLongitude,_tmpEndLatitude,_tmpEndLongitude,_tmpStartTime,_tmpEndTime,_tmpDistance,_tmpStatus)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRouteById(routeId: Long): Flow<Route?> {
    val _sql: String = "SELECT * FROM routes WHERE id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("routes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfVehicleId: Int = getColumnIndexOrThrow(_stmt, "vehicleId")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfStartLatitude: Int = getColumnIndexOrThrow(_stmt, "startLatitude")
        val _columnIndexOfStartLongitude: Int = getColumnIndexOrThrow(_stmt, "startLongitude")
        val _columnIndexOfEndLatitude: Int = getColumnIndexOrThrow(_stmt, "endLatitude")
        val _columnIndexOfEndLongitude: Int = getColumnIndexOrThrow(_stmt, "endLongitude")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _columnIndexOfDistance: Int = getColumnIndexOrThrow(_stmt, "distance")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: Route?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpVehicleId: Long
          _tmpVehicleId = _stmt.getLong(_columnIndexOfVehicleId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpStartLatitude: Double
          _tmpStartLatitude = _stmt.getDouble(_columnIndexOfStartLatitude)
          val _tmpStartLongitude: Double
          _tmpStartLongitude = _stmt.getDouble(_columnIndexOfStartLongitude)
          val _tmpEndLatitude: Double
          _tmpEndLatitude = _stmt.getDouble(_columnIndexOfEndLatitude)
          val _tmpEndLongitude: Double
          _tmpEndLongitude = _stmt.getDouble(_columnIndexOfEndLongitude)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          val _tmpDistance: Double
          _tmpDistance = _stmt.getDouble(_columnIndexOfDistance)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _result = Route(_tmpId,_tmpVehicleId,_tmpUserId,_tmpStartLatitude,_tmpStartLongitude,_tmpEndLatitude,_tmpEndLongitude,_tmpStartTime,_tmpEndTime,_tmpDistance,_tmpStatus)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRoutePoints(routeId: Long): Flow<List<RoutePoint>> {
    val _sql: String = "SELECT * FROM route_points WHERE routeId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("route_points")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<RoutePoint> = mutableListOf()
        while (_stmt.step()) {
          val _item: RoutePoint
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpRouteId: Long
          _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpSpeed: Double
          _tmpSpeed = _stmt.getDouble(_columnIndexOfSpeed)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = RoutePoint(_tmpId,_tmpRouteId,_tmpLatitude,_tmpLongitude,_tmpSpeed,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteRoutePoints(routeId: Long) {
    val _sql: String = "DELETE FROM route_points WHERE routeId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteRouteById(routeId: Long) {
    val _sql: String = "DELETE FROM routes WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
