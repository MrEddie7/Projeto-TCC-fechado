import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { db } from '../db/database.js';

const SESSION_DAYS = 30;

export function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  const hash = scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${hash}`;
}

export function verifyPassword(password, stored) {
  const [salt, hash] = stored.split(':');
  const candidate = scryptSync(password, salt, 64);
  const expected = Buffer.from(hash, 'hex');
  return candidate.length === expected.length && timingSafeEqual(candidate, expected);
}

export function createToken(userId) {
  const token = randomBytes(32).toString('hex');
  const now = Date.now();
  const expiresAt = now + SESSION_DAYS * 24 * 60 * 60 * 1000;
  db.prepare(
    'INSERT INTO tokens (user_id, token, created_at, expires_at) VALUES (?, ?, ?, ?)'
  ).run(userId, token, now, expiresAt);
  return { token, expiresAt };
}

export function deleteToken(token) {
  db.prepare('DELETE FROM tokens WHERE token = ?').run(token);
}

export function getUserByToken(token) {
  const row = db
    .prepare(
      `SELECT u.*, t.token AS _token, t.expires_at AS _expires_at
       FROM tokens t
       JOIN users u ON u.id = t.user_id
       WHERE t.token = ?`
    )
    .get(token);
  if (!row) return null;
  if (row._expires_at < Date.now()) {
    deleteToken(token);
    return null;
  }
  return row;
}

export function createUser({ name, email, phone, password }) {
  const passwordHash = hashPassword(password);
  const createdAt = Date.now();
  const info = db
    .prepare(
      'INSERT INTO users (name, email, phone, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)'
    )
    .run(name, email, phone || '', passwordHash, createdAt, createdAt);
  return { id: Number(info.lastInsertRowid), name, email, phone: phone || '', createdAt };
}

export function getUserByEmail(email) {
  return db.prepare('SELECT * FROM users WHERE email = ?').get(email);
}

export function publicUser(u) {
  return {
    id: Number(u.id),
    name: u.name,
    email: u.email,
    phone: u.phone,
    createdAt: Number(u.created_at),
  };
}