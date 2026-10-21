#!/usr/bin/env node
// ─────────────────────────────────────────────────────────────────────────
// Publica as regras de segurança do Firebase (Firestore + Realtime Database)
// usando a conta de serviço do backend. Equivale ao que o Console faria.
//
// Uso:
//   node scripts/deploy-rules.js
//
// O Admin SDK ignora as regras; elas valem apenas para o app Android.
// ─────────────────────────────────────────────────────────────────────────
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createSign } from 'node:crypto';
import { config } from '../src/config.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

const saPath = config.firebaseServiceAccount;
if (!saPath || !fs.existsSync(saPath)) {
  console.error('[rules] service account não encontrado:', saPath);
  process.exit(1);
}
const sa = JSON.parse(fs.readFileSync(saPath, 'utf8'));
const projectId = sa.project_id;

// ── Token OAuth2 via JWT da conta de serviço ──────────────────────────────
async function accessToken() {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const header = b64({ alg: 'RS256', typ: 'JWT' });
  const claim = b64({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  });
  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${claim}`);
  const jwt = `${header}.${claim}.${signer.sign(sa.private_key).toString('base64url')}`;
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const json = await res.json();
  if (!json.access_token) throw new Error(`token OAuth2: ${JSON.stringify(json)}`);
  return json.access_token;
}

async function deployFirestore(token) {
  const content = fs.readFileSync(path.join(root, 'firebase', 'firestore.rules'), 'utf8');
  const h = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // 1. cria um ruleset
  const created = await fetch(
    `https://firebaserules.googleapis.com/v1/projects/${projectId}/rulesets`,
    {
      method: 'POST',
      headers: h,
      body: JSON.stringify({
        source: { files: [{ name: 'cloud.firestore', content }] },
      }),
    }
  ).then((r) => r.json());
  if (!created.name) throw new Error(`criar ruleset: ${JSON.stringify(created)}`);

  // 2. publica (release) no banco nomeado "securitas"
  const releaseName = `projects/${projectId}/releases/cloud.firestore/${config.firestoreId}`;
  const patch = await fetch(
    `https://firebaserules.googleapis.com/v1/${releaseName}`,
    {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ release: { name: releaseName, rulesetName: created.name } }),
    }
  ).then((r) => r.json());
  if (patch.name) {
    console.log(`[rules] Firestore publicado (${config.firestoreId}) -> ${created.name}`);
  } else {
    // Fallback: criação (se o release ainda não existir)
    const made = await fetch(
      `https://firebaserules.googleapis.com/v1/projects/${projectId}/releases`,
      { method: 'POST', headers: h, body: JSON.stringify({ name: releaseName, rulesetName: created.name }) }
    ).then((r) => r.json());
    console.log('[rules] Firestore release criado ->', made.name || JSON.stringify(made));
  }
}

async function deployRtdb(token) {
  // A Rules API espera o JSON completo, com o wrapper "rules".
  const content = fs.readFileSync(
    path.join(root, 'firebase', 'database.rules.json'), 'utf8'
  );
  const h = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // A Rules API identifica o RTDB pelo arquivo/release "firebaseio.com".
  const created = await fetch(
    `https://firebaserules.googleapis.com/v1/projects/${projectId}/rulesets`,
    { method: 'POST', headers: h, body: JSON.stringify({ source: { files: [{ name: 'firebaseio.com', content }] } }) }
  ).then((r) => r.json());
  if (!created.name) throw new Error(`criar ruleset RTDB: ${JSON.stringify(created)}`);

  const releaseName = `projects/${projectId}/releases/firebaseio.com`;
  const patch = await fetch(
    `https://firebaserules.googleapis.com/v1/${releaseName}`,
    { method: 'PATCH', headers: h, body: JSON.stringify({ release: { name: releaseName, rulesetName: created.name } }) }
  ).then((r) => r.json());
  if (patch.name) {
    console.log(`[rules] Realtime Database publicado -> ${created.name}`);
  } else {
    throw new Error(`publicar RTDB: ${JSON.stringify(patch)}`);
  }
}

const token = await accessToken();
await deployFirestore(token);
await deployRtdb(token);
console.log('[rules] concluído');
