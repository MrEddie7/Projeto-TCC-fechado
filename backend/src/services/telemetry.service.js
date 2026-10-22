import { firestore, rtdb, makeId } from './firestore-store.js';
import { publishToVehicle } from '../realtime/sse.js';
import { haversine } from '../util/geo.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { pushTelemetry } from './telemetry-store.js';
import { mirrorDevice } from './vehicle-cache.js';
import { mirrorRoute, mirrorRoutePoint, cachedRoutes, cachedRoutePoints } from './route-cache.js';

// A posição do veículo e o "lastSeen" do rastreador são escritos a cada
// telemetria; com 4 rastreadores a 5 s isso consome a cota de escrita do
// Firestore em horas (20 mil escritas/dia no plano gratuito). A posição no
// RTDB é sempre atualizada (é o que a API lê para responder "última posição")
// e o histórico de cada ponto continua indo para o Firestore; o documento do
// veículo e o do device são regravados no máximo a cada WRITE_THROTTLE_MS.
const WRITE_THROTTLE_MS = 120_000;
const lastWrite = new Map();

const throttled = (key, now) => {
  const prev = lastWrite.get(key) || 0;
  if (now - prev < WRITE_THROTTLE_MS) return false;
  lastWrite.set(key, now);
  return true;
};

/**
 * Rota em andamento do veículo. Procura no Firestore e, se a cota de leitura
 * estiver esgotada, no espelho do RTDB (routes_mirror).
 */
async function activeRouteFor(vehicleId, fs) {
  try {
    const s = await fs.collection('routes')
      .where('vehicleId', '==', vehicleId).where('status', '==', 'em_andamento').limit(1).get();
    if (!s.empty) return { id: s.docs[0].id, data: s.docs[0].data() };
    return null;
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = (await cachedRoutes(vehicleId)).find((r) => r.status === 'em_andamento');
    return cached ? { id: String(cached.id), data: cached } : null;
  }
}

/** Último ponto do trajeto (para somar a distância). */
async function lastRoutePoint(routeId, fs) {
  try {
    const points = await fs.collection('routes').doc(routeId)
      .collection('points').orderBy('timestamp', 'desc').limit(1).get();
    if (!points.empty) return points.docs[0].data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
  }
  const cached = await cachedRoutePoints(routeId);
  return cached.length ? cached[cached.length - 1] : null;
}

export async function ingestTelemetry({ device, latitude, longitude, speed, heading, battery, timestamp }) {
  const vehicleId = Number(device.vehicleId), ts = Number(timestamp) || Date.now();
  // deviceId fica no ponto para dar rastreio de QUAL rastreador gerou a
  // leitura (o hardware real e o simulador escrevem na mesma colecao).
  const point = {
    id: makeId(), vehicleId, deviceId: device.deviceId,
    latitude: Number(latitude), longitude: Number(longitude),
    speed: Math.max(0, Number(speed) || 0), heading: Number(heading) || 0,
    battery: battery == null ? null : Number(battery), timestamp: ts,
  };

  // 1) RTDB primeiro: é a posição ao vivo (e não depende de cota do Firestore).
  await pushTelemetry(vehicleId, point);

  // 2) Firestore: histórico de longo prazo. Escritas continuam funcionando
  //    mesmo com a cota de leitura estourada; qualquer falha aqui não pode
  //    derrubar a ingestão (a posição já está no RTDB).
  const fs = firestore();
  try {
    await fs.collection('telemetry').doc(String(point.id)).set(point);
  } catch (e) {
    console.warn(`[telemetria] histórico no Firestore indisponível: ${e.message}`);
  }

  const vehicleRef = fs.collection('vehicles').doc(String(vehicleId));
  if (throttled(`veh:${vehicleId}`, ts)) {
    try {
      // Merge apenas dos campos de posição: evita LER o documento do veículo.
      await vehicleRef.set({
        latitude: point.latitude, longitude: point.longitude,
        speed: point.speed, lastUpdate: ts, updatedAt: Date.now(),
      }, { merge: true });
    } catch (e) {
      console.warn(`[telemetria] posição do veículo não gravada: ${e.message}`);
    }
  }

  if (throttled(`dev:${device.deviceId}`, ts)) {
    try {
      await fs.collection('devices').doc(device.deviceId).set({ ...device, lastSeen: ts }, { merge: true });
    } catch (e) {
      console.warn(`[telemetria] lastSeen do device não gravado: ${e.message}`);
    }
    await mirrorDevice({ ...device, lastSeen: ts });
  }

  // 3) Rota em andamento: os pontos entram na polyline. As leituras de rota
  //    podem falhar com a cota estourada — nesse caso a rota é procurada no
  //    espelho do RTDB (a gravação continua indo para o Firestore).
  try {
    const active = await activeRouteFor(vehicleId, fs);
    if (active) {
      const routeId = String(active.id);
      const route = active.data;
      const previous = await lastRoutePoint(routeId, fs);
      const distance = Number(route.distance || 0) +
        (previous ? haversine(previous.latitude, previous.longitude, point.latitude, point.longitude) : 0);
      await fs.collection('routes').doc(routeId).collection('points').doc(String(point.id)).set(point);
      // Espelho no RTDB: mantém o trajeto disponível mesmo com a cota de
      // leitura do Firestore esgotada (é o que o app desenha no mapa).
      await mirrorRoutePoint(routeId, point);
      // O documento da rota (distância/velocidade) é regravado com intervalo
      // maior: a polyline é o que importa, e cada escrita conta na cota.
      if (throttled(`route:${routeId}`, ts)) {
        const updated = {
          ...route, distance,
          maxSpeed: Math.max(Number(route.maxSpeed || 0), point.speed),
          endLatitude: point.latitude, endLongitude: point.longitude, updatedAt: Date.now(),
        };
        await fs.collection('routes').doc(routeId).set(updated);
        await mirrorRoute(updated);
      }
    }
  } catch (e) {
    if (!isQuotaError(e)) console.warn(`[telemetria] rota não atualizada: ${e.message}`);
  }

  publishToVehicle(vehicleId, 'telemetry', point);
  return { vehicleId, pointId: point.id, timestamp: ts };
}
