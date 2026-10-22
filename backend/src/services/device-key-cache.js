// Cache LOCAL (dentro do servidor) das credenciais dos rastreadores.
//
// Por quê: no plano gratuito do Firebase a cota DIÁRIA de leituras do Firestore
// acaba. Sem uma cópia local, a API não consegue nem AUTENTICAR o hardware
// que reporta telemetria (requireDevice lê o documento do device no Firestore)
// e a frota simulada inteira para.
//
// O arquivo fica em backend/.run/device-keys.json (ignorado pelo git) e nunca
// é servido ao app: a apiKey é credencial do hardware, não do usuário.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const FILE = path.join(DIR, '.run', 'device-keys.json');

function readAll() {
  try {
    return JSON.parse(fs.readFileSync(FILE, 'utf8'));
  } catch {
    return {};
  }
}

function writeAll(data) {
  try {
    fs.mkdirSync(path.dirname(FILE), { recursive: true });
    fs.writeFileSync(FILE, JSON.stringify(data, null, 2));
  } catch (e) {
    console.warn(`[device-keys] não foi possível gravar o cache local: ${e.message}`);
  }
}

/** Guarda (ou atualiza) a credencial de um rastreador. */
export function rememberDevice(device) {
  if (!device?.deviceId) return;
  const all = readAll();
  all[device.deviceId] = {
    deviceId: device.deviceId,
    apiKey: device.apiKey || all[device.deviceId]?.apiKey || '',
    vehicleId: Number(device.vehicleId),
    plate: device.plate || all[device.deviceId]?.plate || '',
    model: device.model ?? all[device.deviceId]?.model ?? '',
    firmware: device.firmware ?? all[device.deviceId]?.firmware ?? '',
    lastSeen: Number(device.lastSeen || all[device.deviceId]?.lastSeen || 0),
    createdAt: Number(device.createdAt || all[device.deviceId]?.createdAt || Date.now()),
    cachedAt: Date.now(),
  };
  writeAll(all);
}

/** Credencial conhecida de um rastreador (ou null). */
export function knownDevice(deviceId) {
  return readAll()[String(deviceId)] || null;
}

/** Todas as credenciais conhecidas (formato usado pelo list-devices). */
export function knownDevices() {
  return Object.values(readAll());
}
