import os from 'node:os';
import { config } from './config.js';
import { createApp } from './app.js';

const app = createApp();

// Bind explícito em todas as interfaces: sem host, depende da pilha de rede do
// SO e alguns ambientes escutam só em loopback, o que gera o erro do app
// "Failed to connect to /<IP-da-rede>:3000".
const server = app.listen(config.port, '0.0.0.0', () => {
  console.log(`SECURITAS API (Firestore) em http://localhost:${config.port}`);
  const lan = Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal)
    .map((i) => i.address);
  for (const ip of lan) console.log(`  -> rede local: http://${ip}:${config.port} (URL que o app Android deve usar)`);
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`[api] porta ${config.port} já está em uso — encerre o processo anterior ou defina outra PORT no .env`);
  } else {
    console.error(`[api] falha ao escutar na porta ${config.port}: ${err.message}`);
  }
  process.exit(1);
});
