import { Router } from 'express';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';

const router = Router();

function serializeRoute(r) {
  return {
    id: Number(r.id),
    vehicleId: Number(r.vehicle_id),
    userId: Number(r.user_id),
    startLatitude: r.start_latitude,
    startLongitude: r.start_longitude,
    endLatitude: r.end_latitude,
    endLongitude: r.end_longitude,
    startTime: Number(r.start_time),
    endTime: Number(r.end_time),
    distance: r.distance,
    maxSpeed: r.max_speed,
    status: r.status,
  };
}

function assertOwnsVehicle(userId, vehicleId) {
  const v = db.prepare('SELECT * FROM vehicles WHERE id = ? AND user_id = ?').get(vehicleId, userId);
  if (!v) throw new HttpError(404, 'Veículo não encontrado');
  return v;
}

function assertOwnsRoute(userId, routeId) {
  const r = db
    .prepare(
      `SELECT r.* FROM routes r JOIN vehicles v ON v.id = r.vehicle_id
       WHERE r.id = ? AND v.user_id = ?`
    )
    .get(routeId, userId);
  if (!r) throw new HttpError(404, 'Rota não encontrada');
  return r;
}

// Iniciar rota (o hardware começa a acumular pontos assim que a telemetria chegar)
router.post(
  '/vehicles/:vehicleId/routes/start',
  requireAuth,
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const active = db
      .prepare(
        "SELECT * FROM routes WHERE vehicle_id = ? AND status = 'em_andamento' ORDER BY id DESC LIMIT 1"
      )
      .get(v.id);
    if (active) {
      return res.status(200).json({ route: serializeRoute(active), message: 'Rota já em andamento' });
    }
    const now = Date.now();
    const info = db
      .prepare(
        `INSERT INTO routes (vehicle_id, user_id, start_latitude, start_longitude, start_time, status, updated_at)
         VALUES (?, ?, ?, ?, ?, 'em_andamento', ?)`
      )
      .run(v.id, req.user.id, v.latitude, v.longitude, now, now);
    const route = db.prepare('SELECT * FROM routes WHERE id = ?').get(Number(info.lastInsertRowid));
    res.status(201).json({ route: serializeRoute(route) });
  })
);

// Finalizar rota em andamento
router.post(
  '/vehicles/:vehicleId/routes/stop',
  requireAuth,
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const active = db
      .prepare(
        "SELECT * FROM routes WHERE vehicle_id = ? AND status = 'em_andamento' ORDER BY id DESC LIMIT 1"
      )
      .get(v.id);
    if (!active) throw new HttpError(404, 'Nenhuma rota em andamento');
    const now = Date.now();
    db.prepare(
      `UPDATE routes SET status = 'concluida', end_time = ?,
        end_latitude = ?, end_longitude = ?, updated_at = ?
       WHERE id = ?`
    ).run(now, v.latitude, v.longitude, now, Number(active.id));
    const route = db.prepare('SELECT * FROM routes WHERE id = ?').get(Number(active.id));
    res.json({ route: serializeRoute(route) });
  })
);

// Listar rotas de um veículo
router.get(
  '/vehicles/:vehicleId/routes',
  requireAuth,
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const rows = db
      .prepare('SELECT * FROM routes WHERE vehicle_id = ? ORDER BY start_time DESC')
      .all(v.id);
    res.json({ routes: rows.map(serializeRoute) });
  })
);

// Listar rotas de todos os veículos do usuário
router.get(
  '/routes',
  requireAuth,
  asyncHandler(async (req, res) => {
    const { status } = req.query;
    const rows = status
      ? db
          .prepare(
            `SELECT r.* FROM routes r JOIN vehicles v ON v.id = r.vehicle_id
             WHERE v.user_id = ? AND r.status = ? ORDER BY r.start_time DESC`
          )
          .all(req.user.id, status)
      : db
          .prepare(
            `SELECT r.* FROM routes r JOIN vehicles v ON v.id = r.vehicle_id
             WHERE v.user_id = ? ORDER BY r.start_time DESC`
          )
          .all(req.user.id);
    res.json({ routes: rows.map(serializeRoute) });
  })
);

// Detalhe da rota + pontos geográficos (para desenhar a polyline no mapa)
router.get(
  '/routes/:routeId',
  requireAuth,
  asyncHandler(async (req, res) => {
    const r = assertOwnsRoute(req.user.id, req.params.routeId);
    const points = db
      .prepare(
        'SELECT * FROM route_points WHERE route_id = ? ORDER BY timestamp ASC'
      )
      .all(Number(r.id));
    res.json({
      route: serializeRoute(r),
      points: points.map((p) => ({
        id: Number(p.id),
        latitude: p.latitude,
        longitude: p.longitude,
        speed: p.speed,
        heading: p.heading,
        timestamp: Number(p.timestamp),
      })),
    });
  })
);

// Excluir rota
router.delete(
  '/routes/:routeId',
  requireAuth,
  asyncHandler(async (req, res) => {
    const r = assertOwnsRoute(req.user.id, req.params.routeId);
    db.prepare('DELETE FROM route_points WHERE route_id = ?').run(Number(r.id));
    db.prepare('DELETE FROM routes WHERE id = ?').run(Number(r.id));
    res.json({ ok: true });
  })
);

export default router;