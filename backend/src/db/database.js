import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';
import { config } from '../config.js';

fs.mkdirSync(path.dirname(config.dbPath), { recursive: true });

export const db = new DatabaseSync(config.dbPath);

db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA foreign_keys = ON;');

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL,
  email         TEXT NOT NULL UNIQUE,
  phone         TEXT DEFAULT '',
  password_hash TEXT NOT NULL,
  created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS tokens (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token      TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS vehicles (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plate       TEXT NOT NULL,
  model       TEXT NOT NULL,
  brand       TEXT NOT NULL,
  year        INTEGER NOT NULL,
  color       TEXT DEFAULT '',
  is_blocked  INTEGER NOT NULL DEFAULT 0,
  latitude    REAL NOT NULL DEFAULT 0,
  longitude   REAL NOT NULL DEFAULT 0,
  speed       REAL NOT NULL DEFAULT 0,
  last_update INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id   TEXT NOT NULL UNIQUE,
  api_key     TEXT NOT NULL UNIQUE,
  vehicle_id  INTEGER REFERENCES vehicles(id) ON DELETE SET NULL,
  firmware    TEXT DEFAULT '',
  model       TEXT DEFAULT '',
  last_seen   INTEGER DEFAULT 0,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS telemetry (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  vehicle_id INTEGER REFERENCES vehicles(id) ON DELETE CASCADE,
  device_id  TEXT,
  latitude   REAL NOT NULL,
  longitude  REAL NOT NULL,
  speed      REAL NOT NULL DEFAULT 0,
  heading    REAL NOT NULL DEFAULT 0,
  battery    REAL DEFAULT NULL,
  timestamp  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS routes (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  vehicle_id     INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
  user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  start_latitude REAL DEFAULT 0,
  start_longitude REAL DEFAULT 0,
  end_latitude   REAL DEFAULT 0,
  end_longitude  REAL DEFAULT 0,
  start_time     INTEGER NOT NULL,
  end_time       INTEGER DEFAULT 0,
  distance       REAL DEFAULT 0,
  max_speed      REAL DEFAULT 0,
  status         TEXT NOT NULL DEFAULT 'em_andamento'
);

CREATE TABLE IF NOT EXISTS route_points (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  route_id   INTEGER NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
  latitude   REAL NOT NULL,
  longitude  REAL NOT NULL,
  speed      REAL DEFAULT 0,
  heading    REAL DEFAULT 0,
  timestamp  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS commands (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  vehicle_id      INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
  command         TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'pending',
  note            TEXT DEFAULT '',
  created_at      INTEGER NOT NULL,
  acknowledged_at INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_telemetry_vehicle_ts ON telemetry(vehicle_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_route_points_route ON route_points(route_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_commands_vehicle_status ON commands(vehicle_id, status);

CREATE TABLE IF NOT EXISTS sync_state (
  key   TEXT PRIMARY KEY,
  value INTEGER NOT NULL
);
`);

// Migração: garante a coluna updated_at (usada pela sincronização Firebase).
function ensureColumn(table, column, ddl) {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all();
  if (!cols.some((c) => c.name === column)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${ddl};`);
  }
}
ensureColumn('users', 'updated_at', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('vehicles', 'updated_at', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('routes', 'updated_at', 'INTEGER NOT NULL DEFAULT 0');