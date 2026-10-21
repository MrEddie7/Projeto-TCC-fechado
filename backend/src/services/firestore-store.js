import { getFirebase } from '../firebase/firebase-admin.js';
import { HttpError } from '../util/http-error.js';

export const firestore = () => getFirebase().firestore;
export const rtdb = () => getFirebase().rtdb;

export async function ownedVehicle(uid, vehicleId) {
  const snap = await firestore().collection('vehicles').doc(String(vehicleId)).get();
  if (!snap.exists || snap.data().ownerUid !== uid) throw new HttpError(404, 'Veículo não encontrado');
  return snap.data();
}

export const makeId = () => Date.now() * 1000 + Math.floor(Math.random() * 1000);
