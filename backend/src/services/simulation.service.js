// ─────────────────────────────────────────────────────────────────────────
// Simulação de veículos pelo próprio backend.
//
// Diferente do hardware-simulator.js (um processo por device, iniciado pelo
// api.sh), este serviço move os veículos que o USUÁRIO adicionou à simulação
// pelo app — sem exigir hardware nem processo externo.
//
// Como distinguir as duas origens? O device criado aqui usa
// `model = 'server-sim'`. O api.sh ignora esses devices (não sobe um
// hardware-simulator para eles), evitando telemetria duplicada.
// ─────────────────────────────────────────────────────────────────────────
import { randomBytes } from 'node:crypto';
import { db } from '../db/database.js';
import { config } from '../config.js';
import { HttpError } from '../util/http-error.js';
import { ingestTelemetry } from './telemetry.service.js';
import { acknowledgeCommand, getPendingCommandsForDevice } from './command.service.js';

export const SERVER_SIM_MODEL = 'server-sim';

// Estado do movimento de cada veículo simulado (mantido em memória).
// Chave: vehicleId.
const moveState = new Map();

function deviceOf(vehicleId) {
  return db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(vehicleId);
}

function isServerSimDevice(device) {
  return Boolean(device) && device.model === SERVER_SIM_MODEL;
}

// ── Decisão de simulação ────────────────────────────────────────────────

// Responde se o veículo deve (ou pode) entrar na frota simulada. É o que o
// app consulta após cadastrar um veículo ("pergunta ao backend").
export function getSimulationDecision(vehicle) {
  const device = deviceOf(vehicle.id);
  const simulated = vehicle.simulated === 1;
  const serverSimulated = isServerSimDevice(device);
  const hasHardware = Boolean(device) && !serverSimulated;

  let recommended = false;
  let reason;
  if (!config.simulationEnabled) {
    reason = 'Simulação desativada no servidor (SIMULATION_ENABLED=false).';
  } else if (hasHardware) {
    reason = 'O veículo já possui um rastreador (hardware) vinculado; mantenha como teste real.';
  } else if (simulated || serverSimulated) {
    recommended = true;
    reason = 'O veículo já está na frota simulada e continua sendo movimentado pelo backend.';
  } else {
    recommended = true;
    reason =
      'O veículo ainda não tem rastreador. Adicionar à simulação cria um ' +
      'rastreador virtual que o movimenta automaticamente para demonstração.';
  }

  return {
    vehicleId: Number(vehicle.id),
    simulated,
    hasDevice: Boolean(device),
    deviceSimulated: serverSimulated,
    simulationAvailable: config.simulationEnabled,
    recommended,
    reason,
  };
}

// ── Aplicar a escolha do app ────────────────────────────────────────────

function startPositionFor(vehicle) {
  const base = { lat: Number(vehicle.latitude), lng: Number(vehicle.longitude) };
  if (base.lat && base.lng) return base;
  return {
    lat: config.simulatorStartLat + (Math.random() - 0.5) * 0.02,
    lng: config.simulatorStartLng + (Math.random() - 0.5) * 0.02,
  };
}

export function applySimulationDecision(vehicle, simulate) {
  let device = deviceOf(vehicle.id);

  if (simulate) {
    if (!config.simulationEnabled) {
      throw new HttpError(409, 'Simulação desativada no servidor (SIMULATION_ENABLED=false)');
    }
    if (device && !isServerSimDevice(device)) {
      throw new HttpError(409, 'Este veículo já possui um rastreador (hardware) vinculado');
    }

    const now = Date.now();
    if (!device) {
      const deviceId = `vt_${randomBytes(8).toString('hex')}`;
      const apiKey = randomBytes(24).toString('hex');
      const pos = startPositionFor(vehicle);
      const info = db
        .prepare(
          `INSERT INTO devices (device_id, api_key, vehicle_id, model, simulated, created_at)
           VALUES (?, ?, ?, ?, 1, ?)`
        )
        .run(deviceId, apiKey, vehicle.id, SERVER_SIM_MODEL, now);
      device = db.prepare('SELECT * FROM devices WHERE id = ?').get(Number(info.lastInsertRowid));
      db.prepare(
        `UPDATE vehicles SET latitude = ?, longitude = ?, updated_at = ? WHERE id = ?`
      ).run(pos.lat, pos.lng, now, vehicle.id);
    }

    db.prepare('UPDATE vehicles SET simulated = 1, updated_at = ? WHERE id = ?').run(now, vehicle.id);
    // Reinicia o estado de movimento a partir da posição atual.
    moveState.delete(Number(vehicle.id));
  } else {
    // Volta a ser "teste real": remove apenas o rastreador virtual criado aqui,
    // preservando qualquer hardware real eventualmente vinculado. O histórico de
    // comandos/telemetria do veículo é mantido.
    if (isServerSimDevice(device)) {
      db.prepare('DELETE FROM devices WHERE id = ?').run(device.id);
      device = null;
    }
    db.prepare('UPDATE vehicles SET simulated = 0, updated_at = ? WHERE id = ?').run(
      Date.now(),
      vehicle.id
    );
    moveState.delete(Number(vehicle.id));
  }

  const updated = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(vehicle.id);
  return { vehicle: updated, device };
}

// ── Loop de movimentação ────────────────────────────────────────────────

function stateFor(vehicle) {
  const id = Number(vehicle.id);
  let s = moveState.get(id);
  if (!s) {
    s = {
      lat: Number(vehicle.latitude) || config.simulatorStartLat,
      lng: Number(vehicle.longitude) || config.simulatorStartLng,
      heading: 45,
      speed: 0,
      step: 0,
    };
    moveState.set(id, s);
  }
  return s;
}

// Mesma dinâmica do hardware-simulator.js: acelera até ~40 km/h, faz curvas
// suaves e reduz em paradas periódicas.
function advance(s, dtSec) {
  s.step++;
  const cycle = s.step % 60;
  if (cycle < 4) {
    s.speed = 0;
  } else if (s.speed < 40) {
    s.speed = Math.min(s.speed + 3, 40);
    s.heading = (s.heading + 5) % 360;
  } else if (cycle > 50) {
    s.speed = Math.max(s.speed - 6, 0);
  } else {
    s.heading = (s.heading + 1.2) % 360;
  }
  const ms = s.speed / 3.6;
  const kmPerDegLat = 111320;
  const kmPerDegLng = 111320 * Math.cos((s.lat * Math.PI) / 180);
  s.lat += (ms * dtSec * Math.cos((s.heading * Math.PI) / 180)) / kmPerDegLat;
  s.lng += (ms * dtSec * Math.sin((s.heading * Math.PI) / 180)) / kmPerDegLng;
}

// Sincroniza o controle remoto com o rastreador virtual: pega os comandos
// pendentes (block/unblock) da fila do backend, "executa" e confirma (ACK).
// O ACK atualiza vehicles.is_blocked e notifica o app via SSE.
function processPendingCommands(device) {
  const pending = getPendingCommandsForDevice(device);
  for (const cmd of pending) {
    try {
      acknowledgeCommand(device, cmd.id, {
        ok: true,
        note: 'executado pelo rastreador virtual (backend)',
      });
      console.log(`[sim-bot] veículo ${device.vehicle_id}: comando "${cmd.command}" confirmado`);
    } catch (e) {
      console.warn(`[sim-bot] veículo ${device.vehicle_id}: falha no comando ${cmd.id}:`, e.message);
    }
  }
  return pending.length;
}

export function simulationTick() {
  if (!config.simulationEnabled) return 0;
  const vehicles = db
    .prepare(
      `SELECT v.* FROM vehicles v
       JOIN devices d ON d.vehicle_id = v.id
       WHERE v.simulated = 1 AND d.simulated = 1 AND d.model = ?
       ORDER BY v.id`
    )
    .all(SERVER_SIM_MODEL);

  const dt = config.simulationIntervalMs / 1000;
  let moved = 0;
  for (const v of vehicles) {
    try {
      const device = deviceOf(v.id);
      if (!device) continue;

      // Primeiro aplica o controle remoto (block/unblock), se houver.
      processPendingCommands(device);

      // Relê o veículo: um comando pode ter alterado is_blocked.
      const vehicle = db.prepare('SELECT * FROM vehicles WHERE id = ?').get(v.id);
      const s = stateFor(vehicle);

      if (vehicle.is_blocked === 1) {
        // Bloqueado: permanece parado, mas segue "online" reportando a posição,
        // exatamente como um rastreador com a ignição cortada.
        s.speed = 0;
        ingestTelemetry({
          device,
          latitude: Number(s.lat.toFixed(6)),
          longitude: Number(s.lng.toFixed(6)),
          speed: 0,
          heading: Number(s.heading.toFixed(1)),
          battery: 12.6,
          timestamp: Date.now(),
        });
        continue;
      }

      advance(s, dt);
      ingestTelemetry({
        device,
        latitude: Number(s.lat.toFixed(6)),
        longitude: Number(s.lng.toFixed(6)),
        speed: Number(s.speed.toFixed(1)),
        heading: Number(s.heading.toFixed(1)),
        battery: 12.6,
        timestamp: Date.now(),
      });
      moved++;
    } catch (e) {
      console.warn(`[sim-bot] veículo ${v.id} falhou:`, e.message);
    }
  }
  return moved;
}

// Inicia o loop (chamado no boot do servidor). Retorna o timer ou null.
export function startVehicleSimulation() {
  if (!config.simulationEnabled) {
    console.log('[sim-bot] desativado (SIMULATION_ENABLED=false)');
    return null;
  }
  const timer = setInterval(() => {
    try {
      simulationTick();
    } catch (e) {
      console.warn('[sim-bot] erro no tick:', e.message);
    }
  }, config.simulationIntervalMs);
  console.log(`[sim-bot] movendo veículos simulados a cada ${config.simulationIntervalMs}ms`);
  return timer;
}
