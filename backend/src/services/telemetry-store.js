// Armazenamento de telemetria com fallback no RTDB.
//
// Motivo: no plano gratuito do Firebase a cota DIÁRIA de leituras do Firestore
// acaba rápido. Ler "o último ponto" varrendo a coleção inteira (o que a API
// fazia) consome milhares de leituras por requisição — o app consulta a
// posição a cada poucos segundos e estourava a cota em minutos.
//
// Estratégia:
//  - o último ponto de cada veículo vive no RTDB (telemetry/{vehicleId}) — 0
//    leituras do Firestore;
//  - um histórico curto em anel no RTDB (telemetry_history/{vehicleId}) cobre
//    a janela recente sem depender do Firestore;
//  - o Firestore continua sendo o histórico de longo prazo (escrita), e só é
//    lido quando a cota permite.
import { firestore, rtdb } from './firestore-store.js';
import { isQuotaError } from '../util/firestore-errors.js';

const HISTORY_SIZE = 300; // pontos mantidos no anel do RTDB

function toPoint(raw) {
  if (!raw) return null;
  return {
    id: Number(raw.id),
    latitude: Number(raw.latitude),
    longitude: Number(raw.longitude),
    speed: Number(raw.speed || 0),
    heading: Number(raw.heading || 0),
    battery: raw.battery == null ? null: Number(raw.battery),
    timestamp: Number(raw.timestamp || 0),
  };
}

/** Último ponto conhecido do veículo: RTDB primeiro, Firestore só se faltar. */
export async function latestPoint(vehicleId) {
  const id = Number(vehicleId);
  try {
    const snap = await rtdb().ref(`telemetry/${id}`).get();
    const value = snap.val();
    if (value) return toPoint(value);
  } catch (e) {
    // RTDB indisponível: tenta o Firestore.
  }
  try {
    const s = await firestore().collection('telemetry').where('vehicleId', '==', id).get();
    const points = s.docs.map((d) => d.data()).sort((a, b) => Number(b.timestamp) - Number(a.timestamp));
    return points.length ? toPoint(points[0]) : null;
  } catch (e) {
    if (isQuotaError(e)) return null;
    throw e;
  }
}

/** Histórico recente do veículo (RTDB primeiro, Firestore como reserva). */
export async function recentPoints(vehicleId, { limit = 100, after = 0 } = {}) {
  const id = Number(vehicleId);
  const size = Math.min(Math.max(Number(limit) || 100, 1), 500);
  try {
    const snap = await rtdb().ref(`telemetry_history/${id}`).get();
    const values = snap.val() || {};
    const points = Object.values(values)
      .map(toPoint)
      .filter((p) => p && p.timestamp > Number(after || 0))
      .sort((a, b) => a.timestamp - b.timestamp)
      .slice(-size);
    if (points.length) return points;
  } catch (e) {
    // cai no Firestore
  }
  try {
    const s = await firestore().collection('telemetry').where('vehicleId', '==', id).get();
    return s.docs
      .map((d) => toPoint(d.data()))
      .filter((p) => p && p.timestamp > Number(after || 0))
      .sort((a, b) => a.timestamp - b.timestamp)
      .slice(-size);
  } catch (e) {
    if (isQuotaError(e)) return [];
    throw e;
  }
}

/**
 * Guarda o ponto no RTDB: posição atual + anel de histórico.
 * Usa um contador para o anel (sem leitura para limpar) e o Firestore como
 * histórico de longo prazo.
 */
export async function pushTelemetry(vehicleId, point) {
  const id = Number(vehicleId);
  const doc = {
    id: Number(point.id),
    vehicleId: id,
    deviceId: point.deviceId || '',
    latitude: Number(point.latitude),
    longitude: Number(point.longitude),
    speed: Number(point.speed || 0),
    heading: Number(point.heading || 0),
    battery: point.battery == null ? null : Number(point.battery),
    timestamp: Number(point.timestamp || Date.now()),
  };
  const db = rtdb();
  await db.ref(`telemetry/${id}`).set(doc);

  // Anel de histórico: chave = sequência módulo o tamanho do anel.
  const seqRef = db.ref(`telemetry_seq/${id}`);
  const seqSnap = await seqRef.get();
  const seq = (Number(seqSnap.val()) || 0) + 1;
  await seqRef.set(seq);
  await db.ref(`telemetry_history/${id}/${String(seq % HISTORY_SIZE)}`).set(doc);
  return doc;
}
