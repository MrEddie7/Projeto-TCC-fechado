import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { ownedVehicle } from '../services/firestore-store.js';
import { subscribeVehicle, publishToVehicle } from '../realtime/sse.js';
import { latestPoint, recentPoints } from '../services/telemetry-store.js';
import { isQuotaError } from '../util/firestore-errors.js';

const router = Router();
const point = (p) => ({
  id: Number(p.id), latitude: Number(p.latitude), longitude: Number(p.longitude),
  speed: Number(p.speed), heading: Number(p.heading),
  battery: p.battery == null ? null : Number(p.battery), timestamp: Number(p.timestamp),
});

// A posição e o histórico saem do RTDB (sem cota do Firestore). O Firestore
// continua sendo o histórico de longo prazo.
router.get('/:vehicleId/telemetry/latest', requireAuth, asyncHandler(async (req, res) => {
  const v = await ownedVehicle(req.user.uid, req.params.vehicleId);
  const latest = await latestPoint(v.id);
  res.json({
    latest: latest ? point(latest) : null,
    vehicle: {
      id: Number(v.id), plate: v.plate, isBlocked: v.isBlocked === true,
      lastUpdate: Number(v.lastUpdate || 0), latitude: Number(v.latitude || 0),
      longitude: Number(v.longitude || 0), speed: Number(v.speed || 0),
    },
  });
}));

router.get('/:vehicleId/telemetry/history', requireAuth, asyncHandler(async (req, res) => {
  const v = await ownedVehicle(req.user.uid, req.params.vehicleId);
  const limit = Math.min(Math.max(Number(req.query.limit) || 100, 1), 500);
  const after = Number(req.query.after) || 0;
  const points = await recentPoints(v.id, { limit, after });
  res.json({ points: points.map(point) });
}));

router.get('/:vehicleId/telemetry/stream', requireAuth, asyncHandler(async (req, res) => {
  const v = await ownedVehicle(req.user.uid, req.params.vehicleId);
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
  subscribeVehicle(Number(v.id), res);
  publishToVehicle(Number(v.id), 'presence', { connected: true, timestamp: Date.now(), vehicle: v });
}));

export default router;
