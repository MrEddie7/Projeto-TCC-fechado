import { Router } from 'express';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { subscribeVehicle, publishToVehicle } from '../realtime/sse.js';

const router = Router();

function assertOwnsVehicle(userId, vehicleId) {
  const v = db.prepare('SELECT * FROM vehicles WHERE id = ? AND user_id = ?').get(vehicleId, userId);
  if (!v) throw new HttpError(404, 'Veículo não encontrado');
  return v;
}

// Última posição conhecida do veículo
router.get(
  '/:vehicleId/telemetry/latest',
  requireAuth,
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const last = db
      .prepare(
        'SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT 1'
      )
      .get(v.id);
    res.json({
      latest: last
        ? {
            id: Number(last.id),
            latitude: last.latitude,
            longitude: last.longitude,
            speed: last.speed,
            heading: last.heading,
            battery: last.battery,
            timestamp: Number(last.timestamp),
          }
        : null,
      vehicle: {
        id: Number(v.id),
        plateau: v.plate,
        isBlocked: v.is_blocked === 1,
        lastUpdate: Number(v.last_update),
        latitude: v.latitude,
        longitude: v.longitude,
        speed: v.speed,
      },
    });
  })
);

// Histórico recente (limitado por ?limit=)
router.get(
  '/:vehicleId/telemetry/history',
  requireAuth,
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const limit = Math.min(Math.max(Number(req.query.limit) || 100, 1), 500);
    let rows;
    const after = Number(req.query.after) || 0;
    if (after > 0) {
      rows = db
        .prepare(
          'SELECT * FROM telemetry WHERE vehicle_id = ? AND timestamp > ? ORDER BY timestamp ASC LIMIT ?'
        )
        .all(v.id, after, limit);
    } else {
      rows = db
        .prepare(
          `SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT ?`
        )
        .all(v.id, limit)
        .reverse();
    }
    res.json({
      points: rows.map((t) => ({
        id: Number(t.id),
        latitude: t.latitude,
        longitude: t.longitude,
        speed: t.speed,
        heading: t.heading,
        battery: t.battery,
        timestamp: Number(t.timestamp),
      })),
    });
  })
);

// Stream em tempo real via SSE (Server-Sent Events).
// Eventos: `telemetry`, `blocked`, `command`.
router.get('/:vehicleId/telemetry/stream', requireAuth, (req, res, next) => {
  try {
    assertOwnsVehicle(req.user.id, req.params.vehicleId);
  } catch (e) {
    return next(e);
  }
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  subscribeVehicle(Number(req.params.vehicleId), res);

  const vehicleId = Number(req.params.vehicleId);
  const vehicle = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(vehicleId);
  publishToVehicle(vehicleId, 'presence', {
    connected: true,
    timestamp: Date.now(),
    vehicle: {
      id: Number(vehicle.id),
      plate: vehicle.plate,
      isBlocked: vehicle.is_blocked === 1,
      latitude: vehicle.latitude,
      longitude: vehicle.longitude,
      speed: vehicle.speed,
      lastUpdate: Number(vehicle.last_update),
    },
  });
});

export default router;