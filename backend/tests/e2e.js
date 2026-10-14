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

console.log('== 9. Painel administrativo (overview) ==');
{
  ok('overview exige token', (await api('GET', '/v1/admin/overview')).status === 401);

  const r = await api('GET', '/v1/admin/overview', { token });
  ok('overview responde 200', r.status === 200, JSON.stringify(r.json).slice(0, 120));
  ok('overview traz estado do serviço', !!r.json.service?.name && r.json.service.port > 0);
  ok('overview conta usuários', r.json.counts?.users >= 1);
  ok('overview conta veículos', r.json.counts?.vehicles >= 1);

  const me = r.json.users?.find((u) => u.email === 'aluno@tcc.com');
  ok('overview lista usuário com veículos', !!me && me.vehicles.length >= 1);
  ok('overview veículo tem device', !!me?.vehicles?.[0]?.device?.deviceId);
  ok('overview veículo tem telemetria', me?.vehicles?.[0]?.telemetry?.speed !== undefined);

  ok('overview traz status das telas', Array.isArray(r.json.screens) && r.json.screens.length >= 10);
  const tracking = r.json.screens?.find((s) => s.id === 'tracking');
  ok('tela de rastreamento marcada como ok', tracking?.status === 'ok');
}

console.log('== 10. Localização do celular (/v1/mobile) ==');
{
  ok('mobile/location exige token', (await api('POST', '/v1/mobile/location', { body: {} })).status === 401);

  const bad = await api('POST', '/v1/mobile/location', {
    token,
    body: { latitude: 999, longitude: 0 },
  });
  ok('rejeita coordenadas inválidas', bad.status === 400);

  const created = await api('POST', '/v1/mobile/location', {
    token,
    body: { latitude: -23.561, longitude: -46.655, speed: 12, heading: 45, accuracy: 8 },
  });
  ok('grava localização do celular', created.status === 201 && created.json.location?.id > 0);

  const latest = await api('GET', '/v1/mobile/location/latest', { token });
  ok('lê última localização do celular', latest.json.location?.latitude === -23.561);
}

console.log('== 11. Visão do administrador (frota de todos os usuários) ==');
{
  ok('admin/vehicles exige token', (await api('GET', '/v1/admin/vehicles')).status === 401);

  // Segundo usuário cadastra um veículo próprio.
  const reg = await api('POST', '/v1/auth/register', {
    body: { name: 'Outro Motorista', email: 'outro@tcc.com', password: 'veiculotracker123' },
  });
  const outroToken = reg.json.token;
  const created = await api('POST', '/v1/vehicles', {
    token: outroToken,
    body: { plate: 'XYZ9Z99', model: 'Civic', brand: 'Honda', year: 2021, color: 'Prata' },
  });
  ok('segundo usuário cadastra veículo', created.status === 201, JSON.stringify(created.json));
  const outroVid = created.json.vehicle.id;

  // O veículo do outro usuário NÃO aparece na lista do app do primeiro usuário.
  const meu = await api('GET', '/v1/vehicles', { token });
  ok('app lista só os veículos do próprio usuário', !(meu.json.vehicles || []).some((v) => v.id === outroVid));

  // Mas APARECE na visão do administrador, com o dono identificado.
  const all = await api('GET', '/v1/admin/vehicles', { token });
  const seen = (all.json.vehicles || []).find((v) => v.id === outroVid);
  ok('admin vê veículo de outro usuário', !!seen && all.status === 200);
  ok('admin vê o dono do veículo', seen?.owner?.email === 'outro@tcc.com');

  // Acesso administrativo ao detalhe e à telemetria de qualquer veículo.
  const detail = await api('GET', `/v1/admin/vehicles/${outroVid}`, { token });
  ok('admin abre detalhe de veículo de outro usuário', detail.json.vehicle?.plate === 'XYZ9Z99');
  const tel = await api('GET', `/v1/admin/vehicles/${outroVid}/telemetry/latest`, { token });
  ok('admin lê telemetria de veículo de outro usuário', tel.status === 200);

  // A rota do app continua restrita ao dono (404 para o administrador logado).
  ok('rota do app continua restrita ao dono', (await api('GET', `/v1/vehicles/${outroVid}`, { token })).status === 404);

  // O overview (painel) também expõe a frota achatada com dono.
  const ov = await api('GET', '/v1/admin/overview', { token });
  const flat = (ov.json.vehicles || []).find((v) => v.id === outroVid);
  ok('overview traz frota achatada com dono', flat?.owner?.email === 'outro@tcc.com');
}

console.log('');
console.log(`Resultado: ${passed} passou, ${failed} falhou`);
db.close();
server.close();
process.exit(failed === 0 ? 0 : 1);