package com.tcc.veiculotracker.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tcc.veiculotracker.`data`.local.entity.ApiConfig
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class ApiConfigDao_Impl(
  __db: RoomDatabase,
) : ApiConfigDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfApiConfig: EntityInsertAdapter<ApiConfig>

  private val __deleteAdapterOfApiConfig: EntityDeleteOrUpdateAdapter<ApiConfig>

  private val __updateAdapterOfApiConfig: EntityDeleteOrUpdateAdapter<ApiConfig>
  init {
    this.__db = __db
    this.__insertAdapterOfApiConfig = object : EntityInsertAdapter<ApiConfig>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `api_configs` (`id`,`userId`,`apiUrl`,`apiKey`,`isActive`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ApiConfig) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.userId)
        statement.bindText(3, entity.apiUrl)
        statement.bindText(4, entity.apiKey)
        val _tmp: Int = if (entity.isActive) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.createdAt)
      }
    }
    this.__deleteAdapterOfApiConfig = object : EntityDeleteOrUpdateAdapter<ApiConfig>() {
      protected override fun createQuery(): String = "DELETE FROM `api_configs` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ApiConfig) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfApiConfig = object : EntityDeleteOrUpdateAdapter<ApiConfig>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `api_configs` SET `id` = ?,`userId` = ?,`apiUrl` = ?,`apiKey` = ?,`isActive` = ?,`createdAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ApiConfig) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.userId)
        statement.bindText(3, entity.apiUrl)
        statement.bindText(4, entity.apiKey)
        val _tmp: Int = if (entity.isActive) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.createdAt)
        statement.bindLong(7, entity.id)
      }
    }
  }

  public override suspend fun insert(config: ApiConfig): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfApiConfig.insertAndReturnId(_connection, config)
    _result
  }

  public override suspend fun delete(config: ApiConfig): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfApiConfig.handle(_connection, config)
  }

  public override suspend fun update(config: ApiConfig): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfApiConfig.handle(_connection, config)
  }

  public override fun getConfigsByUser(userId: Long): Flow<List<ApiConfig>> {
    val _sql: String = "SELECT * FROM api_configs WHERE userId = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("api_configs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfApiUrl: Int = getColumnIndexOrThrow(_stmt, "apiUrl")
        val _columnIndexOfApiKey: Int = getColumnIndexOrThrow(_stmt, "apiKey")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "isActive")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<ApiConfig> = mutableListOf()
        while (_stmt.step()) {
          val _item: ApiConfig
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpApiUrl: String
          _tmpApiUrl = _stmt.getText(_columnIndexOfApiUrl)
          val _tmpApiKey: String
          _tmpApiKey = _stmt.getText(_columnIndexOfApiKey)
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = ApiConfig(_tmpId,_tmpUserId,_tmpApiUrl,_tmpApiKey,_tmpIsActive,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getActiveConfig(userId: Long): Flow<ApiConfig?> {
    val _sql: String = "SELECT * FROM api_configs WHERE userId = ? AND isActive = 1 LIMIT 1"
    return createFlow(__db, false, arrayOf("api_configs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfApiUrl: Int = getColumnIndexOrThrow(_stmt, "apiUrl")
        val _columnIndexOfApiKey: Int = getColumnIndexOrThrow(_stmt, "apiKey")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "isActive")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: ApiConfig?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserId: Long
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId)
          val _tmpApiUrl: String
          _tmpApiUrl = _stmt.getText(_columnIndexOfApiUrl)
          val _tmpApiKey: String
          _tmpApiKey = _stmt.getText(_columnIndexOfApiKey)
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = ApiConfig(_tmpId,_tmpUserId,_tmpApiUrl,_tmpApiKey,_tmpIsActive,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deactivateAll(userId: Long) {
    val _sql: String = "UPDATE api_configs SET isActive = 0 WHERE userId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, userId)
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
