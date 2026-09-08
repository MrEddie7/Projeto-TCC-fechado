package com.tcc.veiculotracker.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tcc.veiculotracker.`data`.local.entity.Vehicle
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class VehicleDao_Impl(
  __db: RoomDatabase,
) : VehicleDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfVehicle: EntityInsertAdapter<Vehicle>

  private val __deleteAdapterOfVehicle: EntityDeleteOrUpdateAdapter<Vehicle>

  private val __updateAdapterOfVehicle: EntityDeleteOrUpdateAdapter<Vehicle>
  init {
    this.__db = __db
    this.__insertAdapterOfVehicle = object : EntityInsertAdapter<Vehicle>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `vehicles` (`id`,`userId`,`plate`,`model`,`brand`,`year`,`color`,`isBlocked`,`latitude`,`longitude`,`speed`,`lastUpdate`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Vehicle) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.userId)
        statement.bindText(3, entity.plate)
        statement.bindText(4, entity.model)
        statement.bindText(5, entity.brand)
        statement.bindLong(6, entity.year.toLong())
        statement.bindText(7, entity.color)
        val _tmp: Int = if (entity.isBlocked) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindDouble(9, entity.latitude)
        statement.bindDouble(10, entity.longitude)
        statement.bindDouble(11, entity.speed)
        statement.bindLong(12, entity.lastUpdate)
        statement.bindLong(13, entity.createdAt)
      }
    }
    this.__deleteAdapterOfVehicle = object : EntityDeleteOrUpdateAdapter<Vehicle>() {
      protected override fun createQuery(): String = "DELETE FROM `vehicles` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Vehicle) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfVehicle = object : EntityDeleteOrUpdateAdapter<Vehicle>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `vehicles` SET `id` = ?,`userId` = ?,`plate` = ?,`model` = ?,`brand` = ?,`year` = ?,`color` = ?,`isBlocked` = ?,`latitude` = ?,`longitude` = ?,`speed` = ?,`lastUpdate` = ?,`createdAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Vehicle) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.userId)
        statement.bindText(3, entity.plate)
        statement.bindText(4, entity.model)
        statement.bindText(5, entity.brand)
        statement.bindLong(6, entity.year.toLong())
        statement.bindText(7, entity.color)
        val _tmp: Int = if (entity.isBlocked) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindDouble(9, entity.latitude)
        statement.bindDouble(10, entity.longitude)
        statement.bindDouble(11, entity.speed)
        statement.bindLong(12, entity.lastUpdate)
        statement.bindLong(13, entity.createdAt)
        statement.bindLong(14, entity.id)
      }
    }
  }

  public override suspend fun insert(vehicle: Vehicle): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfVehicle.insertAndReturnId(_connection, vehicle)
    _result
  }

  public override suspend fun delete(vehicle: Vehicle): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfVehicle.handle(_connection, vehicle)
  }

  public override suspend fun update(vehicle: Vehicle): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfVehicle.handle(_connection, vehicle)
  }

  public override fun getVehiclesByUser(userId: Long): Flow<List<Vehicle>> {
    val _sql: String = "SELECT * FROM vehicles WHERE userId = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("vehicles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfPlate: Int = getColumnIndexOrThrow(_stmt, "plate")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfBrand: Int = getColumnIndexOrThrow(_stmt, "brand")
        val _columnIndexOfYear: Int = getColumnIndexOrThrow(_stmt, "year")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfLastUpdate: Int = getColumnIndexOrThrow(_stmt, "lastUpdate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Vehicle> = mutableListOf()
        while (_stmt.step()) {
          val _item: Vehicle
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpPlate: String
          _tmpPlate = _stmt.getText(_columnIndexOfPlate)
          val _tmpModel: String
          _tmpModel = _stmt.getText(_columnIndexOfModel)
          val _tmpBrand: String
          _tmpBrand = _stmt.getText(_columnIndexOfBrand)
          val _tmpYear: Int
          _tmpYear = _stmt.getLong(_columnIndexOfYear).toInt()
          val _tmpColor: String
          _tmpColor = _stmt.getText(_columnIndexOfColor)
          val _tmpIsBlocked: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp != 0
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpSpeed: Double
          _tmpSpeed = _stmt.getDouble(_columnIndexOfSpeed)
          val _tmpLastUpdate: Long
          _tmpLastUpdate = _stmt.getLong(_columnIndexOfLastUpdate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Vehicle(_tmpId,_tmpUserId,_tmpPlate,_tmpModel,_tmpBrand,_tmpYear,_tmpColor,_tmpIsBlocked,_tmpLatitude,_tmpLongitude,_tmpSpeed,_tmpLastUpdate,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getVehicleById(vehicleId: Long): Flow<Vehicle?> {
    val _sql: String = "SELECT * FROM vehicles WHERE id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("vehicles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, vehicleId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfPlate: Int = getColumnIndexOrThrow(_stmt, "plate")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfBrand: Int = getColumnIndexOrThrow(_stmt, "brand")
        val _columnIndexOfYear: Int = getColumnIndexOrThrow(_stmt, "year")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfLastUpdate: Int = getColumnIndexOrThrow(_stmt, "lastUpdate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: Vehicle?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpPlate: String
          _tmpPlate = _stmt.getText(_columnIndexOfPlate)
          val _tmpModel: String
          _tmpModel = _stmt.getText(_columnIndexOfModel)
          val _tmpBrand: String
          _tmpBrand = _stmt.getText(_columnIndexOfBrand)
          val _tmpYear: Int
          _tmpYear = _stmt.getLong(_columnIndexOfYear).toInt()
          val _tmpColor: String
          _tmpColor = _stmt.getText(_columnIndexOfColor)
          val _tmpIsBlocked: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp != 0
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpSpeed: Double
          _tmpSpeed = _stmt.getDouble(_columnIndexOfSpeed)
          val _tmpLastUpdate: Long
          _tmpLastUpdate = _stmt.getLong(_columnIndexOfLastUpdate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = Vehicle(_tmpId,_tmpUserId,_tmpPlate,_tmpModel,_tmpBrand,_tmpYear,_tmpColor,_tmpIsBlocked,_tmpLatitude,_tmpLongitude,_tmpSpeed,_tmpLastUpdate,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getVehiclesListByUser(userId: Long): List<Vehicle> {
    val _sql: String = "SELECT * FROM vehicles WHERE userId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfPlate: Int = getColumnIndexOrThrow(_stmt, "plate")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfBrand: Int = getColumnIndexOrThrow(_stmt, "brand")
        val _columnIndexOfYear: Int = getColumnIndexOrThrow(_stmt, "year")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfLastUpdate: Int = getColumnIndexOrThrow(_stmt, "lastUpdate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Vehicle> = mutableListOf()
        while (_stmt.step()) {
          val _item: Vehicle
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpPlate: String
          _tmpPlate = _stmt.getText(_columnIndexOfPlate)
          val _tmpModel: String
          _tmpModel = _stmt.getText(_columnIndexOfModel)
          val _tmpBrand: String
          _tmpBrand = _stmt.getText(_columnIndexOfBrand)
          val _tmpYear: Int
          _tmpYear = _stmt.getLong(_columnIndexOfYear).toInt()
          val _tmpColor: String
          _tmpColor = _stmt.getText(_columnIndexOfColor)
          val _tmpIsBlocked: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp != 0
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpSpeed: Double
          _tmpSpeed = _stmt.getDouble(_columnIndexOfSpeed)
          val _tmpLastUpdate: Long
          _tmpLastUpdate = _stmt.getLong(_columnIndexOfLastUpdate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Vehicle(_tmpId,_tmpUserId,_tmpPlate,_tmpModel,_tmpBrand,_tmpYear,_tmpColor,_tmpIsBlocked,_tmpLatitude,_tmpLongitude,_tmpSpeed,_tmpLastUpdate,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setBlocked(vehicleId: Long, blocked: Boolean) {
    val _sql: String = "UPDATE vehicles SET isBlocked = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (blocked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, vehicleId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateLocation(
    vehicleId: Long,
    lat: Double,
    lng: Double,
    speed: Double,
    timestamp: Long,
  ) {
    val _sql: String = "UPDATE vehicles SET latitude = ?, longitude = ?, speed = ?, lastUpdate = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, lat)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, lng)
        _argIndex = 3
        _stmt.bindDouble(_argIndex, speed)
        _argIndex = 4
        _stmt.bindLong(_argIndex, timestamp)
        _argIndex = 5
        _stmt.bindLong(_argIndex, vehicleId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(vehicleId: Long) {
    val _sql: String = "DELETE FROM vehicles WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, vehicleId)
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
