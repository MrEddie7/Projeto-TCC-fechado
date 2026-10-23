// Entry serverless do Vercel (catch-all: atende /, /health, /v1/* etc.).
// Reaproveita o app Express montado em src/server.js, que já faz `export
// default app` e só chama app.listen() fora da Vercel (guard em VERCEL=1).
import app from '../src/server.js';

export default app;
