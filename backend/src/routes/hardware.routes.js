import { Router } from 'express';
import { db } from '../db/database.js';
import { requireDevice } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { ingestTelemetry } from '../services/telemetry.service.js';
import {
  getPendingCommandsForDevice,
  acknowledgeCommand,
} from '../services/command.service.js';

const router = Router();

// ── Endpoints consumidos pelo hardware (ESP32 / módulo GPS) ─────────────

// POST /v1/hardware/telemetry
// Headers: x-device-id, x-api-key
// Body: { latitude, longitude, speed, heading, battery, timestamp }
router.post(
  '/telemetry',
  requireDevice,
  asyncHandler(async (req, res) => {
    const { latitude, longitude, speed, heading, battery, timestamp } = req.body || {};
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
    const result = ingestTelemetry({
      device: req.device,
      latitude,
      longitude,
      speed,
      heading,
      battery,
      timestamp,
    });
    res.status(201).json({ ok: true, ...result });
  })
);

// GET /v1/hardware/:deviceId/commands?status=pending
// Fila de comandos aguardando execução no dispositivo.
router.get(
  '/:deviceId/commands',
  requireDevice,
  asyncHandler(async (req, res) => {
    const status = req.query.status || 'pending';
    if (status !== 'pending') {
      const rows = db
        .prepare(
          'SELECT * FROM commands WHERE vehicle_id = ? AND status = ? ORDER BY id DESC LIMIT 50'
        )
        .all(Number(req.device.vehicle_id), status);
      return res.json({ commands: rows.map((c) => ({ id: Number(c.id), command: c.command, status: c.status, note: c.note, createdAt: Number(c.created_at), acknowledgedAt: Number(c.acknowledged_at) })) });
    }
    res.json({ commands: getPendingCommandsForDevice(req.device) });
  })
);

// POST /v1/hardware/:deviceId/commands/:commandId/ack
// Body: { ok: boolean, note?: string }
router.post(
  '/:deviceId/commands/:commandId/ack',
  requireDevice,
  asyncHandler(async (req, res) => {
    const { ok } = req.body || {};
    const { note } = req.body || {};
    if (typeof ok !== 'boolean') {
      throw new HttpError(400, 'Informe ok: true (sucesso) ou ok: false (falha)');
    }
    const updated = acknowledgeCommand(req.device, Number(req.params.commandId), { ok, note });
    res.json({ command: updated });
  })
);

// GET /v1/hardware/:deviceId/status
// Status e vínculo do dispositivo (usado no boot do firmware).
router.get(
  '/:deviceId/status',
  requireDevice,
  asyncHandler(async (req, res) => {
    const vehicle = req.device.vehicle_id
      ? db.prepare('SELECT * FROM vehicles WHERE id = ?').get(Number(req.device.vehicle_id))
      : null;
    res.json({
      deviceId: req.device.device_id,
      registered: true,
      vehicle: vehicle
        ? {
            id: Number(vehicle.id),
            plate: vehicle.plate,
            model: vehicle.model,
            isBlocked: vehicle.is_blocked === 1,
          }
        : null,
      pendingCommands: getPendingCommandsForDevice(req.device).length,
      serverTime: Date.now(),
    });
  })
);

export default router;