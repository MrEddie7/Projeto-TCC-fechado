import { Router } from 'express';
import { randomBytes } from 'node:crypto';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';

const router = Router();
router.use(requireAuth);

const PLATE_REGEX = /^[A-Z]{3}\d[A-Z0-9]\d{2}$|^[A-Z]{3}-\d{4}$/;

function serializeVehicle(row) {
  return {
    id: Number(row.id),
    userId: Number(row.user_id),
    plate: row.plate,
    model: row.model,
    brand: row.brand,
    year: Number(row.year),
    color: row.color,
    isBlocked: row.is_blocked === 1,
    latitude: row.latitude,
    longitude: row.longitude,
    speed: row.speed,
    lastUpdate: Number(row.last_update),
    createdAt: Number(row.created_at),
  };
}

function serializeDevice(row) {
  if (!row) return null;
  return {
    id: Number(row.id),
    deviceId: row.device_id,
    apiKey: row.api_key,
    vehicleId: row.vehicle_id === null ? null : Number(row.vehicle_id),
    firmware: row.firmware,
    model: row.model,
    lastSeen: Number(row.last_seen),
    createdAt: Number(row.created_at),
  };
}

function assertOwnsVehicle(userId, vehicleId) {
  const v = db.prepare('SELECT * FROM vehicles WHERE id = ? AND user_id = ?').get(vehicleId, userId);
  if (!v) throw new HttpError(404, 'Veículo não encontrado');
  return v;
}

// Listar veículos do usuário
router.get(
  '/',
  asyncHandler(async (req, res) => {
    const rows = db
      .prepare('SELECT * FROM vehicles WHERE user_id = ? ORDER BY created_at DESC')
      .all(req.user.id);
    res.json({ vehicles: rows.map(serializeVehicle) });
  })
);

// Detalhe + dispositivo vinculado
router.get(
  '/:id',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.id);
    const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
    res.json({ vehicle: serializeVehicle(v), device: serializeDevice(device) });
  })
);

// Cadastrar veículo
router.post(
  '/',
  asyncHandler(async (req, res) => {
    const { plate, model, brand, year, color } = req.body || {};
    if (!plate || !model || !brand || !year) {
      throw new HttpError(400, 'Informe plate, model, brand e year');
    }
    const plateStr = String(plate)
      .trim()
      .toUpperCase()
      .replace(/\s+/g, '');
    if (!PLATE_REGEX.test(plateStr)) {
      throw new HttpError(400, 'Placa inválida (use formato Mercosul: ABC1D23 ou ABC-1234)');
    }
    const yearNum = Number(year);
    if (!Number.isInteger(yearNum) || yearNum < 1950 || yearNum > new Date().getFullYear() + 1) {
      throw new HttpError(400, 'Ano do veículo inválido');
    }
    const existing = db.prepare('SELECT id FROM vehicles WHERE user_id = ? AND plate = ?').get(
      req.user.id,
      plateStr
    );
    if (existing) throw new HttpError(409, 'Já existe um veículo com esta placa');

    const now = Date.now();
    const info = db
      .prepare(
        `INSERT INTO vehicles (user_id, plate, model, brand, year, color, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(req.user.id, plateStr, model.trim(), brand.trim(), yearNum, color?.trim() || '', now, now);
    const v = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(Number(info.lastInsertRowid));
    res.status(201).json({ vehicle: serializeVehicle(v) });
  })
);

// Atualizar veículo
router.put(
  '/:id',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.id);
    const { plate, model, brand, year, color } = req.body || {};
    const plateStr = plate ? String(plate).trim().toUpperCase().replace(/\s+/g, '') : v.plate;
    if (plate && !PLATE_REGEX.test(plateStr)) {
      throw new HttpError(400, 'Placa inválida (use formato Mercosul: ABC1D23 ou ABC-1234)');
    }
    const yearNum = year === undefined ? Number(v.year) : Number(year);
    db.prepare(
      `UPDATE vehicles SET plate = ?, model = ?, brand = ?, year = ?, color = ?, updated_at = ? WHERE id = ?`
    ).run(
      plateStr,
      model?.trim() || v.model,
      brand?.trim() || v.brand,
      yearNum,
      color?.trim() ?? v.color,
      Date.now(),
      v.id
    );
    const updated = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(v.id);
    res.json({ vehicle: serializeVehicle(updated) });
  })
);

// Excluir veículo
router.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.id);
    db.prepare('DELETE FROM vehicles WHERE id = ?').run(v.id);
    res.json({ ok: true });
  })
);

// Gerar credenciais do dispositivo / hardware (ESP32, módulo GPS) para o veículo.
// Retorna deviceId e apiKey que devem ser gravados no firmware.
router.post(
  '/:id/device',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.id);
    const existing = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
    if (existing) {
      res.status(409).json({
        error: 'Este veículo já possui um dispositivo',
        device: serializeDevice(existing),
      });
      return;
    }
    const deviceId = `vt_${randomBytes(8).toString('hex')}`;
    const apiKey = randomBytes(24).toString('hex');
    const now = Date.now();
    const info = db
      .prepare(
        `INSERT INTO devices (device_id, api_key, vehicle_id, created_at)
         VALUES (?, ?, ?, ?)`
      )
      .run(deviceId, apiKey, v.id, now);
    const device = db.prepare('SELECT * FROM devices WHERE id = ?').get(Number(info.lastInsertRowid));
    res.status(201).json({ device: serializeDevice(device) });
  })
);

export default router;