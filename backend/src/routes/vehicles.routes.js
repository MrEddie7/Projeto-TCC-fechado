import { Router } from 'express';
import { randomBytes } from 'node:crypto';
import { getFirebase } from '../firebase/firebase-admin.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { isQuotaError } from '../util/firestore-errors.js';
import {
  mirrorVehicles, mirrorDevice, cachedVehicles, cachedVehicle, cachedDevice,
} from '../services/vehicle-cache.js';
import {
  getSimulationDecision,
  applySimulationDecision,
} from '../services/simulation.service.js';

const router = Router();
router.use(requireAuth);
const PLATE = /^[A-Z]{3}\d[A-Z0-9]\d{2}$|^[A-Z]{3}-\d{4}$/;
const id = () => Date.now() * 100 + Math.floor(Math.random() * 100);

export function serializeVehicle(v) {
  return {
    id: Number(v.id),
    // Identidade é o ownerUid (string). userId espelha o uid para o app, que
    // ainda usa Long nos modelos antigos (o hashCode do uid é a ponte).
    userId: v.userId ?? v.ownerUid ?? 0,
    ownerUid: v.ownerUid ?? null,
    plate: v.plate,
    model: v.model, brand: v.brand, year: Number(v.year), color: v.color || '',
    isBlocked: v.isBlocked === true, latitude: Number(v.latitude || 0),
    longitude: Number(v.longitude || 0), speed: Number(v.speed || 0),
    lastUpdate: Number(v.lastUpdate || 0), createdAt: Number(v.createdAt || 0),
  };
}
export function serializeDevice(d) {
  if (!d) return null;
  return { id: d.deviceId, deviceId: d.deviceId, apiKey: d.apiKey,
    vehicleId: Number(d.vehicleId), firmware: d.firmware || '', model: d.model || '',
    lastSeen: Number(d.lastSeen || 0), createdAt: Number(d.createdAt || 0) };
}
// Versão para o app: sem a apiKey (é a credencial do hardware, usada só na
// provisionagem do dispositivo). O app precisa saber QUEM é o rastreador e se
// ele está online — nunca a chave que autentica o GPS na API.
export function serializeDevicePublic(d) {
  if (!d) return null;
  const { apiKey, ...pub } = serializeDevice(d);
  return pub;
}

// ── Leitura tolerante à cota do Firestore ────────────────────────────────
// O Firestore é a fonte oficial; se a cota diária de leitura estourar, a API
// responde com o espelho no RTDB em vez de derrubar o app.

// A posição ao vivo vive no RTDB (telemetry/{vehicleId}): é atualizada a
// cada leitura de telemetria, sem gastar cota do Firestore. Os documentos do
// Firestore são regravados com intervalo maior (ver telemetry.service.js), por
// isso a API completa a posição na resposta.
async function livePositions(vehicles) {
  const list = Array.isArray(vehicles) ? vehicles : [vehicles];
  const live = {};
  try {
    const snap = await getFirebase().rtdb.ref('telemetry').get();
    for (const [vid, p] of Object.entries(snap.val() || {})) {
      if (p) live[String(vid)] = p;
    }
  } catch (e) {
    return list;
  }
  return list.map((v) => {
    const p = live[String(v.id)];
    if (!p) return v;
    return {
      ...v,
      latitude: Number(p.latitude ?? v.latitude ?? 0),
      longitude: Number(p.longitude ?? v.longitude ?? 0),
      speed: Number(p.speed ?? v.speed ?? 0),
      lastUpdate: Number(p.timestamp ?? v.lastUpdate ?? 0),
    };
  });
}

async function readVehicle(uid, vehicleId) {
  try {
    const snap = await getFirebase().firestore.collection('vehicles').doc(String(vehicleId)).get();
    if (!snap.exists || snap.data().ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
    const [vehicle] = await livePositions(snap.data());
    await mirrorVehicles([vehicle]);
    return vehicle;
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = await cachedVehicle(vehicleId);
    if (!cached || cached.ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
    const [vehicle] = await livePositions(cached);
    return vehicle;
  }
}

async function readDevice(vehicleId) {
  try {
    const found = await getFirebase().firestore.collection('devices')
      .where('vehicleId', '==', Number(vehicleId)).limit(1).get();
    const device = found.empty ? null : found.docs[0].data();
    if (device) await mirrorDevice(device);
    return device;
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    return await cachedDevice(vehicleId);
  }
}

const owned = readVehicle;

router.get('/', asyncHandler(async (req, res) => {
  let vehicles;
  try {
    const snap = await getFirebase().firestore.collection('vehicles').where('ownerUid', '==', req.user.uid).get();
    vehicles = await livePositions(snap.docs.map((d) => d.data()));
    await mirrorVehicles(vehicles);
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    vehicles = await livePositions(await cachedVehicles(req.user.uid));
  }
  res.json({ vehicles: vehicles.map(serializeVehicle).sort((a, b) => b.createdAt - a.createdAt) });
}));

router.get('/:id', asyncHandler(async (req, res) => {
  const vehicle = await readVehicle(req.user.uid, req.params.id);
  const device = await readDevice(vehicle.id);
  res.json({ vehicle: serializeVehicle(vehicle), device: serializeDevicePublic(device) });
}));

router.post('/', asyncHandler(async (req, res) => {
  const { plate, model, brand, year, color = '' } = req.body || {};
  const normalized = String(plate || '').trim().toUpperCase().replace(/\s+/g, '');
  if (!model || !brand || !PLATE.test(normalized)) throw new HttpError(400, 'Dados do veículo inválidos');
  const duplicate = await getFirebase().firestore.collection('vehicles').where('ownerUid', '==', req.user.uid).where('plate', '==', normalized).limit(1).get();
  if (!duplicate.empty) throw new HttpError(409, 'Já existe um veículo com esta placa');
  const now = Date.now();
  const vehicle = { id: id(), userId: req.user.uid, ownerUid: req.user.uid, plate: normalized, model: String(model), brand: String(brand), year: Number(year), color: String(color), isBlocked: false, latitude: 0, longitude: 0, speed: 0, lastUpdate: 0, createdAt: now, updatedAt: now };
  await getFirebase().firestore.collection('vehicles').doc(String(vehicle.id)).set(vehicle);
  await mirrorVehicles([vehicle]);
  res.status(201).json({ vehicle: serializeVehicle(vehicle) });
}));

router.put('/:id', asyncHandler(async (req, res) => {
  const old = await readVehicle(req.user.uid, req.params.id);
  const next = { ...old, ...Object.fromEntries(['model', 'brand', 'color', 'year', 'plate'].filter((k) => req.body?.[k] !== undefined).map((k) => [k, k === 'plate' ? String(req.body[k]).toUpperCase() : req.body[k]])), updatedAt: Date.now() };
  await getFirebase().firestore.collection('vehicles').doc(String(old.id)).set(next);
  await mirrorVehicles([next]);
  res.json({ vehicle: serializeVehicle(next) });
}));

router.delete('/:id', asyncHandler(async (req, res) => {
  const v = await readVehicle(req.user.uid, req.params.id);
  await getFirebase().firestore.collection('vehicles').doc(String(v.id)).delete();
  res.json({ ok: true });
}));

router.post('/:id/device', asyncHandler(async (req, res) => {
  const v = await readVehicle(req.user.uid, req.params.id);
  const fs = getFirebase().firestore;
  const found = await fs.collection('devices').where('vehicleId', '==', Number(v.id)).limit(1).get();
  if (!found.empty) return res.status(409).json({ error: 'Este veículo já possui um dispositivo', device: serializeDevice(found.docs[0].data()) });
  const now = Date.now();
  const deviceId = `vt_${randomBytes(8).toString('hex')}`;
  const device = { deviceId, apiKey: randomBytes(24).toString('hex'), vehicleId: Number(v.id), firmware: '', model: '', lastSeen: 0, createdAt: now };
  await fs.collection('devices').doc(deviceId).set(device);
  await mirrorDevice(device);
  res.status(201).json({ device: serializeDevice(device) });
}));

// ── Simulação do veículo ────────────────────────────────────────────────
// O app pergunta, logo após cadastrar um veículo, se ele deve entrar na frota
// simulada (rastreador virtual movido pelo simulador de hardware).
router.get('/:id/simulation', asyncHandler(async (req, res) => {
  const v = await readVehicle(req.user.uid, req.params.id);
  const device = await readDevice(v.id);
  res.json({ simulation: getSimulationDecision(v, device) });
}));

router.post('/:id/simulation', asyncHandler(async (req, res) => {
  const v = await readVehicle(req.user.uid, req.params.id);
  const simulate = req.body?.simulate;
  if (typeof simulate !== 'boolean') throw new HttpError(400, 'Informe simulate: true (simular) ou false (teste real)');
  const { vehicle, device } = await applySimulationDecision(v, simulate);
  await mirrorVehicles([vehicle]);
  await mirrorDevice(device);
  res.json({ simulation: getSimulationDecision(vehicle, device), vehicle: serializeVehicle(vehicle), device: serializeDevicePublic(device) });
}));

export default router;
