import os from 'node:os';
import { config } from './config.js';
import app from './app.js';

// `server.js` sobe um listener TCP (uso local, via npm start / api.sh). Em
// ambientes serverless (Vercel) quem importa é o handler, que só precisa do
// app já montado — por isso o app é exportado como default.
export default app;
export { app };

// Bind explícito em todas as interfaces: sem host, depende da pilha de rede do
// SO e alguns ambientes escutam só em loopback, o que gera o erro do app
// "Failed to connect to /<IP-da-rede>:3000".
//
// VERCEL define essa variável apenas no runtime da plataforma; nesse caso não
// subimos listener próprio (o Vercel gerencia a porta e o processo).
if (!process.env.VERCEL) {
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
}

// A API não pode MORRER por um erro de Firebase (cota do Firestore, rede
// instável, etc.): o app Android ficaria sem backend. Erros assíncronos não
// tratados são registrados e o processo continua servindo — o endpoint afetado
// responde com erro, os demais seguem funcionando.
const fatal = (kind) => (err) => {
  console.error(`[api] ${kind} não tratado: ${err?.stack || err?.message || err}`);
};
process.on('unhandledRejection', fatal('promessa rejeitada'));
process.on('uncaughtException', fatal('exceção não capturada'));
