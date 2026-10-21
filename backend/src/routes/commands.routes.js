import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { firestore, ownedVehicle, makeId, rtdb } from '../services/firestore-store.js';

const router = Router();
router.use(requireAuth);
const out = (c) => ({ id:Number(c.id), vehicleId:Number(c.vehicleId), command:c.command, status:c.status, note:c.note || null, createdAt:Number(c.createdAt), acknowledgedAt:c.acknowledgedAt || null });
router.post('/:vehicleId/commands', asyncHandler(async (req,res) => {
  const vehicle=await ownedVehicle(req.user.uid,req.params.vehicleId), command=req.body?.command;
  if (!['block','unblock'].includes(command)) throw new HttpError(400,'Comando inválido');
  const now=Date.now(), data={id:makeId(),vehicleId:Number(vehicle.id),command,status:'pending',note:req.body?.note || '',createdAt:now,acknowledgedAt:null};
  await firestore().collection('commands').doc(String(data.id)).set(data);
  await rtdb().ref(`commands/${vehicle.id}`).set({command,status:'pending',timestamp:now,commandId:data.id});
  res.status(201).json({command:out(data)});
}));
router.get('/:vehicleId/commands', asyncHandler(async (req,res) => {
  const vehicle=await ownedVehicle(req.user.uid,req.params.vehicleId);
  let query=firestore().collection('commands').where('vehicleId','==',Number(vehicle.id));
  if (req.query.status) query=query.where('status','==',req.query.status);
  const snap=await query.get();
  res.json({commands:snap.docs.map(d=>out(d.data())).sort((a,b)=>b.createdAt-a.createdAt).slice(0,100)});
}));
export default router;
