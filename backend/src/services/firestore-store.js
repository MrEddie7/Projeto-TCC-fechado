import { getFirebase } from '../firebase/firebase-admin.js';
import { HttpError } from '../util/http-error.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { cachedVehicle } from './vehicle-cache.js';

export const firestore = () => getFirebase().firestore;
export const rtdb = () => getFirebase().rtdb;

function withVehicleId(data, docId) {
  const raw = data || {};
  const fromField = Number(raw.id);
  const fromDoc = Number(docId);
  const id = Number.isFinite(fromField) ? fromField : fromDoc;
  return { ...raw, id };
}

export async function ownedVehicle(uid, vehicleId) {
  try {
    const snap = await firestore().collection('vehicles').doc(String(vehicleId)).get();
    if (!snap.exists || snap.data().ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
    // Sem o id do documento, enqueueCommand gravava vehicleId=NaN e o
    // Firestore recusava o set — o botão Bloquear não chegava na nuvem.
    return withVehicleId(snap.data(), snap.id);
  } catch (e) {
    // Cota de leitura do Firestore esgotada: usa o espelho no RTDB.
    if (!isQuotaError(e)) throw e;
    const cached = await cachedVehicle(vehicleId);
    if (!cached || cached.ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
    return withVehicleId(cached, vehicleId);
  }
}

export const makeId = () => Date.now() * 1000 + Math.floor(Math.random() * 1000);
