import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.js';
import { notFound, errorHandler } from './middleware/error.js';
import vehicles from './routes/vehicles.routes.js';
import telemetry from './routes/telemetry.routes.js';
import commands from './routes/commands.routes.js';
import routes from './routes/routes.routes.js';
import hardware from './routes/hardware.routes.js';
import mobile from './routes/mobile.routes.js';
import devices from './routes/devices.routes.js';
import admin from './routes/admin.routes.js';
import { requireAuth } from './middleware/auth.js';
import { recordRequest, listLogs, subscribeLogs, logStats } from './services/log-buffer.service.js';

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

  // Buffer em memória com as últimas requisições: o painel web (raiz /) mostra
  // os logs ao vivo a partir daqui, sem depender dos arquivos de log.
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

  app.get('/health', (_req, res) =>
    res.json({ ok: true, service: 'securitas-backend', storage: 'firestore', time: Date.now() })
  );

  // Índice da API: referência rápida dos endpoints em /api. A raiz (/) fica
  // reservada para a aplicação web do painel (public/index.html).
  app.get('/api', (_req, res) =>
    res.json({
      service: 'securitas-backend',
      description: 'API de ponte entre o hardware rastreador (GPS IoT) e o app Securitas',
      storage: 'Firestore + Realtime Database',
      auth: 'Firebase ID Token (app) e x-device-id/x-api-key (hardware)',
      health: '/health',
      endpoints: {
        auth: ['GET /v1/auth/config', 'GET /v1/auth/me'],
        vehicles: [
          'GET /v1/vehicles',
          'POST /v1/vehicles',
          'GET /v1/vehicles/:id',
          'PUT /v1/vehicles/:id',
          'DELETE /v1/vehicles/:id',
          'POST /v1/vehicles/:id/device',
          'GET /v1/vehicles/:id/simulation',
          'POST /v1/vehicles/:id/simulation',
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
          'GET /v1/routes/:id',
          'DELETE /v1/routes/:id',
        ],
        hardware: [
          'POST /v1/hardware/telemetry',
          'GET /v1/hardware/:deviceId/status',
          'GET /v1/hardware/:deviceId/commands',
          'POST /v1/hardware/:deviceId/commands/:commandId/ack',
        ],
        mobile: [
          'POST /v1/mobile/location',
          'GET /v1/mobile/location/latest',
          'POST /v1/mobile/device',
          'GET /v1/mobile/device',
          'DELETE /v1/mobile/device',
        ],
        admin: [
          'GET /v1/admin/overview',
          'GET /v1/admin/vehicles',
          'GET /v1/admin/vehicles/:id',
          'GET /v1/admin/vehicles/:id/telemetry/latest',
          'GET /v1/admin/vehicles/:id/telemetry/history',
          'GET /v1/admin/vehicles/:id/telemetry/stream',
          'GET /v1/admin/vehicles/:id/simulation',
          'POST /v1/admin/vehicles/:id/simulation',
          'POST /v1/admin/cloud/import',
          'GET /v1/admin/logs',
          'GET /v1/admin/logs/stream',
          'GET /v1/admin/log-stats',
        ],
      },
      dashboard: 'GET / (painel web: usuários/veículos, mapa e logs)',
      docs: 'backend/API_ESTRUTURA.txt',
    })
  );

  // Configuração pública para o painel web: a apiKey do Firebase que o cliente
  // usa no login via REST do Identity Toolkit. Não é segredo (é embutida no app).
  app.get('/v1/auth/config', (_req, res) =>
    res.json({ firebaseApiKey: config.firebaseApiKey || '' })
  );

  // Firebase Auth é realizado pelo SDK Android (ou pelo REST do painel web); a
  // API somente valida o token nos demais endpoints.
  app.get('/v1/auth/me', (req, res, next) => requireAuthSafe(req, res, next), (req, res) => res.json({ user: req.user }));

  app.use('/v1/vehicles', vehicles);
  app.use('/v1/vehicles', telemetry);
  app.use('/v1/vehicles', commands);
  app.use('/v1', routes);
  app.use('/v1/hardware', hardware);
  app.use('/v1/mobile', mobile);
  app.use('/v1/mobile', devices);
  // Visão do administrador: qualquer veículo de qualquer usuário (painel web).
  app.use('/v1/admin', admin);

  // Diagnóstico do buffer de logs
  app.get('/v1/admin/log-stats', (req, res, next) => requireAuthSafe(req, res, next), (_req, res) => res.json(logStats()));

  // Histórico de requisições. ?since=<seq> devolve só o que é novo.
  app.get('/v1/admin/logs', (req, res, next) => requireAuthSafe(req, res, next), (req, res) => {
    const since = Number(req.query.since) || 0;
    const limit = Math.min(Math.max(Number(req.query.limit) || 200, 1), 500);
    res.json(listLogs({ since, limit }));
  });

  // Stream ao vivo das requisições (mesmo formato do SSE de telemetria).
  app.get('/v1/admin/logs/stream', (req, res, next) => requireAuthSafe(req, res, next), (_req, res) => {
    res.set({
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    res.flushHeaders?.();
    subscribeLogs(res);
  });

  // Compatibilidade: a antiga página de diagnóstico vivia em /debug.
  app.get(['/debug', '/debug/*'], (_req, res) => res.redirect(301, '/'));

  // Aplicação web do painel: HTML+JS puro servido na raiz (sem build/framework).
  app.use(express.static(path.join(__dirname, '..', 'public')));

  app.use(notFound);
  app.use(errorHandler);

  return app;
}

// requireAuth é async (verifica o ID Token no Firebase Admin). O Express 4 não
// captura rejeições de handlers async sozinho, então envolvemos a chamada.
function requireAuthSafe(req, res, next) {
  return Promise.resolve(requireAuth(req, res, next)).catch(next);
}
