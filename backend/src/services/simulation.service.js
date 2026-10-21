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
import { firestore, rtdb, makeId } from './firestore-store.js';
import { publishToVehicle } from '../realtime/sse.js';
import { getFirebase } from '../firebase/firebase-admin.js';

export const SERVER_SIM_MODEL = 'server-sim';

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
    recommended,
    reason,
  };
}

/**
 * Aplica a escolha { simulate: true|false }.
 * true  → cria o device virtual (model='server-sim') se não existir;
 * false → remove APENAS o device virtual, preservando hardware real.
 * Retorna { vehicle (documento atualizado), device }.
 */
export async function applySimulationDecision(vehicle, simulate) {
  if (!config.simulationEnabled && simulate) {
    throw new HttpError(409, 'Simulação desativada no servidor (SIMULATION_ENABLED=false)');
  }

  const db = firestore();
  const vehicleRef = db.collection('vehicles').doc(String(vehicle.id));
  let device = await simDeviceOf(vehicle.id);

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
  } else if (isServerSim(device)) {
    await db.collection('devices').doc(device.deviceId).delete();
    await rtdb().ref(`telemetry/${vehicle.id}`).remove().catch(() => {});
    device = null;
  }

  await vehicleRef.set({ simulated: Boolean(simulate), updatedAt: Date.now() }, { merge: true });
  const updated = (await vehicleRef.get()).data();
  return { vehicle: updated, device };
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
  const snap = await vehicleRef.get();
  if (!snap.exists) throw new HttpError(404, 'Veículo não encontrado');

  const point = {
    id: makeId(),
    vehicleId: vId,
    latitude: lat,
    longitude: lng,
    speed: spd,
    heading: Number(snap.data().heading || 0),
    battery: snap.data().battery ?? null,
    timestamp: now,
    source: 'manual',
  };

  await db.collection('telemetry').doc(String(point.id)).set(point);
  await vehicleRef.set(
    { latitude: lat, longitude: lng, speed: spd, lastUpdate: now, updatedAt: now },
    { merge: true }
  );
  await rtdb().ref(`telemetry/${vId}`).set(point);
  // Carimbo que o simulador observa para reancorar nesta posição.
  await rtdb().ref(`sim_control/${vId}`).set({ latitude: lat, longitude: lng, updatedAt: now });
  publishToVehicle(vId, 'telemetry', point);

  const updated = (await vehicleRef.get()).data();
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
