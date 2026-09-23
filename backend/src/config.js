import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Carregamento simples de .env (sem dependências externas)
function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  const content = fs.readFileSync(filePath, 'utf8');
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
    if (process.env[key] === undefined) process.env[key] = value;
  }
}

loadEnvFile(path.join(__dirname, '..', '.env'));

const toInt = (v, def) => {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) ? def : n;
};

const toFloat = (v, def) => {
  const n = Number.parseFloat(v);
  return Number.isNaN(n) ? def : n;
};

export const config = {
  port: toInt(process.env.PORT, 3000),
  dbPath: path.resolve(__dirname, '..', process.env.DB_PATH || 'data/veiculotracker.db'),
  jwtSecret: process.env.JWT_SECRET || 'veiculotracker-dev-secret',
  maxTelemetryPoints: toInt(process.env.MAX_TELEMETRY_POINTS, 10000),
  simulatorIntervalMs: toInt(process.env.SIMULATOR_INTERVAL_MS, 5000),
  simulatorStartLat: toFloat(process.env.SIMULATOR_START_LAT, -23.5505),
  simulatorStartLng: toFloat(process.env.SIMULATOR_START_LNG, -46.6333),
  logRequests: (process.env.LOG_REQUESTS || 'true').toLowerCase() === 'true',
};