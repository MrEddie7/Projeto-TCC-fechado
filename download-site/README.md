# Site de download do app (Vercel)

Página estática que serve o APK do app Securitas para instalação direta,
publicada no projeto **`securitas-download`**:

- Produção: <https://securitas-download.vercel.app>
- Projeto Vercel: `securitas-download` (`prj_cGJhnxPfFWv4c2PCaB9duZwAwdxw`)

É um site **estático** (sem framework, sem função serverless) — o APK de 25 MB é
servido como asset de CDN, mais rápido e robusto do que rotear o arquivo pela
function da API. O QR Code da página aponta para esta URL dedicada.

## O que é versionado aqui

| Arquivo | Papel |
|---|---|
| `index.html` | Página de download (estilo + botão + QR). Referencia `qr.png` e o APK relativamente. |
| `qr.png` | QR Code que codifica `https://securitas-download.vercel.app/`. |
| `vercel.json` | Config de build estático (clean URLs, sem framework). |
| `deploy.sh` | Copia o APK da fonte e faz `vercel deploy --prod`. |
| `.gitignore` | Mantém o APK copiado e `.vercel/` fora do versionamento. |

## O APK não é versionado

O binário de 25 MB **não** entra no Git (ignorado por `*.apk`). A **fonte de
verdade** continua em [`backend/public/download/securitas-app-debug.apk`](../backend/public/download/securitas-app-debug.apk).
No momento do deploy, `deploy.sh` copia esse arquivo para cá e o publica como
asset estático — assim o site fica autocontido (mesma origem) sem inflar o repo.

## Regenerar o QR Code

Se a URL mudar, gere o `qr.png` de novo (requer `pip install qrcode pillow`):

```bash
python3 -c "import qrcode; qrcode.make('https://securitas-download.vercel.app/', box_size=10, border=2).save('qr.png')"
```

## Deploy

```bash
VERCEL_TOKEN=<token> ./deploy.sh
```

O script falha se o APK fonte não existir, e espera que o projeto
`securitas-download` já exista na Vercel. Se ele for apagado, recrie-o com o
mesmo nome (framework: nenhum) e o mesmo `projectId` em `.vercel/project.json`
antes de deployar.
