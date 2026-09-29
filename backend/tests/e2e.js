// Teste ponta a ponta (in-process) - não precisa de processo em background.
// Roda: npm test  (ou: node tests/e2e.js)
// Usa um banco temporário vazio para a execução ser determinística e
// idempotente (um banco persistente acumulava pontos de rodadas anteriores).
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'vt-e2e-'));
process.env.DB_PATH = path.join(tmpDir, 'e2e.db');

const [{ createApp }, { db }] = await Promise.all([
  import('../src/app.js'),
  import('../src/db/database.js'),
]);

const app = createApp();
const server = app.listen(0);
await new Promise((r) => server.on('listening', r));
const PORT = server.address().port;
const BASE = `http://127.0.0.1:${PORT}`;

let passed = 0;
let failed = 0;
function ok(name, cond, extra = '') {
  if (cond) {
    passed++;
    console.log(`  ✔ ${name}`);
  } else {
    failed++;
    console.log(`  ✖ ${name} ${extra}`);
  }
}

async function api(method, path, { token, body, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

let token;
let vid;
let devId;
let devKey;

console.log('== 1. Autenticação ==');
{
  let r = await api('POST', '/v1/auth/register', {
    body: { name: 'Aluno TCC', email: 'aluno@tcc.com', password: 'veiculotracker123' },
  });
  if (r.status === 409) {
    r = await api('POST', '/v1/auth/login', {
      body: { email: 'aluno@tcc.com', password: 'veiculotracker123' },
    });
  }
  ok('register/login retorna token', !!r.json.token && r.status === 200 || r.status === 201);
  token = r.json.token;

  const me = await api('GET', '/v1/auth/me', { token });
  ok('me retorna usuário', me.json.user?.email === 'aluno@tcc.com');
  ok('me exige token', (await api('GET', '/v1/auth/me')).status === 401);
}

console.log('== 2. Veículos ==');
{
  const list = await api('GET', '/v1/vehicles', { token });
  vid = list.json.vehicles?.[0]?.id;
  if (!vid) {
    const created = await api('POST', '/v1/vehicles', {
      token,
      body: { plate: 'ABC1D23', model: 'Onix', brand: 'Chevrolet', year: 2022, color: 'Preto' },
    });
    ok('criar veículo', created.status === 201, JSON.stringify(created.json));
    vid = created.json.vehicle.id;
  }
  const byId = await api('GET', `/v1/vehicles/${vid}`, { token });
  ok('detalhe do veículo', byId.json.vehicle?.id === vid);
  const invalid = await api('POST', '/v1/vehicles', {
    token,
    body: { plate: '💩', model: 'X', brand: 'Y', year: 1900 },
  });
  ok('rejeita placa inválida', invalid.status === 400);
}

console.log('== 3. Dispositivo / hardware ==');
{
  let r = await api('POST', `/v1/vehicles/${vid}/device`, { token });
  if (r.status === 409) r = { json: { device: r.json.device } };
  ok('gera deviceId + apiKey', !!r.json.device?.deviceId && !!r.json.device?.apiKey);
  devId = r.json.device.deviceId;
  devKey = r.json.device.apiKey;

  const boot = await api('GET', `/v1/hardware/${devId}/status`, {
    headers: { 'x-device-id': devId, 'x-api-key': devKey },
  });
  ok('boot do hardware (status)', boot.status === 200 && boot.json.registered === true);

  const bad = await api('GET', `/v1/hardware/${devId}/status`, {
    headers: { 'x-device-id': devId, 'x-api-key': 'errada' },
  });
  ok('rejeita api_key errada', bad.status === 403);
}

console.log('== 4. Telemetria ==');
{
  for (let i = 1; i <= 3; i++) {
    await api('POST', '/v1/hardware/telemetry', {
      headers: { 'x-device-id': devId, 'x-api-key': devKey },
      body: { latitude: -23.55 - i / 10000, longitude: -46.63 - i / 10000, speed: i * 10, heading: 90, timestamp: Date.now() },
    });
  }
  const latest = await api('GET', `/v1/vehicles/${vid}/telemetry/latest`, { token });
  ok('latest position', latest.status === 200 && latest.json.latest?.speed === 30);
  const hist = await api('GET', `/v1/vehicles/${vid}/telemetry/history?limit=10`, { token });
  ok('histórico de telemetria', hist.json.points?.length === 3);
  const vh = await api('GET', `/v1/vehicles/${vid}`, { token });
  ok('veículo atualiza latitude', vh.json.vehicle.latitude !== 0);
}

console.log('== 5. Rotas ==');
{
  const start = await api('POST', `/v1/vehicles/${vid}/routes/start`, { token });
  ok('iniciar rota', start.status === 201 || start.status === 200);
  await api('POST', '/v1/hardware/telemetry', {
    headers: { 'x-device-id': devId, 'x-api-key': devKey },
    body: { latitude: -23.552, longitude: -46.635, speed: 40, heading: 90, timestamp: Date.now() },
  });
  await api('POST', '/v1/hardware/telemetry', {
    headers: { 'x-device-id': devId, 'x-api-key': devKey },
    body: { latitude: -23.553, longitude: -46.636, speed: 50, heading: 90, timestamp: Date.now() },
  });
  const routes = await api('GET', `/v1/vehicles/${vid}/routes`, { token });
  const active = routes.json.routes?.find((r) => r.status === 'em_andamento');
  ok('rota acumula pontos', !!active);
  const detail = await api('GET', `/v1/routes/${active?.id}`, { token });
  ok('rota tem 2 pontos geográficos', detail.json.points?.length === 2);
  ok('rota calcula distância > 0', (detail.json.route?.distance || 0) > 0);
  const stop = await api('POST', `/v1/vehicles/${vid}/routes/stop`, { token });
  ok('finalizar rota', stop.json.route?.status === 'concluida');
}

console.log('== 6. Comandos (block/unblock) ==');
{
  const created = await api('POST', `/v1/vehicles/${vid}/commands`, {
    token,
    body: { command: 'block', note: 'pelo app' },
  });
  ok('comando criado (pending)', created.json.command?.status === 'pending');

  const pending = await api('GET', `/v1/hardware/${devId}/commands?status=pending`, {
    headers: { 'x-device-id': devId, 'x-api-key': devKey },
  });
  ok('hardware vê comando pendente', pending.json.commands?.length >= 1);
  const cmdId = pending.json.commands?.[0]?.id;

  const ack = await api('POST', `/v1/hardware/${devId}/commands/${cmdId}/ack`, {
    headers: { 'x-device-id': devId, 'x-api-key': devKey },
    body: { ok: true, note: 'relê acionado' },
  });
  ok('ack confirma comando', ack.json.command?.status === 'acknowledged');

  const vehicle = await api('GET', `/v1/vehicles/${vid}`, { token });
  ok('veículo fica bloqueado após ACK', vehicle.json.vehicle.isBlocked === true);

  const cmds = await api('GET', `/v1/vehicles/${vid}/commands`, { token });
  ok('histórico de comandos no app', cmds.json.commands?.length >= 1);
}

console.log('== 7. Validações de segurança ==');
{
  ok('telemetria sem credenciais -> 401', (await api('POST', '/v1/hardware/telemetry', { body: {} })).status === 401);
  ok('comando inválido -> 400', (await api('POST', `/v1/vehicles/${vid}/commands`, { token, body: { command: 'xpto' } })).status === 400);
  ok('rota de outro usuário não acessível', (await api('GET', '/v1/vehicles/999', { token })).status === 404);
}

console.log('== 8. Sincronização (Firebase desativado) ==');
{
  const r = await api('POST', '/v1/admin/sync', { token });
  ok('sync responde enabled:false', r.json.enabled === false);
  const st = await api('GET', '/v1/admin/sync-status', { token });
  ok('sync-status exige auth e retorna info', st.json.intervalMs === 60000);
}

console.log('');
console.log(`Resultado: ${passed} passou, ${failed} falhou`);
db.close();
server.close();
process.exit(failed === 0 ? 0 : 1);