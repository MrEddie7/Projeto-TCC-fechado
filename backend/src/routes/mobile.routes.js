import { Router } from 'express';
import { getFirebase } from '../firebase/firebase-admin.js';
import { requireAuth } from '../middleware/auth.js';
import { savePushToken, removePushToken } from '../services/notifications.service.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';

// Localização do celular do usuário (app Android).
// Autenticação: Authorization: Bearer <token-app> (não é hardware).
const router = Router();
router.use(requireAuth);

function serializeLocation(row) {
  return {
    id: row.id,
    userId: row.userId,
    latitude: row.latitude,
    longitude: row.longitude,
    speed: row.speed,
    heading: row.heading,
    accuracy: row.accuracy,
    timestamp: Number(row.timestamp),
    source: row.source,
  };
}

// POST /v1/mobile/location
// Body: { latitude, longitude, speed?, heading?, accuracy?, timestamp?, source? }
router.post(
  '/location',
  asyncHandler(async (req, res) => {
    const { latitude, longitude, speed, heading, accuracy, timestamp, source } = req.body || {};
    if (latitude === undefined || longitude === undefined) {
      throw new HttpError(400, 'Informe latitude e longitude no corpo da requisição');
    }
    if (
      typeof latitude !== 'number' ||
      typeof longitude !== 'number' ||
      latitude < -90 ||
      latitude > 90 ||
      longitude < -180 ||
      longitude > 180
    ) {
      throw new HttpError(400, 'Coordenadas inválidas');
    }
    const now = Date.now();
    const location = {
      id: req.user.uid,
      userId: req.user.uid,
      latitude, longitude,
      speed: Number(speed) || 0,
      heading: Number(heading) || 0,
      accuracy: Number(accuracy) || 0,
      timestamp: Number(timestamp) || now,
      source: source || 'mobile_app',
    };
    // Estado atual, não histórico: RTDB é a fonte para a última localização.
    await getFirebase().rtdb.ref(`mobile_locations/${req.user.uid}`).set(location);
    res.status(201).json({ ok: true, location: serializeLocation(location) });
  })
);

// POST /v1/mobile/push-token
// Registra o token FCM deste aparelho para receber push (ex.: alerta de
// bloqueio confirmado pelo rastreador). Autenticado pelo ID Token do Firebase.
router.post(
  '/push-token',
  asyncHandler(async (req, res) => {
    const token = typeof req.body?.token === 'string' ? req.body.token.trim() : '';
    if (!token || token.length > 4096) throw new HttpError(400, 'Token FCM inválido');
    await savePushToken(req.user.uid, token);
    res.status(201).json({ ok: true });
  })
);

// DELETE /v1/mobile/push-token
// Revoga o token deste aparelho (logout).
router.delete(
  '/push-token',
  asyncHandler(async (req, res) => {
    const token = typeof req.body?.token === 'string' ? req.body.token.trim() : '';
    if (token) await removePushToken(req.user.uid, token);
    res.json({ ok: true });
  })
);

// GET /v1/mobile/location/latest
// Última localização conhecida do celular do usuário.
router.get(
  '/location/latest',
  asyncHandler(async (req, res) => {
    const snapshot = await getFirebase().rtdb.ref(`mobile_locations/${req.user.uid}`).get();
    res.json({ location: snapshot.exists() ? serializeLocation(snapshot.val()) : null });
  })
);

export default router;
