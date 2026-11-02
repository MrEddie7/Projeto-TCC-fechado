// Visão do ADMINISTRADOR: acesso a QUALQUER veículo do sistema, de qualquer
// usuário. O painel web (raiz /) usa estas rotas para exibir e monitorar a
// frota inteira — não apenas os veículos do usuário autenticado.
//
// Depois da migração, tudo vem DIRETAMENTE do Firestore (coleções users,
// vehicles, devices, telemetry) — não existe mais SQLite. O app Android
// continua usando /v1/vehicles (que filtra pelo próprio dono); aqui é o
// "lado administrador".
import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { serializeVehicle, serializeDevice } from './vehicles.routes.js';
import { subscribeVehicle, publishToVehicle } from '../realtime/sse.js';
import { firestore, makeId } from '../services/firestore-store.js';
import { config } from '../config.js';
import { mirrorVehicles, mirrorDevice } from '../services/vehicle-cache.js';
import {
  buildOverview,
  listAllVehicles,
  getVehicleAny,
  latestTelemetry,
  telemetryHistory,
  rawVehicle,
  deviceOfVehicle,
  readCollection,
} from '../services/admin.service.js';
import {
  getSimulationDecision,
  applySimulationDecision,
  simulateFromBody,
  setVehiclePosition,
  getSimControl,
  syncFirebaseUsers,
  syncMobileDevicesToRtdb,
} from '../services/simulation.service.js';
import { COMMANDS, commandFromBody, enqueueCommand, serializeCommand } from '../services/commands.service.js';

const router = Router();
router.use(requireAuth);

// ── Visão geral (painel) ────────────────────────────────────────────────

router.get(
  '/overview',
  asyncHandler(async (_req, res) => {
    res.json(await buildOverview());
  })
);

// ── Frota: todos os veículos de todos os usuários ───────────────────────

router.get(
  '/vehicles',
  asyncHandler(async (_req, res) => {
    const vehicles = await listAllVehicles();
    res.json({ vehicles: vehicles.map(({ _raw, ...v }) => v) });
  })
);

router.get(
  '/vehicles/:id',
  asyncHandler(async (req, res) => {
    const detail = await getVehicleAny(req.params.id);
    const { telemetry, ...rest } = detail;
    // telemetry é o histórico completo; aqui só interessa o último ponto.
    const last = telemetry.sort((a, b) => Number(b.timestamp) - Number(a.timestamp))[0] || null;
    res.json({ ...rest, telemetry: last });
  })
);

router.get(
  '/vehicles/:id/telemetry/latest',
  asyncHandler(async (req, res) => {
    res.json(await latestTelemetry(req.params.id));
  })
);

router.get(
  '/vehicles/:id/telemetry/history',
  asyncHandler(async (req, res) => {
    res.json(
      await telemetryHistory(req.params.id, {
        limit: req.query.limit,
        after: req.query.after,
      })
    );
  })
);

// ── Comando de bloqueio (painel do administrador) ────────────────────────
//
// Diferente de /v1/vehicles/:id/commands (que exige o dono do veículo), aqui
// o painel admin comanda QUALQUER veículo da frota. O comando segue o mesmo
// caminho: fica "pending" até o rastreador confirmar (ACK).
router.post(
  '/vehicles/:id/commands',
  asyncHandler(async (req, res) => {
    const vehicle = await rawVehicle(req.params.id);
    const command = commandFromBody(req.body);
    if (!COMMANDS.includes(command)) throw new HttpError(400, 'Comando inválido');
    const data = await enqueueCommand(vehicle.id, command, req.body?.note || '');
    res.status(201).json({ command: serializeCommand(data) });
  })
);

// Stream em tempo real (SSE) de qualquer veículo. Mesmos eventos da rota do
// app: `presence`, `telemetry`, `blocked`, `command`. A telemetria ao vivo
// chega aqui quando o hardware/simulador publica via /v1/hardware/telemetry.
router.get('/vehicles/:id/telemetry/stream', asyncHandler(async (req, res) => {
  const vehicle = await rawVehicle(req.params.id);
  const vehicleId = Number(vehicle.id);
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  subscribeVehicle(vehicleId, res);
  publishToVehicle(vehicleId, 'presence', {
    connected: true,
    timestamp: Date.now(),
    vehicle: {
      id: vehicleId,
      plate: vehicle.plate,
      isBlocked: vehicle.isBlocked === true,
      latitude: Number(vehicle.latitude || 0),
      longitude: Number(vehicle.longitude || 0),
      speed: Number(vehicle.speed || 0),
      lastUpdate: Number(vehicle.lastUpdate || 0),
    },
  });
}));

// ── Sincronização com o celular (Firestore) ─────────────────────────────
//
// Não há mais importação para o SQLite: o Firestore JÁ é o banco da API.
// A rota permanece para o botão "Sincronizar do celular" do painel e devolve
// apenas um resumo do que existe na nuvem.
router.post(
  '/cloud/import',
  asyncHandler(async (_req, res) => {
    // Leitura tolerante à cota do Firestore: cai no espelho do RTDB.
    const [users, vehicles, devices] = await Promise.all([
      readCollection('users'),
      readCollection('vehicles'),
      readCollection('devices'),
    ]);
    res.json({
      import: {
        storage: 'firestore',
        accounts: users.length,
        cloudVehicles: vehicles.length,
        devices: devices.length,
        importedAt: Date.now(),
      },
    });
  })
);

// ── Sincronização Firebase ───────────────────────────────────────────────
// POST /v1/admin/firebase/sync
// Faz dois syncs úteis:
// 1. Firebase Auth users -> Firestore users (batch de todos os usuários)
// 2. Firestore mobile_devices -> RTDB mobile_devices (tempo real)
router.post(
  '/firebase/sync',
  asyncHandler(async (_req, res) => {
    const [usersResult, devicesResult] = await Promise.all([
      syncFirebaseUsers(),
      syncMobileDevicesToRtdb(),
    ]);
    res.json({
      sync: {
        users: usersResult,
        mobileDevices: devicesResult,
        timestamp: Date.now(),
      },
    });
  })
);

// ── Simulação: seletor do painel ────────────────────────────────────────

router.get(
  '/vehicles/:id/simulation',
  asyncHandler(async (req, res) => {
    const vehicle = await rawVehicle(req.params.id);
    const device = await deviceOfVehicle(vehicle.id);
    res.json({ simulation: getSimulationDecision(vehicle, device) });
  })
);

// Adiciona/tira qualquer veículo da simulação: { simulate: true | false }.
// Veículo com rastreador real não pode ser simulado.
router.post(
  '/vehicles/:id/simulation',
  asyncHandler(async (req, res) => {
    const vehicle = await rawVehicle(req.params.id);
    const simulate = simulateFromBody(req.body);
    if (typeof simulate !== 'boolean') {
      throw new HttpError(400, 'Informe simulate: true (simular) ou false (teste real)');
    }
    const { vehicle: updated, device } = await applySimulationDecision(vehicle, simulate);
    res.json({
      simulation: getSimulationDecision(updated, device),
      vehicle: { ...serializeVehicle(updated), simulated: Boolean(device && device.model === 'server-sim') },
      device: serializeDevice(device),
    });
  })
);

// ── Simulação: criar um veículo novo já com rastreador virtual ──────────
//
// O botão "Simular" só consegue agir em veículo que já existe (o app cria os
// veículos sem device). Esta rota cria o veículo direto na frota simulada e
// devolve o device COM a apiKey — é a chave que permite rodar o
// hardware-simulator local apontando para esta API.
const PLATE = /^[A-Z]{3}\d[A-Z0-9]\d{2}$|^[A-Z]{3}-\d{4}$/;

router.post(
  '/simulation/vehicles',
  asyncHandler(async (req, res) => {
    // Precisa checar ANTES de gravar: criar o veículo e só então descobrir que
    // a simulação está desligada deixava um veículo órfão no Firestore (foi o
    // que aconteceu na Vercel, onde SIMULATION_ENABLED=false).
    if (!config.simulationEnabled) {
      throw new HttpError(409, 'Simulação desativada no servidor (SIMULATION_ENABLED=false)');
    }
    const { plate, model, brand, year, color = '' } = req.body || {};
    const normalized = String(plate || '').trim().toUpperCase().replace(/\s+/g, '');
    if (!model || !brand || !PLATE.test(normalized)) {
      throw new HttpError(400, 'Informe placa (ABC1D23 ou ABC-1234), modelo e marca');
    }

    const fs = firestore();
    const duplicate = await fs
      .collection('vehicles')
      .where('ownerUid', '==', req.user.uid)
      .where('plate', '==', normalized)
      .limit(1)
      .get();
    if (!duplicate.empty) throw new HttpError(409, `Você já tem um veículo com a placa ${normalized}`);

    const now = Date.now();
    const vehicle = {
      id: makeId(),
      userId: req.user.uid,
      ownerUid: req.user.uid,
      plate: normalized,
      model: String(model),
      brand: String(brand),
      year: Number(year) || 0,
      color: String(color),
      isBlocked: false,
      latitude: 0,
      longitude: 0,
      speed: 0,
      lastUpdate: 0,
      createdAt: now,
      updatedAt: now,
    };
    await fs.collection('vehicles').doc(String(vehicle.id)).set(vehicle);

    // applySimulationDecision cria o device server-sim (com apiKey) e, quando a
    // API roda local, sobe o simulador desse rastreador. Se falhar depois de o
    // veículo já existir, apaga o documento em vez de deixar lixo.
    let applied;
    try {
      applied = await applySimulationDecision(vehicle, true);
    } catch (err) {
      await fs.collection('vehicles').doc(String(vehicle.id)).delete().catch(() => {});
      throw err;
    }
    const { vehicle: updated, device } = applied;
    await mirrorVehicles([updated]);
    if (device) await mirrorDevice(device);

    res.status(201).json({
      vehicle: { ...serializeVehicle(updated), simulated: true },
      device: serializeDevice(device),
      simulation: getSimulationDecision(updated, device),
    });
  })
);

// ── Posição manual: arrastar o veículo no mapa do painel ────────────────
//
// Define a posição (latitude/longitude, opcional velocidade) de QUALQUER
// veículo. O simulador de hardware reancora nessa posição no próximo ciclo.
router.post(
  '/vehicles/:id/position',
  asyncHandler(async (req, res) => {
    const { latitude, longitude, speed } = req.body || {};
    const { vehicle, point } = await setVehiclePosition(req.params.id, {
      latitude,
      longitude,
      speed,
    });
    res.json({ vehicle: serializeVehicle(vehicle), point });
  })
);

export default router;
