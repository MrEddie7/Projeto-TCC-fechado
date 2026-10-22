// Espelho (cache) de veículos e rastreadores no RTDB.
//
// O Firestore é a fonte oficial, mas no plano gratuito a cota DIÁRIA de
// leituras acaba. O app lista os veículos a cada poucos segundos; sem cache,
// uma cota estourada deixa o app sem lista nenhuma. O espelho no RTDB mantém
// a API utilizável nesse cenário e é reescrito a cada leitura bem-sucedida do
// Firestore (ou a cada escrita), então volta a ser consistente assim que a
// cota se recupera.
//
// Regra: o espelho NUNCA guarda a apiKey do rastreador — é a credencial do
// hardware e não deve trafegar fora do Firestore.
import { getFirebase } from '../firebase/firebase-admin.js';

const VEHICLES = 'vehicles_mirror';
const DEVICES = 'devices_mirror';
const rtdb = () => getFirebase().rtdb;

const publicDevice = (d) => {
  if (!d) return null;
  const { apiKey, ...pub } = d;
  return {
    id: d.deviceId,
    deviceId: d.deviceId,
    vehicleId: Number(d.vehicleId),
    firmware: d.firmware || '',
    model: d.model || '',
    lastSeen: Number(d.lastSeen || 0),
    createdAt: Number(d.createdAt || 0),
  };
};

export async function mirrorVehicles(vehicles) {
  const list = (vehicles || []).filter((v) => v && v.id != null);
  if (!list.length) return;
  const updates = {};
  for (const v of list) updates[String(v.id)] = { ...v, id: Number(v.id) };
  try {
    await rtdb().ref(VEHICLES).update(updates);
  } catch (e) {
    // cache é acessório: falha aqui não pode derrubar a requisição
  }
}

export async function mirrorDevice(device) {
  const pub = publicDevice(device);
  if (!pub) return;
  try {
    await rtdb().ref(`${DEVICES}/${pub.deviceId}`).set(pub);
  } catch (e) {
    // idem
  }
}

export async function cachedVehicles(uid) {
  const snap = await rtdb().ref(VEHICLES).get();
  const all = snap.val() || {};
  return Object.values(all)
    .filter((v) => v && v.ownerUid === uid)
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
}

export async function cachedVehicle(vehicleId) {
  const snap = await rtdb().ref(`${VEHICLES}/${vehicleId}`).get();
  return snap.val() || null;
}

export async function cachedDevice(vehicleId) {
  const snap = await rtdb().ref(DEVICES).get();
  const all = snap.val() || {};
  return Object.values(all).find((d) => d && Number(d.vehicleId) === Number(vehicleId)) || null;
}
