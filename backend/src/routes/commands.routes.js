import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';
import { firestore, ownedVehicle } from '../services/firestore-store.js';
import { COMMANDS, enqueueCommand, serializeCommand } from '../services/commands.service.js';

const router = Router();
router.use(requireAuth);
const out = serializeCommand;
router.post('/:vehicleId/commands', asyncHandler(async (req,res) => {
  const vehicle=await ownedVehicle(req.user.uid,req.params.vehicleId), command=req.body?.command;
  if (!COMMANDS.includes(command)) throw new HttpError(400,'Comando inválido');
  const data=await enqueueCommand(vehicle.id,command,req.body?.note || '');
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
