import { getUserByToken } from '../services/auth.service.js';
import { db } from '../db/database.js';

export function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: 'Token de autenticação obrigatório' });
  }
  const user = getUserByToken(token);
  if (!user) {
    return res.status(401).json({ error: 'Token inválido ou expirado' });
  }
  req.user = user;
  next();
}

export function requireDevice(req, res, next) {
  const deviceId = req.headers['x-device-id'];
  const apiKey = req.headers['x-api-key'] || req.body?.apiKey;
  if (!deviceId || !apiKey) {
    return res
      .status(401)
      .json({ error: 'Headers x-device-id e x-api-key são obrigatórios' });
  }
  const device = db
    .prepare('SELECT * FROM devices WHERE device_id = ? AND api_key = ?')
    .get(deviceId, apiKey);
  if (!device) {
    return res.status(403).json({ error: 'Dispositivo não autorizado' });
  }
  req.device = device;
  next();
}