import { Router } from 'express';
import { requireDevice } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { firestore, rtdb } from '../services/firestore-store.js';
import { ingestTelemetry } from '../services/telemetry.service.js';
import { isQuotaError } from '../util/firestore-errors.js';
import { acknowledgeCommand, listCommandsForVehicle, serializeCommand } from '../services/commands.service.js';
const router=Router();
const out=serializeCommand;
router.post('/telemetry',requireDevice,asyncHandler(async(req,res)=>{const {latitude,longitude}=req.body||{};if(typeof latitude!=='number'||typeof longitude!=='number'||latitude<-90||latitude>90||longitude<-180||longitude>180)throw new HttpError(400,'Coordenadas inválidas');res.status(201).json({ok:true,...await ingestTelemetry({...req.body,device:req.device})});}));
router.get('/:deviceId/commands',requireDevice,asyncHandler(async(req,res)=>{
  const status=req.query.status||'pending';
  const commands=await listCommandsForVehicle(req.device.vehicleId,status);
  res.json({commands:commands.map(out)});
}));
router.post('/:deviceId/commands/:commandId/ack',requireDevice,asyncHandler(async(req,res)=>{
  const command=await acknowledgeCommand(req.device.vehicleId,req.params.commandId,{
    ok:req.body?.ok,
    note:req.body?.note,
  });
  res.json({command:out(command)});
}));
router.get('/:deviceId/status',requireDevice,asyncHandler(async(req,res)=>{let s;try{s=await firestore().collection('vehicles').doc(String(req.device.vehicleId)).get();}catch(e){if(!isQuotaError(e))throw e;s={exists:false,data:()=>null};}const pending=await listCommandsForVehicle(req.device.vehicleId,'pending');const v=s.exists?s.data():null;
// Heartbeat: o firmware chama esta rota mesmo sem GPS conectado, para manter
// o rastreador marcado como "online" no painel.
const seen=Date.now();await firestore().collection('devices').doc(req.device.deviceId).set({lastSeen:seen},{merge:true});
res.json({deviceId:req.device.deviceId,registered:true,vehicle:v?{id:Number(v.id),plate:v.plate,model:v.model,isBlocked:v.isBlocked===true,latitude:Number(v.latitude||0),longitude:Number(v.longitude||0),speed:Number(v.speed||0)}:null,pendingCommands:pending.length,lastSeen:seen,serverTime:Date.now()});}));
// Alvo de posição definido no painel (arrastar no mapa). O simulador consulta
// isto a cada ciclo e reancora quando o carimbo muda. 204 quando não há alvo.
router.get('/:deviceId/position',requireDevice,asyncHandler(async(req,res)=>{const s=await rtdb().ref(`sim_control/${Number(req.device.vehicleId)}`).get();if(!s.exists())return res.status(204).end();res.json(s.val());}));
export default router;
