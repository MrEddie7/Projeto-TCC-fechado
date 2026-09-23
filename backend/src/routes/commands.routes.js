import { Router } from 'express';
import { db } from '../db/database.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { createCommand, listCommands } from '../services/command.service.js';

const router = Router();
router.use(requireAuth);

function assertOwnsVehicle(userId, vehicleId) {
  const v = db.prepare('SELECT * FROM vehicles WHERE id = ? AND user_id = ?').get(vehicleId, userId);
  if (!v) throw new HttpError(404, 'Veículo não encontrado');
  return v;
}

// Enviar comando ao hardware (block / unblock). O dispositivo pega na fila
// via polling e responde com ACK.
router.post(
  '/:vehicleId/commands',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const { command, note } = req.body || {};
    const created = createCommand(v.id, command, { note });
    res.status(201).json({ command: created });
  })
);

// Histórico de comandos do veículo (opcionalmente filtrar por ?status=)
router.get(
  '/:vehicleId/commands',
  asyncHandler(async (req, res) => {
    const v = assertOwnsVehicle(req.user.id, req.params.vehicleId);
    const { status } = req.query;
    res.json({ commands: listCommands(v.id, { status }) });
  })
);

export default router;