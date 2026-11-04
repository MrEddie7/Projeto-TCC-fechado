// Decisão de simulação + rastreador virtual, agora sobre o Firestore.
//
// O painel (e o app) perguntam se um veículo pode entrar na frota simulada.
// A simulação é representada por um "device" com model='server-sim': quando
// ele existe, o veículo é movimentado pelo simulador de hardware (api.sh /
// hardware-simulator.js), que envia telemetria normalmente para a API.
//
// Antes da migração isso vivia no SQLite (simulation.service.js). Aqui as
// mesmas regras são aplicadas sobre os documentos do Firestore.
import { randomBytes } from 'node:crypto';
import { config } from '../config.js';
import { HttpError } from '../util/http-error.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { firestore, rtdb, makeId } from './firestore-store.js';
import { publishToVehicle } from '../realtime/sse.js';
import { getFirebase } from '../firebase/firebase-admin.js';
import { rememberDevice } from './device-key-cache.js';
import { cachedVehicle } from './vehicle-cache.js';
import { startSimulatedDevice, stopSimulatedDevice, simulationRunnerAvailable } from './simulation-fleet.js';

export const SERVER_SIM_MODEL = 'server-sim';

const PLATE = /^[A-Z]{3}\d[A-Z0-9]\d{2}$|^[A-Z]{3}-\d{4}$/;

const isServerSim = (device) => Boolean(device) && device.model === SERVER_SIM_MODEL;

/** Device virtual vinculado ao veículo (ou null). */
async function simDeviceOf(vehicleId) {
  const snap = await firestore()
    .collection('devices')
    .where('vehicleId', '==', Number(vehicleId))
    .limit(1)
    .get();
  if (snap.empty) return null;
  return { deviceId: snap.docs[0].id, ...snap.docs[0].data() };
}

/**
 * Responde se o veículo deve (ou pode) entrar na frota simulada.
 * @param {object} vehicle documento do Firestore
 * @param {object|null} [device] device já carregado (evita nova leitura)
 */
/** Lê `simulate` mesmo quando o runtime entrega o JSON como string. */
export function simulateFromBody(body) {
  if (body == null) return undefined;
  let value = body;
  if (typeof body === 'string') {
    try { value = JSON.parse(body); } catch { return undefined; }
  }
  if (typeof value === 'object' && Buffer.isBuffer(value)) {
    try { value = JSON.parse(value.toString()); } catch { return undefined; }
  }
  if (typeof value === 'object' && value && 'simulate' in value) value = value.simulate;
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

export function getSimulationDecision(vehicle, device = undefined) {
  const hasDevice = device !== undefined ? Boolean(device) : Boolean(vehicle.device);
  const resolved = device !== undefined ? device : vehicle.device;
  const serverSimulated = isServerSim(resolved);

  let recommended = false;
  let reason;
  if (!config.simulationEnabled) {
    reason = 'Simulação desativada no servidor (SIMULATION_ENABLED=false).';
  } else if (hasDevice && !serverSimulated) {
    reason = 'O veículo já possui um rastreador (hardware) vinculado; mantenha como teste real.';
  } else if (serverSimulated) {
    recommended = true;
    reason = 'O veículo já está na frota simulada e continua sendo movimentado pelo simulador.';
  } else {
    recommended = true;
    reason =
      'O veículo ainda não tem rastreador. Adicionar à simulação cria um ' +
      'rastreador virtual que o movimenta automaticamente para demonstração.';
  }

  return {
    vehicleId: Number(vehicle.id),
    simulated: serverSimulated,
    hasDevice,
    deviceSimulated: serverSimulated,
    simulationAvailable: config.simulationEnabled,
    // A simulação pode estar disponível (criar carro/chave), mas o processo só
    // roda onde `canSpawn()` é true (API local, não na Vercel serverless).
    runnerAvailable: simulationRunnerAvailable(),
    recommended,
    reason,
  };
}

/**
 * Aplica a escolha { simulate: true|false }.
 * true  → cria o device virtual (model='server-sim') se não existir;
 * false → remove APENAS o device virtual, preservando hardware real.
 * Retorna { vehicle (documento atualizado), device, runner } — `runner` diz se
 * o processo do simulador realmente subiu (em Vercel/START_SIMULATOR=false é
 * normal existir device virtual sem runner local).
 */
export async function applySimulationDecision(vehicle, simulate) {
  if (!config.simulationEnabled && simulate) {
    throw new HttpError(409, 'Simulação desativada no servidor (SIMULATION_ENABLED=false)');
  }

  const db = firestore();
  const vehicleRef = db.collection('vehicles').doc(String(vehicle.id));
  let device = await simDeviceOf(vehicle.id);
  let runner = { started: false, managedBy: 'api' };

  if (simulate) {
    if (device && !isServerSim(device)) {
      throw new HttpError(409, 'Este veículo já possui um rastreador (hardware) vinculado');
    }
    const now = Date.now();
    if (!device) {
      const hasPosition = Number(vehicle.latitude) && Number(vehicle.longitude);
      const startLat = hasPosition
        ? Number(vehicle.latitude)
        : config.simulatorStartLat + (Math.random() - 0.5) * 0.02;
      const startLng = hasPosition
        ? Number(vehicle.longitude)
        : config.simulatorStartLng + (Math.random() - 0.5) * 0.02;
      const deviceId = `vt_${randomBytes(8).toString('hex')}`;
      device = {
        deviceId,
        apiKey: randomBytes(24).toString('hex'),
        vehicleId: Number(vehicle.id),
        firmware: '',
        model: SERVER_SIM_MODEL,
        lastSeen: 0,
        createdAt: now,
      };
      await db.collection('devices').doc(deviceId).set(device);
      await vehicleRef.set({ latitude: startLat, longitude: startLng, updatedAt: now }, { merge: true });
    }
    rememberDevice({ ...device, plate: vehicle.plate || '' });
    const started = startSimulatedDevice(device);
    runner = { started, managedBy: 'api' };
    if (!started) {
      console.warn(
        `[sim] veículo ${vehicle.id}: simulador não iniciou ` +
          '(SIMULATION_ENABLED/START_SIMULATOR/caminho do hardware-simulator.js)'
      );
    }
  } else if (isServerSim(device)) {
    const removedId = device.deviceId;
    stopSimulatedDevice(removedId);
    await db.collection('devices').doc(removedId).delete();
    await rtdb().ref(`telemetry/${vehicle.id}`).remove().catch(() => {});
    // Comandos pendentes do rastreador virtual que acabou de sair não têm
    // mais quem os confirme. Sem esta limpeza, eles ficavam "pending" para
    // sempre e o painel/app pareciam travados.
    try {
      const cmds = await db.collection('commands')
        .where('vehicleId', '==', Number(vehicle.id))
        .get();
      const pending = cmds.docs.filter((d) => d.data().status === 'pending');
      await Promise.all(pending.map((d) => d.ref.delete()));
    } catch (e) {
      if (!isQuotaError(e)) {
        console.warn(`[sim] não foi possível limpar comandos pendentes: ${e.message}`);
      }
    }
    await rtdb().ref(`commands/${vehicle.id}`).remove().catch(() => {});
    device = null;
  }

  await vehicleRef.set({ simulated: Boolean(simulate), updatedAt: Date.now() }, { merge: true });
  const updated = (await vehicleRef.get()).data();
  return { vehicle: updated, device, runner };
}

/**
 * Cria um veículo já na frota simulada, do dono informado.
 *
 * Usado tanto pelo painel admin (`POST /v1/admin/simulation/vehicles`) quanto
 * pelo app (`POST /v1/simulation/vehicles`). Centraliza validação de placa,
 * checagem de duplicata, criação do device server-sim e rollback caso a
 * simulação falhe — sem deixar veículo órfão no Firestore.
 *
 * @returns {Promise<{ vehicle: object, device: object|null, runner: object }>}
 */
export async function createSimulatedVehicleForOwner(ownerUid, input = {}) {
  // Checar ANTES de gravar: criar o veículo e só então descobrir que a
  // simulação está desligada deixaria um veículo órfão no Firestore.
  if (!config.simulationEnabled) {
    throw new HttpError(409, 'Simulação desativada no servidor (SIMULATION_ENABLED=false)');
  }
  const { plate, model, brand, year, color = '' } = input || {};
  const normalized = String(plate || '').trim().toUpperCase().replace(/\s+/g, '');
  if (!model || !brand || !PLATE.test(normalized)) {
    throw new HttpError(400, 'Informe placa (ABC1D23 ou ABC-1234), modelo e marca');
  }

  const db = firestore();
  const duplicate = await db
    .collection('vehicles')
    .where('ownerUid', '==', ownerUid)
    .where('plate', '==', normalized)
    .limit(1)
    .get();
  if (!duplicate.empty) throw new HttpError(409, `Você já tem um veículo com a placa ${normalized}`);

  const now = Date.now();
  const vehicle = {
    id: makeId(),
    userId: ownerUid,
    ownerUid,
    plate: normalized,
    model: String(model),
    brand: String(brand),
    year: Number(year) || 0,
    color: String(color),
    isBlocked: false,
    latitude: 0,
    longitude: 0,
    speed: 0,
    lastUpdate: 0,
    createdAt: now,
    updatedAt: now,
  };
  const vehicleRef = db.collection('vehicles').doc(String(vehicle.id));
  await vehicleRef.set(vehicle);

  // applySimulationDecision cria o device server-sim (com apiKey) e, quando a
  // API roda local, sobe o simulador desse rastreador. Se falhar depois de o
  // veículo já existir, apaga o documento em vez de deixar lixo.
  try {
    return await applySimulationDecision(vehicle, true);
  } catch (err) {
    await vehicleRef.delete().catch(() => {});
    throw err;
  }
}

// ── Posição manual (painel: arrastar o veículo no mapa) ───────────────────
//
// O simulador de hardware se move sozinho pelas rotas de SP e só lê a posição
// no boot. Para o administrador "teleportar" o veículo pelo painel, gravamos
// aqui a posição escolhida e um carimbo em `sim_control/{vehicleId}` no RTDB.
// O simulador consulta esse carimbo a cada ciclo e reancora quando ele muda.

/** Alvo de posição atual do veículo (ou null se nunca foi definido). */
export async function getSimControl(vehicleId) {
  const snap = await rtdb().ref(`sim_control/${Number(vehicleId)}`).get();
  return snap.exists() ? snap.val() : null;
}

/**
 * Define manualmente a posição do veículo (latitude/longitude e, opcional,
 * velocidade). Atualiza o documento do veículo, o último ponto de telemetria
 * (Firestore + RTDB), notifica quem acompanha ao vivo (SSE) e registra o alvo
 * para o simulador reancorar.
 * @returns {{ vehicle: object, point: object }}
 */
export async function setVehiclePosition(vehicleId, { latitude, longitude, speed = 0 }) {
  const lat = Number(latitude);
  const lng = Number(longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    throw new HttpError(400, 'Coordenadas inválidas');
  }
  const vId = Number(vehicleId);
  const now = Date.now();
  const spd = Math.max(0, Number(speed) || 0);

  const db = firestore();
  const vehicleRef = db.collection('vehicles').doc(String(vId));
  // A cota do Firestore pode estar esgotada justamente quando o administrador
  // arrasta o veículo no mapa. A posição manual é publicada no RTDB, que é o
  // canal lido pelo simulador; o documento do Firestore é secundário.
  let current = null;
  try {
    const snap = await vehicleRef.get();
    if (!snap.exists) throw new HttpError(404, 'Veículo não encontrado');
    current = snap.data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    current = await cachedVehicle(vId).catch(() => null);
    if (!current) throw new HttpError(503, 'Cota do Firestore esgotada e veículo sem espelho no RTDB');
  }

  const point = {
    id: makeId(),
    vehicleId: vId,
    latitude: lat,
    longitude: lng,
    speed: spd,
    heading: Number(current.heading || 0),
    battery: current.battery ?? null,
    timestamp: now,
    source: 'manual',
  };

  try {
    await db.collection('telemetry').doc(String(point.id)).set(point);
    await vehicleRef.set(
      { latitude: lat, longitude: lng, speed: spd, lastUpdate: now, updatedAt: now },
      { merge: true }
    );
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    console.warn('[sim] cota do Firestore esgotada: posição manual gravada só no RTDB');
  }
  await rtdb().ref(`telemetry/${vId}`).set(point);
  // Carimbo que o simulador observa para reancorar nesta posição.
  await rtdb().ref(`sim_control/${vId}`).set({ latitude: lat, longitude: lng, updatedAt: now });
  publishToVehicle(vId, 'telemetry', point);

  let updated = { ...current, ...point, lastUpdate: now, updatedAt: now };
  try {
    updated = (await vehicleRef.get()).data() || updated;
  } catch (e) {
    if (!isQuotaError(e)) throw e;
  }
  return { vehicle: updated, point };
}

// ── Sincronização Firebase ───────────────────────────────────────────────
// Batch sync: Firebase Auth users -> Firestore users collection
// Útil para popular o Firestore com usuários criados direto no Console.
export async function syncFirebaseUsers() {
  const fb = getFirebase();
  const auth = fb.auth;
  const db = firestore();

  let synced = 0;
  let errors = 0;
  const batchSize = 1000;
  let nextPageToken;

  do {
    const list = await auth.listUsers(batchSize, nextPageToken);
    for (const u of list.users) {
      try {
        await db.collection('users').doc(u.uid).set({
          firebaseUid: u.uid,
          email: u.email || '',
          name: u.displayName || u.email?.split('@')[0] || 'Usuário',
          phone: u.phoneNumber || '',
          updatedAt: Date.now(),
        }, { merge: true });
        synced++;
      } catch (e) {
        console.error(`[sync] erro ao sincronizar user ${u.uid}:`, e.message);
        errors++;
      }
    }
    nextPageToken = list.pageToken;
  } while (nextPageToken);

  return { synced, errors, timestamp: Date.now() };
}

// Sync: Firestore mobile_devices -> RTDB mobile_devices (tempo real)
// Permite que o painel veja os dispositivos móveis registrados em tempo real.
export async function syncMobileDevicesToRtdb() {
  const snap = await firestore().collection('mobile_devices').get();
  const updates = {};

  for (const doc of snap.docs) {
    const data = doc.data();
    updates[doc.id] = {
      id: data.id,
      userId: data.userId,
      vehicleId: data.vehicleId ?? null,
      deviceName: data.deviceName,
      createdAt: Number(data.createdAt),
      updatedAt: Number(data.updatedAt),
    };
  }

  await rtdb().ref('mobile_devices').set(updates || {});
  return { count: snap.size, timestamp: Date.now() };
}
