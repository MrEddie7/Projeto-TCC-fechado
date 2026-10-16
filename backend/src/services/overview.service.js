// Painel administrativo do backend.
//
// Reúne, em uma única chamada, o que a aplicação web (raiz /) precisa mostrar:
//   1. estado do serviço da API em execução (uptime, porta, contadores);
//   2. usuários cadastrados e os veículos de cada um (com device e telemetria);
//   3. "saúde" de cada tela do sistema, inferida a partir dos dados reais dos
//      endpoints que a tela consome no app Android.
//
// É somente leitura: nenhuma query altera o banco. Qualquer falha de leitura
// isolada não derruba o painel inteiro (vira status "error" naquele item).

import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { db } from '../db/database.js';
import { config } from '../config.js';
import { getFirebase, getFirebaseInitError } from '../firebase/firebase-admin.js';
import { readCloudAccounts, CLOUD_ID_BASE } from './firestore-read.service.js';
import { getSimulationDecision } from './simulation.service.js';

// Janela usada para considerar uma telemetria/localização "recente".
const RECENT_MS = 5 * 60 * 1000;

function readVersion() {
  try {
    const pkgPath = fileURLToPath(new URL('../../package.json', import.meta.url));
    return JSON.parse(fs.readFileSync(pkgPath, 'utf8')).version || null;
  } catch {
    return null;
  }
}

function scalar(sql, ...params) {
  const row = db.prepare(sql).get(...params);
  return Number(Object.values(row || {})[0] || 0);
}

function count(table, where = '', ...params) {
  return scalar(`SELECT COUNT(*) AS n FROM ${table} ${where}`, ...params);
}

function serviceInfo() {
  const uptimeSeconds = Math.round(process.uptime());
  return {
    name: 'securitas-backend',
    version: readVersion(),
    node: process.version,
    platform: process.platform,
    pid: process.pid,
    env: process.env.NODE_ENV || 'development',
    port: config.port,
    dbPath: config.dbPath,
    uptimeSeconds,
    startedAt: Date.now() - uptimeSeconds * 1000,
    time: Date.now(),
  };
}

function counts() {
  const recent = Date.now() - RECENT_MS;
  return {
    users: count('users'),
    vehicles: count('vehicles'),
    // Divisão simulador x teste real (dados fora da simulação).
    simulatedVehicles: count('vehicles', 'WHERE simulated = 1'),
    realVehicles: count('vehicles', 'WHERE simulated = 0'),
    blockedVehicles: count('vehicles', 'WHERE is_blocked = 1'),
    onlineVehicles: count('vehicles', 'WHERE last_update >= ?', recent),
    devices: count('devices'),
    simulatedDevices: count('devices', 'WHERE simulated = 1'),
    realDevices: count('devices', 'WHERE simulated = 0'),
    activeDevices: count('devices', 'WHERE last_seen >= ?', recent),
    telemetryPoints: count('telemetry'),
    routes: count('routes'),
    activeRoutes: count('routes', "WHERE status = 'em_andamento'"),
    completedRoutes: count('routes', "WHERE status = 'concluida'"),
    commands: count('commands'),
    pendingCommands: count('commands', "WHERE status = 'pending'"),
    mobileLocations: count('mobile_locations'),
    recentMobileLocations: count('mobile_locations', 'WHERE timestamp >= ?', recent),
  };
}

function syncInfo() {
  const rows = db.prepare('SELECT key, value FROM sync_state').all();
  const marks = Object.fromEntries(rows.map((r) => [r.key, Number(r.value)]));
  return {
    enabled: config.firebaseSyncEnabled,
    intervalMs: config.firebaseSyncIntervalMs,
    lastRun: marks.last_run ?? null,
    watermarks: {
      users: marks.users ?? null,
      vehicles: marks.vehicles ?? null,
      routes: marks.routes ?? null,
    },
    credentials: getFirebase() ? 'ok' : `ausente (${getFirebaseInitError()})`,
    databaseUrl: config.firebaseDatabaseUrl || null,
    firestoreId: config.firestoreId,
  };
}

// Usuários + veículos + device + última telemetria de cada veículo
// + última localização (GPS) do celular de cada usuário.
function usersWithVehicles() {
  const users = db
    .prepare('SELECT id, name, email, phone, created_at FROM users ORDER BY id ASC')
    .all();

  return users.map((u) => {
    const vehicles = db
      .prepare('SELECT * FROM vehicles WHERE user_id = ? ORDER BY created_at DESC')
      .all(u.id);
    const lastMobile = db
      .prepare('SELECT * FROM mobile_locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1')
      .get(u.id);

    const mappedVehicles = vehicles.map((v) => {
      const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
      const last = db
        .prepare('SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT 1')
        .get(v.id);
      const origin = v.origin || 'backend';
      return {
        id: Number(v.id),
        plate: v.plate,
        brand: v.brand,
        model: v.model,
        year: Number(v.year),
        color: v.color,
        isBlocked: v.is_blocked === 1,
        // true = frota do simulador; false = teste real (fora da simulação).
        simulated: v.simulated === 1,
        // 'backend' = nasceu na API/SQLite; 'firestore' = importado do celular.
        origin,
        // source espelha origin para que renderPhoneVehicles e renderSimulation do
        // painel tratem corretamente os veículos importados do celular.
        source: origin,
        latitude: v.latitude,
        longitude: v.longitude,
        speed: v.speed,
        lastUpdate: Number(v.last_update),
        online: Number(v.last_update) >= Date.now() - RECENT_MS,
        // Decisão de simulação (para o seletor do painel): informa se o veículo
        // pode entrar/sair do simulador e por quê.
        simulation: getSimulationDecision(v),
        device: device
          ? {
              deviceId: device.device_id,
              firmware: device.firmware,
              model: device.model,
              lastSeen: Number(device.last_seen),
              simulated: device.simulated === 1,
            }
          : null,
        telemetry: last
          ? {
              latitude: last.latitude,
              longitude: last.longitude,
              speed: last.speed,
              battery: last.battery,
              timestamp: Number(last.timestamp),
            }
          : null,
      };
    });

    return {
      id: Number(u.id),
      name: u.name,
      email: u.email,
      phone: u.phone,
      createdAt: Number(u.created_at),
      // O usuário tem dados de TESTE REAL quando há posição do celular (que só
      // entra pelo app, nunca pelo simulador de hardware) ou veículo não marcado
      // como simulado.
      realTest: Boolean(lastMobile) || mappedVehicles.some((v) => !v.simulated),
      // Última posição conhecida do celular deste usuário, para o painel
      // mostrar o aparelho "junto do veículo".
      mobileLocation: lastMobile
        ? {
            latitude: lastMobile.latitude,
            longitude: lastMobile.longitude,
            speed: lastMobile.speed,
            heading: lastMobile.heading,
            accuracy: lastMobile.accuracy,
            timestamp: Number(lastMobile.timestamp),
            source: lastMobile.source,
          }
        : null,
      vehicles: mappedVehicles,
    };
  });
}

// "Saúde" por tela do sistema. Cada tela do app tem um endpoint de referência
// e um sinal de dado que indica se ela funciona (ok), está sem dados (warn) ou
// com problema (error). Não executa efeitos colaterais.
function screensHealth(c) {
  const screens = [
    {
      id: 'login',
      name: 'Login',
      endpoint: 'POST /v1/auth/login',
      status: 'ok',
      detail: `${c.users} usuário(s) cadastrado(s)`,
    },
    {
      id: 'register',
      name: 'Registro',
      endpoint: 'POST /v1/auth/register',
      status: 'ok',
      detail: 'Autenticação local (scrypt) e sessão de 30 dias',
    },
    {
      id: 'dashboard',
      name: 'Painel / Dashboard',
      endpoint: 'GET /v1/vehicles',
      status: c.vehicles > 0 ? 'ok' : 'warn',
      detail: c.vehicles > 0 ? `${c.vehicles} veículo(s) na frota` : 'Nenhum veículo cadastrado',
    },
    {
      id: 'tracking',
      name: 'Rastreamento',
      endpoint: 'GET /v1/vehicles/:id/telemetry/stream',
      status: c.onlineVehicles > 0 ? 'ok' : 'warn',
      detail:
        c.onlineVehicles > 0
          ? `${c.onlineVehicles} veículo(s) enviando GPS agora (${c.simulatedVehicles} do simulador, ${c.realVehicles} teste real)`
          : 'Sem telemetria nos últimos 5 min',
    },
    {
      id: 'remote',
      name: 'Controle Remoto',
      endpoint: 'POST /v1/vehicles/:id/commands',
      status: c.pendingCommands > 0 ? 'warn' : 'ok',
      detail:
        c.pendingCommands > 0
          ? `${c.pendingCommands} comando(s) aguardando ACK do hardware`
          : `${c.commands} comando(s) processado(s)`,
    },
    {
      id: 'history',
      name: 'Histórico de Rotas',
      endpoint: 'GET /v1/routes/:id',
      status: c.routes > 0 ? 'ok' : 'warn',
      detail: c.routes > 0 ? `${c.routes} rota(s) (${c.activeRoutes} em andamento)` : 'Nenhuma rota registrada',
    },
    {
      id: 'vehicle',
      name: 'Cadastro de Veículo',
      endpoint: 'POST /v1/vehicles',
      status: 'ok',
      detail: 'Validação de placa Mercosul/tradicional ativa',
    },
    {
      id: 'vehicle-detail',
      name: 'Detalhe do Veículo',
      endpoint: 'GET /v1/vehicles/:id',
      status: c.vehicles > 0 ? 'ok' : 'warn',
      detail: c.devices > 0 ? `${c.devices} device(s) vinculado(s)` : 'Nenhum hardware vinculado',
    },
    {
      id: 'apilink',
      name: 'Linkagem de API',
      endpoint: 'GET /v1/hardware/:deviceId/status',
      status: c.devices > 0 ? 'ok' : 'warn',
      detail: c.devices > 0 ? 'Credenciais de hardware geradas' : 'Nenhum device para vincular',
    },
    {
      id: 'device-location',
      name: 'Localização do Celular',
      endpoint: 'GET /v1/mobile/location/latest',
      status: c.recentMobileLocations > 0 ? 'ok' : 'warn',
      detail:
        c.recentMobileLocations > 0
          ? `Teste real · recebendo posição do app agora (${c.recentMobileLocations} recente(s))`
          : `${c.mobileLocations} ponto(s) no histórico (teste real, fora do simulador)`,
    },
    {
      id: 'settings',
      name: 'Configurações',
      endpoint: 'local (app)',
      status: 'ok',
      detail: 'Preferências locais; sem dependência do backend',
    },
  ];

  return screens;
}

// ── Firestore: contas e veículos enviados pelo app ──────────────────────
//
// O app Android grava, vinculados à conta logada (Firebase Auth), o documento
// da conta (users/{id}, com firebaseUid) e os veículos dessa conta
// (vehicles/{id}, com ownerUid/ownerEmail). Aqui esses documentos são lidos e
// mesclados ao painel quando ainda NÃO existem no SQLite (por exemplo, cadastro
// feito no app antes de o backend entrar em cena). A leitura vem de
// firestore-read.service.js. É somente leitura: nada é gravado de volta.
//
// Para trazer esses veículos de vez para o backend (com rastreador e controle de
// simulação), o painel chama POST /v1/admin/cloud/import.

function mapCloudUser(u) {
  const cloudId = Number(u.docId);
  return {
    id: CLOUD_ID_BASE + (Number.isFinite(cloudId) ? cloudId : 0),
    cloudId,
    name: u.name || u.email || 'Conta do app',
    email: u.email || '',
    phone: u.phone || '',
    createdAt: Number(u.createdAt) || 0,
    realTest: false,
    mobileLocation: null,
    vehicles: [],
    source: 'firestore',
    origin: 'firestore',
    firebaseUid: u.firebaseUid || null,
  };
}

function mapCloudVehicle(v) {
  const docId = Number(v.docId ?? v.id);
  if (!Number.isFinite(docId)) return null;
  return {
    id: CLOUD_ID_BASE + docId,
    cloudId: docId,
    plate: v.plate || '',
    brand: v.brand || '',
    model: v.model || '',
    year: Number(v.year) || 0,
    color: v.color || '',
    isBlocked: v.isBlocked === true,
    simulated: false,
    latitude: Number(v.latitude) || 0,
    longitude: Number(v.longitude) || 0,
    speed: Number(v.speed) || 0,
    lastUpdate: Number(v.lastUpdate) || 0,
    online: Number(v.lastUpdate) >= Date.now() - RECENT_MS,
    device: null,
    telemetry: null,
    origin: 'firestore',
    source: 'firestore',
  };
}

/**
 * Mescla as contas/veículos do Firestore na lista de usuários do painel.
 * Um item só é adicionado quando ainda não existe no SQLite (dedupe por email
 * para contas e por placa para veículos), então o que já veio pelo backend não
 * aparece duplicado. Retorna o resumo para exibir no painel.
 */
function mergeCloudAccounts(users, cloud) {
  const stats = { users: 0, vehicles: 0, error: cloud.error };
  const emailKey = (value) => String(value || '').trim().toLowerCase();
  const byEmail = new Map();
  for (const u of users) {
    const key = emailKey(u.email);
    if (key) byEmail.set(key, u);
  }

  // 1. Contas do app que ainda não estão no SQLite viram entradas próprias.
  const cloudUsersById = new Map();
  for (const raw of cloud.users) {
    const cloudUser = mapCloudUser(raw);
    cloudUsersById.set(cloudUser.cloudId, cloudUser);
    const key = emailKey(cloudUser.email);
    if (key && byEmail.has(key)) continue; // conta já cadastrada no SQLite
    users.push(cloudUser);
    if (key) byEmail.set(key, cloudUser);
    stats.users++;
  }

  // 2. Veículos do app que ainda não estão no SQLite, ligados ao dono.
  const plates = new Set();
  for (const u of users) {
    for (const v of u.vehicles) plates.add(String(v.plate || '').trim().toUpperCase());
  }

  for (const raw of cloud.vehicles) {
    const vehicle = mapCloudVehicle(raw);
    if (!vehicle) continue;
    const plate = vehicle.plate.trim().toUpperCase();
    if (!plate || plates.has(plate)) continue; // já exibido a partir do SQLite
    plates.add(plate);

    // Dono: e-mail gravado no veículo pelo app; senão a conta da nuvem à qual o
    // veículo pertence (userId -> users/{id}).
    let owner = byEmail.get(emailKey(raw.ownerEmail));
    if (!owner) {
      const cloudOwner = cloudUsersById.get(Number(raw.userId));
      owner = cloudOwner ? byEmail.get(emailKey(cloudOwner.email)) || cloudOwner : null;
    }
    if (!owner) {
      // Dono sem correspondência: cria uma conta só-pela-nuvem para o veículo
      // não sumir do painel.
      const ownerCloudId = Number(raw.userId);
      owner = {
        id: CLOUD_ID_BASE + (Number.isFinite(ownerCloudId) ? ownerCloudId : 0),
        cloudId: Number.isFinite(ownerCloudId) ? ownerCloudId : null,
        name: raw.ownerEmail || 'Conta do app',
        email: raw.ownerEmail || '',
        phone: '',
        createdAt: 0,
        realTest: false,
        mobileLocation: null,
        vehicles: [],
        source: 'firestore',
        origin: 'firestore',
      };
      users.push(owner);
      const key = emailKey(owner.email);
      if (key) byEmail.set(key, owner);
      stats.users++;
    }

    owner.vehicles.push({
      ...vehicle,
      owner: { id: owner.id, name: owner.name, email: owner.email },
    });
    stats.vehicles++;
  }

  return stats;
}

export async function buildOverview() {
  const c = counts();
  const users = usersWithVehicles();

  // Acrescenta contas e veículos que o app enviou ao Firestore e que ainda não
  // existem no SQLite do backend.
  const inbound = mergeCloudAccounts(users, await readCloudAccounts());

  // Lista achatada de TODOS os veículos, com o dono embutido. É o que o mapa
  // do administrador usa para exibir a frota inteira (de todos os usuários).
  const vehicles = [];
  for (const u of users) {
    for (const v of u.vehicles) {
      vehicles.push({ ...v, owner: { id: u.id, name: u.name, email: u.email } });
    }
  }

  // Veículos que vieram do celular (app -> Firestore): já importados no SQLite
  // (origin='firestore', com rastreador) ou ainda só na nuvem (source='firestore').
  const phoneVehicles = vehicles.filter((v) => v.origin === 'firestore');

  return {
    service: serviceInfo(),
    counts: { ...c, phoneVehicles: phoneVehicles.length },
    sync: { ...syncInfo(), inbound },
    users,
    vehicles,
    phoneVehicles,
    screens: screensHealth(c),
  };
}
