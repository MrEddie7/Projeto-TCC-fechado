import { db } from '../db/database.js';
import { config } from '../config.js';
import { publishToVehicle } from '../realtime/sse.js';
import { haversine } from '../util/geo.js';

// Escreve a telemetria do dispositivo, atualiza a última posição do veículo,
// registra o ponto na rota em andamento e avisa o app em tempo real (SSE).
export function ingestTelemetry({ device, latitude, longitude, speed, heading, battery, timestamp }) {
  const deviceRow = device || null;
  const vehicleId = deviceRow?.vehicle_id ?? null;

  const ts = timestamp || Date.now();
  const normSpeed = Math.max(0, Number(speed) || 0);
  const lat = Number(latitude);
  const lng = Number(longitude);

  if (deviceRow) {
    db.prepare('UPDATE devices SET last_seen = ? WHERE id = ?').run(ts, deviceRow.id);
  }

  let pointId = null;
  if (vehicleId) {
    const info = db
      .prepare(
        `INSERT INTO telemetry (vehicle_id, device_id, latitude, longitude, speed, heading, battery, timestamp)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(vehicleId, deviceRow?.device_id, lat, lng, normSpeed, Number(heading) || 0, battery ?? null, ts);
    pointId = Number(info.lastInsertRowid);

    // Limita o histórico para não crescer sem limite (configurável via env).
    db.prepare(
      `DELETE FROM telemetry WHERE vehicle_id = ? AND id NOT IN (
         SELECT id FROM telemetry WHERE vehicle_id = ? ORDER BY timestamp DESC LIMIT ?
       )`
    ).run(vehicleId, vehicleId, config.maxTelemetryPoints);

    db.prepare(
      `UPDATE vehicles
       SET latitude = ?, longitude = ?, speed = ?, last_update = ?, updated_at = ?
       WHERE id = ?`
    ).run(lat, lng, normSpeed, ts, Date.now(), vehicleId);

    appendToActiveRoute(vehicleId, lat, lng, normSpeed, heading || 0, ts);

    publishToVehicle(vehicleId, 'telemetry', {
      vehicleId: Number(vehicleId),
      latitude: lat,
      longitude: lng,
      speed: normSpeed,
      heading: Number(heading) || 0,
      battery: battery ?? null,
      timestamp: ts,
    });
  }

  return { vehicleId: vehicleId ? Number(vehicleId) : null, pointId, timestamp: ts };
}

function appendToActiveRoute(vehicleId, lat, lng, speed, heading, ts) {
  const route = db
    .prepare(
      `SELECT * FROM routes WHERE vehicle_id = ? AND status = 'em_andamento' ORDER BY id DESC LIMIT 1`
    )
    .get(vehicleId);
  if (!route) return;

  const lastPoint = db
    .prepare(
      'SELECT * FROM route_points WHERE route_id = ? ORDER BY timestamp DESC LIMIT 1'
    )
    .get(Number(route.id));

  db.prepare(
    `INSERT INTO route_points (route_id, latitude, longitude, speed, heading, timestamp)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(Number(route.id), lat, lng, speed, heading, ts);

  let distance = Number(route.distance) || 0;
  if (lastPoint) {
    distance += haversine(lastPoint.latitude, lastPoint.longitude, lat, lng);
  }

  db.prepare(
    `UPDATE routes SET distance = ?, max_speed = MAX(max_speed, ?),
       end_latitude = ?, end_longitude = ?, updated_at = ?
     WHERE id = ?`
  ).run(distance, speed, lat, lng, Date.now(), Number(route.id));
}