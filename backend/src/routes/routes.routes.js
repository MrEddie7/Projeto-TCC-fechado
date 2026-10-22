import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { firestore, ownedVehicle, makeId } from '../services/firestore-store.js';
import {
  mirrorRoute,
  mirrorRoutes,
  mirrorRoutePoint,
  cachedRoutes,
  cachedRoute,
  cachedRoutePoints,
} from '../services/route-cache.js';
const router=Router();
// `ownerUid` (string) é a identidade real do dono; `userId` numérico é o
// espelho legado para o app (hash do uid). O app usa o ownerUid para
// recalcular o id numérico quando o userId veio como string.
// Hash do UID no formato Java (String.hashCode) → mesmo id do app Android.
function numericUserId(v){const n=Number(v.userId);if(Number.isFinite(n)&&n>0)return n;const uid=String(v.ownerUid||'');let h=0;for(let i=0;i<uid.length;i++)h=(Math.imul(31,h)+uid.charCodeAt(i))|0;return Math.abs(h);}
const out=r=>({id:Number(r.id),vehicleId:Number(r.vehicleId),userId:Number(r.userId||0)||0,ownerUid:r.ownerUid||null,startLatitude:Number(r.startLatitude||0),startLongitude:Number(r.startLongitude||0),endLatitude:Number(r.endLatitude||0),endLongitude:Number(r.endLongitude||0),startTime:Number(r.startTime),endTime:Number(r.endTime||0),distance:Number(r.distance||0),maxSpeed:Number(r.maxSpeed||0),status:r.status});

// ── Leituras tolerantes à cota do Firestore ──────────────────────────────
// O Firestore é a fonte oficial; o espelho no RTDB (routes_mirror) cobre o
// caso em que a cota diária de leitura acaba (ver API_ESTRUTURA.txt, 12b).
async function activeRoute(vehicleId) {
  try {
    const s = await firestore().collection('routes')
      .where('vehicleId', '==', Number(vehicleId)).where('status', '==', 'em_andamento').limit(1).get();
    return s.empty ? null : s.docs[0].data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = await cachedRoutes(vehicleId);
    return cached.find((r) => r.status === 'em_andamento') || null;
  }
}

async function ownedRoute(uid, id) {
  try {
    const s = await firestore().collection('routes').doc(String(id)).get();
    if (!s.exists || s.data().ownerUid !== uid) throw new HttpError(404, 'Rota não encontrada');
    return s.data();
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    const cached = await cachedRoute(id);
    if (!cached || cached.ownerUid !== uid) throw new HttpError(404, 'Rota não encontrada');
    return cached;
  }
}

router.post('/vehicles/:vehicleId/routes/start',requireAuth,asyncHandler(async(req,res)=>{
  const v=await ownedVehicle(req.user.uid,req.params.vehicleId), fs=firestore();
  const old=await activeRoute(v.id);
  if(old)return res.json({route:out(old),message:'Rota já em andamento'});
  const now=Date.now(),r={id:makeId(),ownerUid:req.user.uid,vehicleId:Number(v.id),userId:numericUserId(v),startLatitude:Number(v.latitude||0),startLongitude:Number(v.longitude||0),endLatitude:0,endLongitude:0,startTime:now,endTime:0,distance:0,maxSpeed:0,status:'em_andamento'};
  await fs.collection('routes').doc(String(r.id)).set(r);
  await mirrorRoute(r);
  res.status(201).json({route:out(r)});
}));

router.post('/vehicles/:vehicleId/routes/stop',requireAuth,asyncHandler(async(req,res)=>{
  const v=await ownedVehicle(req.user.uid,req.params.vehicleId),fs=firestore();
  const current=await activeRoute(v.id);
  if(!current)throw new HttpError(404,'Nenhuma rota em andamento');
  const r={...current,status:'concluida',endTime:Date.now(),endLatitude:Number(v.latitude||0),endLongitude:Number(v.longitude||0)};
  await fs.collection('routes').doc(String(r.id)).set(r);
  await mirrorRoute(r);
  res.json({route:out(r)});
}));

router.get('/vehicles/:vehicleId/routes',requireAuth,asyncHandler(async(req,res)=>{
  const v=await ownedVehicle(req.user.uid,req.params.vehicleId);
  let routes;
  try {
    const s=await firestore().collection('routes').where('vehicleId','==',Number(v.id)).get();
    routes=s.docs.map(d=>d.data());
    await mirrorRoutes(routes);
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    routes=await cachedRoutes(v.id);
  }
  res.json({routes:routes.map(out).sort((a,b)=>b.startTime-a.startTime)});
}));

router.get('/routes/:routeId',requireAuth,asyncHandler(async(req,res)=>{
  const r=await ownedRoute(req.user.uid,req.params.routeId);
  let points;
  try {
    const s=await firestore().collection('routes').doc(String(r.id)).collection('points').get();
    points=s.docs.map(d=>d.data());
  } catch (e) {
    if (!isQuotaError(e)) throw e;
    points=await cachedRoutePoints(r.id);
  }
  res.json({route:out(r),points:points.sort((a,b)=>a.timestamp-b.timestamp)});
}));

router.delete('/routes/:routeId',requireAuth,asyncHandler(async(req,res)=>{
  const r=await ownedRoute(req.user.uid,req.params.routeId);
  await firestore().collection('routes').doc(String(r.id)).delete();
  res.json({ok:true});
}));

// Usado pela ingestão de telemetria: acrescenta um ponto ao espelho da rota
// (o Firestore continua sendo o histórico oficial).
export { mirrorRoutePoint };
export default router;
