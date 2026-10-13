import { Router } from 'express';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';

// Localização do celular do usuário (app Android).
// Autenticação: Authorization: Bearer <token-app> (não é hardware).
const router = Router();
router.use(requireAuth);

function serializeLocation(row) {
  return {
    id: Number(row.id),
    userId: Number(row.user_id),
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
    const info = db
      .prepare(
        `INSERT INTO mobile_locations (user_id, latitude, longitude, speed, heading, accuracy, timestamp, source, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(
        req.user.id,
        latitude,
        longitude,
        speed || 0,
        heading || 0,
        accuracy || 0,
        timestamp || now,
        source || 'mobile_app',
        now
      );
    const location = db
      .prepare('SELECT * FROM mobile_locations WHERE id = ?')
      .get(Number(info.lastInsertRowid));
    res.status(201).json({ ok: true, location: serializeLocation(location) });
  })
);

// GET /v1/mobile/location/latest
// Última localização conhecida do celular do usuário.
router.get(
  '/location/latest',
  asyncHandler(async (req, res) => {
    const location = db
      .prepare('SELECT * FROM mobile_locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1')
      .get(req.user.id);
    res.json({ location: location ? serializeLocation(location) : null });
  })
);

export default router;
