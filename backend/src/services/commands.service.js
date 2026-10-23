// Fila de comandos enviados ao rastreador (bloqueio/desbloqueio do veículo).
//
// O comando NÃO é aplicado pelo servidor: ele é gravado como "pending" e
// consumido pelo próprio rastreador, que confirma o ACK. Só o ACK grava o
// estado final (`vehicles.isBlocked`) e libera o comando.
import { firestore, makeId, rtdb } from './firestore-store.js';

/** Comandos aceitos pelo protocolo do firmware. */
export const COMMANDS = ['block', 'unblock'];

/** Formato de saída dos comandos (usado pelas rotas do app e do painel). */
export const serializeCommand = (c) => ({
  id: Number(c.id),
  vehicleId: Number(c.vehicleId),
  command: c.command,
  status: c.status,
  note: c.note || null,
  createdAt: Number(c.createdAt),
  acknowledgedAt: c.acknowledgedAt || null,
});

/**
 * Enfileira um comando para o rastreador do veículo.
 *
 * - Firestore: cola de comandos (o firmware consulta `?status=pending`).
 * - RTDB: espelho `commands/{vehicleId}`, consumido pelo app.
 *
 * @param {number|string} vehicleId
 * @param {'block'|'unblock'} command
 * @param {string} [note]
 */
export async function enqueueCommand(vehicleId, command, note = '') {
  const now = Date.now();
  const data = {
    id: makeId(),
    vehicleId: Number(vehicleId),
    command,
    status: 'pending',
    note: note || '',
    createdAt: now,
    acknowledgedAt: null,
  };
  await firestore().collection('commands').doc(String(data.id)).set(data);
  await rtdb().ref(`commands/${vehicleId}`).set({
    command,
    status: 'pending',
    timestamp: now,
    commandId: data.id,
  });
  return data;
}
