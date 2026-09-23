import { config } from '../config.js';

export function notFound(req, res) {
  res.status(404).json({ error: `Rota não encontrada: ${req.method} ${req.originalUrl}` });
}

export function errorHandler(err, req, res, _next) {
  const status = err.status || 500;
  if (config.logRequests) {
    console.error(`[error] ${req.method} ${req.originalUrl} -> ${status}: ${err.message}`);
    if (status >= 500) console.error(err.stack);
  }
  res.status(status).json({
    error: status >= 500 ? 'Erro interno do servidor' : err.message,
  });
}

export function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}