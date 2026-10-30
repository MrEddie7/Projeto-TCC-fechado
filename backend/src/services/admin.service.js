// Painel administrativo lendo DIRETAMENTE do Firestore.
//
// Antes da migração, o painel (raiz /) era montado a partir do SQLite
// (overview.service.js). Agora o Firestore é o único banco da API, então a
// visão do administrador é derivada das coleções `users`, `vehicles`,
// `devices`, `telemetry`, `routes`, `commands` e `mobile_devices`, mais a
// última localização de cada celular no Realtime Database.
//
// É somente leitura: nada aqui grava no Firestore. O formato das respostas é o
// mesmo que o painel já consome (counts, users, vehicles, phoneVehicles,
// screens, service, sync), para não exigir mudança de contrato no HTML.
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { config } from '../config.js';
import { getFirebase, getFirebaseInitError } from '../firebase/firebase-admin.js';
import { getSimulationDecision } from './simulation.service.js';
import { latestPoint, recentPoints } from './telemetry-store.js';
import { cachedVehicle, cachedDevice } from './vehicle-cache.js';
import { isQuotaError } from '../util/firestore-errors.js';

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

const num = (v, def = 0) => (Number.isFinite(Number(v)) ? Number(v) : def);
const ms = (v) => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  // Timestamp do Firestore (admin) ou objeto { seconds, nanoseconds }.
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (Number.isFinite(Number(v.seconds))) return Number(v.seconds) * 1000;
  return 0;
};
const online = (lastUpdate) => num(lastUpdate) >= Date.now() - RECENT_MS;

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
    // O banco agora é remoto (Firestore); mantém a chave para o painel.
    dbPath: `Firestore "${config.firestoreId}"`,
    uptimeSeconds,
    startedAt: Date.now() - uptimeSeconds * 1000,
    time: Date.now(),
  };
}

function syncInfo() {
  return {
    // Não há mais espelhamento SQLite -> Firebase: o Firestore é a fonte.
    enabled: getFirebase() != null,
    intervalMs: null,
    lastRun: null,
    watermarks: { users: null, vehicles: null, routes: null },
    credentials: getFirebase() ? 'ok' : `ausente (${getFirebaseInitError()})`,
    databaseUrl: config.firebaseDatabaseUrl || null,
    firestoreId: config.firestoreId,
  };
}

// Espelhos no RTDB para as coleções que o painel consulta sempre. O RTDB não
// cobra cota de leitura do Firestore, então o painel continua de pé quando a
// cota diária do Firestore acaba.
const RTDB_MIRRORS = {
  vehicles: 'vehicles_mirror',
  devices: 'devices_mirror',
};

/**
 * Lê uma coleção inteira (o volume do projeto cabe em memória).
 * Se a cota de leitura do Firestore acabar, cai no espelho do RTDB quando
 * existe; para as demais coleções devolve lista vazia em vez de derrubar o
 * painel inteiro com um 500.
 */
async function readCollection(name) {
  try {
    const snap = await getFirebase().firestore.collection(name).get();
    return snap.docs.map((d) => ({ docId: d.id, ...d.data() }));
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const mirror = RTDB_MIRRORS[name];
    if (!mirror) {
      console.warn(`[admin] cota de leitura esgotada: coleção "${name}" veio vazia`);
      return [];
    }
    const snap = await getFirebase().rtdb.ref(mirror).get();
    const data = snap.val() || {};
    return Object.entries(data).map(([docId, doc]) => ({ docId, ...doc }));
  }
}

/** Cópia rasa sem os Timestamps do Firestore, que não serializam em JSON. */
function jsonSafe(value) {
  if (Array.isArray(value)) return value.map(jsonSafe);
  if (value && typeof value === 'object') {
    if (typeof value.toMillis === 'function') return value.toMillis();
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = jsonSafe(v);
    return out;
  }
  return value;
}

// ── Serialização (mesmos formatos do antigo overview.service) ───────────

function serializeDevice(device) {
  if (!device) return null;
  return {
    deviceId: device.deviceId || device.docId,
    apiKey: device.apiKey || '',
    firmware: device.firmware || '',
    model: device.model || '',
    lastSeen: num(device.lastSeen),
    simulated: device.model === 'server-sim',
  };
}

function serializeVehicle(v, { device, telemetry }) {
  return {
    id: num(v.id),
    plate: v.plate || '',
    brand: v.brand || '',
    model: v.model || '',
    year: num(v.year),
    color: v.color || '',
    isBlocked: v.isBlocked === true,
    // O Firestore não guarda mais o flag `simulated`; a simulação é decidida
    // pelo device virtual (model='server-sim') vinculado ao veículo.
    simulated: device?.model === 'server-sim',
    // Tudo agora vem do Firestore, inclusive o que o app cadastrou.
    origin: 'firestore',
    source: 'firestore',
    latitude: num(v.latitude),
    longitude: num(v.longitude),
    speed: num(v.speed),
    lastUpdate: num(v.lastUpdate),
    online: online(v.lastUpdate),
    // O rastreador real manda um heartbeat a cada 60s mesmo sem GPS ligado.
    // "connected" = o dispositivo falou com a API recentemente; "online" =
    // chegou telemetria de GPS nos ultimos minutos.
    connected: online(device?.lastSeen),
    lastSeen: num(device?.lastSeen),
    createdAt: num(v.createdAt),
    simulation: getSimulationDecision(v, device),
    device,
    telemetry,
  };
}

function mapMobileLocation(raw) {
  if (!raw) return null;
  return {
    latitude: num(raw.latitude),
    longitude: num(raw.longitude),
    speed: num(raw.speed),
    heading: num(raw.heading),
    accuracy: num(raw.accuracy),
    timestamp: num(raw.timestamp),
    source: raw.source || 'mobile_app',
  };
}

function userEntry({ uid, doc, vehicles, mobileLocation }) {
  const isPhone = vehicles.length > 0;
  const device = doc?.mobileDevice || null;
  return {
    id: uid,
    name: doc?.name || doc?.email || device?.deviceName || 'Conta do app',
    email: doc?.email || '',
    phone: doc?.phone || '',
    createdAt: num(doc?.createdAt),
    // Dados de teste real: posição do celular (só entra pelo app) ou veículo
    // fora do simulador.
    ownerUid: uid,
    realTest: Boolean(mobileLocation) || vehicles.some((v) => !v.simulated),
    mobileLocation: mobileLocation || null,
    mobileDevice: device
      ? {
          id: device.id ?? uid,
          userId: device.userId ?? uid,
          vehicleId: device.vehicleId == null ? null : num(device.vehicleId),
          deviceName: device.deviceName || '',
          createdAt: num(device.createdAt),
          updatedAt: num(device.updatedAt),
        }
      : null,
    vehicles,
    source: 'firestore',
    origin: 'firestore',
    firebaseUid: uid,
    // Sinaliza que a conta só existe no app (sem documento em users/).
    hasAccountDoc: Boolean(doc),
    isPhoneDevice: isPhone,
  };
}

// ── Montagem do overview ───────────────────────────────────────────────

export async function buildOverview() {
  const fb = getFirebase();
  if (!fb) throw new Error(`Firebase indisponível: ${getFirebaseInitError()}`);

  const [rawUsers, rawVehicles, rawDevices, rawRoutes, rawCommands, rawMobiles, mobileLocations, liveTelemetry] =
    await Promise.all([
      readCollection('users'),
      readCollection('vehicles'),
      readCollection('devices'),
      readCollection('routes'),
      readCollection('commands'),
      readCollection('mobile_devices'),
      fb.rtdb.ref('mobile_locations').get().then((s) => s.val() || {}).catch(() => ({})),
      // A telemetria ao vivo vem do RTDB: ler a coleção inteira no Firestore
      // custava uma leitura por ponto e estourava a cota diária.
      fb.rtdb.ref('telemetry').get().then((s) => s.val() || {}).catch(() => ({})),
    ]);

  // Índices auxiliares.
  const deviceByVehicle = new Map();
  for (const d of rawDevices) {
    const vid = num(d.vehicleId, NaN);
    if (Number.isFinite(vid)) deviceByVehicle.set(vid, d);
  }

  const telemetryByVehicle = new Map();
  for (const [vid, p] of Object.entries(liveTelemetry || {})) {
    if (!p) continue;
    const id = num(vid, NaN);
    if (Number.isFinite(id)) telemetryByVehicle.set(id, p);
  }

  // Usuários: documento em users/ + quem tem veículo, device móvel ou posição
  // do celular (conta que só existe no app).
  const uidSet = new Set();
  for (const u of rawUsers) uidSet.add(u.docId);
  for (const v of rawVehicles) if (v.ownerUid) uidSet.add(v.ownerUid);
  for (const d of rawMobiles) uidSet.add(d.docId);
  for (const uid of Object.keys(mobileLocations)) uidSet.add(uid);

  const usersById = new Map(rawUsers.map((u) => [u.docId, u]));
  const mobileDeviceByUid = new Map(rawMobiles.map((d) => [d.docId, d]));
  const vehiclesByUid = new Map();
  for (const v of rawVehicles) {
    if (!v.ownerUid) continue;
    const list = vehiclesByUid.get(v.ownerUid) || [];
    list.push(v);
    vehiclesByUid.set(v.ownerUid, list);
  }

  const users = [];
  for (const uid of uidSet) {
    const owned = (vehiclesByUid.get(uid) || []).sort((a, b) => num(b.createdAt) - num(a.createdAt));
    const mapped = owned.map((v) =>
      serializeVehicle(v, {
        device: serializeDevice(deviceByVehicle.get(num(v.id))),
        telemetry: telemetryByVehicle.has(num(v.id))
          ? {
              latitude: num(telemetryByVehicle.get(num(v.id)).latitude),
              longitude: num(telemetryByVehicle.get(num(v.id)).longitude),
              speed: num(telemetryByVehicle.get(num(v.id)).speed),
              battery: telemetryByVehicle.get(num(v.id)).battery ?? null,
              timestamp: num(telemetryByVehicle.get(num(v.id)).timestamp),
            }
          : null,
      })
    );
    users.push(
      userEntry({
        uid,
        doc: usersById.get(uid) || null,
        vehicles: mapped,
        mobileLocation: mapMobileLocation(mobileLocations[uid]),
      })
    );
    // O device móvel é anexado ao usuário depois (precisa do doc bruto).
    const entry = users[users.length - 1];
    const rawDevice = mobileDeviceByUid.get(uid);
    entry.mobileDevice = rawDevice
      ? {
          id: rawDevice.id ?? uid,
          userId: rawDevice.userId ?? uid,
          vehicleId: rawDevice.vehicleId == null ? null : num(rawDevice.vehicleId),
          deviceName: rawDevice.deviceName || '',
          createdAt: num(rawDevice.createdAt),
          updatedAt: num(rawDevice.updatedAt),
        }
      : null;
  }

  users.sort((a, b) => String(a.name).localeCompare(String(b.name), 'pt-BR'));

  // Lista achatada de todos os veículos, com o dono embutido (mapa do painel).
  const vehicles = [];
  for (const u of users) {
    for (const v of u.vehicles) {
      vehicles.push({ ...v, owner: { id: u.id, name: u.name, email: u.email } });
    }
  }

  // Veículos cadastrados pelo celular: no modelo novo, todo veículo do
  // Firestore foi criado pelo app.
  const phoneVehicles = vehicles;

  const activeRoutes = rawRoutes.filter((r) => r.status === 'em_andamento').length;
  const recentMobile = Object.values(mobileLocations).filter(
    (l) => num(l.timestamp) >= Date.now() - RECENT_MS
  ).length;

  const counts = {
    users: users.length,
    vehicles: vehicles.length,
    simulatedVehicles: vehicles.filter((v) => v.simulated).length,
    realVehicles: vehicles.filter((v) => !v.simulated).length,
    blockedVehicles: vehicles.filter((v) => v.isBlocked).length,
    onlineVehicles: vehicles.filter((v) => v.online).length,
    // Veículos cujo rastreador falou com a API (heartbeat) nos últimos minutos.
    connectedVehicles: vehicles.filter((v) => v.connected).length,
    devices: rawDevices.length,
    simulatedDevices: rawDevices.filter((d) => d.model === 'server-sim').length,
    realDevices: rawDevices.filter((d) => d.model !== 'server-sim').length,
    activeDevices: rawDevices.filter((d) => online(d.lastSeen)).length,
    // Pontos de telemetria guardados no anel do RTDB (o histórico recente).
    // A coleção do Firestore não é varrida: cada documento custaria uma
    // leitura e estouraria a cota diária.
    telemetryPoints: Object.values(liveTelemetry || {}).filter(Boolean).length,
    routes: rawRoutes.length,
    activeRoutes,
    completedRoutes: rawRoutes.length - activeRoutes,
    commands: rawCommands.length,
    pendingCommands: rawCommands.filter((c) => c.status === 'pending').length,
    mobileLocations: Object.keys(mobileLocations).length,
    recentMobileLocations: recentMobile,
    mobileDevices: rawMobiles.length,
    phoneVehicles: phoneVehicles.length,
  };

  return {
    service: serviceInfo(),
    counts,
    sync: syncInfo(),
    users,
    vehicles,
    phoneVehicles,
    mobileDevices: rawMobiles.map((d) => ({
      id: d.id ?? d.docId,
      userId: d.userId ?? d.docId,
      vehicleId: d.vehicleId == null ? null : num(d.vehicleId),
      deviceName: d.deviceName || '',
      createdAt: num(d.createdAt),
      updatedAt: num(d.updatedAt),
    })),
    screens: screensHealth(counts),
  };
}

// ── Frota (rotas /v1/admin/vehicles) ───────────────────────────────────

/** Todos os veículos do sistema, com dono, device e última telemetria. */
export async function listAllVehicles() {
  const fb = getFirebase();
  if (!fb) throw new Error(`Firebase indisponível: ${getFirebaseInitError()}`);

  const [rawVehicles, rawUsers, rawMobiles, rawDevices] = await Promise.all([
    readCollection('vehicles'),
    readCollection('users'),
    readCollection('mobile_devices'),
    readCollection('devices'),
  ]);

  // A última telemetria vem do RTDB (telemetry/{vehicleId}). Ler a coleção
  // inteira de telemetria no Firestore custava uma leitura por ponto — era o
  // que estourava a cota diária do plano gratuito.
  const telemetryByVehicle = new Map();
  try {
    const rtdbSnap = await fb.rtdb.ref('telemetry').get();
    const live = rtdbSnap.val() || {};
    for (const [vid, p] of Object.entries(live)) {
      if (!p) continue;
      telemetryByVehicle.set(num(vid, NaN), p);
    }
  } catch (e) {
    console.warn(`[admin] telemetria ao vivo indisponível: ${e.message}`);
  }

  const deviceByVehicle = new Map();
  for (const d of rawDevices) {
    const vid = num(d.vehicleId, NaN);
    if (Number.isFinite(vid)) deviceByVehicle.set(vid, d);
  }

  const usersById = new Map(rawUsers.map((u) => [u.docId, u]));
  const mobileByUid = new Map(rawMobiles.map((d) => [d.docId, d]));

  const owners = new Map();
  const ownerOf = (uid) => {
    if (!owners.has(uid)) {
      const doc = usersById.get(uid);
      const device = mobileByUid.get(uid);
      owners.set(uid, {
        id: uid,
        name: doc?.name || doc?.email || device?.deviceName || 'Conta do app',
        email: doc?.email || '',
      });
    }
    return owners.get(uid);
  };

  return rawVehicles
    .filter((v) => v.ownerUid)
    .map((v) => {
      const device = deviceByVehicle.get(num(v.id)) || null;
      const last = telemetryByVehicle.get(num(v.id)) || null;
      return {
        ...serializeVehicle(v, { device: serializeDevice(device), telemetry: null }),
        owner: ownerOf(v.ownerUid),
        telemetry: last,
        _raw: { device, telemetry: last },
      };
    })
    .sort((a, b) => num(b.createdAt) - num(a.createdAt));
}

/** Detalhe de um veículo (qualquer dono) + device + telemetria recente. */
export async function getVehicleAny(vehicleId) {
  const vehicle = await vehicleAny(vehicleId);
  let device = null;
  try {
    const deviceSnap = await getFirebase().firestore
      .collection('devices').where('vehicleId', '==', num(vehicle.id)).limit(1).get();
    device = deviceSnap.empty ? null : deviceSnap.docs[0].data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    device = await cachedDevice(vehicle.id);
  }
  // Histórico recente via RTDB (sem varrer a coleção do Firestore).
  const points = await recentPoints(vehicle.id, { limit: 200 });

  return {
    vehicle: serializeVehicle(vehicle, { device: serializeDevice(device), telemetry: null }),
    device: serializeDevice(device),
    owner: await ownerOf(vehicle.ownerUid),
    telemetry: points,
  };
}

async function vehicleAny(vehicleId) {
  try {
    const snap = await getFirebase().firestore.collection('vehicles').doc(String(vehicleId)).get();
    if (!snap.exists) throw new HttpErrorNotFound();
    return snap.data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = await cachedVehicle(vehicleId);
    if (!cached) throw new HttpErrorNotFound();
    return cached;
  }
}

async function ownerOf(uid) {
  if (!uid) return null;
  let doc = null;
  let device = null;
  try {
    const [userSnap, deviceSnap] = await Promise.all([
      getFirebase().firestore.collection('users').doc(uid).get(),
      getFirebase().firestore.collection('mobile_devices').doc(uid).get(),
    ]);
    doc = userSnap.exists ? userSnap.data() : null;
    device = deviceSnap.exists ? deviceSnap.data() : null;
  } catch (e) {
    // Cota de leitura esgotada: o painel ainda mostra a conta pelo uid.
    if (!isQuotaError(e)) throw e;
  }
  return {
    id: uid,
    name: doc?.name || doc?.email || device?.deviceName || 'Conta do app',
    email: doc?.email || '',
  };
}

function HttpErrorNotFound() {
  const e = new Error('Veículo não encontrado');
  e.status = 404;
  return e;
}

/** Último ponto de telemetria de qualquer veículo. */
export async function latestTelemetry(vehicleId) {
  const vehicle = await vehicleAny(vehicleId);
  // O último ponto vem do RTDB (sem gastar cota de leitura do Firestore).
  const last = await latestPoint(vehicle.id);
  return {
    latest: last
      ? {
          id: num(last.id),
          latitude: num(last.latitude),
          longitude: num(last.longitude),
          speed: num(last.speed),
          heading: num(last.heading),
          battery: last.battery == null ? null : num(last.battery),
          timestamp: num(last.timestamp),
        }
      : null,
    vehicle: {
      id: num(vehicle.id),
      plate: vehicle.plate || '',
      isBlocked: vehicle.isBlocked === true,
      lastUpdate: num(vehicle.lastUpdate),
      latitude: num(vehicle.latitude),
      longitude: num(vehicle.longitude),
      speed: num(vehicle.speed),
    },
    owner: await ownerOf(vehicle.ownerUid),
  };
}

/** Histórico recente de telemetria de qualquer veículo. */
export async function telemetryHistory(vehicleId, { limit = 100, after = 0 } = {}) {
  const vehicle = await vehicleAny(vehicleId);
  const size = Math.min(Math.max(num(limit) || 100, 1), 500);
  const points = await recentPoints(vehicle.id, { limit: size, after });
  return {
    points: points.map((p) => ({
      id: num(p.id),
      latitude: num(p.latitude),
      longitude: num(p.longitude),
      speed: num(p.speed),
      heading: num(p.heading),
      battery: p.battery == null ? null : num(p.battery),
      timestamp: num(p.timestamp),
    })),
  };
}

/** Documento do veículo (cru) — usado pela simulação/mapa do painel. */
export async function rawVehicle(vehicleId) {
  try {
    const snap = await getFirebase().firestore.collection('vehicles').doc(String(vehicleId)).get();
    if (!snap.exists) throw HttpErrorNotFound();
    return snap.data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = await cachedVehicle(vehicleId);
    if (!cached) throw HttpErrorNotFound();
    return cached;
  }
}

/** Device vinculado ao veículo (ou null). */
export async function deviceOfVehicle(vehicleId) {
  try {
    const snap = await getFirebase()
      .firestore.collection('devices')
      .where('vehicleId', '==', num(vehicleId))
      .limit(1)
      .get();
    return snap.empty ? null : snap.docs[0].data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    return await cachedDevice(vehicleId);
  }
}

// ── Saúde por tela (inferida dos dados reais) ──────────────────────────

function screensHealth(c) {
  return [
    {
      id: 'login',
      name: 'Login',
      endpoint: 'Firebase Auth (client) · GET /v1/auth/me',
      status: 'ok',
      detail: `${c.users} conta(s) no Firestore`,
    },
    {
      id: 'register',
      name: 'Registro',
      endpoint: 'Firebase Auth (client) · POST /v1/mobile/device',
      status: 'ok',
      detail: 'Cadastro no Firebase Auth; a API registra o dispositivo móvel',
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
      endpoint: 'GET /v1/vehicles/:id/telemetry/latest',
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
          : `${c.mobileLocations} aparelho(s) com posição conhecida`,
    },
    {
      id: 'settings',
      name: 'Configurações',
      endpoint: 'local (app)',
      status: 'ok',
      detail: 'Preferências locais; sem dependência do backend',
    },
  ];
}

export { jsonSafe, ms, readCollection };
