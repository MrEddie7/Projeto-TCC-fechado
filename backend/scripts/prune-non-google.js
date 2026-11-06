#!/usr/bin/env node
// ─────────────────────────────────────────────────────────────────────────
// Limpeza do Firebase: mantém apenas contas Google reais e tudo o que
// pertence a elas (veículos, rastreadores, telemetria, rotas e comandos).
//
// Regra de classificação: uma conta é "Google real" quando o Firebase Auth
// lista `google.com` em providerData. Contas criadas por e-mail/senha (testes,
// emuladores, duplicatas sem provedor) são removidas em cascata.
//
// Uso:
//   node scripts/prune-non-google.js                     # dry-run (padrão)
//   node scripts/prune-non-google.js --apply             # backup + limpeza
//   SECURITAS_ADMIN_PASSWORD='...' \
//     node scripts/prune-non-google.js --apply \
//     --admin-email=edwinclarwis777@gmail.com            # + senha do painel
//
// O dry-run só LÊ. O --apply grava um backup JSON em
// backend/.run/backup-prune-AAAA-MM-DD-HHmmss.json antes de apagar qualquer
// coisa. A operação é idempotente: se falhar no meio, pode rodar de novo.
//
// A senha do admin NUNCA é versionada nem impressa: vem da variável de
// ambiente SECURITAS_ADMIN_PASSWORD.
// ─────────────────────────────────────────────────────────────────────────
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from '../src/config.js';
import { getFirebase, getFirebaseInitError } from '../src/firebase/firebase-admin.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(__dirname, '..');
const runDir = path.join(backendRoot, '.run');

const args = process.argv.slice(2);
const apply = args.includes('--apply');
const passwordOnly = args.includes('--password-only');
const help = args.includes('--help') || args.includes('-h');
const adminEmail = (args.find((a) => a.startsWith('--admin-email=')) || '')
  .slice('--admin-email='.length)
  .trim()
  .toLowerCase();

const KEEP_PROVIDER = 'google.com';
const BATCH_LIMIT = 500;

if (help) {
  console.log(`Limpeza do Firebase — mantém somente contas Google reais.

  node scripts/prune-non-google.js [--apply] [--admin-email=EMAIL]

  --apply                 executa a limpeza (sem isso é dry-run)
  --admin-email=EMAIL     define senha na conta Google informada, usando
                          a variável de ambiente SECURITAS_ADMIN_PASSWORD
  --password-only         só define a senha do admin (não mexe nos dados)
`);
  process.exit(0);
}

const log = (...a) => console.log(...a);
const section = (title) => log(`\n── ${title} ${'─'.repeat(Math.max(0, 58 - title.length))}`);

function jsonSafe(value) {
  if (Array.isArray(value)) return value.map(jsonSafe);
  if (value && typeof value === 'object') {
    if (typeof value.toMillis === 'function') return value.toMillis();
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = jsonSafe(v);
    return out;
  }
  return value;
}

function timestampSlug() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function chunk(list, size) {
  const out = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

function isGoogleUser(user) {
  return (user.providerData || []).some((p) => p.providerId === KEEP_PROVIDER);
}

function userLabel(user) {
  return `${user.email || '(sem e-mail)'} [${user.uid}]`;
}

async function commitDeletes(db, refs) {
  let deleted = 0;
  for (const group of chunk(refs, BATCH_LIMIT)) {
    if (!group.length) continue;
    const batch = db.batch();
    for (const ref of group) batch.delete(ref);
    await batch.commit();
    deleted += group.length;
    process.stdout.write(`\r  apagando… ${deleted}/${refs.length}`);
  }
  if (refs.length) process.stdout.write('\n');
  return deleted;
}

async function readRtdb(rtdb, node) {
  const snap = await rtdb.ref(node).get();
  return snap.val() || {};
}

async function main() {
  const fb = getFirebase();
  if (!fb) {
    console.error('Firebase indisponível:', getFirebaseInitError()?.message || 'credenciais ausentes');
    process.exit(1);
  }
  const { auth, firestore: db, rtdb } = fb;

  if (adminEmail && !process.env.SECURITAS_ADMIN_PASSWORD) {
    console.error('--admin-email exige a variável SECURITAS_ADMIN_PASSWORD.');
    process.exit(1);
  }

  if (passwordOnly) {
    if (!adminEmail) {
      console.error('--password-only exige --admin-email=EMAIL.');
      process.exit(1);
    }
    const user = await auth.getUserByEmail(adminEmail).catch(() => null);
    if (!user || !isGoogleUser(user)) {
      console.error(`Conta Google "${adminEmail}" não encontrada no Auth.`);
      process.exit(1);
    }
    await auth.updateUser(user.uid, { password: process.env.SECURITAS_ADMIN_PASSWORD });
    log(`Senha definida para ${adminEmail} (não é exibida nem gravada).`);
    return;
  }

  section('Firebase Auth');
  const authUsers = [];
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    authUsers.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);

  const keepUsers = authUsers.filter(isGoogleUser);
  const dropUsers = authUsers.filter((u) => !isGoogleUser(u));
  const keepUids = new Set(keepUsers.map((u) => u.uid));

  log(`Total de contas: ${authUsers.length}`);
  log(`Manter (Google real): ${keepUsers.length}`);
  for (const u of keepUsers) log(`  ✓ ${userLabel(u)}`);
  log(`Remover (sem google.com): ${dropUsers.length}`);
  for (const u of dropUsers) {
    const providers = (u.providerData || []).map((p) => p.providerId).join(',') || 'nenhum provedor';
    log(`  ✕ ${userLabel(u)} · ${providers}`);
  }

  section('Firestore');
  const collections = ['users', 'vehicles', 'devices', 'routes', 'commands', 'mobile_devices', 'telemetry'];
  const docs = {};
  for (const name of collections) {
    const snap = await db.collection(name).get();
    docs[name] = snap.docs.map((d) => ({ docId: d.id, data: jsonSafe(d.data()) }));
    log(`${name}: ${docs[name].length}`);
  }

  // Veículos: mantém apenas os que pertencem a uma conta Google real.
  const keepVehicles = docs.vehicles.filter((d) => keepUids.has(d.data.ownerUid));
  const dropVehicles = docs.vehicles.filter((d) => !keepUids.has(d.data.ownerUid));
  const keepVehicleIds = new Set(keepVehicles.map((d) => String(Number(d.data.id ?? d.docId))));
  const dropVehicleIds = new Set(dropVehicles.map((d) => String(Number(d.data.id ?? d.docId))));
  const vehicleIsKept = (v) => keepVehicleIds.has(String(Number(v)));

  log(`\nVeículos mantidos: ${keepVehicles.length}`);
  for (const d of keepVehicles) log(`  ✓ ${d.data.plate || d.docId} (id ${d.data.id ?? d.docId})`);
  log(`Veículos removidos: ${dropVehicles.length}`);
  for (const d of dropVehicles) log(`  ✕ ${d.data.plate || d.docId} (id ${d.data.id ?? d.docId}) · dono ${d.data.ownerUid || '—'}`);

  // Rastreadores: só ficam os ligados a veículos mantidos.
  const keepDevices = docs.devices.filter((d) => vehicleIsKept(d.data.vehicleId));
  const dropDevices = docs.devices.filter((d) => !vehicleIsKept(d.data.vehicleId));
  const keepDeviceIds = new Set(keepDevices.map((d) => d.data.deviceId || d.docId));

  // Rotas e comandos: remove tudo que não aponta para um veículo mantido
  // (inclui órfãos de veículos que já não existem no Firestore).
  const keepRoutes = docs.routes.filter((d) => vehicleIsKept(d.data.vehicleId));
  const dropRoutes = docs.routes.filter((d) => !vehicleIsKept(d.data.vehicleId));
  const keepRouteIds = new Set(keepRoutes.map((d) => String(Number(d.data.id ?? d.docId))));

  const keepCommands = docs.commands.filter((d) => vehicleIsKept(d.data.vehicleId));
  const dropCommands = docs.commands.filter((d) => !vehicleIsKept(d.data.vehicleId));

  // Telemetria: mantém apenas pontos de veículos mantidos (remove órfãos).
  const keepTelemetry = docs.telemetry.filter((d) => vehicleIsKept(d.data.vehicleId));
  const dropTelemetry = docs.telemetry.filter((d) => !vehicleIsKept(d.data.vehicleId));

  // Usuários do Firestore: o docId é o UID (ou o e-mail, em duplicatas antigas).
  const keepUserDocs = docs.users.filter((d) => keepUids.has(d.docId));
  const dropUserDocs = docs.users.filter((d) => !keepUids.has(d.docId));

  // Mobile: o docId/userId é o UID da conta.
  const keepMobile = docs.mobile_devices.filter((d) => keepUids.has(d.docId) || keepUids.has(d.data.userId));
  const dropMobile = docs.mobile_devices.filter((d) => !(keepUids.has(d.docId) || keepUids.has(d.data.userId)));

  section('Resumo do Firestore');
  const summary = [
    ['users', keepUserDocs.length, dropUserDocs.length],
    ['vehicles', keepVehicles.length, dropVehicles.length],
    ['devices', keepDevices.length, dropDevices.length],
    ['routes', keepRoutes.length, dropRoutes.length],
    ['commands', keepCommands.length, dropCommands.length],
    ['mobile_devices', keepMobile.length, dropMobile.length],
    ['telemetry', keepTelemetry.length, dropTelemetry.length],
  ];
  for (const [name, keep, drop] of summary) {
    log(`${name.padEnd(16)} manter ${String(keep).padStart(5)} · remover ${String(drop).padStart(5)}`);
  }
  if (dropDevices.length) {
    log('\nRastreadores removidos:');
    for (const d of dropDevices) log(`  ✕ ${d.data.deviceId || d.docId} (veículo ${d.data.vehicleId ?? '—'})`);
  }
  if (dropRoutes.length) {
    log('\nRotas removidas:');
    for (const d of dropRoutes) log(`  ✕ ${d.data.id ?? d.docId} (veículo ${d.data.vehicleId ?? '—'})`);
  }

  section('Realtime Database');
  const rtdbNodes = [
    'vehicles_mirror',
    'devices_mirror',
    'telemetry',
    'telemetry_history',
    'telemetry_seq',
    'commands',
    'sim_control',
    'mobile_devices',
    'mobile_locations',
    'routes_mirror',
    'route_points_mirror',
    'route_points_seq',
  ];
  const rtdbData = {};
  const rtdbRemovals = {}; // caminho absoluto -> null (update apaga a chave)
  const rtdbKeeps = {
    vehicles_mirror: (k) => vehicleIsKept(k),
    devices_mirror: (k) => keepDeviceIds.has(k),
    telemetry: (k) => vehicleIsKept(k),
    telemetry_history: (k) => vehicleIsKept(k),
    telemetry_seq: (k) => vehicleIsKept(k),
    commands: (k) => vehicleIsKept(k),
    sim_control: (k) => vehicleIsKept(k),
    mobile_devices: (k) => keepUids.has(k),
    mobile_locations: (k) => keepUids.has(k),
    routes_mirror: (k) => keepRouteIds.has(String(Number(k))),
    route_points_mirror: (k) => keepRouteIds.has(String(Number(k))),
    route_points_seq: (k) => keepRouteIds.has(String(Number(k))),
  };

  for (const node of rtdbNodes) {
    const data = await readRtdb(rtdb, node);
    rtdbData[node] = data;
    const keys = Object.keys(data);
    const keepFn = rtdbKeeps[node];
    const dropped = keys.filter((k) => !keepFn(k));
    for (const k of dropped) rtdbRemovals[`${node}/${k}`] = null;
    log(`${node.padEnd(22)} manter ${String(keys.length - dropped.length).padStart(3)} · remover ${String(dropped.length).padStart(3)}`);
  }

  section('Cache local de rastreadores');
  const cacheFile = path.join(runDir, 'device-keys.json');
  let localCache = {};
  try {
    localCache = JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
  } catch { /* arquivo ainda não existe */ }
  const dropCacheIds = Object.keys(localCache).filter((id) => !keepDeviceIds.has(id));
  log(`device-keys.json: manter ${Object.keys(localCache).length - dropCacheIds.length} · remover ${dropCacheIds.length}`);
  for (const id of dropCacheIds) log(`  ✕ ${id} (${localCache[id]?.plate || '—'})`);

  // ── Senha do admin ────────────────────────────────────────────────────
  let adminUser = null;
  if (adminEmail) {
    adminUser = keepUsers.find((u) => (u.email || '').toLowerCase() === adminEmail) || null;
    if (!adminUser) {
      console.error(`\nConta admin "${adminEmail}" não está entre as contas Google mantidas.`);
      process.exit(1);
    }
    log(`\nSenha do painel será definida em: ${adminEmail}`);
  }

  if (!apply) {
    section('Dry-run');
    log('Nada foi alterado. Rode com --apply para executar a limpeza.');
    if (!adminEmail) {
      log('Dica: para definir a senha do painel sem repetir a limpeza, use:');
      log('  SECURITAS_ADMIN_PASSWORD=\'...\' npm run prune -- --password-only --admin-email=SEU_EMAIL');
    }
    return;
  }

  // ── Backup ────────────────────────────────────────────────────────────
  section('Backup');
  fs.mkdirSync(runDir, { recursive: true });
  const backupPath = path.join(runDir, `backup-prune-${timestampSlug()}.json`);
  const backup = {
    createdAt: new Date().toISOString(),
    kept: {
      authUsers: keepUsers.map((u) => ({ uid: u.uid, email: u.email, displayName: u.displayName })),
      vehicles: keepVehicles.map((d) => d.data),
      devices: keepDevices.map((d) => d.data),
      routeIds: [...keepRouteIds],
      deviceIds: [...keepDeviceIds],
    },
    deleted: {
      authUsers: dropUsers.map((u) => ({
        uid: u.uid,
        email: u.email,
        displayName: u.displayName,
        providers: (u.providerData || []).map((p) => p.providerId),
        created: u.metadata?.creationTime,
        lastSignIn: u.metadata?.lastSignInTime,
      })),
      users: dropUserDocs,
      vehicles: dropVehicles,
      devices: dropDevices,
      routes: dropRoutes,
      commands: dropCommands,
      mobile_devices: dropMobile,
      telemetry: dropTelemetry,
      rtdb: Object.fromEntries(
        Object.keys(rtdbRemovals).map((key) => {
          const [node, id] = key.split('/');
          return [key, rtdbData[node]?.[id] ?? null];
        })
      ),
      localDeviceKeys: Object.fromEntries(dropCacheIds.map((id) => [id, localCache[id]])),
    },
  };
  fs.writeFileSync(backupPath, JSON.stringify(backup, null, 2));
  log(`Backup salvo em ${backupPath}`);

  // ── Firestore: dados derivados primeiro ───────────────────────────────
  section('Apagando Firestore');
  const dbDeletes = [
    ['telemetry', dropTelemetry],
    ['routes', dropRoutes],
    ['commands', dropCommands],
    ['devices', dropDevices],
    ['mobile_devices', dropMobile],
    ['vehicles', dropVehicles],
    ['users', dropUserDocs],
  ];
  for (const [name, list] of dbDeletes) {
    if (!list.length) continue;
    log(`${name}: ${list.length}`);
    await commitDeletes(db, list.map((d) => db.collection(name).doc(d.docId)));
  }

  // ── Auth: contas removidas ────────────────────────────────────────────
  section('Apagando contas do Auth');
  if (dropUsers.length) {
    for (const group of chunk(dropUsers.map((u) => u.uid), 1000)) {
      const result = await auth.deleteUsers(group);
      log(`  ${result.successCount} conta(s) removida(s), ${result.failureCount} falha(s)`);
      for (const err of result.errors || []) console.warn(`  ! ${err.error?.message || err}`);
    }
  } else {
    log('Nenhuma conta a remover.');
  }

  // ── RTDB: remove chaves fora dos conjuntos mantidos ───────────────────
  section('Apagando Realtime Database');
  const removalKeys = Object.keys(rtdbRemovals);
  if (removalKeys.length) {
    log(`${removalKeys.length} chave(s) em ${new Set(removalKeys.map((k) => k.split('/')[0])).size} nó(s)`);
    for (const group of chunk(removalKeys, 400)) {
      const updates = {};
      for (const key of group) updates[key] = null;
      await rtdb.ref('/').update(updates);
    }
  } else {
    log('Nenhuma chave a remover.');
  }

  // ── Rebuild dos espelhos com o que restou ─────────────────────────────
  section('Regravando espelhos');
  const vehiclesMirror = {};
  for (const d of keepVehicles) {
    const v = d.data;
    vehiclesMirror[String(Number(v.id ?? d.docId))] = { ...v, id: Number(v.id ?? d.docId) };
  }
  await rtdb.ref('vehicles_mirror').set(vehiclesMirror);
  log(`vehicles_mirror: ${Object.keys(vehiclesMirror).length} veículo(s)`);

  const devicesMirror = {};
  for (const d of keepDevices) {
    const raw = d.data;
    const { apiKey, ...pub } = raw;
    const out = {
      id: raw.deviceId,
      deviceId: raw.deviceId,
      vehicleId: Number(raw.vehicleId),
      firmware: raw.firmware || '',
      model: raw.model || '',
      lastSeen: Number(raw.lastSeen || 0),
      createdAt: Number(raw.createdAt || 0),
    };
    // A chave do rastreador virtual é necessária ao painel; em hardware real
    // ela nunca sai do Firestore.
    if (raw.model === 'server-sim' && apiKey) out.apiKey = apiKey;
    devicesMirror[raw.deviceId || d.docId] = out;
  }
  await rtdb.ref('devices_mirror').set(devicesMirror);
  log(`devices_mirror: ${Object.keys(devicesMirror).length} rastreador(es)`);

  // ── Cache local ───────────────────────────────────────────────────────
  if (dropCacheIds.length) {
    section('Limpando cache local');
    for (const id of dropCacheIds) delete localCache[id];
    fs.mkdirSync(runDir, { recursive: true });
    fs.writeFileSync(cacheFile, JSON.stringify(localCache, null, 2));
    log(`${dropCacheIds.length} entrada(s) removida(s) de ${cacheFile}`);
  }

  // ── Senha do admin (por último) ───────────────────────────────────────
  if (adminUser) {
    section('Senha do painel');
    await auth.updateUser(adminUser.uid, { password: process.env.SECURITAS_ADMIN_PASSWORD });
    log(`Senha definida para ${adminEmail} (não é exibida nem gravada).`);
  }

  section('Concluído');
  log('Limpeza aplicada. Backup:', backupPath);
  log(`Mantidos: ${keepUsers.length} conta(s), ${keepVehicles.length} veículo(s), ` +
    `${keepDevices.length} rastreador(es), ${keepTelemetry.length} ponto(s) de telemetria.`);
}

main().catch((err) => {
  console.error('\nFalha na limpeza:', err);
  process.exit(1);
});
