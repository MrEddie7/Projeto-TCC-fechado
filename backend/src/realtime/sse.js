// Hub simples de Server-Sent Events (SSE) por veículo.
// Quando o hardware envia telemetria (ou um comando é confirmado),
// publicamos um evento para todos os clientes (app) inscritos.

const clientsByVehicle = new Map(); // vehicleId -> Set<res>

function heartbeat(res) {
  res.write(': ping\n\n');
}

export function subscribeVehicle(vehicleId, res) {
  if (!clientsByVehicle.has(vehicleId)) clientsByVehicle.set(vehicleId, new Set());
  const set = clientsByVehicle.get(vehicleId);
  set.add(res);
  res.write('retry: 3000\n\n');

  const interval = setInterval(() => heartbeat(res), 25000);

  res.on('close', () => {
    clearInterval(interval);
    set.delete(res);
    if (set.size === 0) clientsByVehicle.delete(vehicleId);
  });
}

export function publishToVehicle(vehicleId, event, data) {
  const set = clientsByVehicle.get(vehicleId);
  if (!set) return;
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const res of set) {
    try {
      res.write(payload);
    } catch {
      set.delete(res);
    }
  }
}

export function publishToAll(event, data) {
  for (const vehicleId of clientsByVehicle.keys()) {
    publishToVehicle(vehicleId, event, data);
  }
}