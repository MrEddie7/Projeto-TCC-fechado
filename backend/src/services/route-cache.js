// Espelho (cache) das rotas no RTDB.
//
// Mesma ideia do espelho de veículos: o Firestore é a fonte oficial, mas no
// plano gratuito a cota DIÁRIA de leituras acaba e as rotas deixavam de ser
// listadas (o app quebrava ao abrir o histórico). O espelho no RTDB mantém a
// API e o app funcionando nesse cenário e é reescrito a cada leitura/escrita
// bem-sucedida do Firestore.
//
// Estrutura no RTDB:
//   routes_mirror/{routeId}            -> documento da rota
//   route_points_mirror/{routeId}/{n}   -> pontos do trajeto (anel de 300)
//   route_points_seq/{routeId}          -> contador do anel
import { getFirebase } from '../firebase/firebase-admin.js';

const ROUTES = 'routes_mirror';
const POINTS = 'route_points_mirror';
const SEQ = 'route_points_seq';
const RING = 300;

const rtdb = () => getFirebase().rtdb;

export async function mirrorRoute(route) {
  if (!route?.id) return;
  try {
    await rtdb().ref(`${ROUTES}/${route.id}`).set({
      id: Number(route.id),
      ownerUid: route.ownerUid || null,
      vehicleId: Number(route.vehicleId),
      userId: Number(route.userId || 0),
      startLatitude: Number(route.startLatitude || 0),
      startLongitude: Number(route.startLongitude || 0),
      endLatitude: Number(route.endLatitude || 0),
      endLongitude: Number(route.endLongitude || 0),
      startTime: Number(route.startTime || 0),
      endTime: Number(route.endTime || 0),
      distance: Number(route.distance || 0),
      maxSpeed: Number(route.maxSpeed || 0),
      status: route.status || 'em_andamento',
    });
  } catch (e) {
    // cache é acessório: falha aqui não pode derrubar a requisição
  }
}

export async function mirrorRoutes(routes) {
  for (const r of routes || []) await mirrorRoute(r);
}

/** Acrescenta um ponto do trajeto no anel do espelho. */
export async function mirrorRoutePoint(routeId, point) {
  if (!routeId || !point) return;
  try {
    const seqRef = rtdb().ref(`${SEQ}/${routeId}`);
    const seq = (await seqRef.get()).val() || 0;
    await seqRef.set(seq + 1);
    await rtdb().ref(`${POINTS}/${routeId}/${seq % RING}`).set({
      id: String(point.id ?? ''),
      routeId: Number(routeId),
      latitude: Number(point.latitude || 0),
      longitude: Number(point.longitude || 0),
      speed: Number(point.speed || 0),
      heading: Number(point.heading || 0),
      timestamp: Number(point.timestamp || 0),
    });
  } catch (e) {
    // idem
  }
}

export async function cachedRoutes(vehicleId) {
  const snap = await rtdb().ref(ROUTES).get();
  const all = snap.val() || {};
  return Object.values(all)
    .filter((r) => r && Number(r.vehicleId) === Number(vehicleId))
    .sort((a, b) => Number(b.startTime || 0) - Number(a.startTime || 0));
}

export async function cachedRoute(routeId) {
  const snap = await rtdb().ref(`${ROUTES}/${routeId}`).get();
  return snap.val() || null;
}

export async function cachedRoutePoints(routeId) {
  const snap = await rtdb().ref(`${POINTS}/${routeId}`).get();
  const all = snap.val() || {};
  return Object.values(all)
    .filter((p) => p)
    .sort((a, b) => Number(a.timestamp || 0) - Number(b.timestamp || 0));
}
