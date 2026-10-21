// ─────────────────────────────────────────────────────────────────────────
//  SECURITAS - SIMULADOR DE HARDWARE GPS
//  Emula um rastreador veicular (ESP32 + módulo GPS + rede) publicando
//  telemetria HTTP na API e processando comandos (block/unblock).
//
//  Uso:
//    VTSIM_DEVICE_ID=vt_xxxx VTSIM_API_KEY=xxxx npm run simulator
//    node src/hardware-simulator.js --device-id vt_xxx --api-key xxx --url http://localhost:3000
// ─────────────────────────────────────────────────────────────────────────

import { config } from './config.js';
import { createRouteState, advanceOnRoute, getRoute, randomRouteIndex } from './util/road-routes.js';

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
  console.error('Informe VTSIM_DEVICE_ID e VTSIM_API_KEY (gerados em POST /v1/vehicles/:id/device).');
  process.exit(1);
}

// Estado inicial da "viagem simulada". O ponto de partida real vem do
// veículo (definido no boot), senão todos os simuladores da frota
// nasceriam empilhados no mesmo ponto e as rotas se sobreporiam no mapa.
let lat = config.simulatorStartLat;
let lng = config.simulatorStartLng;
let heading = 0;
let speed = 0;
let step = 0;
// Estado do relê de corte de ignição. Quando bloqueado, o veículo para de
// andar (mas continua reportando telemetria, como um rastreador real).
let blocked = false;

// Alvo de posição definido no painel (arrastar o veículo no mapa). Guardamos o
// carimbo (updatedAt) já aplicado para só reancorar quando ELE mudar.
let appliedControlAt = 0;

// Rota viária que este simulador vai percorrer. Determinada no boot a partir
// dos últimos caracteres do DEVICE_ID para distribuir dispositivos pelas vias.
const routeIndex = randomRouteIndex();
let routeState = createRouteState(lat, lng, routeIndex);

function moveVehicle() {
  // Percorre a via real usando waypoints GPS de road-routes.js.
  // Aceleração/desaceleração e paradas semafóricas periódicas.
  step++;
  const route = getRoute(routeIndex);
  const nominalSpeed = route.speedKmh;
  const cycle = step % 60;

  if (cycle < 4) {
    speed = 0; // semáforo vermelho
  } else if (speed < nominalSpeed) {
    speed = Math.min(speed + 5, nominalSpeed); // aceleração
  } else if (cycle > 52) {
    speed = Math.max(speed - 4, 0); // frenagem suave
  } else {
    // Cruzeiro com variação de tráfego ±3 km/h
    const jitter = Math.sin(step * 0.37) * 3;
    speed = Math.max(5, Math.min(nominalSpeed + jitter, nominalSpeed * 1.15));
  }

  if (speed < 0.5) return; // parado: mantém posição na via

  const dt = INTERVAL_MS / 1000;
  const distM = (speed / 3.6) * dt;
  const pos = advanceOnRoute(routeState, distM, speed);
  lat = pos.lat;
  lng = pos.lng;
  heading = pos.heading;
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
    // Só muda o estado físico se o atuador executou de fato (ACK).
    if (ok && cmd.command === 'block') {
      blocked = true;
      console.log('[sim] ignição CORTADA (veículo bloqueado)');
    } else if (ok && cmd.command === 'unblock') {
      blocked = false;
      console.log('[sim] ignição LIBERADA (veículo desbloqueado)');
    }
    console.log(`[sim] confirmado ${ok ? 'ACK' : 'NACK'} para comando ${cmd.id}`);
  }
  return (json.commands || []).length;
}

// Consulta o alvo de posição definido no painel (arrastar o veículo no mapa).
// Reancora apenas quando o carimbo muda, para não brigar com o trajeto normal.
async function pollPositionControl() {
  const res = await fetch(`${API_URL}/v1/hardware/${DEVICE_ID}/position`, {
    headers: { 'x-device-id': DEVICE_ID, 'x-api-key': API_KEY },
  });
  if (res.status !== 200) return; // 204 = sem alvo definido
  const target = await res.json().catch(() => null);
  if (!target || !Number.isFinite(Number(target.updatedAt))) return;
  if (Number(target.updatedAt) <= appliedControlAt) return;
  appliedControlAt = Number(target.updatedAt);
  lat = Number(target.latitude);
  lng = Number(target.longitude);
  // Reposiciona o trajeto na via mais próxima do ponto escolhido, mantendo a
  // rota atual do simulador.
  routeState = createRouteState(lat, lng, routeIndex);
  console.log(
    `[sim] posição definida pelo painel -> ${lat.toFixed(6)}, ${lng.toFixed(6)}`
  );
}

async function sendTelemetry() {
  // Antes de andar, verifica se o painel definiu uma nova posição (arrastar no
  // mapa). Se sim, reancora ali e segue percorrendo a via a partir dali.
  await pollPositionControl();
  if (blocked) {
    // Ignorado o avanço: o veículo permanece na posição, com velocidade 0.
    speed = 0;
  } else {
    moveVehicle();
  }
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
  // Retoma de onde o veículo parou, para não teleportar nem empilhar a frota.
  // Re-ancora o routeState ao waypoint mais próximo da posição atual.
  if (json.vehicle && json.vehicle.latitude && json.vehicle.longitude) {
    lat = Number(json.vehicle.latitude);
    lng = Number(json.vehicle.longitude);
    routeState = createRouteState(lat, lng, routeIndex);
    console.log(`[sim] retomando da posição atual: ${lat.toFixed(6)}, ${lng.toFixed(6)} | rota: ${getRoute(routeIndex).name}`);
  } else {
    console.log(`[sim] iniciando na posição padrão | rota: ${getRoute(routeIndex).name}`);
  }
  // Sincroniza o estado do relê com o que o backend já registrou.
  if (json.vehicle && json.vehicle.isBlocked) {
    blocked = true;
    console.log('[sim] veículo está BLOQUEADO no backend; ignição permanecerá cortada');
  }
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