// Painel administrativo do backend.
//
// Reúne, em uma única chamada, o que a aplicação web (raiz /) precisa mostrar:
//   1. estado do serviço da API em execução (uptime, porta, contadores);
//   2. usuários cadastrados e os veículos de cada um (com device e telemetria);
//   3. "saúde" de cada tela do sistema, inferida a partir dos dados reais dos
//      endpoints que a tela consome no app Android.
//
// É somente leitura: nenhuma query altera o banco. Qualquer falha de leitura
// isolada não derruba o painel inteiro (vira status "error" naquele item).

import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { db } from '../db/database.js';
import { config } from '../config.js';
import { getFirebase, getFirebaseInitError } from '../firebase/firebase-admin.js';

// Janela usada para considerar uma telemetria/localização "recente".
const RECENT_MS = 5 * 60 * 1000;

function readVersion() {
  try {
    const pkgPath = fileURLToPath(new URL('../../package.json', import.meta.url));
    return JSON.parse(fs.readFileSync(pkgPath, 'utf8')).version || null;
  } catch {
    return null;
  }
}

function scalar(sql, ...params) {
  const row = db.prepare(sql).get(...params);
  return Number(Object.values(row || {})[0] || 0);
}

function count(table, where = '', ...params) {
  return scalar(`SELECT COUNT(*) AS n FROM ${table} ${where}`, ...params);
}

function serviceInfo() {
  const uptimeSeconds = Math.round(process.uptime());
  return {
    name: 'veiculotracker-backend',
    version: readVersion(),
    node: process.version,
    platform: process.platform,
    pid: process.pid,
    env: process.env.NODE_ENV || 'development',
    port: config.port,
    dbPath: config.dbPath,
    uptimeSeconds,
    startedAt: Date.now() - uptimeSeconds * 1000,
    time: Date.now(),
  };
}

function counts() {
  const recent = Date.now() - RECENT_MS;
  return {
    users: count('users'),
    vehicles: count('vehicles'),
    // Divisão simulador x teste real (dados fora da simulação).
    simulatedVehicles: count('vehicles', 'WHERE simulated = 1'),
    realVehicles: count('vehicles', 'WHERE simulated = 0'),
    blockedVehicles: count('vehicles', 'WHERE is_blocked = 1'),
    onlineVehicles: count('vehicles', 'WHERE last_update >= ?', recent),
    devices: count('devices'),
    simulatedDevices: count('devices', 'WHERE simulated = 1'),
    realDevices: count('devices', 'WHERE simulated = 0'),
    activeDevices: count('devices', 'WHERE last_seen >= ?', recent),
    telemetryPoints: count('telemetry'),
    routes: count('routes'),
    activeRoutes: count('routes', "WHERE status = 'em_andamento'"),
    completedRoutes: count('routes', "WHERE status = 'concluida'"),
    commands: count('commands'),
    pendingCommands: count('commands', "WHERE status = 'pending'"),
    mobileLocations: count('mobile_locations'),
    recentMobileLocations: count('mobile_locations', 'WHERE timestamp >= ?', recent),
  };
}

function syncInfo() {
  const rows = db.prepare('SELECT key, value FROM sync_state').all();
  const marks = Object.fromEntries(rows.map((r) => [r.key, Number(r.value)]));
  return {
    enabled: config.firebaseSyncEnabled,
    intervalMs: config.firebaseSyncIntervalMs,
    lastRun: marks.last_run ?? null,
    watermarks: {
      users: marks.users ?? null,
      vehicles: marks.vehicles ?? null,
      routes: marks.routes ?? null,
    },
    credentials: getFirebase() ? 'ok' : `ausente (${getFirebaseInitError()})`,
    databaseUrl: config.firebaseDatabaseUrl || null,
    firestoreId: config.firestoreId,
  };
}

// Usuários + veículos + device + última telemetria de cada veículo
// + última localização (GPS) do celular de cada usuário.
function usersWithVehicles() {
  const users = db
    .prepare('SELECT id, name, email, phone, created_at FROM users ORDER BY id ASC')
    .all();

  return users.map((u) => {
    const vehicles = db
      .prepare('SELECT * FROM vehicles WHERE user_id = ? ORDER BY created_at DESC')
      .all(u.id);
    const lastMobile = db
      .prepare('SELECT * FROM mobile_locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1')
      .get(u.id);

    const mappedVehicles = vehicles.map((v) => {
      const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
      const last = db
        .prepare('SELECT * FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT 1')
        .get(v.id);
      return {
        id: Number(v.id),
        plate: v.plate,
        brand: v.brand,
        model: v.model,
        year: Number(v.year),
        color: v.color,
        isBlocked: v.is_blocked === 1,
        // true = frota do simulador; false = teste real (fora da simulação).
        simulated: v.simulated === 1,
        latitude: v.latitude,
        longitude: v.longitude,
        speed: v.speed,
        lastUpdate: Number(v.last_update),
        online: Number(v.last_update) >= Date.now() - RECENT_MS,
        device: device
          ? {
              deviceId: device.device_id,
              firmware: device.firmware,
              model: device.model,
              lastSeen: Number(device.last_seen),
              simulated: device.simulated === 1,
            }
          : null,
        telemetry: last
          ? {
              latitude: last.latitude,
              longitude: last.longitude,
              speed: last.speed,
              battery: last.battery,
              timestamp: Number(last.timestamp),
            }
          : null,
      };
    });

    return {
      id: Number(u.id),
      name: u.name,
      email: u.email,
      phone: u.phone,
      createdAt: Number(u.created_at),
      // O usuário tem dados de TESTE REAL quando há posição do celular (que só
      // entra pelo app, nunca pelo simulador de hardware) ou veículo não marcado
      // como simulado.
      realTest: Boolean(lastMobile) || mappedVehicles.some((v) => !v.simulated),
      // Última posição conhecida do celular deste usuário, para o painel
      // mostrar o aparelho "junto do veículo".
      mobileLocation: lastMobile
        ? {
            latitude: lastMobile.latitude,
            longitude: lastMobile.longitude,
            speed: lastMobile.speed,
            heading: lastMobile.heading,
            accuracy: lastMobile.accuracy,
            timestamp: Number(lastMobile.timestamp),
            source: lastMobile.source,
          }
        : null,
      vehicles: mappedVehicles,
    };
  });
}

// "Saúde" por tela do sistema. Cada tela do app tem um endpoint de referência
// e um sinal de dado que indica se ela funciona (ok), está sem dados (warn) ou
// com problema (error). Não executa efeitos colaterais.
function screensHealth(c) {
  const screens = [
    {
      id: 'login',
      name: 'Login',
      endpoint: 'POST /v1/auth/login',
      status: 'ok',
      detail: `${c.users} usuário(s) cadastrado(s)`,
    },
    {
      id: 'register',
      name: 'Registro',
      endpoint: 'POST /v1/auth/register',
      status: 'ok',
      detail: 'Autenticação local (scrypt) e sessão de 30 dias',
    },
    {
      id: 'dashboard',
      name: 'Painel / Dashboard',
      endpoint: 'GET /v1/vehicles',
      status: c.vehicles > 0 ? 'ok' : 'warn',
      detail: c.vehicles > 0 ? `${c.vehicles} veículo(s) na frota` : 'Nenhum veículo cadastrado',
    },
    {
      id: 'tracking',
      name: 'Rastreamento',
      endpoint: 'GET /v1/vehicles/:id/telemetry/stream',
      status: c.onlineVehicles > 0 ? 'ok' : 'warn',
      detail:
        c.onlineVehicles > 0
          ? `${c.onlineVehicles} veículo(s) enviando GPS agora (${c.simulatedVehicles} do simulador, ${c.realVehicles} teste real)`
          : 'Sem telemetria nos últimos 5 min',
    },
    {
      id: 'remote',
      name: 'Controle Remoto',
      endpoint: 'POST /v1/vehicles/:id/commands',
      status: c.pendingCommands > 0 ? 'warn' : 'ok',
      detail:
        c.pendingCommands > 0
          ? `${c.pendingCommands} comando(s) aguardando ACK do hardware`
          : `${c.commands} comando(s) processado(s)`,
    },
    {
      id: 'history',
      name: 'Histórico de Rotas',
      endpoint: 'GET /v1/routes/:id',
      status: c.routes > 0 ? 'ok' : 'warn',
      detail: c.routes > 0 ? `${c.routes} rota(s) (${c.activeRoutes} em andamento)` : 'Nenhuma rota registrada',
    },
    {
      id: 'vehicle',
      name: 'Cadastro de Veículo',
      endpoint: 'POST /v1/vehicles',
      status: 'ok',
      detail: 'Validação de placa Mercosul/tradicional ativa',
    },
    {
      id: 'vehicle-detail',
      name: 'Detalhe do Veículo',
      endpoint: 'GET /v1/vehicles/:id',
      status: c.vehicles > 0 ? 'ok' : 'warn',
      detail: c.devices > 0 ? `${c.devices} device(s) vinculado(s)` : 'Nenhum hardware vinculado',
    },
    {
      id: 'apilink',
      name: 'Linkagem de API',
      endpoint: 'GET /v1/hardware/:deviceId/status',
      status: c.devices > 0 ? 'ok' : 'warn',
      detail: c.devices > 0 ? 'Credenciais de hardware geradas' : 'Nenhum device para vincular',
    },
    {
      id: 'device-location',
      name: 'Localização do Celular',
      endpoint: 'GET /v1/mobile/location/latest',
      status: c.recentMobileLocations > 0 ? 'ok' : 'warn',
      detail:
        c.recentMobileLocations > 0
          ? `Teste real · recebendo posição do app agora (${c.recentMobileLocations} recente(s))`
          : `${c.mobileLocations} ponto(s) no histórico (teste real, fora do simulador)`,
    },
    {
      id: 'settings',
      name: 'Configurações',
      endpoint: 'local (app)',
      status: 'ok',
      detail: 'Preferências locais; sem dependência do backend',
    },
  ];

  return screens;
}

export function buildOverview() {
  const c = counts();
  const users = usersWithVehicles();

  // Lista achatada de TODOS os veículos, com o dono embutido. É o que o mapa
  // do administrador usa para exibir a frota inteira (de todos os usuários).
  const vehicles = [];
  for (const u of users) {
    for (const v of u.vehicles) {
      vehicles.push({ ...v, owner: { id: u.id, name: u.name, email: u.email } });
    }
  }

  return {
    service: serviceInfo(),
    counts: c,
    sync: syncInfo(),
    users,
    vehicles,
    screens: screensHealth(c),
  };
}
