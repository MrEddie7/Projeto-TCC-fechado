// Notificações push (Firebase Cloud Messaging) da API.
//
// Fluxo: o app Android registra o token FCM do aparelho via
// POST /v1/mobile/push-token (autenticado pelo ID Token do Firebase) e fica
// registrado em `userTokens/{uid}` (Firestore "securitas", gravado pelo
// Admin SDK). Quando o rastreador confirma um comando (ACK), a rota de ACK
// chama notifyVehicleState(), que envia push aos aparelhos do dono.
//
// Firestore "userTokens" é coleção só do backend — as regras de segurança não
// precisam liberar escrita para o app (o app fala com esta API).
import { getFirebase } from '../firebase/firebase-admin.js';
import { getMessaging } from 'firebase-admin/messaging';
// arrayUnion/arrayRemove não são exportados direto do subpacote; chegam via
// FieldValue, a API canônica do SDK Admin.
import { FieldValue } from 'firebase-admin/firestore';
const arrayUnion = FieldValue.arrayUnion;
const arrayRemove = FieldValue.arrayRemove;

export const USER_TOKENS_COLLECTION = 'userTokens';

/**
 * Registra um token FCM para o usuário.
 * `arrayUnion` evita duplicação e mantém tokens antigos de aparelhos que
 * ainda podem receber push (o FCM descarta inválidos no envio).
 */
export async function savePushToken(uid, token) {
  const fb = getFirebase();
  await fb.firestore
    .collection(USER_TOKENS_COLLECTION)
    .doc(String(uid))
    .set(
      {
        uid: String(uid),
        platform: 'android',
        updatedAt: Date.now(),
        tokens: arrayUnion(token),
      },
      { merge: true }
    );
}

/** Remove um token (logout / token renovado). Não falha se já não existir. */
export async function removePushToken(uid, token) {
  const fb = getFirebase();
  await fb.firestore
    .collection(USER_TOKENS_COLLECTION)
    .doc(String(uid))
    .set({ tokens: arrayRemove(token) }, { merge: true });
}

/** Tokens registrados do usuário (lista possivelmente vazia). */
async function tokensOf(uid) {
  const fb = getFirebase();
  const snap = await fb.firestore.collection(USER_TOKENS_COLLECTION).doc(String(uid)).get();
  const tokens = (snap.data()?.tokens ?? []).filter((t) => typeof t === 'string' && t.length > 0);
  return [...new Set(tokens)];
}

function vehicleLabel(v) {
  const name = `${v.brand || ''} ${v.model || ''}`.trim() || 'Veículo';
  const plate = v.plate ? ` (${v.plate})` : '';
  return `${name}${plate}`;
}

/**
 * Notifica o dono do veículo que ele acaba de ser BLOQUEADO ou DESBLOQUEADO
 * (o estado final só existe quando o rastreador confirma o comando).
 *
 * Mensagem é data-only: o Android chama onMessageReceived mesmo com o app em
 * segunda/última instância, e o app renderiza a notificação com seu canal.
 * Falha (cota, token inválido) é logada e NÃO derruba a rota que chamou.
 */
export async function notifyVehicleState(vehicleId, isBlocked) {
  try {
    const fb = getFirebase();
    const snap = await fb.firestore.collection('vehicles').doc(String(vehicleId)).get();
    const vehicle = snap.data();
    if (!vehicle?.ownerUid) return;

    const tokens = await tokensOf(vehicle.ownerUid);
    if (tokens.length === 0) return;

    const message = {
      data: {
        type: 'vehicle_state',
        vehicleId: String(vehicle.id),
        plate: String(vehicle.plate || ''),
        blocked: isBlocked ? 'true' : 'false',
        title: isBlocked ? 'Veículo bloqueado' : 'Veículo desbloqueado',
        body: isBlocked
          ? `${vehicleLabel(vehicle)} foi BLOQUEADO pelo rastreador.`
          : `${vehicleLabel(vehicle)} foi desbloqueado e está livre.`,
      },
      // 'high' → prazo curto de entrega; necessário para o FCM despertar o app.
      android: { priority: 'high', collapseKey: 'vehicle_state' },
    };

    const response = await getMessaging().sendEachForMulticast(message, tokens);
    // Limpa tokens mortos (aparelho recuperado pelo FCM como inválido/ausente).
    const invalid = response.responses
      .map((r, i) => (!r.success && isInvalidToken(r.error) ? tokens[i] : null))
      .filter(Boolean);
    for (const token of invalid) {
      await removePushToken(vehicle.ownerUid, token).catch(() => {});
    }
    if (response.failureCount > 0) {
      console.warn(
        `[fcm] estado do veículo ${vehicleId}: ${response.failureCount} envio(s) falharam`
      );
    }
  } catch (e) {
    console.warn(`[fcm] notifyVehicleState(${vehicleId}) falhou: ${e.message}`);
  }
}

function isInvalidToken(error) {
  const code = error?.code || '';
  return (
    code === 'messaging/registration-token-not-registered' ||
    code === 'messaging/invalid-registration-token' ||
    code === 'messaging/unregistered'
  );
}
