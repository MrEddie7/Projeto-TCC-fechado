import { getFirebase } from '../firebase/firebase-admin.js';
import { knownDevice } from '../services/device-key-cache.js';
import { isQuotaError } from '../util/firestore-errors.js';

// O app consulta a API a cada poucos segundos; gravar o documento do usuário
// em TODA requisição autenticada consome a cota de escrita do Firestore sem
// nenhum ganho (os dados do usuário quase nunca mudam). Fica no máximo uma
// gravação a cada 5 minutos por conta.
const USER_WRITE_INTERVAL_MS = 5 * 60 * 1000;
const lastUserWrite = new Map();

export async function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: 'Token de autenticação obrigatório' });
  }
  try {
    const fb = getFirebase();
    if (!fb) throw new Error('Firebase indisponível');
    const claims = await fb.auth.verifyIdToken(token);
    req.user = {
      uid: claims.uid,
      email: claims.email || '',
      name: claims.name || claims.email?.split('@')[0] || 'Usuário',
      phone: claims.phone_number || '',
    };
    const now = Date.now();
    if (now - (lastUserWrite.get(claims.uid) || 0) > USER_WRITE_INTERVAL_MS) {
      lastUserWrite.set(claims.uid, now);
      await fb.firestore.collection('users').doc(claims.uid).set({
        firebaseUid: claims.uid,
        email: req.user.email,
        name: req.user.name,
        phone: req.user.phone,
        updatedAt: now,
      }, { merge: true });
    }
    next();
  } catch {
    return res.status(401).json({ error: 'Token Firebase inválido ou expirado' });
  }
}

export async function requireDevice(req, res, next) {
  const deviceId = req.headers['x-device-id'];
  const apiKey = req.headers['x-api-key'] || req.body?.apiKey;
  if (!deviceId || !apiKey) {
    return res
      .status(401)
      .json({ error: 'Headers x-device-id e x-api-key são obrigatórios' });
  }
  const fb = getFirebase();
  let device = null;
  try {
    const snap = await fb?.firestore.collection('devices').doc(String(deviceId)).get();
    device = snap?.exists ? { deviceId: snap.id, ...snap.data() } : null;
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    // Cota de leitura esgotada: a credencial continua válida se estiver no
    // cache local do servidor (gravado quando o device foi provisionado).
    device = knownDevice(deviceId);
  }
  if (!device || device.apiKey !== apiKey) {
    return res.status(403).json({ error: 'Dispositivo não autorizado' });
  }
  req.device = device;
  next();
}
