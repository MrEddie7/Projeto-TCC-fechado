// ─────────────────────────────────────────────────────────────────────────
//  VEICULOTRACKER - SIMULADOR DE HARDWARE GPS
//  Emula um rastreador veicular (ESP32 + módulo GPS + rede) publicando
//  telemetria HTTP na API e processando comandos (block/unblock).
//
//  Uso:
//    VTSIM_DEVICE_ID=vt_xxxx VTSIM_API_KEY=xxxx npm run simulator
//    node src/hardware-simulator.js --device-id vt_xxx --api-key xxx --url http://localhost:3000
// ─────────────────────────────────────────────────────────────────────────

import { config } from './config.js';

const args = process.argv.slice(2);
function argValue(name, def) {
  const i = args.indexOf(`--${name}`);
  if (i !== -1 && args[i + 1]) return args[i + 1];
  return def;
}

const DEVICE_ID =
  process.env.VTSIM_DEVICE_ID || argValue('device-id', '');
const API_KEY =
  process.env.VTSIM_API_KEY || argValue('api-key', '');
const API_URL =
  process.env.VTSIM_API_URL ||
  argValue('url', `http://localhost:${config.port}`);
const INTERVAL_MS = Number(process.env.VTSIM_INTERVAL_MS || config.simulatorIntervalMs);

if (!DEVICE_ID || !API_KEY) {
  console.error('Informe VTSIM_DEVICE_ID e VTSIM_API_KEY (gerados em POST /v1/vehicles/:id/device ou via npm run seed).');
  process.exit(1);
}

// Estado inicial da "viagem simulada"
let lat = config.simulatorStartLat;
let lng = config.simulatorStartLng;
let heading = 45; // graus (nordeste)
let speed = 0;
let step = 0;

function moveVehicle() {
  // Acelera até ~40 km/h, faz curvas suaves e reduz em "paradas" periódicas.
  step++;
  const cycle = step % 60;
  if (cycle < 4) {
    speed = 0; // parada no semáforo
  } else if (speed < 40) {
    speed = Math.min(speed + 3, 40);
    heading = (heading + 5) % 360;
  } else if (cycle > 50) {
    speed = Math.max(speed - 6, 0);
  } else {
    heading = (heading + 1.2) % 360;
  }
  const ms = speed / 3.6; // m/s
  const dt = INTERVAL_MS / 1000;
  const kmPerDegLat = 111320;
  const kmPerDegLng = 111320 * Math.cos((lat * Math.PI) / 180);
  lat += (ms * dt * Math.cos((heading * Math.PI) / 180)) / kmPerDegLat;
  lng += (ms * dt * Math.sin((heading * Math.PI) / 180)) / kmPerDegLng;
}

async function request(method, path, body) {
  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'x-device-id': DEVICE_ID,
      'x-api-key': API_KEY,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

async function pollCommands() {
  const { status, json } = await request('GET', `/v1/hardware/${DEVICE_ID}/commands?status=pending`);
  if (status !== 200) return 0;
  for (const cmd of json.commands || []) {
    console.log(`[sim] comando recebido: ${cmd.command} (id=${cmd.id})`);
    // Simula "execução física" no relê de corte de ignição.
    const ok = Math.random() > 0.05; // 5% de chance de falha para testes
    await request('POST', `/v1/hardware/${DEVICE_ID}/commands/${cmd.id}/ack`, {
      ok,
      note: ok ? 'executado pelo relê' : 'falha no atuador',
    });
    console.log(`[sim] confirmado ${ok ? 'ACK' : 'NACK'} para comando ${cmd.id}`);
  }
  return (json.commands || []).length;
}

async function sendTelemetry() {
  moveVehicle();
  const { status, json } = await request('POST', '/v1/hardware/telemetry', {
    latitude: Number(lat.toFixed(6)),
    longitude: Number(lng.toFixed(6)),
    speed: Number(speed.toFixed(1)),
    heading: Number(heading.toFixed(1)),
    battery: 12.6,
    timestamp: Date.now(),
  });
  console.log(
    `[sim] telemetria -> ${status} | ${lat.toFixed(6)}, ${lng.toFixed(6)} | ${speed.toFixed(1)} km/h`
  );
}

async function boot() {
  console.log(`[sim] conectando em ${API_URL} como ${DEVICE_ID}...`);
  const { status, json } = await request('GET', `/v1/hardware/${DEVICE_ID}/status`);
  if (status !== 200) {
    console.error(`[sim] falha no boot (${status}). Verifique device_id/api_key.`);
    console.error(json.error || '');
    process.exit(1);
  }
  console.log(
    `[sim] dispositivo registrado. Veículo: ${json.vehicle?.plate || 'sem vínculo'} | pendentes: ${json.pendingCommands}`
  );
}

await boot();
console.log(`[sim] enviando telemetria a cada ${INTERVAL_MS}ms (Ctrl+C para parar)`);

await sendTelemetry();
const timer = setInterval(async () => {
  try {
    await sendTelemetry();
    await pollCommands();
  } catch (e) {
    console.error('[sim] erro:', e.message);
  }
}, INTERVAL_MS);

process.on('SIGINT', () => {
  clearInterval(timer);
  process.exit(0);
});