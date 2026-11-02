// Frota de rastreadores virtuais (model='server-sim').
//
// Cada veículo em simulação precisa de um processo `hardware-simulator.js`
// falando com a API (telemetria + ACK de block/unblock). Antes isso só
// nascia no `api.sh start`; o painel criar o device não movimentava nada
// até um restart. Agora a própria API sobe e derruba os simuladores.
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from '../config.js';
import { getFirebase } from '../firebase/firebase-admin.js';
import { rememberDevice, forgetDevice } from './device-key-cache.js';

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SIMULATOR = path.join(DIR, 'src', 'hardware-simulator.js');
const children = new Map();

function canSpawn() {
  if (process.env.VERCEL) return false;
  if (!config.simulationEnabled) return false;
  return (process.env.START_SIMULATOR || 'true').toLowerCase() !== 'false';
}

function pipeTagged(stream, deviceId, write) {
  stream.on('data', (buf) => {
    for (const line of String(buf).split('\n')) {
      if (line) write(`[sim ${deviceId}] ${line}\n`);
    }
  });
}

/** Sobe o rastreador virtual deste device, se ainda não estiver no ar. */
export function startSimulatedDevice(device) {
  if (!canSpawn() || !device?.deviceId || !device?.apiKey) return false;
  const id = String(device.deviceId);
  if (children.has(id) && !children.get(id).killed) return true;

  const child = spawn(process.execPath, [SIMULATOR], {
    cwd: DIR,
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
  child.on('exit', (code, signal) => {
    if (children.get(id) === child) children.delete(id);
    console.log(`[sim-fleet] ${id} encerrou (code=${code} signal=${signal || '-'})`);
  });
  children.set(id, child);
  rememberDevice(device);
  console.log(`[sim-fleet] ${id} no ar (pid ${child.pid}) veículo ${device.vehicleId ?? '?'}`);
  return true;
}

/** Derruba o rastreador virtual deste device. */
export function stopSimulatedDevice(deviceId) {
  const id = String(deviceId || '');
  if (!id) return false;
  const child = children.get(id);
  forgetDevice(id);
  if (!child) return false;
  child.kill('SIGTERM');
  children.delete(id);
  return true;
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
  for (const d of selected) startSimulatedDevice(d);
  console.log(`[sim-fleet] ${selected.length} rastreador(es) virtual(is) ativo(s)`);
}

export function stopSimulationFleet() {
  for (const [id, child] of children) {
    child.kill('SIGTERM');
    children.delete(id);
  }
}
