import { Router } from 'express';
import { getFirebase } from '../firebase/firebase-admin.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/error.js';
import { HttpError } from '../util/http-error.js';

// Registro do telefone: um documento por UID Firebase, sem dependência SQL.
const router = Router();
router.use(requireAuth);

const serialize = (data) => ({
  id: data.id,
  userId: data.userId,
  vehicleId: data.vehicleId ?? null,
  deviceName: data.deviceName,
  createdAt: Number(data.createdAt),
  updatedAt: Number(data.updatedAt),
});

router.post('/device', asyncHandler(async (req, res) => {
  const deviceName = String(req.body?.deviceName || '').trim();
  const vehicleId = req.body?.vehicleId == null ? null : Number(req.body.vehicleId);
  if (!deviceName) throw new HttpError(400, 'Informe o nome do dispositivo (deviceName)');
  const { firestore } = getFirebase();
  if (vehicleId != null) {
    const vehicle = await firestore.collection('vehicles').doc(String(vehicleId)).get();
    if (!vehicle.exists || vehicle.data().ownerUid !== req.user.uid) {
      throw new HttpError(404, 'Veículo não encontrado ou não pertence ao usuário');
    }
  }
  const ref = firestore.collection('mobile_devices').doc(req.user.uid);
  const before = await ref.get();
  const now = Date.now();
  const data = {
    id: req.user.uid, userId: req.user.uid, vehicleId, deviceName,
    createdAt: before.exists ? Number(before.data().createdAt) : now,
    updatedAt: now,
  };
  await ref.set(data);
  res.status(before.exists ? 200 : 201).json({ ok: true, device: serialize(data) });
}));

router.get('/device', asyncHandler(async (req, res) => {
  const snap = await getFirebase().firestore.collection('mobile_devices').doc(req.user.uid).get();
  res.json({ device: snap.exists ? serialize(snap.data()) : null });
}));

router.delete('/device', asyncHandler(async (req, res) => {
  await getFirebase().firestore.collection('mobile_devices').doc(req.user.uid).delete();
  res.json({ ok: true });
}));

export default router;
