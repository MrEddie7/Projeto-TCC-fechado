// Detecta o estouro de cota do Firestore (plano gratuito do projeto).
// Quando dispara, a API não pode morrer: precisa cair no fallback (RTDB).
export function isQuotaError(err) {
  if (!err) return false;
  const code = err.code ?? err.status;
  // 8 = RESOURCE_EXHAUSTED (gRPC), 429 = HTTP
  if (code === 8 || code === 429 || code === 'resource-exhausted') return true;
  const msg = String(err.message || err.details || '');
  return /quota exceeded|resource[_ ]exhausted|too many requests/i.test(msg);
}

export const QUOTA_MESSAGE =
  'Cota do Firestore esgotada (plano gratuito). A API segue funcionando com o ' +
  'RTDB; as leituras do Firestore voltam após a redefinição diária da cota.';
