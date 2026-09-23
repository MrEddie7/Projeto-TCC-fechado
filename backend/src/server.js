import { config } from './config.js';
import { createApp } from './app.js';
import { db } from './db/database.js';
import { startFirebaseSync } from './services/firebase-sync.service.js';

const app = createApp();

const syncTimer = startFirebaseSync();

app.listen(config.port, () => {
  console.log('==================================================');
  console.log('  VEICULOTRACKER API - ponte hardware <-> app');
  console.log(`  http://localhost:${config.port}`);
  console.log(`  Health check  : GET /health`);
  console.log(`  Sync Firebase : ${config.firebaseSyncEnabled ? `a cada ${config.firebaseSyncIntervalMs}ms` : 'desativado'}`);
  console.log(`  Doc (TXT)     : backend/API_ESTRUTURA.txt`);
  console.log('==================================================');
  console.log(`  Banco: ${config.dbPath}`);
});

// Encerramento gracioso (fecha o SQLite corretamente)
function shutdown() {
  console.log('\nEncerrando...');
  if (syncTimer) clearInterval(syncTimer);
  try {
    db.close();
  } catch {}
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);