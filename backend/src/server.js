import { config } from './config.js';
import { createApp } from './app.js';
import { db } from './db/database.js';

const app = createApp();

app.listen(config.port, () => {
  console.log('==================================================');
  console.log('  VEICULOTRACKER API - ponte hardware <-> app');
  console.log(`  http://localhost:${config.port}`);
  console.log(`  Health check  : GET /health`);
  console.log(`  Doc (TXT)     : backend/API_ESTRUTURA.txt`);
  console.log('==================================================');
  console.log(`  Banco: ${config.dbPath}`);
});

// Encerramento gracioso (fecha o SQLite corretamente)
function shutdown() {
  console.log('\nEncerrando...');
  try {
    db.close();
  } catch {}
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);