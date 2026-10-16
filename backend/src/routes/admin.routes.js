// Visão do ADMINISTRADOR: acesso a qualquer veículo do sistema, de qualquer
// usuário. O painel web (raiz /) usa estas rotas para exibir e monitorar a
// frota inteira — não apenas os veículos do usuário autenticado.
//
// O app Android continua usando /v1/vehicles (que filtra pelo próprio dono);
// estas rotas são o "lado administrador", como o restante de /v1/admin.
import { Router } from 'express';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { serializeVehicle, serializeDevice } from './vehicles.routes.js';
import { subscribeVehicle, publishToVehicle } from '../realtime/sse.js';
import {
  getSimulationDecision,
  applySimulationDecision,
} from '../services/simulation.service.js';
import { importFromCloud } from '../services/cloud-import.service.js';

const router = Router();
router.use(requireAuth);

function ownerOf(userId) {
  const u = db.prepare('SELECT id, name, email, phone FROM users WHERE id = ?').get(userId);
  return u ? { id: Number(u.id), name: u.name, email: u.email, phone: u.phone } : null;
}

function getVehicleAny(vehicleId) {
  const v = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(vehicleId);
  if (!v) throw new HttpError(404, 'Veículo não encontrado');
  return v;
}

// Veículo serializado + dono, para o painel saber de quem é cada veículo.
function withOwner(v) {
  return { ...serializeVehicle(v), owner: ownerOf(v.user_id) };
}

// Lista TODOS os veículos cadastrados por todos os usuários.
router.get(
  '/vehicles',
  asyncHandler(async (_req, res) => {
    const rows = db.prepare('SELECT * FROM vehicles ORDER BY created_at DESC').all();
    res.json({ vehicles: rows.map(withOwner) });
  })
);

// Detalhe de qualquer veículo + dispositivo vinculado + proprietário.
router.get(
  '/vehicles/:id',
  asyncHandler(async (req, res) => {
    const v = getVehicleAny(req.params.id);
    const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
    res.json({ vehicle: withOwner(v), device: serializeDevice(device), owner: ownerOf(v.user_id) });
  })
);

// Última posição conhecida de qualquer veículo.
router.get(
  '/vehicles/:id/telemetry/latest',
  asyncHandler(async (req, res) => {
    const v = getVehicleAny(req.params.id);
    const last = db
      .prepare('SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT 1')
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
        plate: v.plate,
        isBlocked: v.is_blocked === 1,
        lastUpdate: Number(v.last_update),
        latitude: v.latitude,
        longitude: v.longitude,
        speed: v.speed,
      },
      owner: ownerOf(v.user_id),
    });
  })
);

// Histórico recente de qualquer veículo (limitado por ?limit=).
router.get(
  '/vehicles/:id/telemetry/history',
  asyncHandler(async (req, res) => {
    const v = getVehicleAny(req.params.id);
    const limit = Math.min(Math.max(Number(req.query.limit) || 100, 1), 500);
    const after = Number(req.query.after) || 0;
    let rows;
    if (after > 0) {
      rows = db
        .prepare(
          'SELECT * FROM telemetry WHERE vehicle_id = ? AND timestamp > ? ORDER BY timestamp ASC LIMIT ?'
        )
        .all(v.id, after, limit);
    } else {
      rows = db
        .prepare('SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT ?')
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

// Stream em tempo real (SSE) de qualquer veículo — o administrador acompanha
// a frota inteira. Mesmos eventos da rota do app: `presence`, `telemetry`,
// `blocked`, `command`.
router.get('/vehicles/:id/telemetry/stream', (req, res, next) => {
  let v;
  try {
    v = getVehicleAny(req.params.id);
  } catch (e) {
    return next(e);
  }
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  subscribeVehicle(Number(v.id), res);
  publishToVehicle(Number(v.id), 'presence', {
    connected: true,
    timestamp: Date.now(),
    vehicle: {
      id: Number(v.id),
      plate: v.plate,
      isBlocked: v.is_blocked === 1,
      latitude: v.latitude,
      longitude: v.longitude,
      speed: v.speed,
      lastUpdate: Number(v.last_update),
    },
  });
});

// ── Sincronização com o celular (Firestore) ─────────────────────────────
//
// Traz para o SQLite as contas/veículos que o app enviou ao Firestore, ligando
// cada veículo ao dono e garantindo um rastreador REAL (model='app-tracker').
// Assim o rastreador acompanha o veículo cadastrado pelo usuário, e não a frota
// do simulador. Body opcional: { createTracker: false } para importar sem criar
// o rastreador.
router.post(
  '/cloud/import',
  asyncHandler(async (req, res) => {
    const createTracker = req.body?.createTracker !== false;
    const result = await importFromCloud({ createTracker });
    res.json({ import: result });
  })
);

// ── Simulação: seletor do painel ────────────────────────────────────────
//
// Lê a decisão de simulação de qualquer veículo (para montar o seletor).
router.get(
  '/vehicles/:id/simulation',
  asyncHandler(async (req, res) => {
    const v = getVehicleAny(req.params.id);
    res.json({ simulation: getSimulationDecision(v) });
  })
);

// Adiciona/tira qualquer veículo da simulação: { simulate: true | false }.
// Mesmas regras do app; veículo com rastreador real não pode ser simulado.
router.post(
  '/vehicles/:id/simulation',
  asyncHandler(async (req, res) => {
    const v = getVehicleAny(req.params.id);
    const simulate = req.body?.simulate;
    if (typeof simulate !== 'boolean') {
      throw new HttpError(400, 'Informe simulate: true (simular) ou false (teste real)');
    }
    const { vehicle, device } = applySimulationDecision(v, simulate);
    res.json({
      simulation: getSimulationDecision(vehicle),
      vehicle: serializeVehicle(vehicle),
      device: serializeDevice(device),
    });
  })
);

export default router;
