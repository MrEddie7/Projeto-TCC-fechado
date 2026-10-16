// ─────────────────────────────────────────────────────────────────────────
// Leitura (somente leitura) das contas/veículos que o app Android enviou ao
// Firestore, vinculados à conta logada (Firebase Auth).
//
// O app grava:
//   users/{id}     -> { name, email, phone, firebaseUid, createdAt }
//   vehicles/{id}  -> { plate, model, brand, year, color, ..., ownerUid,
//                       ownerEmail, userId }
//
// Usada em dois lugares:
//   1. painel (overview.service) para mesclar contas/veículos que ainda não
//      existem no SQLite;
//   2. importação para o SQLite (cloud-import.service), quando o administrador
//      quer trazer o veículo do celular para o backend.
//
// Um pequeno cache evita bater no Firestore em cada refresh do painel. A
// importação força a releitura (force: true).
// ─────────────────────────────────────────────────────────────────────────
import { getFirebase } from '../firebase/firebase-admin.js';

const CLOUD_CACHE_MS = 5000;

// Ids vindos da nuvem recebem um deslocamento para nunca colidirem com os ids
// (positivos e pequenos) do SQLite no painel.
export const CLOUD_ID_BASE = 1_000_000_000;

let cloudCache = { at: 0, data: null };

export function invalidateCloudCache() {
  cloudCache = { at: 0, data: null };
}

export async function readCloudAccounts({ force = false } = {}) {
  const fb = getFirebase();
  if (!fb) return { users: [], vehicles: [], error: null };
  if (!force && cloudCache.data && Date.now() - cloudCache.at < CLOUD_CACHE_MS) {
    return cloudCache.data;
  }
  try {
    const [usersSnap, vehiclesSnap] = await Promise.all([
      fb.firestore.collection('users').get(),
      fb.firestore.collection('vehicles').get(),
    ]);
    const data = {
      users: usersSnap.docs.map((d) => ({ docId: d.id, ...d.data() })),
      vehicles: vehiclesSnap.docs.map((d) => ({ docId: d.id, ...d.data() })),
      error: null,
    };
    cloudCache = { at: Date.now(), data };
    return data;
  } catch (e) {
    // Painel continua funcionando só com o SQLite se o Firestore estiver fora.
    console.warn('[firestore] leitura indisponível:', e.message);
    return { users: [], vehicles: [], error: e.message };
  }
}
