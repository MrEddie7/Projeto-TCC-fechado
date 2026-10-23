# Deploy da API na Vercel

O repositório está configurado com **um serviço** (`backend`) no `vercel.json`:
o Express responde na raiz do domínio — a API em `/v1/*`, o painel web em `/`
e a página de download em `/download/`, exatamente como roda localmente.

```
vercel.json
└── services.backend  (root: backend, framework: express)
    └── rewrites: /(.*) -> backend
```

Não há *bindings* porque o serviço `site` (Laravel, PHP) não é chamado pelo
`backend`, e o `backend` não chama o `site`.

## Variáveis de ambiente (obrigatórias)

Configure em **Vercel → Project → Settings → Environment Variables**:

| Variável | Valor |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Conteúdo **inteiro** do `backend/firebase-service-account.json`, em uma linha |
| `FIREBASE_DATABASE_URL` | `https://securitas-ce0bd-default-rtdb.firebaseio.com` |
| `FIRESTORE_ID` | `securitas` |
| `FIREBASE_API_KEY` | apiKey Web do Firebase (usada pelo login do painel) |
| `SIMULATION_ENABLED` | `false` (produção: sem rastreadores virtuais) |

Para gerar o valor de `FIREBASE_SERVICE_ACCOUNT_JSON` sem erro de aspas:

```bash
node -e "console.log(JSON.stringify(require('./backend/firebase-service-account.json')))"
```

Cole a saída (uma única linha) como valor da variável.

## Deploy

```bash
npx vercel --prod
```

Ou conecte o repositório em **Vercel → Add New → Project**.

## Observações

- **Sem listener próprio**: quando `VERCEL=1` está definido (runtime da
  plataforma), `src/server.js` não chama `app.listen()`; exporta o app como
  handler. Localmente (`api.sh`), o listener continua igual.
- **Sem sistema de arquivos**: as credenciais vêm de
  `FIREBASE_SERVICE_ACCOUNT_JSON`; o caminho de arquivo continua valendo para
  o uso local.
- **`site` (Laravel)**: é uma aplicação PHP 8.3 com Blade/Eloquent — não roda
  como serviço `vite` na Vercel. Deve ser publicada em hospedagem PHP
  (Laravel Cloud, Railway, Render) ou ficar de fora do projeto.
