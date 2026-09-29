import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.js';
import { notFound, errorHandler } from './middleware/error.js';
import authRoutes from './routes/auth.routes.js';
import vehiclesRoutes from './routes/vehicles.routes.js';
import telemetryRoutes from './routes/telemetry.routes.js';
import commandsRoutes from './routes/commands.routes.js';
import routesRoutes from './routes/routes.routes.js';
import hardwareRoutes from './routes/hardware.routes.js';
import { syncOnce } from './services/firebase-sync.service.js';
import { getFirebase, getFirebaseInitError } from './firebase/firebase-admin.js';
import { recordRequest, listLogs, subscribeLogs, logStats } from './services/log-buffer.service.js';
import { requireAuth } from './middleware/auth.js';
import { db } from './db/database.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function createApp() {
  const app = express();

  app.use(express.json({ limit: '100kb' }));

  // CORS liberado para desenvolvimento (aplicativo mobile não é afetado por CORS).
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,x-device-id,x-api-key');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });

  // Sempre registramos no buffer em memória (a página /debug depende dele),
  // mas só escrevemos no console quando LOG_REQUESTS estiver ligado.
  app.use((req, res, next) => {
    const startedAt = process.hrtime.bigint();
    res.on('finish', () => {
      const durationMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
      recordRequest({
        method: req.method,
        path: req.originalUrl,
        status: res.statusCode,
        durationMs: Math.round(durationMs * 100) / 100,
      });
      if (config.logRequests) {
        console.log(`[req] ${req.method} ${req.originalUrl} -> ${res.statusCode}`);
      }
    });
    next();
  });

  app.get('/health', (_req, res) => res.json({ ok: true, service: 'veiculotracker-backend', time: Date.now() }));

  // Índice da API: evita 404 ao abrir a URL base no navegador e serve de
  // referência rápida dos endpoints durante o desenvolvimento.
  app.get('/', (_req, res) =>
    res.json({
      service: 'veiculotracker-backend',
      description: 'API de ponte entre o hardware rastreador (GPS IoT) e o app Securitas',
      health: '/health',
      endpoints: {
        auth: ['POST /v1/auth/register', 'POST /v1/auth/login', 'GET /v1/auth/me', 'POST /v1/auth/logout'],
        vehicles: [
          'GET /v1/vehicles',
          'POST /v1/vehicles',
          'GET /v1/vehicles/:id',
          'PUT /v1/vehicles/:id',
          'DELETE /v1/vehicles/:id',
          'POST /v1/vehicles/:id/device',
        ],
        telemetry: [
          'GET /v1/vehicles/:id/telemetry/latest',
          'GET /v1/vehicles/:id/telemetry/history',
          'GET /v1/vehicles/:id/telemetry/stream',
        ],
        commands: ['POST /v1/vehicles/:id/commands', 'GET /v1/vehicles/:id/commands'],
        routes: [
          'POST /v1/vehicles/:id/routes/start',
          'POST /v1/vehicles/:id/routes/stop',
          'GET /v1/vehicles/:id/routes',
          'GET /v1/routes',
          'GET /v1/routes/:id',
          'DELETE /v1/routes/:id',
        ],
        hardware: [
          'POST /v1/hardware/telemetry',
          'GET /v1/hardware/:deviceId/status',
          'GET /v1/hardware/:deviceId/commands',
          'POST /v1/hardware/:deviceId/commands/:commandId/ack',
        ],
        admin: ['POST /v1/admin/sync', 'GET /v1/admin/sync-status', 'GET /v1/admin/logs', 'GET /v1/admin/logs/stream'],
      },
      auth: 'Bearer <token> no app; headers x-device-id e x-api-key no hardware',
      debug: 'GET /debug (mapa + logs em tempo real)',
      docs: 'backend/API_ESTRUTURA.txt',
    })
  );

  app.use('/v1/auth', authRoutes);
  app.use('/v1/vehicles', vehiclesRoutes);
  app.use('/v1/vehicles', telemetryRoutes);
  app.use('/v1/vehicles', commandsRoutes);
  app.use('/v1', routesRoutes);
  app.use('/v1/hardware', hardwareRoutes);

  // Força uma rodada de sincronização SQLite -> Firebase manualmente.
  // Com ?full=1 (ou {"full":true}) zera as marcas d'água e reenvia tudo.
  app.post('/v1/admin/sync', requireAuth, async (req, res) => {
    const full = req.query.full === '1' || req.body?.full === true;
    const result = await syncOnce({ full });
    res.json({ ok: true, full, ...result });
  });

  // Status da sincronização
  app.get('/v1/admin/sync-status', requireAuth, (_req, res) => {
    const rows = db.prepare('SELECT key, value FROM sync_state').all();
    const marks = Object.fromEntries(rows.map((r) => [r.key, Number(r.value)]));
    res.json({
      enabled: config.firebaseSyncEnabled,
      intervalMs: config.firebaseSyncIntervalMs,
      lastRun: marks.last_run ?? null,
      watermarks: { users: marks.users ?? null, vehicles: marks.vehicles ?? null, routes: marks.routes ?? null },
      credentials: getFirebase() ? 'ok' : `ausente (${getFirebaseInitError()})`,
    });
  });

  // Diagnóstico do buffer de logs
  app.get('/v1/admin/log-stats', requireAuth, (_req, res) => {
    res.json(logStats());
  });

  // Página de diagnóstico: mapa + logs da API, sem build nem framework.
  app.use('/debug', express.static(path.join(__dirname, '..', 'public')));

  // Histórico de requisições. ?since=<seq> devolve só o que é novo.
  app.get('/v1/admin/logs', requireAuth, (req, res) => {
    const since = Number(req.query.since) || 0;
    const limit = Math.min(Math.max(Number(req.query.limit) || 200, 1), 500);
    res.json(listLogs({ since, limit }));
  });

  // Stream ao vivo das requisições (mesmo formato do SSE de telemetria).
  app.get('/v1/admin/logs/stream', requireAuth, (req, res) => {
    res.set({
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    res.flushHeaders?.();
    subscribeLogs(res);
  });

  app.use(notFound);
  app.use(errorHandler);

  return app;
}