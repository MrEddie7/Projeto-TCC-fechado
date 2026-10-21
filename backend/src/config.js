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

// A apiKey do Firebase é pública (vai embutida no SDK do cliente). O painel web
// precisa dela para autenticar via REST do Identity Toolkit (Firebase Auth).
// Prioridade: variável de ambiente FIREBASE_API_KEY; se ausente, lê a chave do
// google-services.json do app Android.
function resolveFirebaseApiKey() {
  if (process.env.FIREBASE_API_KEY) return process.env.FIREBASE_API_KEY;
  try {
    const gsPath = path.resolve(__dirname, '..', '..', 'app', 'google-services.json');
    const gs = JSON.parse(fs.readFileSync(gsPath, 'utf8'));
    return gs.client?.[0]?.api_key?.[0]?.current_key || '';
  } catch {
    return '';
  }
}

const toInt = (v, def) => {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) ? def : n;
};

const toFloat = (v, def) => {
  const n = Number.parseFloat(v);
  return Number.isNaN(n) ? def : n;
};

// Porta TCP válida (1..65535). PORT=0 (comum em ambientes de execução/hospedagem)
// faria o Node escutar em uma porta aleatória e o app falharia com
// "Failed to connect ...:3000" — então valor inválido cai no padrão.
const toPort = (v, def) => {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) || n < 1 || n > 65535 ? def : n;
};

export const config = {
  port: toPort(process.env.PORT, 3000),
  simulatorIntervalMs: toInt(process.env.SIMULATOR_INTERVAL_MS, 5000),
  simulatorStartLat: toFloat(process.env.SIMULATOR_START_LAT, -23.5505),
  simulatorStartLng: toFloat(process.env.SIMULATOR_START_LNG, -46.6333),
  logRequests: (process.env.LOG_REQUESTS || 'true').toLowerCase() === 'true',

  // ── Simulação de veículos ─────────────────────────────────────────────
  // Quando um veículo entra na simulação, a API cria um "rastreador virtual"
  // (device com model='server-sim'). Quem movimenta o veículo é o simulador
  // de hardware, iniciado pelo api.sh.
  simulationEnabled: (process.env.SIMULATION_ENABLED || 'true').toLowerCase() === 'true',

  // ── Firebase (Firestore + Realtime Database) ──────────────────────────
  firebaseServiceAccount: process.env.FIREBASE_SERVICE_ACCOUNT
    ? path.resolve(__dirname, '..', process.env.FIREBASE_SERVICE_ACCOUNT)
    : null,
  firebaseDatabaseUrl: process.env.FIREBASE_DATABASE_URL || '',
  // Apelido do banco no Firestore ("securitas" no app Android).
  firestoreId: process.env.FIRESTORE_ID || 'securitas',
  // apiKey pública usada pelo painel web para o login REST do Firebase Auth.
  firebaseApiKey: resolveFirebaseApiKey(),
};
