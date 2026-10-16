import { config } from './config.js';
import { createApp } from './app.js';
import { db } from './db/database.js';
import { startFirebaseSync } from './services/firebase-sync.service.js';
import { startVehicleSimulation } from './services/simulation.service.js';
import { importFromCloud } from './services/cloud-import.service.js';

const app = createApp();

const syncTimer = startFirebaseSync();
const simBotTimer = startVehicleSimulation();

app.listen(config.port, () => {
  console.log('==================================================');
  console.log('  SECURITAS API - ponte hardware <-> app');
  console.log(`  http://localhost:${config.port}`);
  console.log(`  Health check  : GET /health`);
  console.log(`  Sync Firebase : ${config.firebaseSyncEnabled ? `a cada ${config.firebaseSyncIntervalMs}ms` : 'desativado'}`);
  console.log(`  Simulação     : ${config.simulationEnabled ? `veículos do app a cada ${config.simulationIntervalMs}ms` : 'desativada'}`);
  console.log(`  Doc (TXT)     : backend/API_ESTRUTURA.txt`);
  console.log('==================================================');
  console.log(`  Banco: ${config.dbPath}`);

  // Importa automaticamente as contas/veículos que o app enviou ao Firestore
  // para o SQLite, sem bloquear o boot. Só executa se o Firebase estiver ativo.
  if (config.firebaseSyncEnabled) {
    importFromCloud({ createTracker: true })
      .then((stats) => {
        if (stats.error) {
          console.warn('[boot] importFromCloud: Firestore indisponível —', stats.error);
        } else {
          console.log(
            `[boot] importFromCloud: ${stats.accounts} conta(s) · ` +
            `${stats.cloudVehicles} veículo(s) no Firestore · ` +
            `${stats.usersCreated} usuário(s) criado(s) · ` +
            `${stats.vehiclesCreated} veículo(s) importado(s) · ` +
            `${stats.trackersCreated} rastreador(es) criado(s)`
          );
        }
      })
      .catch((e) => console.warn('[boot] importFromCloud erro:', e.message));
  }
});

// Encerramento gracioso (fecha o SQLite corretamente)
function shutdown() {
  console.log('\nEncerrando...');
  if (syncTimer) clearInterval(syncTimer);
  if (simBotTimer) clearInterval(simBotTimer);
  try {
    db.close();
  } catch {}
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);