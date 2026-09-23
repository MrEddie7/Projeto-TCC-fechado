import { db } from '../db/database.js';
import { publishToVehicle } from '../realtime/sse.js';

const VALID_COMMANDS = new Set(['block', 'unblock']);

// Cria um comando na fila do dispositivo. O hardware pega via polling
// (GET /v1/hardware/:deviceId/commands) e responde com ACK/NACK.
export function createCommand(vehicleId, command, { note = '' } = {}) {
  if (!VALID_COMMANDS.has(command)) {
    const err = new Error(`Comando inválido. Use um destes: ${[...VALID_COMMANDS].join(', ')}`);
    err.status = 400;
    throw err;
  }
  const now = Date.now();
  const info = db
    .prepare(
      `INSERT INTO commands (vehicle_id, command, status, note, created_at)
       VALUES (?, ?, 'pending', ?, ?)`
    )
    .run(vehicleId, command, note || '', now);

  const cmd = db
    .prepare('SELECT * FROM commands WHERE id = ?')
    .get(Number(info.lastInsertRowid));

  publishToVehicle(vehicleId, 'command', serialize(cmd));
  return serialize(cmd);
}

export function listCommands(vehicleId, { status } = {}) {
  let rows;
  if (status) {
    rows = db
      .prepare(
        'SELECT * FROM commands WHERE vehicle_id = ? AND status = ? ORDER BY id DESC LIMIT 100'
      )
      .all(vehicleId, status);
  } else {
    rows = db
      .prepare('SELECT * FROM commands WHERE vehicle_id = ? ORDER BY id DESC LIMIT 100')
      .all(vehicleId);
  }
  return rows.map(serialize);
}

export function getPendingCommandsForDevice(device) {
  if (!device.vehicle_id) return [];
  return db
    .prepare(
      `SELECT * FROM commands
       WHERE vehicle_id = ? AND status = 'pending'
       ORDER BY id ASC`
    )
    .all(Number(device.vehicle_id))
    .map(serialize);
}

export function acknowledgeCommand(device, commandId, { ok, note }) {
  const cmd = db.prepare('SELECT * FROM commands WHERE id = ?').get(commandId);
  if (!cmd) {
    const err = new Error('Comando não encontrado');
    err.status = 404;
    throw err;
  }
  if (Number(cmd.vehicle_id) !== Number(device.vehicle_id)) {
    const err = new Error('Comando pertence a outro veículo');
    err.status = 403;
    throw err;
  }

  const status = ok ? 'acknowledged' : 'failed';
  const now = Date.now();
  db.prepare(
    `UPDATE commands SET status = ?, note = ?, acknowledged_at = ? WHERE id = ?`
  ).run(status, note || cmd.note || '', now, commandId);

  // Ao confirmar block/unblock, reflete o estado físico no veículo e notifica o app.
  if (ok && (cmd.command === 'block' || cmd.command === 'unblock')) {
    const blocked = cmd.command === 'block' ? 1 : 0;
    db.prepare(
      'UPDATE vehicles SET is_blocked = ?, updated_at = ? WHERE id = ?'
    ).run(blocked, Date.now(), Number(cmd.vehicle_id));
    publishToVehicle(Number(cmd.vehicle_id), 'blocked', {
      vehicleId: Number(cmd.vehicle_id),
      isBlocked: blocked === 1,
      command: cmd.command,
      timestamp: now,
    });
  }

  const updated = db.prepare('SELECT * FROM commands WHERE id = ?').get(commandId);
  return serialize(updated);
}

function serialize(c) {
  return {
    id: Number(c.id),
    vehicleId: Number(c.vehicle_id),
    command: c.command,
    status: c.status,
    note: c.note,
    createdAt: Number(c.created_at),
    acknowledgedAt: Number(c.acknowledged_at) || null,
  };
}