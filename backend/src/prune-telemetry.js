#!/usr/bin/env node
// ─────────────────────────────────────────────────────────────────────────
// Manutenção da coleção `telemetry` do Firestore.
//
// A cada leitura de GPS o ponto é gravado como um documento novo. Sem
// limpeza, a coleção cresce sem parar (o simulador sozinho wrote ~3 mil
// pontos/dia por veículo) e cada consulta que varre a coleção fica mais cara —
// foi o que estourou a cota diária de leituras do plano gratuito.
//
// O script mantém os últimos KEEP pontos de cada veículo e apaga o resto.
// Os ids dos documentos são derivados do horário (makeId = timestamp*1000+rand),
// então dá para localizar os pontos antigos por faixa de id.
//
// Uso:
//   node src/prune-telemetry.js            # mantém 400 pontos por veículo
//   node src/prune-telemetry.js 100        # mantém 100
//   node src/prune-telemetry.js --dry      # só mostra o que seria apagado
// ─────────────────────────────────────────────────────────────────────────
import { getFirebase, getFirebaseInitError } from './firebase/firebase-admin.js';

const args = process.argv.slice(2);
const dry = args.includes('--dry');
const KEEP = Math.max(10, Number(args.find((a) => /^\d+$/.test(a))) || 400);
const BATCH = 400;

const fb = getFirebase();
if (!fb) {
  console.error(`Firebase indisponível: ${getFirebaseInitError()?.message || 'erro desconhecido'}`);
  process.exit(1);
}

const db = fb.firestore;

// 1) vehicles -> pontos por veículo (consulta por faixa de id, sem varrer tudo)
const vehicles = await db.collection('vehicles').get();
let totalDeleted = 0;

// O instante mais recente de cada veículo vem do RTDB (telemetry/{vehicleId}),
// que não consome cota de leitura do Firestore — e evita depender de um índice
// composto (vehicleId + timestamp) para ordenar por data.
const live = (await fb.rtdb.ref('telemetry').get()).val() || {};

for (const v of vehicles.docs) {
  const vehicleId = Number(v.data().id ?? v.id);
  if (!Number.isFinite(vehicleId)) continue;

  const cutoff = Number(live[String(vehicleId)]?.timestamp || 0);
  if (!cutoff) {
    console.log(`veículo ${vehicleId}: sem posição no RTDB — pulando`);
    continue;
  }

  // A pontuação "corta" em cutoff - KEEP pontos equivalentes. Como o
  // espaçamento médio é de 30 s (intervalo do simulador), mantém ~KEEP pontos.
  const estimate = 30_000;
  const oldestKeepTs = cutoff - KEEP * estimate;
  const startId = String(oldestKeepTs * 1000);
  const endId = String(cutoff * 1000 + 999);

  const old = await db.collection('telemetry')
    .where('__name__', '>=', startId)
    .where('__name__', '<=', endId)
    .get();

  const victims = old.docs.filter((d) => Number(d.data().vehicleId) === vehicleId);
  if (!victims.length) {
    console.log(`veículo ${vehicleId}: nada a apagar`);
    continue;
  }
  console.log(`veículo ${vehicleId}: ${victims.length} ponto(s) antigo(s) para apagar`);
  totalDeleted += victims.length;
  if (dry) continue;

  for (let i = 0; i < victims.length; i += BATCH) {
    const batch = db.batch();
    for (const d of victims.slice(i, i + BATCH)) batch.delete(d.ref);
    await batch.commit();
  }
}

console.log(dry
  ? `[dry-run] ${totalDeleted} ponto(s) seriam apagados`
  : `[prune] ${totalDeleted} ponto(s) apagados`);
