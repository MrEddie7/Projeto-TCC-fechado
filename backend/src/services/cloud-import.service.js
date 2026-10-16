// ─────────────────────────────────────────────────────────────────────────
// Importa para o SQLite as contas e veículos que o app Android enviou ao
// Firestore (celular -> Firestore -> backend).
//
// Por que importar? Um veículo que só existe no Firestore não tem registro no
// backend e, portanto, não pode ter rastreador (device) nem entrar/sair da
// simulação. A importação:
//   1. cria o usuário dono no SQLite (por e-mail) quando ele só existe no app;
//   2. cria o veículo vinculado ao dono (origin = 'firestore');
//   3. garante um rastreador (device) REAL para o veículo — ou seja, o rastreador
//      passa a acompanhar o veículo cadastrado pelo usuário, e não a frota do
//      simulador (device model = 'app-tracker', simulated = 0).
//
// É idempotente: rodar de novo não duplica (dedupe por e-mail para contas e por
// placa+dono para veículos; só cria device se o veículo ainda não tiver um).
//
// As linhas criadas nascem com origin = 'firestore' e updated_at = 0 para que a
// sincronização SQLite -> Firebase NÃO as reenvie (o documento original já é do
// app). Ver firebase-sync.service.js.
// ─────────────────────────────────────────────────────────────────────────
import { randomBytes } from 'node:crypto';
import { db } from '../db/database.js';
import { createUser, getUserByEmail } from './auth.service.js';
import { readCloudAccounts, invalidateCloudCache } from './firestore-read.service.js';

export const APP_TRACKER_MODEL = 'app-tracker';

const emailKey = (value) => String(value || '').trim().toLowerCase();
const normPlate = (value) => String(value || '').trim().toUpperCase().replace(/\s+/g, '');

// Cria (uma única vez) o usuário dono no SQLite a partir de uma conta do app.
function ensureCloudUser({ name, email, phone }, stats) {
  const key = emailKey(email);
  if (!key) return null;
  const existing = getUserByEmail(key);
  if (existing) return existing;

  // A conta vive no Firebase Auth; a senha local é aleatória e inutilizável,
  // então o login local por e-mail/senha fica bloqueado (fail-closed).
  const created = createUser({
    name: String(name || email).trim() || email,
    email: key,
    phone: phone || '',
    password: randomBytes(32).toString('hex'),
  });
  db.prepare("UPDATE users SET origin = 'firestore', updated_at = 0 WHERE id = ?").run(created.id);
  stats.usersCreated++;
  return db.prepare('SELECT * FROM users WHERE id = ?').get(created.id);
}

function ensureTracker(vehicle, stats) {
  const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(vehicle.id);
  if (device) return device;
  const now = Date.now();
  db.prepare(
    `INSERT INTO devices (device_id, api_key, vehicle_id, firmware, model, simulated, created_at)
     VALUES (?, ?, ?, '', ?, 0, ?)`
  ).run(
    `vt_${randomBytes(8).toString('hex')}`,
    randomBytes(24).toString('hex'),
    vehicle.id,
    APP_TRACKER_MODEL,
    now
  );
  stats.trackersCreated++;
  return db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(vehicle.id);
}

function ensureVehicle(owner, cloudVehicle, stats, { createTracker }) {
  const plate = normPlate(cloudVehicle.plate);
  if (!plate) {
    stats.skipped++;
    return null;
  }

  let vehicle = db
    .prepare('SELECT * FROM vehicles WHERE user_id = ? AND plate = ?')
    .get(owner.id, plate);

  if (!vehicle) {
    const now = Date.now();
    // Tenta usar o id original do Firestore como id no SQLite para manter
    // consistência com os documentos já existentes (evita id drift).
    const desiredId = Number(cloudVehicle.docId ?? cloudVehicle.id);
    const existing = Number.isFinite(desiredId) && desiredId > 0
      ? db.prepare('SELECT * FROM vehicles WHERE id = ?').get(desiredId)
      : null;

    if (existing && existing.user_id === owner.id) {
      // Já existe com o mesmo id e dono — apenas atualiza metadados.
      db.prepare(
        `UPDATE vehicles SET plate=?, model=?, brand=?, year=?, color=?, is_blocked=?,
         latitude=?, longitude=?, speed=?, last_update=?, updated_at=0
         WHERE id=?`
      ).run(
        plate,
        String(cloudVehicle.model || '').trim() || '—',
        String(cloudVehicle.brand || '').trim() || '—',
        Number(cloudVehicle.year) || 0,
        String(cloudVehicle.color || '').trim(),
        cloudVehicle.isBlocked === true ? 1 : 0,
        Number(cloudVehicle.latitude) || 0,
        Number(cloudVehicle.longitude) || 0,
        Number(cloudVehicle.speed) || 0,
        Number(cloudVehicle.lastUpdate) || 0,
        desiredId
      );
      vehicle = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(desiredId);
    } else {
      const info = db
        .prepare(
          `INSERT INTO vehicles
             (user_id, plate, model, brand, year, color, is_blocked,
              latitude, longitude, speed, last_update, simulated, origin, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'firestore', ?, 0)`
        )
        .run(
          owner.id,
          plate,
          String(cloudVehicle.model || '').trim() || '—',
          String(cloudVehicle.brand || '').trim() || '—',
          Number(cloudVehicle.year) || 0,
          String(cloudVehicle.color || '').trim(),
          cloudVehicle.isBlocked === true ? 1 : 0,
          Number(cloudVehicle.latitude) || 0,
          Number(cloudVehicle.longitude) || 0,
          Number(cloudVehicle.speed) || 0,
          Number(cloudVehicle.lastUpdate) || 0,
          Number(cloudVehicle.createdAt) || now
        );
      vehicle = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(Number(info.lastInsertRowid));
      stats.vehiclesCreated++;
    }
  } else {
    // Veículo já existe — garante que updated_at=0 para não reenviar ao Firestore.
    if (Number(vehicle.updated_at) > 0) {
      db.prepare("UPDATE vehicles SET updated_at=0 WHERE id=?").run(vehicle.id);
    }
  }

  if (createTracker) ensureTracker(vehicle, stats);
  return vehicle;
}

export async function importFromCloud({ createTracker = true } = {}) {
  const cloud = await readCloudAccounts({ force: true });
  const stats = {
    ok: !cloud.error,
    error: cloud.error || null,
    accounts: cloud.users.length,
    cloudVehicles: cloud.vehicles.length,
    usersCreated: 0,
    vehiclesCreated: 0,
    trackersCreated: 0,
    skipped: 0,
  };
  if (cloud.error) return stats;

  // 1. Contas do app -> usuários no SQLite (dedupe por e-mail).
  const usersById = new Map();
  for (const raw of cloud.users) {
    const user = ensureCloudUser(raw, stats);
    if (user) usersById.set(Number(raw.docId), user);
  }
  // índice por firebaseUid/ownerUid
  const usersByFirebaseUid = new Map();
  for (const raw of cloud.users) {
    if (raw.firebaseUid) usersByFirebaseUid.set(String(raw.firebaseUid), raw);
    usersByFirebaseUid.set(String(raw.docId), raw);
  }

  // 2. Veículos do app -> veículos no SQLite, ligados ao dono.
  const demo = getUserByEmail(emailKey('aluno@tcc.com'));
  for (const raw of cloud.vehicles) {
    let owner = null;
    // 1) ownerEmail
    const oe = emailKey(raw.ownerEmail);
    if (oe) {
      owner = getUserByEmail(oe);
      if (!owner) owner = ensureCloudUser({ name: raw.ownerEmail, email: raw.ownerEmail }, stats);
    }
    // 2) ownerUid / firebaseUid
    if (!owner) {
      const ref = raw.ownerUid || raw.ownerUID || raw.owner_id || raw.firebaseUid;
      if (ref) {
        // Bug 8 fix: o segundo lookup era cópia idêntica do primeiro (copiar/colar).
        // Agora tenta pelo firebaseUid e faz fallback pelo docId numérico da conta.
        const cu = usersByFirebaseUid.get(String(ref)) || usersById.get(Number(ref));
        if (cu) {
          const ce = emailKey(cu.email);
          owner = ce ? (getUserByEmail(ce) || ensureCloudUser(cu, stats)) : null;
        }
      }
    }
    // 3) userId
    if (!owner) owner = usersById.get(Number(raw.userId)) || null;
    if (!owner && Number.isFinite(Number(raw.userId))) {
      owner = db.prepare('SELECT * FROM users WHERE id = ?').get(Number(raw.userId)) || null;
    }
    // 4) fallback demo
    if (!owner) owner = demo;
    if (!owner) {
      stats.skipped++;
      continue;
    }
    ensureVehicle(owner, raw, stats, { createTracker });
  }

  invalidateCloudCache();
  return stats;
}
