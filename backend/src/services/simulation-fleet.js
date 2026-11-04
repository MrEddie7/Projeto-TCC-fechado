// Frota de rastreadores virtuais (model='server-sim').
//
// Cada veículo em simulação precisa de um processo `hardware-simulator.js`
// falando com a API (telemetria + ACK de block/unblock). Antes isso só
// nascia no `api.sh start`; o painel criar o device não movimentava nada
// até um restart. Agora a própria API sobe e derruba os simuladores.
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from '../config.js';
import { getFirebase } from '../firebase/firebase-admin.js';
import { rememberDevice, forgetDevice } from './device-key-cache.js';

// `import.meta.url` aponta para backend/src/services/simulation-fleet.js.
// O simulador fica em backend/src/hardware-simulator.js — por isso o caminho
// é SRC_DIR/hardware-simulator.js e NÃO SRC_DIR/src/hardware-simulator.js.
const SRC_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SIMULATOR = path.join(SRC_DIR, 'hardware-simulator.js');
const children = new Map();
const desired = new Map(); // deviceId -> device, para o respawn saber o que subir
const pendingRestarts = new Map();
const stopping = new Set();
const MAX_RESTARTS = 3;

function canSpawn() {
  if (process.env.VERCEL) return false;
  if (!config.simulationEnabled) return false;
  return (process.env.START_SIMULATOR || 'true').toLowerCase() !== 'false';
}

/**
 * A API consegue subir/derrubar processos de simulador neste runtime?
 *
 * Diferente de `config.simulationEnabled`: na Vercel a simulação pode estar
 * habilitada (criar rastreador virtual + gerar apiKey), mas processos não
 * sobrevivem entre requisições — nesse caso é `false` e o app/painel avisa
 * que o simulador precisa rodar em outra máquina.
 */
export function simulationRunnerAvailable() {
  return canSpawn();
}

function pipeTagged(stream, deviceId, write) {
  stream.on('data', (buf) => {
    for (const line of String(buf).split('\n')) {
      if (line) write(`[sim ${deviceId}] ${line}\n`);
    }
  });
}

function clearRestart(deviceId) {
  const state = pendingRestarts.get(deviceId);
  if (!state) return;
  if (state.timer) clearTimeout(state.timer);
  pendingRestarts.delete(deviceId);
}

/** Reagenda o simulador quando ele cai sozinho (API/Firebase transitório). */
function scheduleRestart(deviceId) {
  if (stopping.has(deviceId) || !canSpawn()) return;
  const state = pendingRestarts.get(deviceId) || { attempts: 0, timer: null };
  if (state.timer) return;
  if (state.attempts >= MAX_RESTARTS) {
    console.error(
      `[sim-fleet] ${deviceId} não voltou após ${MAX_RESTARTS} tentativas; ` +
        'remova/adicione a simulação para tentar de novo'
    );
    pendingRestarts.delete(deviceId);
    return;
  }

  state.attempts += 1;
  const delay = Math.min(2000 * state.attempts, 10000);
  state.timer = setTimeout(() => {
    state.timer = null;
    const device = desired.get(deviceId);
    if (!device || stopping.has(deviceId)) {
      pendingRestarts.delete(deviceId);
      return;
    }
    startSimulatedDevice(device, { retry: true });
  }, delay);
  if (state.timer.unref) state.timer.unref();
  pendingRestarts.set(deviceId, state);
  console.warn(
    `[sim-fleet] ${deviceId} tentará subir de novo em ${delay}ms ` +
      `(${state.attempts}/${MAX_RESTARTS})`
  );
}

/** Sobe o rastreador virtual deste device, se ainda não estiver no ar. */
export function startSimulatedDevice(device, { retry = false } = {}) {
  if (!canSpawn() || !device?.deviceId || !device?.apiKey) return false;
  const id = String(device.deviceId);
  desired.set(id, device);

  if (!retry) {
    // Chamada manual (painel/app): zera o histórico de falhas e o stop anterior.
    clearRestart(id);
    stopping.delete(id);
  }

  const current = children.get(id);
  if (current && current.exitCode === null && !current.killed) return true;

  if (!fs.existsSync(SIMULATOR)) {
    console.error(`[sim-fleet] simulador não encontrado em ${SIMULATOR}`);
    return false;
  }

  rememberDevice(device);
  const child = spawn(process.execPath, [SIMULATOR], {
    cwd: SRC_DIR,
    env: {
      ...process.env,
      VTSIM_DEVICE_ID: id,
      VTSIM_API_KEY: String(device.apiKey),
      VTSIM_API_URL: `http://127.0.0.1:${config.port}`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  pipeTagged(child.stdout, id, (s) => process.stdout.write(s));
  pipeTagged(child.stderr, id, (s) => process.stderr.write(s));

  // Só considera "no ar" quando o processo realmente iniciou. O log antigo
  // aparecia mesmo quando o Node morria em seguida com MODULE_NOT_FOUND.
  child.once('spawn', () => {
    console.log(`[sim-fleet] ${id} no ar (pid ${child.pid}) veículo ${device.vehicleId ?? '?'}`);
  });

  child.once('error', (err) => {
    if (children.get(id) === child) children.delete(id);
    console.error(`[sim-fleet] ${id} falhou ao iniciar: ${err.message}`);
    if (!stopping.has(id)) scheduleRestart(id);
  });

  child.once('exit', (code, signal) => {
    const wasCurrent = children.get(id) === child;
    if (wasCurrent) children.delete(id);
    console.log(`[sim-fleet] ${id} encerrou (code=${code} signal=${signal || '-'})`);
    if (wasCurrent && !stopping.has(id)) scheduleRestart(id);
    if (stopping.has(id)) stopping.delete(id);
  });

  children.set(id, child);
  return true;
}

/** Derruba o rastreador virtual deste device. */
export function stopSimulatedDevice(deviceId) {
  const id = String(deviceId || '');
  if (!id) return false;
  stopping.add(id);
  desired.delete(id);
  clearRestart(id);
  forgetDevice(id);
  const child = children.get(id);
  if (!child) return false;

  child.kill('SIGTERM');
  children.delete(id);
  // Garante que um simulador travado não fique órfão.
  const force = setTimeout(() => {
    try { child.kill('SIGKILL'); } catch { /* já morreu */ }
  }, 3000);
  if (force.unref) force.unref();
  child.once('exit', () => clearTimeout(force));
  return true;
}

/** Estado atual do runner para um device (usado pela API/painel). */
export function simulationRunner(deviceId) {
  const child = children.get(String(deviceId || ''));
  const running = Boolean(child && child.exitCode === null && !child.killed);
  return { started: running, pid: running ? child.pid : null };
}

/** Sobe um simulador para cada device `server-sim` já cadastrado. */
export async function startSimulationFleet() {
  if (!canSpawn()) {
    console.log('[sim-fleet] inativo (Vercel, SIMULATION_ENABLED=false ou START_SIMULATOR=false)');
    return;
  }
  const fb = getFirebase();
  if (!fb) {
    console.warn('[sim-fleet] Firebase indisponível; frota virtual não iniciou');
    return;
  }

  let devices = [];
  try {
    const snap = await fb.firestore.collection('devices').get();
    devices = snap.docs
      .map((d) => ({ deviceId: d.id, ...d.data() }))
      .filter((d) => d.model === 'server-sim' && d.apiKey && d.vehicleId != null);
  } catch (e) {
    console.warn(`[sim-fleet] não foi possível listar devices: ${e.message}`);
    return;
  }

  const fixedId = process.env.VTSIM_DEVICE_ID;
  const fixedKey = process.env.VTSIM_API_KEY;
  if (fixedId && fixedKey) {
    const found = devices.find((d) => d.deviceId === fixedId);
    devices = found
      ? [found]
      : [{ deviceId: fixedId, apiKey: fixedKey, vehicleId: 0, model: 'server-sim' }];
  }

  const limit = Number(process.env.SIM_FLEET || 0);
  const selected = limit > 0 ? devices.slice(0, limit) : devices;
  let started = 0;
  for (const d of selected) {
    if (startSimulatedDevice(d)) started += 1;
  }
  console.log(`[sim-fleet] ${started}/${selected.length} rastreador(es) virtual(is) ativo(s)`);
}

export function stopSimulationFleet() {
  for (const [id, child] of children) {
    stopping.add(id);
    desired.delete(id);
    clearRestart(id);
    child.kill('SIGTERM');
    children.delete(id);
  }
}
