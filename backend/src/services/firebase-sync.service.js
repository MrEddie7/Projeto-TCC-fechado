import { db } from '../db/database.js';
import { config } from '../config.js';
import { getFirebase } from '../firebase/firebase-admin.js';

const STATE_KEYS = ['users', 'vehicles', 'routes', 'last_run'];

function getLastSync(key) {
  const row = db.prepare('SELECT value FROM sync_state WHERE key = ?').get(key);
  // -1 garante que linhas antigas (updated_at = 0) sejam sincronizadas na 1ª rodada.
  return row ? Number(row.value) : -1;
}

function setLastSync(key, value) {
  db.prepare(
    'INSERT INTO sync_state (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value'
  ).run(key, value);
}

// ── Firestore: users / vehicles / routes ────────────────────────────────

async function syncUsers(firestore, since, cutoff) {
  const rows = db
    .prepare('SELECT * FROM users WHERE updated_at > ? AND updated_at <= ?')
    .all(since, cutoff);
  if (rows.length === 0) return 0;
  for (const u of rows) {
    await firestore
      .collection('users')
      .doc(String(u.id))
      .set({
        name: u.name,
        email: u.email,
        phone: u.phone,
        createdAt: Number(u.created_at),
      });
  }
  return rows.length;
}

async function syncVehicles(firestore, since, cutoff) {
  const rows = db
    .prepare('SELECT * FROM vehicles WHERE updated_at > ? AND updated_at <= ?')
    .all(since, cutoff);
  for (const v of rows) {
    await firestore
      .collection('vehicles')
      .doc(String(v.id))
      .set({
        id: Number(v.id),
        userId: Number(v.user_id),
        plate: v.plate,
        model: v.model,
        brand: v.brand,
        year: Number(v.year),
        color: v.color,
        isBlocked: v.is_blocked === 1,
        latitude: Number(v.latitude),
        longitude: Number(v.longitude),
        speed: Number(v.speed),
        lastUpdate: Number(v.last_update),
        createdAt: Number(v.created_at),
      });
  }
  return rows.length;
}

async function syncRoutes(firestore, since, cutoff) {
  const rows = db
    .prepare('SELECT * FROM routes WHERE updated_at > ? AND updated_at <= ?')
    .all(since, cutoff);
  for (const r of rows) {
    await firestore
      .collection('routes')
      .doc(String(r.id))
      .set({
        id: Number(r.id),
        vehicleId: Number(r.vehicle_id),
        userId: Number(r.user_id),
        startLatitude: Number(r.start_latitude),
        startLongitude: Number(r.start_longitude),
        endLatitude: Number(r.end_latitude),
        endLongitude: Number(r.end_longitude),
        startTime: Number(r.start_time),
        endTime: Number(r.end_time),
        distance: Number(r.distance),
        maxSpeed: Number(r.max_speed),
        status: r.status,
      });
  }
  return rows.length;
}

// ── Realtime Database: telemetria e comandos ────────────────────────────

async function syncTelemetry(rtdb) {
  // Publica a última posição conhecida de cada veículo.
  const vehicles = db.prepare('SELECT id FROM vehicles').all();
  let count = 0;
  for (const v of vehicles) {
    const last = db
      .prepare(
        'SELECT latitude, longitude, speed, heading, timestamp FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT 1'
      )
      .get(Number(v.id));
    if (!last) continue;
    await rtdb.ref(`telemetry/${v.id}`).set({
      latitude: Number(last.latitude),
      longitude: Number(last.longitude),
      speed: Number(last.speed),
      heading: Number(last.heading),
      timestamp: Number(last.timestamp),
    });
    count++;
  }
  return count;
}

async function syncCommands(rtdb) {
  // Publica o comando mais recente de cada veículo (o app escuta commands/{vehicleId}).
  const vehicles = db.prepare('SELECT id FROM vehicles').all();
  let count = 0;
  for (const v of vehicles) {
    const last = db
      .prepare(
        'SELECT command, status, created_at FROM commands WHERE vehicle_id = ? ORDER BY id DESC LIMIT 1'
      )
      .get(Number(v.id));
    if (!last) continue;
    await rtdb.ref(`commands/${v.id}`).set({
      command: last.command,
      timestamp: Number(last.created_at),
      status: last.status,
    });
    count++;
  }
  return count;
}

// ── Tick de sincronização ───────────────────────────────────────────────

// Zera as marcas d'água para forçar o reenvio de tudo.
export function resetSyncWatermarks() {
  for (const key of STATE_KEYS) {
    if (key === 'last_run') continue;
    db.prepare(
      "INSERT INTO sync_state (key, value) VALUES (?, -1) ON CONFLICT(key) DO UPDATE SET value = -1"
    ).run(key);
  }
}

export async function syncOnce({ full = false } = {}) {
  const fb = getFirebase();
  if (!fb) {
    return { enabled: false, reason: 'FIREBASE_SYNC_ENABLED=false ou sem credenciais' };
  }

  const { firestore, rtdb } = fb;
  const started = Date.now();

  // Corte fechado: tudo que for gravado depois deste instante entra na
  // próxima rodada. Usar Date.now() no fim da rodada fazia o intervalo ficar
  // aberto e engolia as linhas criadas durante a própria sincronização.
  const cutoff = Date.now();
  if (full) resetSyncWatermarks();

  const summary = { users: 0, vehicles: 0, routes: 0, telemetry: 0, commands: 0 };
  const failed = [];

  // A marca só avança para a coleção que foi escrita com sucesso: se o
  // Firestore falhar no meio, avançar perderia os registros para sempre.
  for (const [key, fn, target] of [
    ['users', syncUsers, firestore],
    ['vehicles', syncVehicles, firestore],
    ['routes', syncRoutes, firestore],
  ]) {
    try {
      summary[key] = await fn(target, getLastSync(key), cutoff);
      setLastSync(key, cutoff);
    } catch (e) {
      failed.push(key);
      console.warn(`[sync] ${key} ->`, e.message);
    }
  }

  if (rtdb) {
    // RTDB sempre publica o estado atual, não um delta, então não usa marca.
    try {
      summary.telemetry = await syncTelemetry(rtdb);
    } catch (e) {
      failed.push('telemetry');
      console.warn('[sync] telemetry ->', e.message);
    }
    try {
      summary.commands = await syncCommands(rtdb);
    } catch (e) {
      failed.push('commands');
      console.warn('[sync] commands ->', e.message);
    }
  }

  setLastSync('last_run', Date.now());

  console.log(
    `[sync] rodada em ${Date.now() - started}ms | users=${summary.users} vehicles=${summary.vehicles} ` +
      `routes=${summary.routes} telemetry=${summary.telemetry} commands=${summary.commands}` +
      (failed.length ? ` | FALHOU: ${failed.join(', ')}` : '')
  );
  return { enabled: true, durationMs: Date.now() - started, summary, failed };
}

export function startFirebaseSync() {
  if (!config.firebaseSyncEnabled) {
    console.log(
      '[sync] desativado (FIREBASE_SYNC_ENABLED=false). Defina as credenciais no .env para habilitar.'
    );
    return null;
  }
  const interval = setInterval(() => {
    syncOnce().catch((e) => console.warn('[sync] erro na rodada:', e.message));
  }, config.firebaseSyncIntervalMs);
  // Rodada imediata ao iniciar (e log do status).
  syncOnce().catch((e) => console.warn('[sync] erro na 1ª rodada:', e.message));
  console.log(`[sync] agendado a cada ${config.firebaseSyncIntervalMs}ms`);
  return interval;
}