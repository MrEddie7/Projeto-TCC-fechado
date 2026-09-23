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

function registerVehicle() {
  const now = Date.now();
  const info = db
    .prepare(
      `INSERT INTO vehicles (user_id, plate, model, brand, year, color, latitude, longitude, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .run(
      user.id,
      DEMO.plate,
      DEMO.model,
      DEMO.brand,
      DEMO.year,
      DEMO.color,
      DEMO.startLat,
      DEMO.startLng,
      now,
      now
    );
  return db.prepare('SELECT * FROM vehicles WHERE id = ?').get(Number(info.lastInsertRowid));
}

const vehicle = existing || registerVehicle();

console.log(`[seed] veículo: ${vehicle.plate} (id=${vehicle.id})`);

const device = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(vehicle.id);
if (!device) {
  const deviceId = `vt_${randomBytes(8).toString('hex')}`;
  const apiKey = randomBytes(24).toString('hex');
  const now = Date.now();
  db.prepare(
    'INSERT INTO devices (device_id, api_key, vehicle_id, created_at) VALUES (?, ?, ?, ?)'
  ).run(deviceId, apiKey, vehicle.id, now);
  console.log('[seed] dispositivo criado.');
  const d = db.prepare('SELECT * FROM devices WHERE vehicle_id = ?').get(vehicle.id);
  console.log('');
  console.log('  device_id :', d.device_id);
  console.log('  api_key   :', d.api_key);
  console.log('  vehicle_id:', d.vehicle_id);
  console.log('');
} else {
  console.log('[seed] dispositivo já existia.');
  console.log('');
  console.log('  device_id :', device.device_id);
  console.log('  api_key   :', device.api_key);
  console.log('  vehicle_id:', device.vehicle_id);
  console.log('');
}

console.log(`[seed] inicialize o simulador com:`);
console.log(`  VTSIM_DEVICE_ID=<acima> VTSIM_API_KEY=<acima> npm run simulator`);
db.close();