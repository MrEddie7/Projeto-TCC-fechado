import { config } from './config.js';
import { randomBytes } from 'node:crypto';
import { db } from './db/database.js';
import { createUser, getUserByEmail } from './services/auth.service.js';

const DEMO = {
  name: process.env.SEED_NAME || 'Aluno TCC',
  email: process.env.SEED_EMAIL || 'aluno@tcc.com',
  phone: process.env.SEED_PHONE || '11988888888',
  password: process.env.SEED_PASSWORD || 'veiculotracker123',
  plate: process.env.SEED_PLATE || 'ABC1D23',
  model: process.env.SEED_MODEL || 'Onix',
  brand: process.env.SEED_BRAND || 'Chevrolet',
  year: Number(process.env.SEED_YEAR || 2022),
  color: process.env.SEED_COLOR || 'Preto',
  startLat: config.simulatorStartLat,
  startLng: config.simulatorStartLng,
};

let user = getUserByEmail(DEMO.email);
if (!user) {
  user = createUser(DEMO);
  console.log(`[seed] usuário criado: ${DEMO.email}`);
} else {
  console.log(`[seed] usuário já existia: ${DEMO.email} (id=${user.id})`);
}

const existing = db
  .prepare('SELECT * FROM vehicles WHERE user_id = ? AND plate = ?')
  .get(user.id, DEMO.plate);

function registerVehicle(plate = DEMO.plate, model = DEMO.model, brand = DEMO.brand) {
  const now = Date.now();
  const info = db
    .prepare(
      `INSERT INTO vehicles (user_id, plate, model, brand, year, color, latitude, longitude, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .run(
      user.id,
      plate,
      model,
      brand,
      DEMO.year,
      DEMO.color,
      // Cada veículo da frota nasce num ponto diferente, para as rotas
      // simuladas não coincidirem no mapa.
      DEMO.startLat + (Math.random() - 0.5) * 0.02,
      DEMO.startLng + (Math.random() - 0.5) * 0.02,
      now,
      now
    );
  return db.prepare('SELECT * FROM vehicles WHERE id = ?').get(Number(info.lastInsertRowid));
}

const vehicle = existing || registerVehicle();
console.log(`[seed] veículo: ${vehicle.plate} (id=${vehicle.id})`);

function ensureDevice(v) {
  const found = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id);
  if (found) return { device: found, created: false };
  const deviceId = `vt_${randomBytes(8).toString('hex')}`;
  const apiKey = randomBytes(24).toString('hex');
  db.prepare(
    'INSERT INTO devices (device_id, api_key, vehicle_id, created_at) VALUES (?, ?, ?, ?)'
  ).run(deviceId, apiKey, v.id, Date.now());
  return { device: db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(v.id), created: true };
}

const first = ensureDevice(vehicle);
console.log(`[seed] dispositivo ${first.created ? 'criado' : 'já existia'}.`);
console.log('');
console.log('  device_id :', first.device.device_id);
console.log('  api_key   :', first.device.api_key);
console.log('  vehicle_id:', first.device.vehicle_id);
console.log('');

// SEED_FLEET=N cria N-1 veículos extras, cada um com seu próprio device, para
// simular vários veículos ao mesmo tempo. As placas saem do principal
// incrementando o dígito final (ABC1D23 -> ABC1D24, ABC1D25...).
const fleetSize = Math.max(1, Number(process.env.SEED_FLEET || 1));
if (fleetSize > 1) {
  console.log(`[seed] frota de ${fleetSize} veículos:`);
  const FLEET = [
    ['Sedan', 'Toyota'],
    ['Furacao', 'Honda'],
    ['Kombi', 'Volkswagen'],
    ['Onix', 'Chevrolet'],
    ['Civic', 'Honda'],
  ];
  const stem = DEMO.plate.slice(0, 6);
  const lastDigit = Number(DEMO.plate.slice(-1)) || 0;
  for (let i = 1; i < fleetSize; i++) {
    const plate = stem + (((lastDigit + i) % 10) + 10) % 10;
    const [model, brand] = FLEET[(i - 1) % FLEET.length];
    let v = db.prepare('SELECT * FROM vehicles WHERE user_id = ? AND plate = ?').get(user.id, plate);
    if (!v) v = registerVehicle(plate, model, brand);
    const r = ensureDevice(v);
    console.log(`  ${String(i).padStart(2)}. ${v.plate}  ${v.brand} ${v.model}  ${r.device.device_id}`);
  }
  console.log('');
}

console.log('[seed] simulação da frota inteira:');
console.log('  npm run api          # sobe a API + um simulador por veículo');
console.log('');
console.log('[seed] veículo único:');
console.log(`  VTSIM_DEVICE_ID=${first.device.device_id} VTSIM_API_KEY=${first.device.api_key} npm run simulator`);
db.close();