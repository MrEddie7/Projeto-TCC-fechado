import { Router } from 'express';
import { requireDevice } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { firestore, rtdb } from '../services/firestore-store.js';
import { ingestTelemetry } from '../services/telemetry.service.js';
import { notifyVehicleState } from '../services/notifications.service.js';
import { publishToVehicle } from '../realtime/sse.js';
import { isQuotaError } from '../util/firestore-errors.js';
const router=Router();
const out=c=>({id:Number(c.id),vehicleId:Number(c.vehicleId),command:c.command,status:c.status,note:c.note||'',createdAt:Number(c.createdAt),acknowledgedAt:c.acknowledgedAt||null});
router.post('/telemetry',requireDevice,asyncHandler(async(req,res)=>{const {latitude,longitude}=req.body||{};if(typeof latitude!=='number'||typeof longitude!=='number'||latitude<-90||latitude>90||longitude<-180||longitude>180)throw new HttpError(400,'Coordenadas inválidas');res.status(201).json({ok:true,...await ingestTelemetry({...req.body,device:req.device})});}));
router.get('/:deviceId/commands',requireDevice,asyncHandler(async(req,res)=>{let s;try{s=await firestore().collection('commands').where('vehicleId','==',Number(req.device.vehicleId)).get();}catch(e){if(!isQuotaError(e))throw e;s={docs:[]};}const status=req.query.status||'pending';res.json({commands:s.docs.map(d=>d.data()).filter(c=>c.status===status).sort((a,b)=>a.createdAt-b.createdAt).map(out)});}));
router.post('/:deviceId/commands/:commandId/ack',requireDevice,asyncHandler(async(req,res)=>{if(typeof req.body?.ok!=='boolean')throw new HttpError(400,'Informe ok');const ref=firestore().collection('commands').doc(String(req.params.commandId)),s=await ref.get();if(!s.exists||Number(s.data().vehicleId)!==Number(req.device.vehicleId))throw new HttpError(404,'Comando não encontrado');const now=Date.now(), c={...s.data(),status:req.body.ok?'acknowledged':'failed',note:req.body.note||s.data().note||'',acknowledgedAt:now};await ref.set(c);if(req.body.ok){const blocked=c.command==='block';await firestore().collection('vehicles').doc(String(c.vehicleId)).set({isBlocked:blocked,updatedAt:now},{merge:true});await rtdb().ref(`commands/${c.vehicleId}`).remove();publishToVehicle(c.vehicleId,'blocked',{vehicleId:c.vehicleId,isBlocked:blocked,command:c.command,timestamp:now});
// Push para o dono: estado final do bloqueio só existe com o ACK.
// Aguardamos (Vercel encerra o worker após a resposta) mas falha é engolida
// dentro de notifyVehicleState para o ACK nunca depender do FCM.
await notifyVehicleState(c.vehicleId, blocked);}res.json({command:out(c)});}));
router.get('/:deviceId/status',requireDevice,asyncHandler(async(req,res)=>{let s,pending;try{s=await firestore().collection('vehicles').doc(String(req.device.vehicleId)).get();pending=await firestore().collection('commands').where('vehicleId','==',Number(req.device.vehicleId)).where('status','==','pending').get();}catch(e){if(!isQuotaError(e))throw e;s={exists:false,data:()=>null};pending={size:0};}const v=s.exists?s.data():null;
// Heartbeat: o firmware chama esta rota mesmo sem GPS conectado, para manter
// o rastreador marcado como "online" no painel.
const seen=Date.now();await firestore().collection('devices').doc(req.device.deviceId).set({lastSeen:seen},{merge:true});
res.json({deviceId:req.device.deviceId,registered:true,vehicle:v?{id:Number(v.id),plate:v.plate,model:v.model,isBlocked:v.isBlocked===true,latitude:Number(v.latitude||0),longitude:Number(v.longitude||0),speed:Number(v.speed||0)}:null,pendingCommands:pending.size,lastSeen:seen,serverTime:Date.now()});}));
// Alvo de posição definido no painel (arrastar no mapa). O simulador consulta
// isto a cada ciclo e reancora quando o carimbo muda. 204 quando não há alvo.
router.get('/:deviceId/position',requireDevice,asyncHandler(async(req,res)=>{const s=await rtdb().ref(`sim_control/${Number(req.device.vehicleId)}`).get();if(!s.exists())return res.status(204).end();res.json(s.val());}));
export default router;
