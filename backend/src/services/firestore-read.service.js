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

    // Indexa usuários por docId para enriquecer veículos sem ownerEmail/ownerUid.
    // O app grava userId (numérico do Room) nos veículos mas pode omitir ownerEmail
    // quando o usuário ainda não está logado no Firebase Auth.
    const usersByDocId = new Map();
    const users = usersSnap.docs.map((d) => {
      const u = { docId: d.id, ...d.data() };
      usersByDocId.set(String(d.id), u);
      return u;
    });

    const vehicles = vehiclesSnap.docs.map((d) => {
      const v = { docId: d.id, ...d.data() };
      // Enriquece com ownerEmail caso esteja ausente mas userId aponte para um
      // documento de usuário conhecido no mesmo Firestore.
      if (!v.ownerEmail && !v.ownerUid && v.userId != null) {
        const ownerDoc = usersByDocId.get(String(v.userId));
        if (ownerDoc) {
          v.ownerEmail = ownerDoc.email || v.ownerEmail;
          v.ownerUid   = ownerDoc.firebaseUid || v.ownerUid;
        }
      }
      return v;
    });

    const data = { users, vehicles, error: null };
    cloudCache = { at: Date.now(), data };
    return data;
  } catch (e) {
    // Painel continua funcionando só com o SQLite se o Firestore estiver fora.
    console.warn('[firestore] leitura indisponível:', e.message);
    return { users: [], vehicles: [], error: e.message };
  }
}
