import { firestore, rtdb, makeId } from './firestore-store.js';
import { publishToVehicle } from '../realtime/sse.js';
import { haversine } from '../util/geo.js';

export async function ingestTelemetry({ device, latitude, longitude, speed, heading, battery, timestamp }) {
  const vehicleId=Number(device.vehicleId), ts=Number(timestamp)||Date.now();
  const point={id:makeId(),vehicleId,latitude:Number(latitude),longitude:Number(longitude),speed:Math.max(0,Number(speed)||0),heading:Number(heading)||0,battery:battery==null?null:Number(battery),timestamp:ts};
  const fs=firestore(), vehicleRef=fs.collection('vehicles').doc(String(vehicleId));
  await fs.collection('telemetry').doc(String(point.id)).set(point);
  const vehicleSnap=await vehicleRef.get();
  if (!vehicleSnap.exists) throw new Error('Veículo do dispositivo não encontrado');
  const vehicle={...vehicleSnap.data(),latitude:point.latitude,longitude:point.longitude,speed:point.speed,lastUpdate:ts,updatedAt:Date.now()};
  await vehicleRef.set(vehicle);
  await fs.collection('devices').doc(device.deviceId).set({...device,lastSeen:ts},{merge:true});
  await rtdb().ref(`telemetry/${vehicleId}`).set(point);
  const active=await fs.collection('routes').where('vehicleId','==',vehicleId).where('status','==','em_andamento').limit(1).get();
  if (!active.empty) {
    const route=active.docs[0].data(), points=await active.docs[0].ref.collection('points').orderBy('timestamp','desc').limit(1).get();
    const previous=points.empty?null:points.docs[0].data();
    const distance=Number(route.distance||0)+(previous?haversine(previous.latitude,previous.longitude,point.latitude,point.longitude):0);
    await active.docs[0].ref.collection('points').doc(String(point.id)).set(point);
    await active.docs[0].ref.set({...route,distance,maxSpeed:Math.max(Number(route.maxSpeed||0),point.speed),endLatitude:point.latitude,endLongitude:point.longitude,updatedAt:Date.now()});
  }
  publishToVehicle(vehicleId,'telemetry',point);
  return {vehicleId,pointId:point.id,timestamp:ts};
}
