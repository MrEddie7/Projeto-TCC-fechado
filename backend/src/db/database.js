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
  simulated   INTEGER NOT NULL DEFAULT 0,
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
  simulated   INTEGER NOT NULL DEFAULT 0,
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

CREATE TABLE IF NOT EXISTS mobile_locations (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  latitude   REAL NOT NULL,
  longitude  REAL NOT NULL,
  speed      REAL NOT NULL DEFAULT 0,
  heading    REAL NOT NULL DEFAULT 0,
  accuracy   REAL DEFAULT 0,
  timestamp  INTEGER NOT NULL,
  source     TEXT DEFAULT 'mobile_app',
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mobile_locations_user_ts ON mobile_locations(user_id, timestamp);
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
ensureColumn('vehicles', 'simulated', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('devices', 'simulated', 'INTEGER NOT NULL DEFAULT 0');

// Retrocompatibilidade: marca como "simulador" a frota criada pelo seed do
// projeto (única origem de devices para o hardware-simulator até aqui).
// Roda uma única vez e fica registrada em sync_state; os dados criados depois
// (via app/API) nascem com simulated=0, ou seja, "teste real".
function markSeedFleetSimulatedOnce() {
  const done = db.prepare("SELECT value FROM sync_state WHERE key = 'backfill_simulated_v1'").get();
  if (done) return;
  const demoEmail = process.env.SEED_EMAIL || 'aluno@tcc.com';
  const demoUser = db.prepare('SELECT id FROM users WHERE email = ?').get(demoEmail);
  if (demoUser) {
    db.prepare(
      `UPDATE vehicles SET simulated = 1
       WHERE simulated = 0
         AND user_id = ?
         AND id IN (SELECT vehicle_id FROM devices WHERE vehicle_id IS NOT NULL)`
    ).run(demoUser.id);
    db.prepare(
      `UPDATE devices SET simulated = 1
       WHERE simulated = 0
         AND vehicle_id IN (SELECT id FROM vehicles WHERE simulated = 1)`
    ).run();
  }
  db.prepare(
    "INSERT OR IGNORE INTO sync_state (key, value) VALUES ('backfill_simulated_v1', 1)"
  ).run();
}
markSeedFleetSimulatedOnce();