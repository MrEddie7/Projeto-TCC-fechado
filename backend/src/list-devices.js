#!/usr/bin/env node
// ─────────────────────────────────────────────────────────────────────────
// Lista os rastreadores (devices) que o simulador de hardware deve emular.
//
// Saída: uma linha por device, com TAB entre device_id, api_key e placa.
// Os dados vêm do FIRESTORE (o banco atual da API) — não há mais SQLite.
//
// Uso:
//   node src/list-devices.js [limite]
//   node src/list-devices.js --device vt_xxx --key xxxx     (fixa um device)
//
// É chamado pelo api.sh para subir um simulador por veículo.
// ─────────────────────────────────────────────────────────────────────────
import { getFirebase, getFirebaseInitError } from './firebase/firebase-admin.js';

const args = process.argv.slice(2);
function argValue(name) {
  const i = args.indexOf(`--${name}`);
  return i !== -1 && args[i + 1] ? args[i + 1] : null;
}

// Um device fixo vindo do .env (VTSIM_DEVICE_ID / VTSIM_API_KEY) tem
// precedência e dispensa a consulta ao Firestore.
const fixedDevice = process.env.VTSIM_DEVICE_ID || argValue('device');
const fixedKey = process.env.VTSIM_API_KEY || argValue('key');

if (fixedDevice && fixedKey) {
  process.stdout.write(`${fixedDevice}\t${fixedKey}\t(fixo)\n`);
  process.exit(0);
}

const limit = Number(process.env.SIM_FLEET || args[0] || 0);

try {
  const fb = getFirebase();
  if (!fb) throw new Error(getFirebaseInitError()?.message || 'Firebase indisponível');

  const [deviceSnap, vehicleSnap] = await Promise.all([
    fb.firestore.collection('devices').get(),
    fb.firestore.collection('vehicles').get(),
  ]);

  const vehicles = new Map(vehicleSnap.docs.map((d) => [String(d.data().id ?? d.id), d.data()]));

  // Inclui rastreadores virtuais (model='server-sim'): eles representam
  // veículos na frota simulada e DEVEM ser movimentados pelo simulador.
  const rows = deviceSnap.docs
    .map((d) => ({ deviceId: d.id, ...d.data() }))
    .filter((d) => d.vehicleId != null)
    .sort((a, b) => String(a.deviceId).localeCompare(String(b.deviceId)));

  const selected = limit > 0 ? rows.slice(0, limit) : rows;
  for (const d of selected) {
    const vehicle = vehicles.get(String(d.vehicleId)) || {};
    process.stdout.write(`${d.deviceId}\t${d.apiKey}\t${vehicle.plate || ''}\n`);
  }
} catch (e) {
  process.stderr.write(`[list-devices] ${e.message}\n`);
  process.exit(1);
}
