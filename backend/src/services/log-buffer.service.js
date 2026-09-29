// Buffer circular em memória com as últimas requisições atendidas.
//
// Existe para a página de diagnóstico (/debug) poder mostrar o que está
// acontecendo na API sem depender dos arquivos de log: o buffer vive no
// mesmo processo, então funciona igual independente de como a API foi
// iniciada (npm start, api.sh, api.ps1, PM2...).
//
// Cada entrada recebe um `seq` crescente. O cliente guarda o último seq
// que viu e pede apenas o que apareceu depois, o que evita reenviar o
// histórico inteiro a cada atualização.

const MAX_ENTRIES = 500;

const entries = [];
let seq = 0;

// Assinantes do stream de logs (respostas SSE abertas).
const subscribers = new Set();

/**
 * Registra uma requisição atendida.
 * @param {object} entry
 * @param {string} entry.method
 * @param {string} entry.path
 * @param {number} entry.status
 * @param {number} [entry.durationMs]
 * @param {string} [entry.level] 'info' | 'warn' | 'error'
 */
export function recordRequest(entry) {
  const item = {
    seq: ++seq,
    at: Date.now(),
    level: entry.level || (entry.status >= 500 ? 'error' : entry.status >= 400 ? 'warn' : 'info'),
    method: entry.method,
    path: entry.path,
    status: entry.status,
    durationMs: entry.durationMs ?? null,
  };

  entries.push(item);
  // Ring buffer: descarta as entradas mais antigas ao estourar o limite.
  if (entries.length > MAX_ENTRIES) entries.splice(0, entries.length - MAX_ENTRIES);

  const payload = `data: ${JSON.stringify(item)}\n\n`;
  for (const res of subscribers) {
    try {
      res.write(payload);
    } catch {
      subscribers.delete(res);
    }
  }

  return item;
}

/**
 * Entradas registradas, opcionalmente apenas as posteriores a `since`.
 * @param {object} [opts]
 * @param {number} [opts.since] devolve só o que tem seq maior que este
 * @param {number} [opts.limit]
 */
export function listLogs({ since = 0, limit = 200 } = {}) {
  const from = Math.max(0, Math.min(limit, MAX_ENTRIES));
  const matched = since > 0 ? entries.filter((e) => e.seq > since) : entries;
  return {
    entries: matched.slice(-from),
    lastSeq: seq,
    // Informa quando o histórico já foi descartado pelo ring buffer, para
    // o cliente saber que pode ter perdido linhas.
    truncated: entries.length >= MAX_ENTRIES,
  };
}

export function subscribeLogs(res) {
  subscribers.add(res);
  res.write('retry: 3000\n\n');

  const interval = setInterval(() => {
    try {
      res.write(': ping\n\n');
    } catch {
      clearInterval(interval);
    }
  }, 25000);

  res.on('close', () => {
    clearInterval(interval);
    subscribers.delete(res);
  });
}

export function logStats() {
  return { total: entries.length, lastSeq: seq, max: MAX_ENTRIES, subscribers: subscribers.size };
}
