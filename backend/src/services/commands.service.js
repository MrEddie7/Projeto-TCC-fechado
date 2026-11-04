// Fila de comandos enviados ao rastreador (bloqueio/desbloqueio do veículo).
//
// O comando NÃO é aplicado pelo servidor: ele é gravado como "pending" e
// consumido pelo próprio rastreador, que confirma o ACK. Só o ACK grava o
// estado final (`vehicles.isBlocked`) e libera o comando.
import { getFirebase, getFirebaseInitError } from '../firebase/firebase-admin.js';
import { firestore, makeId, rtdb } from './firestore-store.js';
import { HttpError } from '../util/http-error.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { publishToVehicle } from '../realtime/sse.js';
import { notifyVehicleState } from './notifications.service.js';
import { cachedVehicle, mirrorVehicles } from './vehicle-cache.js';

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
 * Lê `command` do body mesmo quando o runtime (ex.: Vercel) entrega o JSON
 * como string em vez de objeto — nesse caso `req.body.command` é undefined e
 * a rota respondia 400 sem gravar nada no Firebase.
 */
export function commandFromBody(body) {
  if (body == null) return undefined;
  if (typeof body === 'string') {
    try {
      return JSON.parse(body)?.command;
    } catch {
      return undefined;
    }
  }
  if (typeof body.command === 'string') return body.command;
  if (typeof body === 'object' && Buffer.isBuffer(body)) {
    try {
      return JSON.parse(body.toString())?.command;
    } catch {
      return undefined;
    }
  }
  return body.command;
}

/**
 * Enfileira um comando para o rastreador do veículo.
 *
 * - Firestore: fila de comandos (o firmware consulta `?status=pending`).
 * - RTDB: espelho `commands/{vehicleId}`, consumido pelo app.
 *
 * @param {number|string} vehicleId
 * @param {'block'|'unblock'} command
 * @param {string} [note]
 */
export async function enqueueCommand(vehicleId, command, note = '') {
  const fb = getFirebase();
  if (!fb) {
    throw new HttpError(
      503,
      `Firebase indisponível: ${getFirebaseInitError()?.message || 'não inicializado'}`
    );
  }
  const vid = Number(vehicleId);
  if (!Number.isFinite(vid)) throw new HttpError(400, 'Veículo inválido');

  const now = Date.now();
  const data = {
    id: makeId(),
    vehicleId: vid,
    command,
    status: 'pending',
    note: note || '',
    createdAt: now,
    acknowledgedAt: null,
  };

  // RTDB primeiro: não depende da cota do Firestore e é o que o console
  // do Realtime Database mostra imediatamente após o clique.
  await fb.rtdb.ref(`commands/${vid}`).set({
    command,
    status: 'pending',
    timestamp: now,
    commandId: data.id,
  });

  try {
    await fb.firestore.collection('commands').doc(String(data.id)).set(data);
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    console.warn(`[commands] Firestore indisponível, comando ${data.id} ficou só no RTDB`);
  }

  publishToVehicle(vid, 'command', serializeCommand(data));
  return data;
}

/**
 * Lista comandos do veículo. Se o Firestore estiver indisponível (cota),
 * cai no espelho `commands/{vehicleId}` do RTDB — é o que o simulador
 * precisa para o botão Bloquear continuar funcionando.
 */
export async function listCommandsForVehicle(vehicleId, status = 'pending') {
  const vid = Number(vehicleId);
  let commands = [];
  try {
    const snap = await firestore().collection('commands').where('vehicleId', '==', vid).get();
    commands = snap.docs.map((d) => ({ ...d.data(), id: d.data().id ?? d.id }));
  } catch (e) {
    if (!isQuotaError(e)) throw e;
  }
  if (status) commands = commands.filter((c) => c.status === status);
  if (status === 'pending' && !commands.length) {
    try {
      const snap = await rtdb().ref(`commands/${vid}`).get();
      const v = snap.val();
      if (v?.command && (v.status === 'pending' || !v.status)) {
        commands = [{
          id: v.commandId || Date.now(),
          vehicleId: vid,
          command: v.command,
          status: 'pending',
          note: '',
          createdAt: Number(v.timestamp || Date.now()),
          acknowledgedAt: null,
        }];
      }
    } catch {
      // Sem RTDB também: devolve a lista vazia (o rastreador tenta de novo).
    }
  }
  return commands.sort((a, b) => Number(a.createdAt) - Number(b.createdAt));
}

/**
 * ACK do rastreador (real ou simulado). Atualiza o comando, grava
 * `vehicles.isBlocked` e limpa o espelho no RTDB.
 */
export async function acknowledgeCommand(vehicleId, commandId, { ok, note } = {}) {
  if (typeof ok !== 'boolean') throw new HttpError(400, 'Informe ok');
  const vid = Number(vehicleId);
  const cid = String(commandId);
  const now = Date.now();
  const ref = firestore().collection('commands').doc(cid);
  let data = null;
  try {
    const snap = await ref.get();
    if (snap.exists) data = snap.data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
  }
  if (!data) {
    try {
      const snap = await rtdb().ref(`commands/${vid}`).get();
      const v = snap.val();
      const matches = v?.command && (String(v.commandId ?? '') === cid || v.commandId == null);
      if (matches) {
        data = {
          id: Number(v.commandId || cid),
          vehicleId: vid,
          command: v.command,
          status: 'pending',
          note: '',
          createdAt: Number(v.timestamp || now),
        };
      }
    } catch {
      // segue para o 404 abaixo
    }
  }
  if (!data || Number(data.vehicleId) !== vid) {
    throw new HttpError(404, 'Comando não encontrado');
  }

  const updated = {
    ...data,
    id: Number(data.id || cid),
    vehicleId: vid,
    status: ok ? 'acknowledged' : 'failed',
    note: note || data.note || '',
    acknowledgedAt: now,
  };
  try {
    await ref.set(updated);
  } catch (e) {
    if (!isQuotaError(e)) throw e;
  }

  if (ok) {
    const blocked = updated.command === 'block';
    try {
      await firestore().collection('vehicles').doc(String(vid)).set(
        { isBlocked: blocked, updatedAt: now },
        { merge: true },
      );
    } catch (e) {
      if (!isQuotaError(e)) throw e;
    }
    // Mantém o espelho do RTDB alinhado: o APP/painel em modo offline lê
    // `vehicles_mirror`, e sem isto o bloqueio só aparecia no Firestore.
    try {
      const cache = await cachedVehicle(vid);
      if (cache) {
        await mirrorVehicles([{ ...cache, isBlocked: blocked, updatedAt: now }]);
      } else {
        // Espelho ainda vazio (nunca listado): sem isto o ACK só existiria no
        // Firestore e o app/painel em modo offline continuaria vendo o estado
        // antigo. O comando é raro, então a leitura extra não pesa na cota.
        const snap = await firestore().collection('vehicles').doc(String(vid)).get();
        if (snap.exists) {
          await mirrorVehicles([{ ...snap.data(), id: vid, isBlocked: blocked, updatedAt: now }]);
        }
      }
    } catch {
      // espelho é acessório: o Firestore já tem o estado final
    }
    await rtdb().ref(`commands/${vid}`).remove().catch(() => {});
    publishToVehicle(vid, 'blocked', {
      vehicleId: vid,
      isBlocked: blocked,
      command: updated.command,
      timestamp: now,
    });
    await notifyVehicleState(vid, blocked);
  }
  return updated;
}
