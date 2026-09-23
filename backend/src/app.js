import express from 'express';
import { config } from './config.js';
import { notFound, errorHandler } from './middleware/error.js';
import authRoutes from './routes/auth.routes.js';
import vehiclesRoutes from './routes/vehicles.routes.js';
import telemetryRoutes from './routes/telemetry.routes.js';
import commandsRoutes from './routes/commands.routes.js';
import routesRoutes from './routes/routes.routes.js';
import hardwareRoutes from './routes/hardware.routes.js';
import { syncOnce } from './services/firebase-sync.service.js';
import { requireAuth } from './middleware/auth.js';
import { db } from './db/database.js';

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

  if (config.logRequests) {
    app.use((req, res, next) => {
      res.on('finish', () => {
        console.log(`[req] ${req.method} ${req.originalUrl} -> ${res.statusCode}`);
      });
      next();
    });
  }

  app.get('/health', (_req, res) => res.json({ ok: true, service: 'veiculotracker-backend', time: Date.now() }));

  app.use('/v1/auth', authRoutes);
  app.use('/v1/vehicles', vehiclesRoutes);
  app.use('/v1/vehicles', telemetryRoutes);
  app.use('/v1/vehicles', commandsRoutes);
  app.use('/v1', routesRoutes);
  app.use('/v1/hardware', hardwareRoutes);

  // Força uma rodada de sincronização SQLite -> Firebase manualmente
  app.post('/v1/admin/sync', requireAuth, async (req, res) => {
    const result = await syncOnce();
    res.json({ ok: true, ...result });
  });

  // Status da sincronização
  app.get('/v1/admin/sync-status', requireAuth, (_req, res) => {
    const row = db.prepare("SELECT value FROM sync_state WHERE key = 'last_run'").get();
    res.json({
      enabled: config.firebaseSyncEnabled,
      intervalMs: config.firebaseSyncIntervalMs,
      lastRun: row ? Number(row.value) : null,
    });
  });

  app.use(notFound);
  app.use(errorHandler);

  return app;
}