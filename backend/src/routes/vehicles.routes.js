import { Router } from 'express';
import { randomBytes } from 'node:crypto';
import { getFirebase } from '../firebase/firebase-admin.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
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
async function owned(uid, vehicleId) {
  const snap = await getFirebase().firestore.collection('vehicles').doc(String(vehicleId)).get();
  if (!snap.exists || snap.data().ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
  return snap.data();
}

router.get('/', asyncHandler(async (req, res) => {
  const snap = await getFirebase().firestore.collection('vehicles').where('ownerUid', '==', req.user.uid).get();
  res.json({ vehicles: snap.docs.map(d => serializeVehicle(d.data())).sort((a,b) => b.createdAt-a.createdAt) });
}));
router.get('/:id', asyncHandler(async (req, res) => {
  const vehicle = await owned(req.user.uid, req.params.id);
  const devices = await getFirebase().firestore.collection('devices').where('vehicleId', '==', Number(vehicle.id)).limit(1).get();
  res.json({ vehicle: serializeVehicle(vehicle), device: devices.empty ? null : serializeDevice(devices.docs[0].data()) });
}));
router.post('/', asyncHandler(async (req,res) => {
  const { plate, model, brand, year, color = '' } = req.body || {};
  const normalized = String(plate || '').trim().toUpperCase().replace(/\s+/g, '');
  if (!model || !brand || !PLATE.test(normalized)) throw new HttpError(400, 'Dados do veículo inválidos');
  const duplicate = await getFirebase().firestore.collection('vehicles').where('ownerUid','==',req.user.uid).where('plate','==',normalized).limit(1).get();
  if (!duplicate.empty) throw new HttpError(409, 'Já existe um veículo com esta placa');
  const now=Date.now(), vehicle={ id:id(), userId:req.user.uid, ownerUid:req.user.uid, plate:normalized, model:String(model), brand:String(brand), year:Number(year), color:String(color), isBlocked:false, latitude:0, longitude:0, speed:0, lastUpdate:0, createdAt:now, updatedAt:now };
  await getFirebase().firestore.collection('vehicles').doc(String(vehicle.id)).set(vehicle);
  res.status(201).json({ vehicle:serializeVehicle(vehicle) });
}));
router.put('/:id', asyncHandler(async (req,res) => {
  const old=await owned(req.user.uid,req.params.id);
  const next={...old, ...Object.fromEntries(['model','brand','color','year','plate'].filter(k=>req.body?.[k]!==undefined).map(k=>[k,k==='plate'?String(req.body[k]).toUpperCase():req.body[k]])), updatedAt:Date.now()};
  await getFirebase().firestore.collection('vehicles').doc(String(old.id)).set(next);
  res.json({vehicle:serializeVehicle(next)});
}));
router.delete('/:id', asyncHandler(async (req,res) => {
  const v=await owned(req.user.uid,req.params.id);
  await getFirebase().firestore.collection('vehicles').doc(String(v.id)).delete();
  res.json({ok:true});
}));
router.post('/:id/device', asyncHandler(async (req,res) => {
  const v=await owned(req.user.uid,req.params.id), fs=getFirebase().firestore;
  const found=await fs.collection('devices').where('vehicleId','==',Number(v.id)).limit(1).get();
  if (!found.empty) return res.status(409).json({error:'Este veículo já possui um dispositivo',device:serializeDevice(found.docs[0].data())});
  const now=Date.now(), deviceId=`vt_${randomBytes(8).toString('hex')}`, device={deviceId,apiKey:randomBytes(24).toString('hex'),vehicleId:Number(v.id),firmware:'',model:'',lastSeen:0,createdAt:now};
  await fs.collection('devices').doc(deviceId).set(device);
  res.status(201).json({device:serializeDevice(device)});
}));

// ── Simulação do veículo ────────────────────────────────────────────────
// O app pergunta, logo após cadastrar um veículo, se ele deve entrar na frota
// simulada (rastreador virtual movido pelo simulador de hardware).
router.get('/:id/simulation', asyncHandler(async (req,res) => {
  const v=await owned(req.user.uid,req.params.id), fs=getFirebase().firestore;
  const found=await fs.collection('devices').where('vehicleId','==',Number(v.id)).limit(1).get();
  res.json({simulation:getSimulationDecision(v, found.empty?null:found.docs[0].data())});
}));
router.post('/:id/simulation', asyncHandler(async (req,res) => {
  const v=await owned(req.user.uid,req.params.id), simulate=req.body?.simulate;
  if (typeof simulate!=='boolean') throw new HttpError(400,'Informe simulate: true (simular) ou false (teste real)');
  const {vehicle,device}=await applySimulationDecision(v,simulate);
  res.json({simulation:getSimulationDecision(vehicle,device),vehicle:serializeVehicle(vehicle),device:serializeDevice(device)});
}));
export default router;
